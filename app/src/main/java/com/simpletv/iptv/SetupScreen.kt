package com.simpletv.iptv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/** صفحه‌ی افزودن لیست: QR بزرگ، سه قدم ساده و مدیریت لیست‌هایی که قبلاً اضافه شده‌اند. */
@Composable
fun SetupScreen(vm: AppViewModel) {
    val serverUrl by vm.serverUrl.collectAsState()
    val message by vm.message.collectAsState()
    val channels by vm.channels.collectAsState()
    val sources by vm.sources.collectAsState()

    Row(
        Modifier.fillMaxSize().background(Palette.Bg).padding(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.logo), contentDescription = "OpenStream", modifier = Modifier.size(64.dp))
                Text(
                    if (sources.isEmpty()) "به OpenStream خوش آمدید" else "افزودن لیست یا جستجو با گوشی",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Step("۱", "گوشی خود را به همین اینترنت خانه وصل کنید.")
            Step("۲", "دوربین گوشی را روی کد روبه‌رو بگیرید.")
            Step("۳", "لینک لیست را بفرستید، یا اسم شبکه را برای جستجو بنویسید.")
            if (message != null) {
                Text("❌ $message", fontSize = 22.sp, color = Palette.Fail)
            }

            if (sources.isNotEmpty()) {
                Text("لیست‌های شما (شبکه‌های همه‌ی آن‌ها با هم دیده می‌شوند):", fontSize = 20.sp, color = Palette.Dim)
                LazyColumn(Modifier.heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sources, key = { it.id }) { source ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${source.name}  (${source.count})",
                                fontSize = 20.sp,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { vm.removeSource(source.id) }) { Text("🗑 حذف", fontSize = 18.sp) }
                        }
                    }
                }
            }
            if (channels.isNotEmpty()) {
                Text("برای برگشت، دکمه‌ی «برگشت» ریموت را بزنید.", fontSize = 18.sp, color = Palette.Dim)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val url = serverUrl
            if (url != null) {
                val qr = remember(url) { qrBitmap(url) }
                Image(qr, contentDescription = "کد QR", modifier = Modifier.size(260.dp))
                Text(url, fontSize = 22.sp, color = Color.White)
            } else {
                Text(
                    "تلویزیون به اینترنت خانه وصل نیست.\nاتصال وای‌فای یا کابل را بررسی کنید.",
                    fontSize = 22.sp,
                    color = Palette.Warn,
                    modifier = Modifier.width(280.dp),
                )
            }
        }
    }
}

/** یک قدم از راهنمای سه‌مرحله‌ای: شماره‌ی آبی بزرگ و یک جمله‌ی کوتاه. */
@Composable
private fun Step(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(number, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Primary)
        Text(text, fontSize = 22.sp, color = Color.White)
    }
}
