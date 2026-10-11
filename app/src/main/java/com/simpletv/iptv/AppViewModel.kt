package com.simpletv.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * مغز اپ: همه‌ی وضعیت‌ها (لیست‌ها، شبکه‌ها، دسته‌ها، علاقه‌مندی‌ها، جستجو، سلامت شبکه‌ها) اینجاست
 * و صفحه‌های Compose فقط آن را می‌خوانند.
 *
 * وضعیت‌ها به شکل [StateFlow] هستند: داخل کلاس نسخه‌ی قابل‌تغییر (`_name`) و بیرون فقط‌خواندنی (`name`).
 * کارهای سنگین (خواندن فایل، شبکه) روی `Dispatchers.IO` انجام می‌شوند تا رابط کاربری قفل نشود.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    /** تنظیمات کوچک: علاقه‌مندی‌ها و آخرین شبکه‌ای که دیده شده. */
    private val prefs = app.getSharedPreferences("tv", 0)

    // هر لیست در یک فایل جدا ذخیره می‌شود و فهرستشان در sources.json است.
    private val dir = File(app.filesDir, "playlists")
    private val indexFile = File(dir, "sources.json")
    private val legacyFile = File(app.filesDir, "playlist.m3u")

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    /** همه‌ی شبکه‌ها از همه‌ی لیست‌ها، ادغام‌شده و بدون تکرار. شماره‌ی هر شبکه = جایش در این لیست + ۱. */
    val channels: StateFlow<List<Channel>> = _channels

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    /** لیست‌هایی که کاربر اضافه کرده است. */
    val sources: StateFlow<List<Source>> = _sources

    private val _favorites = MutableStateFlow(prefs.getStringSet("fav", emptySet())!!.toSet())
    /** آدرس شبکه‌های علاقه‌مندی؛ آدرس ثابت‌تر از شماره است چون شماره با افزودن لیست جدید عوض می‌شود. */
    val favorites: StateFlow<Set<String>> = _favorites

    private val _currentIndex = MutableStateFlow(0)
    /** شماره‌ی شبکه‌ی در حال پخش در [channels] (از صفر). */
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _showSetup = MutableStateFlow(false)
    /** آیا کاربر صفحه‌ی افزودن لیست را باز کرده است؟ (وقتی هیچ لیستی نیست، این صفحه خودکار دیده می‌شود.) */
    val showSetup: StateFlow<Boolean> = _showSetup

    private val _serverUrl = MutableStateFlow<String?>(null)
    /** آدرسی که در QR گذاشته می‌شود (مثلاً `http://192.168.1.20:8080`)، یا null اگر تلویزیون به شبکه وصل نیست. */
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _message = MutableStateFlow<String?>(null)
    /** آخرین پیام خطای افزودن لیست، برای نشان دادن روی تلویزیون. */
    val message: StateFlow<String?> = _message

    private val _groups = MutableStateFlow<List<ChannelGroup>>(emptyList())
    /** دسته‌ها (همه، هر لیست، و دسته‌های داخل لیست‌ها). یک‌بار موقع بارگذاری ساخته می‌شوند. */
    val groups: StateFlow<List<ChannelGroup>> = _groups

    private val _loaded = MutableStateFlow(false)
    /** آیا خواندن لیست‌های ذخیره‌شده تمام شده؟ تا آن موقع صفحه‌ی سیاه می‌ماند تا صفحه‌ی QR اشتباهی نپرد. */
    val loaded: StateFlow<Boolean> = _loaded

    private val _searchQuery = MutableStateFlow("")
    /** عبارتی که کاربر جستجو کرده (از تلویزیون یا گوشی). */
    val searchQuery: StateFlow<String> = _searchQuery

    /** هر بار که این عدد زیاد شود، منوی شبکه‌ها باز می‌شود (برای جستجو از روی گوشی). */
    private val _openBrowser = MutableStateFlow(0)
    val openBrowser: StateFlow<Int> = _openBrowser

    private val _health = MutableStateFlow<Map<String, HealthEntry>>(emptyMap())
    /** نتیجه‌ی بررسی سلامت به تفکیک آدرس شبکه؛ فقط شبکه‌هایی که بررسی شده‌اند در آن هستند. */
    val health: StateFlow<Map<String, HealthEntry>> = _health

    /** سقف بررسی همزمان؛ ۴ تا هم برای سرعت کافی است و هم تلویزیون ضعیف را خفه نمی‌کند. */
    private val probeSlots = Semaphore(4)

    /** وب‌سرور صفحه‌ی گوشی؛ لیست‌ها و جستجوها از آن به اینجا می‌رسند. */
    private val server = LocalWebServer(
        port = PORT,
        onSubmit = { addPlaylist(it) },
        onSearch = { searchFromPhone(it) },
    )

    init {
        // خواندن لیست‌ها و پیدا کردن IP کار سنگین است؛ روی ترد اصلی انجام نمی‌شود تا اپ سریع باز شود.
        viewModelScope.launch(Dispatchers.IO) {
            dir.mkdirs()
            migrateLegacy()
            val (sources, all) = loadAll()
            applyLoaded(sources, all, keepUrl = prefs.getString("last", null))
            _loaded.value = true
            refreshAddress()
        }
        server.start(viewModelScope)
    }

    // ---- لیست‌ها ----

    /** نسخه‌های قبلی اپ فقط یک لیست داشتند؛ همان به‌عنوان اولین لیست نگه داشته می‌شود. */
    private fun migrateLegacy() {
        if (legacyFile.exists() && !indexFile.exists()) {
            legacyFile.copyTo(File(dir, "legacy.m3u"), overwrite = true)
            writeIndex(listOf(Source("legacy", "لیست قبلی", 0)))
            legacyFile.delete()
        }
    }

    /** فهرست لیست‌ها را از `sources.json` می‌خواند؛ اگر فایل نبود یا خراب بود، فهرست خالی می‌دهد. */
    private fun readIndex(): List<Source> = runCatching {
        val arr = JSONArray(indexFile.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Source(o.getString("id"), o.getString("name"), o.optInt("count"))
        }
    }.getOrDefault(emptyList())

    /** فهرست لیست‌ها را در `sources.json` ذخیره می‌کند. */
    private fun writeIndex(list: List<Source>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("count", it.count)) }
        indexFile.writeText(arr.toString())
    }

    /** همه‌ی لیست‌ها را می‌خواند و ادغام می‌کند؛ شبکه‌ی تکراری (با لینک یکسان) فقط یک‌بار می‌آید. */
    private fun loadAll(): Pair<List<Source>, List<Channel>> {
        val seen = HashSet<String>()
        val all = ArrayList<Channel>()
        val sources = ArrayList<Source>()
        for (entry in readIndex()) {
            val file = File(dir, "${entry.id}.m3u")
            if (!file.exists()) continue
            val list = runCatching { file.bufferedReader().use { M3uParser.parse(it, entry.name) } }
                .getOrDefault(emptyList())
            sources.add(entry.copy(count = list.size))
            for (c in list) if (seen.add(c.url)) all.add(c)
        }
        return sources to all
    }

    /**
     * نتیجه‌ی بارگذاری را در وضعیت‌ها می‌گذارد.
     *
     * @param keepUrl آدرس شبکه‌ای که باید همچنان انتخاب بماند (مثلاً شبکه‌ی در حال پخش وقتی لیست جدید اضافه می‌شود)
     */
    private fun applyLoaded(sources: List<Source>, list: List<Channel>, keepUrl: String?) {
        _sources.value = sources
        _currentIndex.value = list.indexOfFirst { it.url == keepUrl }.coerceAtLeast(0)
        publish(list, sources)
    }

    // دسته‌بندی‌ها یک‌بار موقع ورود لیست ساخته می‌شوند، نه هر بار که منو باز می‌شود.
    private fun publish(list: List<Channel>, sources: List<Source>) {
        val byGroup = LinkedHashMap<String, MutableList<Int>>()
        val bySource = LinkedHashMap<String, MutableList<Int>>()
        list.forEachIndexed { i, c ->
            byGroup.getOrPut(c.group) { ArrayList() }.add(i)
            bySource.getOrPut(c.source) { ArrayList() }.add(i)
        }
        val result = ArrayList<ChannelGroup>(byGroup.size + bySource.size + 1)
        result.add(ChannelGroup(ALL_NAME, IntArray(list.size) { it }))
        // وقتی چند لیست داریم، هر لیست هم یک دسته می‌شود
        if (sources.size > 1) bySource.forEach { (name, ids) -> result.add(ChannelGroup("📁 $name", ids.toIntArray())) }
        byGroup.forEach { (name, ids) -> result.add(ChannelGroup(name, ids.toIntArray())) }
        _groups.value = result
        _channels.value = list
    }

    /** آدرس فعلی تلویزیون در شبکه را دوباره می‌خواند (ممکن است بعد از وصل شدن وای‌فای عوض شده باشد). */
    fun refreshAddress() {
        _serverUrl.value = LocalWebServer.localIp()?.let { "http://$it:$PORT" }
    }

    /** صفحه‌ی افزودن لیست و QR را باز می‌کند. */
    fun openSetup() {
        refreshAddress()
        _message.value = null
        _showSetup.value = true
    }

    /** صفحه‌ی افزودن لیست را می‌بندد و به پخش برمی‌گردد. */
    fun closeSetup() {
        _showSetup.value = false
    }

    /**
     * یک لیست جدید (لینک، متن یا Xtream) را اضافه می‌کند.
     *
     * لیست اول در فایل موقت `incoming.tmp` ساخته و بررسی می‌شود؛ فقط اگر دست‌کم یک شبکه داشت
     * به فایل نهایی منتقل می‌شود، پس لیست خراب هیچ‌وقت لیست‌های سالم قبلی را خراب نمی‌کند.
     * اگر لیستی با همین اسم از قبل بود، به‌جای تکرار، به‌روز می‌شود.
     *
     * @return تعداد شبکه‌های لیست جدید، یا خطا با پیام فارسی ساده
     */
    private suspend fun addPlaylist(sub: LocalWebServer.Submission): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "incoming.tmp")
            val name: String
            when {
                sub.xtream != null -> {
                    XtreamClient.writePlaylist(sub.xtream, tmp)
                    name = "Xtream: ${hostOf(sub.xtream.server)}"
                }
                sub.url != null -> {
                    download(sub.url, tmp)
                    name = nameOfUrl(sub.url)
                }
                else -> {
                    tmp.writeText(sub.text!!)
                    name = "لیست متنی ${readIndex().count { it.name.startsWith("لیست متنی") } + 1}"
                }
            }
            val list = tmp.bufferedReader().use { M3uParser.parse(it, name) }
            if (list.isEmpty()) {
                tmp.delete()
                error("در این لیست شبکه‌ای پیدا نشد. لینک را بررسی کنید.")
            }

            // اگر همین لیست قبلاً اضافه شده بود، به‌جای تکرار، به‌روز می‌شود.
            val index = readIndex()
            val id = index.firstOrNull { it.name == name }?.id ?: System.currentTimeMillis().toString()
            tmp.copyTo(File(dir, "$id.m3u"), overwrite = true)
            tmp.delete()
            writeIndex(index.filterNot { it.id == id } + Source(id, name, list.size))

            val keep = _channels.value.getOrNull(_currentIndex.value)?.url
            val (sources, all) = loadAll()
            applyLoaded(sources, all, keepUrl = keep)
            _showSetup.value = false
            _message.value = null
            list.size
        }.onFailure { _message.value = it.message }
    }

    /** لیستی را با شناسه‌اش حذف می‌کند. شبکه‌ی در حال پخش (اگر از لیست دیگری باشد) انتخاب می‌ماند. */
    fun removeSource(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(dir, "$id.m3u").delete()
            writeIndex(readIndex().filterNot { it.id == id })
            val keep = _channels.value.getOrNull(_currentIndex.value)?.url
            val (sources, all) = loadAll()
            applyLoaded(sources, all, keepUrl = keep)
        }
    }

    /** اسم میزبان یک آدرس سرور، برای ساختن اسم لیست Xtream. */
    private fun hostOf(server: String): String {
        val withScheme = if (server.contains("://")) server else "http://$server"
        return runCatching { URL(withScheme).host }.getOrDefault(server)
    }

    /** اسم خوانا برای لیست اینترنتی: «میزبان/اسم‌فایل»، مثلاً `example.com/list.m3u`. */
    private fun nameOfUrl(address: String): String {
        val url = runCatching { URL(address) }.getOrNull() ?: return "لیست اینترنتی"
        val file = url.path.substringAfterLast('/').takeIf { it.isNotBlank() }?.take(28)
        return if (file != null) "${url.host}/$file" else url.host
    }

    /** لیست M3U را از [address] دانلود می‌کند و در [target] می‌نویسد. */
    private fun download(address: String, target: File) {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", Net.USER_AGENT)
        if (conn.responseCode !in 200..299) error("دریافت لیست ممکن نشد (کد ${conn.responseCode}).")
        conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    // ---- جستجو ----

    /** عبارت جستجو را عوض می‌کند (با هر حرفی که روی تلویزیون تایپ می‌شود). */
    fun setSearch(query: String) {
        _searchQuery.value = query
    }

    /** جستجو از روی گوشی: عبارت را می‌گذارد و منوی شبکه‌ها را روی تلویزیون باز می‌کند. */
    private fun searchFromPhone(query: String) {
        _searchQuery.value = query
        _showSetup.value = false
        _openBrowser.update { it + 1 }
    }

    // ---- انتخاب شبکه ----

    /**
     * شبکه‌ی شماره‌ی [index] را انتخاب می‌کند. اگر از انتها بگذرد از اول برمی‌گردد (چرخشی)
     * و شبکه برای باز شدن بعدی اپ ذخیره می‌شود.
     */
    fun select(index: Int) {
        val list = _channels.value
        if (list.isEmpty()) return
        val i = ((index % list.size) + list.size) % list.size
        _currentIndex.value = i
        prefs.edit().putString("last", list[i].url).apply()
    }

    // بالا و پایین ریموت: شبکه‌هایی که می‌دانیم خراب‌اند رد می‌شوند.
    /** شبکه‌ی بعدی (دکمه‌ی بالا)؛ شبکه‌های خراب رد می‌شوند. */
    fun next() = step(1)

    /** شبکه‌ی قبلی (دکمه‌ی پایین)؛ شبکه‌های خراب رد می‌شوند. */
    fun prev() = step(-1)

    /**
     * یک قدم در جهت [direction] (۱ یا ۱-) به اولین شبکه‌ای که خراب نیست می‌رود.
     * اگر همه‌ی شبکه‌ها خراب علامت خورده باشند، همان قدم ساده انجام می‌شود تا کاربر گیر نکند.
     */
    private fun step(direction: Int) {
        val list = _channels.value
        if (list.isEmpty()) return
        val health = _health.value
        var i = _currentIndex.value
        repeat(list.size) {
            i = ((i + direction) % list.size + list.size) % list.size
            if (health[list[i].url]?.status != Health.FAIL) {
                select(i)
                return
            }
        }
        select(_currentIndex.value + direction)
    }

    /** رفتن به شبکه با شماره‌ی نمایش‌داده‌شده (از ۱)؛ شماره‌ی نامعتبر نادیده گرفته می‌شود. */
    fun selectNumber(number: Int) {
        if (number in 1.._channels.value.size) select(number - 1)
    }

    /** شبکه را به علاقه‌مندی‌ها اضافه می‌کند یا اگر هست، حذف می‌کند. */
    fun toggleFavorite(url: String) {
        val set = _favorites.value.toMutableSet()
        if (!set.add(url)) set.remove(url)
        _favorites.value = set
        prefs.edit().putStringSet("fav", set).apply()
    }

    // ---- بررسی سلامت شبکه‌ها ----

    /** وضعیت چند شبکه را یک‌جا و با زمان فعلی ثبت می‌کند. */
    private fun setHealth(urls: Collection<String>, status: Health) {
        val entry = HealthEntry(status, System.currentTimeMillis())
        _health.update { map -> map + urls.associateWith { entry } }
    }

    /** نتیجه‌ی واقعی پخش (از خود پلیر) هم در همین وضعیت ثبت می‌شود. */
    fun reportPlayback(url: String, ok: Boolean) = setHealth(listOf(url), if (ok) Health.OK else Health.FAIL)

    /**
     * شبکه‌های یک صفحه را همزمان (با سقف ۴ تا) بررسی می‌کند.
     * اگر کاربر صفحه را عوض کند، این تابع لغو می‌شود و بررسی‌های نیمه‌کاره به «بررسی نشده» برمی‌گردند.
     */
    suspend fun checkChannels(urls: List<String>, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val todo = urls.distinct().filter { url ->
            val e = _health.value[url]
            force || e == null || e.status == Health.UNKNOWN || now - e.at > RECHECK_MS
        }
        if (todo.isEmpty()) return
        setHealth(todo, Health.CHECKING)
        try {
            coroutineScope {
                todo.map { url ->
                    async {
                        val ok = probeSlots.withPermit {
                            withTimeoutOrNull(PROBE_TIMEOUT_MS) { StreamProbe.isAlive(url) } ?: false
                        }
                        setHealth(listOf(url), if (ok) Health.OK else Health.FAIL)
                    }
                }.awaitAll()
            }
        } finally {
            _health.update { map ->
                map.mapValues { (url, e) ->
                    if (url in todo && e.status == Health.CHECKING) HealthEntry(Health.UNKNOWN, 0) else e
                }
            }
        }
    }

    /** وقتی ViewModel از بین می‌رود، پورت وب‌سرور آزاد می‌شود. */
    override fun onCleared() {
        server.stop()
    }

    companion object {
        const val PORT = 8080
        const val ALL_NAME = "همه شبکه‌ها"
        private const val RECHECK_MS = 10 * 60 * 1000L
        private const val PROBE_TIMEOUT_MS = 15_000L
    }
}
