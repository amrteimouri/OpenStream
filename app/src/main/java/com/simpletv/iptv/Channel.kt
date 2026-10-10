package com.simpletv.iptv

/** یک دسته از شبکه‌ها؛ فقط شماره‌ی شبکه‌ها را نگه می‌دارد تا حافظه کم مصرف شود. */
class ChannelGroup(val name: String, val indices: IntArray)

/** یک لیست اضافه‌شده توسط کاربر (لینک M3U، متن یا اشتراک Xtream). */
data class Source(val id: String, val name: String, val count: Int)

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
