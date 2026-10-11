package com.simpletv.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * تنها Activity اپ؛ فقط ViewModel را می‌سازد و محتوای Compose را نشان می‌دهد.
 * تنظیم `configChanges` در AndroidManifest باعث می‌شود با تغییر ورودی (مثل وصل شدن کیبورد)
 * Activity دوباره ساخته نشود و پخش قطع نشود.
 */
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppRoot(vm)
            }
        }
    }
}

/**
 * ریشه‌ی رابط کاربری: چیدمان را راست‌به‌چپ می‌کند و بین صفحه‌ی افزودن لیست ([SetupScreen])
 * و پلیر ([PlayerScreen]) یکی را نشان می‌دهد.
 *
 * صفحه‌ی افزودن لیست وقتی دیده می‌شود که هنوز لیستی نداریم یا کاربر خودش آن را باز کرده باشد.
 */
@Composable
fun AppRoot(vm: AppViewModel) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val channels by vm.channels.collectAsState()
        val showSetup by vm.showSetup.collectAsState()
        val loaded by vm.loaded.collectAsState()
        val setupVisible = channels.isEmpty() || showSetup

        BackHandler(enabled = showSetup && channels.isNotEmpty()) { vm.closeSetup() }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // تا خواندن لیست ذخیره‌شده تمام نشده، صفحه‌ی سیاه می‌ماند تا صفحه‌ی QR اشتباهی نپرد.
            if (loaded) {
                if (setupVisible) SetupScreen(vm) else PlayerScreen(vm)
            }
        }
    }
}
