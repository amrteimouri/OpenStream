package com.simpletv.iptv

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** اطلاعات ورود به یک سرویس Xtream Codes. */
class XtreamLogin(val server: String, val user: String, val pass: String)

/**
 * خواندن شبکه‌های زنده از API استاندارد Xtream Codes و تبدیل آن به یک فایل M3U ساده،
 * تا بقیه‌ی اپ دقیقاً مثل لیست‌های معمولی با آن کار کند.
 */
object XtreamClient {
    /**
     * با اطلاعات [login] وارد سرور می‌شود و شبکه‌های زنده را در فایل M3U [target] می‌نویسد.
     *
     * مراحل: ۱) اعتبارسنجی نام کاربری با `player_api.php`، ۲) خواندن دسته‌ها، ۳) خواندن شبکه‌ها،
     * ۴) نوشتن خط‌های `#EXTINF` و آدرس `/live/<user>/<pass>/<id>.<ext>`.
     *
     * @throws IllegalStateException با پیام فارسی ساده اگر آدرس یا رمز غلط باشد، اشتراک فعال نباشد
     *   یا سرور از نوع Xtream نباشد
     */
    fun writePlaylist(login: XtreamLogin, target: File) {
        val base = normalize(login.server)
        val query = "username=${enc(login.user)}&password=${enc(login.pass)}"
        val api = "$base/player_api.php?$query"

        val info = try {
            JSONObject(fetch(api))
        } catch (_: JSONException) {
            error("جواب این آدرس از نوع Xtream نیست. آدرس سرور را بررسی کنید.")
        }
        val userInfo = info.optJSONObject("user_info") ?: error("نام کاربری یا رمز عبور درست نیست.")
        if (userInfo.opt("auth")?.toString() != "1") error("نام کاربری یا رمز عبور درست نیست.")
        val status = userInfo.optString("status")
        if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) {
            error("اشتراک شما فعال نیست ($status).")
        }

        // اگر سرور m3u8 را پشتیبانی می‌کند همان، وگرنه ts
        val formats = userInfo.optJSONArray("allowed_output_formats")
        val extension = if (formats == null || contains(formats, "m3u8") || formats.length() == 0) "m3u8" else "ts"

        val categories = HashMap<String, String>()
        runCatching {
            val arr = JSONArray(fetch("$api&action=get_live_categories"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                categories[o.optString("category_id")] = o.optString("category_name")
            }
        }

        val streams = try {
            JSONArray(fetch("$api&action=get_live_streams"))
        } catch (_: JSONException) {
            error("لیست شبکه‌ها از سرور خوانده نشد.")
        }
        if (streams.length() == 0) error("این اشتراک شبکه‌ی زنده‌ای ندارد.")

        target.bufferedWriter().use { w ->
            w.write("#EXTM3U\n")
            for (i in 0 until streams.length()) {
                val o = streams.getJSONObject(i)
                val id = o.optString("stream_id")
                if (id.isBlank()) continue
                val name = clean(o.optString("name")).ifBlank { id }
                val logo = clean(o.optString("stream_icon"))
                val group = categories[o.optString("category_id")]?.let(::clean)?.takeIf { it.isNotBlank() }
                    ?: M3uParser.DEFAULT_GROUP
                w.write("#EXTINF:-1 tvg-logo=\"$logo\" group-title=\"$group\",$name\n")
                w.write("$base/live/${enc(login.user)}/${enc(login.pass)}/$id.$extension\n")
            }
        }
    }

    /** از آدرسی که کاربر می‌زند فقط «پروتکل + سرور + پورت» می‌ماند (مثلاً /c یا /get.php حذف می‌شود). */
    private fun normalize(raw: String): String {
        val withScheme = if (raw.contains("://")) raw.trim() else "http://${raw.trim()}"
        val url = runCatching { URL(withScheme) }.getOrNull() ?: error("آدرس سرور درست نیست.")
        val port = if (url.port > 0) ":${url.port}" else ""
        return "${url.protocol}://${url.host}$port"
    }

    /** متن جواب یک آدرس را می‌گیرد (برای JSON های API)؛ با کد غیر ۲xx خطا می‌دهد. */
    private fun fetch(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", Net.USER_AGENT)
        if (conn.responseCode !in 200..299) error("سرور جواب نداد (کد ${conn.responseCode}).")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun contains(arr: JSONArray, value: String): Boolean =
        (0 until arr.length()).any { arr.optString(it).equals(value, ignoreCase = true) }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** نقل‌قول و خط جدید فرمت M3U را خراب می‌کنند. */
    private fun clean(s: String) = s.replace('"', '\'').replace('\n', ' ').replace('\r', ' ').trim()
}
