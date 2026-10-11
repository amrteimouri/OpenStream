package com.simpletv.iptv

import androidx.compose.ui.graphics.Color

/** رنگ‌های اپ: تیره و با کنتراست بالا تا از دور روی تلویزیون خوانا باشد. */
object Palette {
    // رنگ‌های پایه
    val Bg = Color(0xFF0B1220)
    val Card = Color(0xFF162036)
    val Primary = Color(0xFF3B82F6)
    // رنگ متن وضعیت (روی پس‌زمینه‌ی تیره)
    val Ok = Color(0xFF4ADE80)
    val Fail = Color(0xFFF87171)
    val Warn = Color(0xFFFBBF24)
    val Dim = Color(0xFF94A3B8)
    val Scrim = Color(0xCC000000)
    val Side = Color(0xFF0F1A2E)

    // رنگ نقطه‌ی وضعیت؛ پررنگ‌تر تا روی پس‌زمینه‌ی سفیدِ فوکوس هم دیده شود
    val OkDot = Color(0xFF16A34A)
    val FailDot = Color(0xFFDC2626)
    val WarnDot = Color(0xFFF59E0B)
}
