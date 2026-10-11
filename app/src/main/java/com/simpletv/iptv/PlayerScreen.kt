package com.simpletv.iptv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * صفحه‌ی اصلی اپ: ویدیوی تمام‌صفحه به‌همراه کنترل با ریموت.
 *
 * دکمه‌ها (وقتی منوی شبکه‌ها بسته است):
 * - بالا / پایین (و CH+/CH-): شبکه‌ی بعدی / قبلی
 * - OK، چپ، راست و منو: باز کردن لیست شبکه‌ها
 * - ۰ تا ۹: شماره‌ی شبکه؛ بعد از ۱٫۸ ثانیه بدون دکمه‌ی دیگر، به آن شبکه می‌رود
 *
 * نکته: OK هنگام «رها شدن» دکمه پردازش می‌شود، نه هنگام فشردن؛ وگرنه رها کردن دکمه روی
 * منوی تازه‌باز‌شده به‌عنوان کلیک روی اولین شبکه حساب می‌شد و منو بلافاصله بسته می‌شد.
 */
@Composable
fun PlayerScreen(vm: AppViewModel) {
    val channels by vm.channels.collectAsState()
    val index by vm.currentIndex.collectAsState()
    val channel = channels.getOrNull(index) ?: return

    var showList by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(true) }
    var typed by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(index) {
        infoVisible = true
        delay(5000)
        infoVisible = false
    }
    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) {
            delay(1800)
            typed.toIntOrNull()?.let { vm.selectNumber(it) }
            typed = ""
        }
    }
    LaunchedEffect(showList) {
        if (!showList) runCatching { focus.requestFocus() }
    }
    BackHandler(enabled = showList) { showList = false }

    // جستجو از روی گوشی: منوی شبکه‌ها خودش باز می‌شود
    val openRequest by vm.openBrowser.collectAsState()
    LaunchedEffect(openRequest) { if (openRequest > 0) showList = true }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                if (showList) return@onKeyEvent false
                val code = ev.key.nativeKeyCode
                // OK را هنگام «رها شدن» دکمه پردازش می‌کنیم؛ وگرنه رها کردنش روی منوی تازه‌باز‌شده کلیک حساب می‌شد و منو را می‌بست.
                if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                    if (ev.type == KeyEventType.KeyUp) showList = true
                    return@onKeyEvent true
                }
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { vm.next(); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.prev(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MENU -> { showList = true; true }
                    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                        if (typed.length < 4) typed += (ev.key.nativeKeyCode - KeyEvent.KEYCODE_0).toString()
                        true
                    }
                    else -> false
                }
            },
    ) {
        VideoSurface(
            url = channel.url,
            onMessage = { status = it },
            onResult = { ok -> vm.reportPlayback(channel.url, ok) },
        )

        status?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    it,
                    fontSize = 30.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.Scrim)
                        .padding(horizontal = 40.dp, vertical = 24.dp),
                )
            }
        }

        if (typed.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.TopStart) {
                Text(
                    typed,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Scrim)
                        .padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
        }

        if (infoVisible && !showList) {
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.BottomCenter) {
                InfoBar(number = index + 1, channel = channel)
            }
        }

        if (showList) {
            ChannelBrowser(vm = vm, onClose = { showList = false })
        }
    }
}

/** نوار پایین ویدیو که چند ثانیه بعد از عوض شدن شبکه دیده می‌شود: شماره، لوگو، اسم، دسته و راهنمای دکمه‌ها. */
@Composable
private fun InfoBar(number: Int, channel: Channel) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Scrim)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                number.toString(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.clip(CircleShape).background(Palette.Primary).padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Logo(channel, 72)
            Column {
                Text(channel.name, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text(channel.group, fontSize = 22.sp, color = Palette.Dim, maxLines = 1)
            }
        }
        Text(
            "▲ ▼  تغییر شبکه        OK  لیست شبکه‌ها        ۰-۹  رفتن به شماره",
            fontSize = 20.sp,
            color = Palette.Dim,
        )
    }
}

/** لوگوی شبکه؛ اگر لوگو نداشت، حرف اول اسم در یک دایره‌ی رنگی نشان داده می‌شود. */
@Composable
fun Logo(channel: Channel, size: Int) {
    if (channel.logo != null) {
        AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Palette.Primary.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                channel.name.firstOrNull()?.toString() ?: "?",
                fontSize = (size * 0.45f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/**
 * پلیر ویدیو (ExoPlayer) برای آدرس [url].
 *
 * رفتار در برابر خطا:
 * - لینک بدون پسوند `.m3u8` که پلیر نشناخت: یک‌بار به‌عنوان HLS امتحان می‌شود
 * - خطای دائمی (۴۰۱/۴۰۳/۴۰۴ یا فرمت ناسازگار): فوراً با دلیل ساده اعلام می‌شود
 * - خطای موقت: تا [MAX_RETRIES] بار، هر ۳ ثانیه دوباره امتحان می‌شود
 * - اگر [STALL_MS] بدون شروع پخش و بدون خطا بگذرد: شبکه «جواب نمی‌دهد» حساب می‌شود
 *
 * @param onMessage پیام وضعیت برای نشان دادن روی ویدیو؛ null یعنی پخش جریان دارد
 * @param onResult نتیجه‌ی واقعی پخش (true = پخش شد)؛ وضعیت سلامت شبکه را با آن اصلاح می‌کنیم
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoSurface(url: String, onMessage: (String?) -> Unit, onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message by rememberUpdatedState(onMessage)
    val result by rememberUpdatedState(onResult)
    val player = remember {
        // همان هویت (User-Agent) و قوانین ریدایرکت بررسی سلامت؛ وگرنه ممکن است «سالم» بخورد ولی پخش نشود.
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
        // بافر کوچک‌تر: شروع پخش و عوض کردن شبکه سریع‌تر می‌شود.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5_000, 20_000, 800, 1_500)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(loadControl)
            .build()
            .apply { playWhenReady = true }
    }
    val retries = remember { AtomicInteger(0) }
    val forceHls = remember { AtomicBoolean(false) }
    var trigger by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> message("در حال بارگذاری…")
                    Player.STATE_READY -> {
                        retries.set(0)
                        message(null)
                        result(true)
                    }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // لینک‌های بدون پسوند .m3u8: یک بار به‌عنوان HLS امتحان می‌کنیم.
                if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED &&
                    forceHls.compareAndSet(false, true)
                ) {
                    trigger++
                    return
                }
                val fatal = fatalReason(error)
                if (fatal != null) {
                    result(false)
                    message("$fatal\n$CHOOSE_ANOTHER")
                    return
                }
                if (retries.incrementAndGet() <= MAX_RETRIES) {
                    message("اتصال قطع شد. دوباره امتحان می‌کنیم…")
                    scope.launch {
                        delay(3000)
                        trigger++
                    }
                } else {
                    result(false)
                    message("این شبکه الان پخش نمی‌شود.\n$CHOOSE_ANOTHER")
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        retries.set(0)
        forceHls.set(false)
    }
    LaunchedEffect(url, trigger) {
        message("در حال بارگذاری…")
        // اگر کاربر پشت‌سرهم شبکه را عوض کند، فقط شبکه‌ی آخر واقعاً بارگذاری می‌شود.
        delay(250)
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (forceHls.get()) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
        // اگر مدتی بدون خطا ولی بدون شروع پخش ماند، شبکه را خراب حساب می‌کنیم.
        delay(STALL_MS)
        if (player.playbackState != Player.STATE_READY) {
            result(false)
            message("این شبکه جواب نمی‌دهد.\n$CHOOSE_ANOTHER")
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                this.player = player
            }
        },
    )
}

/** خطاهایی که تلاش دوباره فایده ندارد، با پیام ساده‌ی فارسی. اگر خطا موقتی باشد null برمی‌گرداند. */
@OptIn(androidx.media3.common.util.UnstableApi::class)
private fun fatalReason(e: PlaybackException): String? {
    val http = generateSequence(e.cause) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
    if (http != null) {
        return when (http.responseCode) {
            401, 403 -> "این شبکه برای شما بسته است (خطای ${http.responseCode}).\nشاید لینک منقضی شده یا اشتراک لازم است."
            404, 410 -> "لینک این شبکه دیگر وجود ندارد."
            else -> null
        }
    }
    return when (e.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        -> "این شبکه با تلویزیون شما سازگار نیست."
        else -> null
    }
}

/** تعداد تلاش دوباره برای خطاهای موقت پخش. */
private const val MAX_RETRIES = 5

/** اگر این‌قدر (میلی‌ثانیه) بدون شروع پخش بگذرد، شبکه خراب حساب می‌شود. */
private const val STALL_MS = 20_000L

/** جمله‌ی پایانی همه‌ی پیام‌های خطا. */
private const val CHOOSE_ANOTHER = "با دکمه‌ی بالا یا پایین شبکه‌ی دیگری را انتخاب کنید."
