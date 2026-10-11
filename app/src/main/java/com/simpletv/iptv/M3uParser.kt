package com.simpletv.iptv

import java.io.BufferedReader

/**
 * خواننده‌ی فایل M3U / M3U8.
 *
 * قالب فایل: هر شبکه یک خط `#EXTINF` (اطلاعات: اسم، لوگو، دسته) و بعد یک خط آدرس دارد:
 * ```
 * #EXTINF:-1 tvg-logo="https://.../logo.png" group-title="News",اسم شبکه
 * https://example.com/live/stream.m3u8
 * ```
 * فایل خط‌به‌خط خوانده می‌شود، نه یک‌جا؛ پس لیست‌های چندده‌مگابایتی هم حافظه‌ی زیادی نمی‌گیرند.
 */
object M3uParser {
    const val DEFAULT_GROUP = "سایر"

    /** پیدا کردن ویژگی‌هایی مثل `tvg-logo="..."` در خط #EXTINF */
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    /**
     * همه‌ی شبکه‌های یک فایل M3U را برمی‌گرداند.
     *
     * @param reader خواننده‌ی متن فایل
     * @param source اسم لیست؛ روی هر شبکه ثبت می‌شود تا بعداً بشود بر اساس لیست دسته‌بندی کرد
     */
    fun parse(reader: BufferedReader, source: String = ""): List<Channel> {
        val result = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var group = DEFAULT_GROUP

        reader.forEachLine { raw ->
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: DEFAULT_GROUP
                    // اسم شبکه بعد از آخرین «,» است؛ ولی اسم یا ویژگی‌ها ممکن است خودشان کاما داشته باشند،
                    // پس جستجوی کاما را از بعد از آخرین نقل‌قول شروع می‌کنیم.
                    val afterAttrs = line.lastIndexOf('"').coerceAtLeast(0)
                    val comma = line.indexOf(',', afterAttrs)
                    name = if (comma >= 0) line.substring(comma + 1).trim() else null
                }
                line.startsWith("#") -> Unit // بقیه‌ی خط‌های توضیحی (مثل #EXTVLCOPT) نادیده گرفته می‌شوند
                else -> {
                    // هر خطی که # ندارد آدرس شبکه است
                    val title = name?.takeIf { it.isNotBlank() }
                        ?: line.substringAfterLast('/').substringBefore('?')
                    result.add(Channel(title, line, logo, group, source))
                    name = null
                    logo = null
                    group = DEFAULT_GROUP
                }
            }
        }
        return result
    }
}
