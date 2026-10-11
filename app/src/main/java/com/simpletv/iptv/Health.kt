package com.simpletv.iptv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * وضعیت سلامت یک شبکه.
 *
 * - [UNKNOWN] هنوز بررسی نشده
 * - [CHECKING] بررسی در جریان است
 * - [OK] سرور جواب داد و ویدیو دریافت شد (یا پلیر واقعاً آن را پخش کرد)
 * - [FAIL] جواب نداد، آدرس خراب است یا پلیر نتوانست پخش کند
 */
enum class Health { UNKNOWN, CHECKING, OK, FAIL }

/**
 * نتیجه‌ی آخرین بررسی یک شبکه.
 *
 * @property at زمان بررسی (میلی‌ثانیه)؛ نتیجه‌های قدیمی‌تر از ۱۰ دقیقه دوباره بررسی می‌شوند
 */
data class HealthEntry(val status: Health, val at: Long)

/**
 * بررسی سبک این‌که یک شبکه واقعاً جواب می‌دهد یا نه، بدون پخش کردن آن.
 * برای لینک‌های m3u8 اول خود لیست و بعد اولین تکه‌ی ویدیو را می‌گیرد.
 */
object StreamProbe {
    private const val TIMEOUT_MS = 4_000
    private const val MAX_DEPTH = 3

    /** اگر کاربر صفحه را عوض کند و کار لغو شود، اتصال باز فوراً بسته می‌شود تا منابع هدر نروند. */
    suspend fun isAlive(url: String): Boolean = coroutineScope {
        val current = AtomicReference<HttpURLConnection?>(null)
        val work = async(Dispatchers.IO) { runCatching { probe(url, 0, current) }.getOrDefault(false) }
        try {
            work.await()
        } catch (e: CancellationException) {
            current.get()?.disconnect()
            throw e
        }
    }

    /**
     * زنجیره‌ی HLS را دنبال می‌کند: لیست اصلی ← لیست کیفیت ← اولین تکه‌ی ویدیو.
     *
     * @param depth عمق فعلی زنجیره؛ از [MAX_DEPTH] بیشتر نمی‌شود تا لیست‌های حلقه‌ای گیر نکنند
     * @param current آخرین اتصال باز، تا در صورت لغو شدن کار از بیرون بسته شود
     * @return true اگر شبکه به‌نظر سالم است
     */
    private fun probe(url: String, depth: Int, current: AtomicReference<HttpURLConnection?>): Boolean {
        if (depth > MAX_DEPTH) return true
        val conn = open(url, current) ?: return false
        try {
            if (conn.responseCode !in 200..299) return false
            val head = conn.inputStream.use { readUpTo(it, 32 * 1024) }
            if (head.isEmpty()) return false
            val isPlaylist = head.size >= 7 && String(head, 0, 7, Charsets.ISO_8859_1) == "#EXTM3U"
            if (!isPlaylist) {
                // صفحه‌ی خطای HTML با کد ۲۰۰ ویدیو نیست
                val start = String(head, 0, minOf(head.size, 64), Charsets.ISO_8859_1).trimStart().lowercase()
                if (start.startsWith("<")) {
                    // فایل DASH (.mpd) یک XML است و سالم حساب می‌شود؛ بقیه‌ی صفحه‌های HTML خراب‌اند.
                    return String(head, 0, minOf(head.size, 2048), Charsets.ISO_8859_1).contains("<MPD", ignoreCase = true)
                }
                // یک تکه‌ی ویدیو دست‌کم باید چند صد بایت باشد (یک بسته‌ی TS = ۱۸۸ بایت)
                return depth == 0 || head.size >= 188
            }
            val next = String(head, Charsets.UTF_8).lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return false
            return probe(URL(URL(url), next).toString(), depth + 1, current)
        } finally {
            conn.disconnect()
        }
    }

    /** باز کردن اتصال و دنبال کردن ریدایرکت‌ها (حتی بین http و https). */
    private fun open(start: String, current: AtomicReference<HttpURLConnection?>): HttpURLConnection? {
        var address = start
        repeat(5) {
            val conn = URL(address).openConnection() as HttpURLConnection
            current.set(conn)
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", Net.USER_AGENT)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null) return null
                address = URL(URL(address), location).toString()
            } else {
                return conn
            }
        }
        return null
    }

    /** حداکثر [limit] بایت از ابتدای جریان را می‌خواند (برای بررسی سرآغاز فایل، نه دانلود کامل). */
    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val buf = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val n = input.read(buf, total, limit - total)
            if (n < 0) break
            total += n
        }
        return buf.copyOf(total)
    }
}
