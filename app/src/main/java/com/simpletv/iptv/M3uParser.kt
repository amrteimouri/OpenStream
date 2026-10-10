package com.simpletv.iptv

import java.io.BufferedReader

/** خواندن خط‌به‌خط فایل M3U، تا لیست‌های بزرگ حافظه‌ی زیادی نگیرند. */
object M3uParser {
    const val DEFAULT_GROUP = "سایر"

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

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
                    val afterAttrs = line.lastIndexOf('"').coerceAtLeast(0)
                    val comma = line.indexOf(',', afterAttrs)
                    name = if (comma >= 0) line.substring(comma + 1).trim() else null
                }
                line.startsWith("#") -> Unit
                else -> {
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
