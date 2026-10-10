package com.simpletv.iptv

/**
 * یکسان‌سازی متن برای جستجو: حروف عربی «ي» و «ك» به فارسی تبدیل می‌شوند و نیم‌فاصله و اعراب حذف می‌شوند،
 * تا «کیهان» و «كيهان» یکی حساب شوند.
 */
fun normalizeForSearch(text: String): String {
    val sb = StringBuilder(text.length)
    for (ch in text.lowercase()) {
        when (ch) {
            'ي', 'ى' -> sb.append('ی')
            'ك' -> sb.append('ک')
            '‌' -> sb.append(' ')
            in 'ً'..'ٟ' -> Unit
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/** شماره‌ی شبکه‌هایی که همه‌ی کلمه‌های جستجو در اسم یا دسته‌شان هست. */
fun searchChannels(channels: List<Channel>, query: String): IntArray {
    val words = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return IntArray(0)
    val result = IntArray(channels.size)
    var n = 0
    channels.forEachIndexed { i, c ->
        if (words.all { it in c.searchKey }) result[n++] = i
    }
    return result.copyOf(n)
}
