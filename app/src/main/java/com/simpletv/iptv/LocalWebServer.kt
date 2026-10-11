package com.simpletv.iptv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * یک وب‌سرور خیلی کوچک روی شبکه‌ی خانه.
 * کاربر با گوشی QR را اسکن می‌کند، لینک لیست را در یک صفحه‌ی ساده وارد می‌کند و لیست به تلویزیون می‌رسد.
 */
class LocalWebServer(
    private val port: Int,
    private val onSubmit: suspend (Submission) -> Result<Int>,
    private val onSearch: (String) -> Unit,
) {
    /** آنچه کاربر در صفحه‌ی گوشی فرستاده: لینک، متن لیست یا اطلاعات Xtream. */
    class Submission(val url: String?, val text: String?, val xtream: XtreamLogin?)

    private var server: ServerSocket? = null

    /**
     * سرور را روی پورت [port] شروع می‌کند. هر اتصال در یک coroutine جدا پردازش می‌شود.
     * اگر پورت مشغول باشد یا سرور بسته شود، بی‌صدا تمام می‌شود.
     */
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val s = ServerSocket(port)
                server = s
                while (isActive) {
                    val client = s.accept()
                    launch { handle(client) }
                }
            } catch (_: Exception) {
                // سرور بسته شد یا پورت مشغول است
            }
        }
    }

    /** سرور را می‌بندد (هنگام بسته شدن اپ). */
    fun stop() {
        runCatching { server?.close() }
    }

    /**
     * یک درخواست HTTP را می‌خواند و جواب می‌دهد.
     *
     * پروتکل عمداً ساده است: GET همیشه صفحه‌ی اصلی را می‌دهد و POST فرم را پردازش می‌کند.
     * یک POST می‌تواند جستجو (`q`)، لینک/متن لیست (`url`، `text`) یا اطلاعات Xtream
     * (`xserver`، `xuser`، `xpass`) باشد.
     */
    private suspend fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 30_000
            val input = sock.getInputStream().buffered()
            val output = sock.getOutputStream()
            try {
                val requestLine = readLine(input) ?: return
                var contentLength = 0
                while (true) {
                    val header = readLine(input) ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("content-length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                    }
                }
                if (!requestLine.startsWith("POST", ignoreCase = true)) {
                    respond(output, PAGE)
                    return
                }
                if (contentLength !in 1..MAX_BODY) {
                    respond(output, resultPage(false, "حجم درخواست درست نیست."))
                    return
                }
                val body = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(body, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                val form = String(body, 0, read, Charsets.UTF_8).split('&').associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                        URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                }
                val query = form["q"]?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    onSearch(query)
                    respond(output, resultPage(true, "نتیجه‌ی جستجوی «${query.replace("<", "&lt;")}» روی تلویزیون نمایش داده شد."))
                    return
                }
                val url = form["url"]?.trim()?.takeIf { it.isNotEmpty() }
                val text = form["text"]?.takeIf { it.isNotBlank() }
                val server = form["xserver"]?.trim().orEmpty()
                val user = form["xuser"]?.trim().orEmpty()
                val pass = form["xpass"].orEmpty()
                val anyXtream = server.isNotEmpty() || user.isNotEmpty() || pass.isNotEmpty()
                if (anyXtream && (server.isEmpty() || user.isEmpty() || pass.isEmpty())) {
                    respond(output, resultPage(false, "برای Xtream هر سه مورد (آدرس سرور، نام کاربری و رمز) لازم است."))
                    return
                }
                if (!anyXtream && url == null && text == null) {
                    respond(output, resultPage(false, "لینک یا متن لیست را وارد کنید."))
                    return
                }
                val login = if (anyXtream) XtreamLogin(server, user, pass) else null
                onSubmit(Submission(url, text, login)).fold(
                    onSuccess = { respond(output, resultPage(true, "لیست اضافه شد ($it شبکه). به تلویزیون نگاه کنید؛ می‌توانید لیست دیگری هم اضافه کنید.")) },
                    onFailure = { respond(output, resultPage(false, it.message ?: "خطا در خواندن لیست.")) },
                )
            } catch (_: Exception) {
                // اتصال قطع شد
            }
        }
    }

    /** یک صفحه‌ی HTML با کد ۲۰۰ و هدرهای لازم می‌فرستد. */
    private fun respond(out: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    /** یک خط (تا `
`) را بایت‌به‌بایت می‌خواند؛ `` حذف می‌شود. اگر جریان تمام شده باشد null برمی‌گرداند. */
    private fun readLine(input: InputStream): String? {
        val sb = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.size() == 0) null else sb.toString("ISO-8859-1")
            if (b == 10) break
            if (b != 13) sb.write(b)
        }
        return sb.toString("ISO-8859-1")
    }

    companion object {
        /** سقف حجم فرم؛ ۳۰ مگابایت برای چسباندن متن یک لیست بزرگ کافی است. */
        const val MAX_BODY = 30 * 1024 * 1024

        /**
         * آدرس IP تلویزیون در شبکه‌ی خانه (مثلاً `192.168.1.20`) یا null اگر به شبکه وصل نیست.
         * فقط آدرس‌های خصوصی IPv4 (`192.168.x.x`، `10.x.x.x`، …) حساب می‌شوند.
         */
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        /** استایل مشترک صفحه‌ها: تیره، راست‌به‌چپ و با دکمه‌ها و کادرهای بزرگ برای لمس با انگشت. */
        private const val STYLE =
            "body{font-family:sans-serif;margin:0;padding:20px;background:#0f172a;color:#fff;direction:rtl;text-align:right}" +
                "h1{font-size:22px}label{display:block;margin:18px 0 6px;font-size:16px}" +
                "input,textarea{width:100%;box-sizing:border-box;padding:12px;font-size:16px;border-radius:8px;border:0}" +
                "textarea{height:120px}button{margin-top:20px;width:100%;padding:16px;font-size:18px;border:0;" +
                "border-radius:8px;background:#2563eb;color:#fff}.ok{color:#4ade80;font-size:20px}" +
                ".err{color:#f87171;font-size:20px}small{color:#94a3b8}"

        /** ابتدای هر صفحه‌ی HTML (تا بازشدن `<body>`). */
        private const val HEAD = "<!doctype html><html lang=\"fa\" dir=\"rtl\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>OpenStream</title>" +
            "<style>$STYLE</style></head><body>"

        /** صفحه‌ی اصلی: کادر جستجو، فرم لینک/متن لیست و فرم ورود Xtream. */
        private const val PAGE = HEAD +
            "<h1>جستجوی شبکه</h1>" +
            "<form method=\"post\" action=\"/\">" +
            "<input name=\"q\" placeholder=\"اسم شبکه را بنویسید\" autocomplete=\"off\">" +
            "<button type=\"submit\">نمایش نتیجه روی تلویزیون</button></form>" +
            "<h1 style=\"margin-top:40px\">افزودن لیست شبکه‌ها به تلویزیون</h1>" +
            "<form method=\"post\" action=\"/\">" +
            "<label>لینک لیست (M3U)</label>" +
            "<input name=\"url\" type=\"url\" placeholder=\"http://...\" autocomplete=\"off\">" +
            "<label>یا متن لیست را اینجا بچسبانید</label>" +
            "<textarea name=\"text\"></textarea>" +
            "<button type=\"submit\">ارسال به تلویزیون</button></form>" +
            "<h1 style=\"margin-top:40px\">اشتراک Xtream Codes دارید؟</h1>" +
            "<form method=\"post\" action=\"/\">" +
            "<label>آدرس سرور</label>" +
            "<input name=\"xserver\" placeholder=\"http://server.com:8080\" autocomplete=\"off\">" +
            "<label>نام کاربری</label><input name=\"xuser\" autocomplete=\"off\">" +
            "<label>رمز عبور</label><input name=\"xpass\" type=\"password\" autocomplete=\"off\">" +
            "<button type=\"submit\">ورود و دریافت شبکه‌ها</button></form>" +
            "<p><small>این صفحه فقط روی اینترنت خانه‌ی شما کار می‌کند.</small></p></body></html>"

        /** صفحه‌ی نتیجه بعد از ارسال فرم؛ [ok] رنگ سبز یا قرمز پیام را تعیین می‌کند. */
        private fun resultPage(ok: Boolean, message: String): String =
            HEAD + "<p class=\"${if (ok) "ok" else "err"}\">${if (ok) "✅" else "❌"} " +
                message.replace("<", "&lt;") +
                "</p><p><a href=\"/\" style=\"color:#93c5fd\">بازگشت</a></p></body></html>"
    }
}
