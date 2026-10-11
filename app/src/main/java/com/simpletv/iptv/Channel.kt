package com.simpletv.iptv

/**
 * یک دسته از شبکه‌ها (مثلاً «خبری» یا «همه شبکه‌ها»).
 *
 * @property name اسمی که در نوار کناری نشان داده می‌شود
 * @property indices شماره‌ی شبکه‌ها در لیست اصلی؛ به‌جای نگه داشتن خود شبکه‌ها فقط شماره‌شان ذخیره می‌شود
 *   تا با لیست‌های ده‌هزار شبکه‌ای حافظه‌ی زیادی مصرف نشود.
 */
class ChannelGroup(val name: String, val indices: IntArray)

/**
 * یک لیست اضافه‌شده توسط کاربر (لینک M3U، متن یا اشتراک Xtream).
 *
 * @property id نام فایل لیست روی دیسک (`playlists/<id>.m3u`)
 * @property name اسم قابل‌نمایش، مثلاً آدرس سرور
 * @property count تعداد شبکه‌های این لیست هنگام آخرین خواندن
 */
data class Source(val id: String, val name: String, val count: Int)

/**
 * یک شبکه‌ی تلویزیونی.
 *
 * @property name اسم شبکه
 * @property url آدرس مستقیم استریم (HLS، DASH یا TS)
 * @property logo آدرس لوگو (`tvg-logo`)؛ اگر نبود null است
 * @property group دسته‌بندی از `group-title` لیست
 * @property source اسم لیستی که این شبکه از آن آمده
 */
data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val source: String = "",
) {
    /** متن نرمال‌شده برای جستجو؛ یک‌بار موقع ساخت لیست محاسبه می‌شود. */
    val searchKey: String = normalizeForSearch("$name $group")
}
