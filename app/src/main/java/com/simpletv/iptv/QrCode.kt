package com.simpletv.iptv

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * از یک متن (اینجا آدرس صفحه‌ی افزودن لیست) کد QR می‌سازد.
 *
 * @param text متنی که باید در QR باشد
 * @param size اندازه‌ی ضلع تصویر بر حسب پیکسل
 * @return تصویر سیاه و سفید با حاشیه‌ی کوچک؛ برای اسکن شدن از دور، بزرگ و پرکنتراست است
 */
fun qrBitmap(text: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2))
    val pixels = IntArray(size * size) { i ->
        if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
