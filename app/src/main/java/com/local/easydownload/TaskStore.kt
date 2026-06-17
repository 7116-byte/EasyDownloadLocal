package com.local.easydownload

import android.content.Context
import android.net.Uri

object TaskStore {
    private const val PREF_NAME = "download_tasks"
    private const val KEY_ITEMS = "items"

    fun load(context: Context): List<MediaItem> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_ITEMS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 11) return@mapNotNull null
            runCatching {
                MediaItem(
                    url = parts[0].decodePart(),
                    type = MediaType.valueOf(parts[1].decodePart()),
                    title = parts[2].decodePart(),
                    coverUrl = parts[3].decodePart(),
                    size = parts[4].decodePart(),
                    source = parts[5].decodePart(),
                    selected = parts[6].decodePart().toBoolean(),
                    localPath = parts[7].decodePart(),
                    status = parts[8].decodePart(),
                    progress = parts[9].decodePart().toIntOrNull() ?: 0,
                    speed = parts[10].decodePart()
                )
            }.getOrNull()
        }.toList()
    }

    fun add(context: Context, item: MediaItem) {
        val next = (load(context) + item).takeLast(200)
        save(context, next)
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, items: List<MediaItem>) {
        val raw = items.joinToString("\n") { item ->
            listOf(
                item.url,
                item.type.name,
                item.title,
                item.coverUrl,
                item.size,
                item.source,
                item.selected.toString(),
                item.localPath,
                item.status,
                item.progress.toString(),
                item.speed
            ).joinToString("\t") { it.encodePart() }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, raw).apply()
    }

    private fun String.encodePart(): String = Uri.encode(this).orEmpty()

    private fun String.decodePart(): String = Uri.decode(this)
}
