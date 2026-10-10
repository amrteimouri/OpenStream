package com.simpletv.iptv

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private const val FAVORITES = "⭐ علاقه‌مندی‌ها"
private const val SEARCH = "🔍 جستجو"

/** هر صفحه ۸ شبکه دارد تا روی صفحه‌ی تلویزیون همه‌ی ردیف‌ها بدون اسکرول و خوانا جا شوند. */
private const val PAGE_SIZE = 8

/**
 * منوی شبکه‌ها: دسته‌ها در نوار کناری، شبکه‌ها عمودی در یک لیست.
 * وقتی با دکمه‌ی پایین به آخرین شبکه برسی، صفحه‌ی بعد باز می‌شود (و برعکس).
 * با باز شدن هر صفحه، شبکه‌های همان صفحه بررسی می‌شوند.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelBrowser(vm: AppViewModel, onClose: () -> Unit) {
    val channels by vm.channels.collectAsState()
    val baseGroups by vm.groups.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val current by vm.currentIndex.collectAsState()
    val health by vm.health.collectAsState()
    val query by vm.searchQuery.collectAsState()

    // نتیجه‌ی جستجو با هر بار تایپ دوباره حساب می‌شود (روی لیست‌های بزرگ هم سریع است)
    val searchResults = remember(channels, query) { searchChannels(channels, query) }
    val groups = remember(baseGroups, channels, favorites, searchResults) {
        val fav = IntArray(channels.size)
        var n = 0
        channels.forEachIndexed { i, c -> if (c.url in favorites) fav[n++] = i }
        buildList {
            add(ChannelGroup(SEARCH, searchResults))
            if (n > 0) add(ChannelGroup(FAVORITES, fav.copyOf(n)))
            addAll(baseGroups)
        }
    }
    if (groups.isEmpty()) return

    // اگر جستجویی در جریان است (مثلاً از گوشی فرستاده شده)، منو روی نتیجه‌ها باز می‌شود.
    var groupName by remember { mutableStateOf(if (query.isNotBlank()) SEARCH else AppViewModel.ALL_NAME) }
    val group = groups.firstOrNull { it.name == groupName } ?: groups.first()
    var page by remember { mutableIntStateOf(if (groupName == SEARCH) 0 else current / PAGE_SIZE) }
    val pageCount = maxOf(1, (group.indices.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val safePage = page.coerceIn(0, pageCount - 1)
    val pageIds = remember(group, safePage) {
        val from = safePage * PAGE_SIZE
        group.indices.copyOfRange(from, minOf(from + PAGE_SIZE, group.indices.size))
    }

    // بررسی سلامت شبکه‌های این صفحه؛ اگر کاربر تند بین صفحه‌ها یا دسته‌ها برود، شروع نمی‌شود/لغو می‌شود.
    LaunchedEffect(pageIds) {
        delay(500)
        vm.checkChannels(pageIds.map { channels[it].url })
    }
    var recheckTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(recheckTick) {
        if (recheckTick > 0) vm.checkChannels(pageIds.map { channels[it].url }, force = true)
    }

    // مدیریت فوکوس
    val rowFocus = remember { List(PAGE_SIZE) { FocusRequester() } }
    var pendingFocus by remember { mutableStateOf<Int?>(null) } // -1 یعنی آخرین ردیف
    var focusedPos by remember { mutableIntStateOf(0) }
    var listHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val pos = pageIds.indexOf(current).coerceAtLeast(0)
        runCatching { rowFocus[pos].requestFocus() }
    }
    LaunchedEffect(pendingFocus, safePage) {
        val target = pendingFocus ?: return@LaunchedEffect
        val pos = if (target < 0) pageIds.size - 1 else target
        runCatching { rowFocus[pos.coerceIn(0, PAGE_SIZE - 1)].requestFocus() }
        pendingFocus = null
    }

    fun statusOf(id: Int) = health[channels[id].url]?.status ?: Health.UNKNOWN
    val okCount = pageIds.count { statusOf(it) == Health.OK }
    val failCount = pageIds.count { statusOf(it) == Health.FAIL }
    val checkingCount = pageIds.count { statusOf(it) == Health.CHECKING }

    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        // نوار کناری: دسته‌ها و دو کار
        Column(
            Modifier.width(250.dp).fillMaxHeight().background(Palette.Side).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("شبکه‌ها", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it.name }) { g ->
                    SideItem(
                        text = g.name,
                        count = g.indices.size,
                        selected = g.name == group.name,
                        onFocused = {
                            if (groupName != g.name) {
                                groupName = g.name
                                page = 0
                            }
                        },
                        onClick = { runCatching { rowFocus[0].requestFocus() } },
                    )
                }
            }
            SideItem(text = "🔄 بررسی دوباره", count = null, selected = false, onClick = { recheckTick++ })
            SideItem(text = "➕ افزودن لیست", count = null, selected = false, onClick = { vm.openSetup() })
        }

        // لیست شبکه‌ها
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (group.name == SEARCH) {
                SearchBar(query = query, onChange = { vm.setSearch(it); page = 0 })
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (group.name == SEARCH) "نتیجه‌ی جستجو" else group.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (checkingCount > 0) Summary("در حال بررسی $checkingCount", Palette.WarnDot)
                if (failCount > 0) Summary("خراب $failCount", Palette.FailDot)
                if (okCount > 0) Summary("سالم $okCount", Palette.OkDot)
                Text("  صفحه ${safePage + 1} از $pageCount", fontSize = 18.sp, color = Palette.Dim)
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onFocusChanged { listHasFocus = it.hasFocus }
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown || !listHasFocus) return@onPreviewKeyEvent false
                        when (ev.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN ->
                                if (focusedPos >= pageIds.size - 1 && safePage < pageCount - 1) {
                                    page = safePage + 1
                                    pendingFocus = 0
                                    true
                                } else false
                            KeyEvent.KEYCODE_DPAD_UP ->
                                if (focusedPos <= 0 && safePage > 0) {
                                    page = safePage - 1
                                    pendingFocus = -1
                                    true
                                } else false
                            else -> false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pageIds.forEachIndexed { pos, id ->
                    val channel = channels[id]
                    ChannelRow(
                        number = id + 1,
                        channel = channel,
                        health = statusOf(id),
                        isCurrent = id == current,
                        isFavorite = channel.url in favorites,
                        onClick = {
                            vm.select(id)
                            onClose()
                        },
                        onLongClick = { vm.toggleFavorite(channel.url) },
                        onFocused = { focusedPos = pos },
                        modifier = Modifier.weight(1f).focusRequester(rowFocus[pos]),
                    )
                }
                if (pageIds.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                group.name != SEARCH -> "شبکه‌ای در این دسته نیست."
                                query.isBlank() -> "اسم شبکه را بنویسید.\nتایپ با گوشی راحت‌تر است: «➕ افزودن لیست» را بزنید و QR را اسکن کنید."
                                else -> "شبکه‌ای با این اسم پیدا نشد."
                            },
                            fontSize = 22.sp,
                            color = Palette.Dim,
                        )
                    }
                }
                // ردیف‌های خالی برای صفحه‌ی آخر تا اندازه‌ی ردیف‌ها ثابت بماند
                repeat(PAGE_SIZE - pageIds.size) { Spacer(Modifier.weight(1f)) }
            }

            Text(
                "OK = پخش     نگه داشتن OK = علاقه‌مندی     رفتن به پایین‌ترین شبکه = صفحه‌ی بعد",
                fontSize = 16.sp,
                color = Palette.Dim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Summary(text: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Text(" $text", fontSize = 18.sp, color = Color.White)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SideItem(
    text: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Primary.copy(alpha = 0.35f) else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (count != null) Text(count.toString(), fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    number: Int,
    channel: Channel,
    health: Health,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (health == Health.FAIL) 0.6f else 1f)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Card,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = if (isCurrent) Border(BorderStroke(2.dp, Palette.Primary), shape = shape) else Border.None,
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(number.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
            Logo(channel, 32)
            Text(channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            if (isFavorite) Text("⭐", fontSize = 18.sp)
            if (isCurrent) Text("▶ پخش", fontSize = 16.sp, color = Palette.Primary)
            when (health) {
                Health.OK -> StatusDot(Palette.OkDot, "سالم")
                Health.FAIL -> StatusDot(Palette.FailDot, "خراب")
                Health.CHECKING -> StatusDot(Palette.WarnDot, "بررسی…")
                Health.UNKNOWN -> Unit
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(" $label", fontSize = 16.sp)
    }
}

/** کادر جستجو؛ با OK روی آن، کیبورد تلویزیون باز می‌شود. */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        onClick = {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Card,
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF24365C),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🔍", fontSize = 22.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("اسم شبکه (OK = کیبورد)", fontSize = 22.sp, color = Palette.Dim)
                    inner()
                },
            )
        }
    }
}
