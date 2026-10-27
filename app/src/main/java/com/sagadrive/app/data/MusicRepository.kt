package com.sagadrive.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioPermission {
    fun name(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, name()) == PackageManager.PERMISSION_GRANTED
}

object MediaIds {
    const val ROOT = "root"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val SONG_PREFIX = "song:"
    const val ALBUM_PREFIX = "album:"
    const val ARTIST_PREFIX = "artist:"
}

/** Local library backed by MediaStore. Returns Media3 items usable by both the phone UI and Android Auto. */
class MusicRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun rootItem(): MediaItem = folder(MediaIds.ROOT, "SagaDrive", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun categories(): List<MediaItem> = listOf(
        folder(MediaIds.SONGS, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        folder(MediaIds.ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        folder(MediaIds.ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
    )

    suspend fun songs(): List<MediaItem> = querySongs(null, null)
    suspend fun songsByAlbum(albumId: Long) =
        querySongs("${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(albumId.toString()))
    suspend fun songsByArtist(artistId: Long) =
        querySongs("${MediaStore.Audio.Media.ARTIST_ID} = ?", arrayOf(artistId.toString()))

    /** Turns a bare mediaId (what Android Auto sends back) into a playable item. */
    suspend fun resolve(mediaId: String): MediaItem? {
        if (!mediaId.startsWith(MediaIds.SONG_PREFIX)) return null
        val id = mediaId.removePrefix(MediaIds.SONG_PREFIX).toLongOrNull() ?: return null
        return querySongs("${MediaStore.Audio.Media._ID} = ?", arrayOf(id.toString())).firstOrNull()
    }

    suspend fun albums(): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<MediaItem>()
        runQuery(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST),
            "${MediaStore.Audio.Albums.ALBUM} COLLATE NOCASE ASC",
        ) { c ->
            val id = c.getLong(0)
            out += MediaItem.Builder().setMediaId(MediaIds.ALBUM_PREFIX + id).setMediaMetadata(
                MediaMetadata.Builder().setTitle(c.getString(1)).setArtist(clean(c.getString(2)))
                    .setArtworkUri(albumArt(id)).setIsBrowsable(true).setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM).build()
            ).build()
        }
        out
    }

    suspend fun artists(): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<MediaItem>()
        runQuery(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST),
            "${MediaStore.Audio.Artists.ARTIST} COLLATE NOCASE ASC",
        ) { c ->
            out += MediaItem.Builder().setMediaId(MediaIds.ARTIST_PREFIX + c.getLong(0)).setMediaMetadata(
                MediaMetadata.Builder().setTitle(clean(c.getString(1)) ?: "Unknown artist")
                    .setIsBrowsable(true).setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST).build()
            ).build()
        }
        out
    }

    private suspend fun querySongs(extraSelection: String?, args: Array<String>?): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<MediaItem>()
            val m = MediaStore.Audio.Media
            val selection = "${m.IS_MUSIC} != 0" + (extraSelection?.let { " AND $it" } ?: "")
            runQuery(
                m.EXTERNAL_CONTENT_URI,
                arrayOf(m._ID, m.TITLE, m.ARTIST, m.ALBUM, m.ALBUM_ID, m.DURATION),
                "${m.TITLE} COLLATE NOCASE ASC", selection, args,
            ) { c ->
                val id = c.getLong(0)
                out += MediaItem.Builder()
                    .setMediaId(MediaIds.SONG_PREFIX + id)
                    .setUri(ContentUris.withAppendedId(m.EXTERNAL_CONTENT_URI, id))
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(c.getString(1) ?: "Unknown title")
                            .setArtist(clean(c.getString(2))).setAlbumTitle(c.getString(3))
                            .setArtworkUri(albumArt(c.getLong(4))).setDurationMs(c.getLong(5))
                            .setIsBrowsable(false).setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()
                    ).build()
            }
            out
        }

    private inline fun runQuery(
        uri: Uri, projection: Array<String>, order: String,
        selection: String? = null, args: Array<String>? = null,
        row: (android.database.Cursor) -> Unit,
    ) {
        if (!AudioPermission.granted(context)) return
        try {
            resolver.query(uri, projection, selection, args, order)?.use { while (it.moveToNext()) row(it) }
        } catch (_: SecurityException) { /* permission revoked mid-query: return what we have */ }
    }

    private fun folder(id: String, title: String, type: Int) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(type).build()
        ).build()

    private fun albumArt(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    private fun clean(v: String?): String? = v?.takeIf { it.isNotBlank() && it != "<unknown>" }
}
