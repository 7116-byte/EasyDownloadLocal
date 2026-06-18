package com.local.easydownload

import android.content.Context

data class DownloadSettings(
    val includeVideo: Boolean = true,
    val includeImage: Boolean = true,
    val includeAudio: Boolean = false,
    val includePlaylist: Boolean = true
) {
    fun allows(type: MediaType): Boolean = when (type) {
        MediaType.Video -> includeVideo
        MediaType.Image, MediaType.Gif -> includeImage
        MediaType.Audio -> includeAudio
        MediaType.Playlist -> includePlaylist
        MediaType.Web, MediaType.File, MediaType.Other -> false
    }
}

object AppSettings {
    private const val PREF_NAME = "app_settings"
    private const val KEY_VIDEO = "include_video"
    private const val KEY_IMAGE = "include_image"
    private const val KEY_AUDIO = "include_audio"
    private const val KEY_PLAYLIST = "include_playlist"

    fun load(context: Context): DownloadSettings {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return DownloadSettings(
            includeVideo = pref.getBoolean(KEY_VIDEO, true),
            includeImage = pref.getBoolean(KEY_IMAGE, true),
            includeAudio = pref.getBoolean(KEY_AUDIO, false),
            includePlaylist = pref.getBoolean(KEY_PLAYLIST, true)
        )
    }

    fun save(context: Context, settings: DownloadSettings) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VIDEO, settings.includeVideo)
            .putBoolean(KEY_IMAGE, settings.includeImage)
            .putBoolean(KEY_AUDIO, settings.includeAudio)
            .putBoolean(KEY_PLAYLIST, settings.includePlaylist)
            .apply()
    }
}
