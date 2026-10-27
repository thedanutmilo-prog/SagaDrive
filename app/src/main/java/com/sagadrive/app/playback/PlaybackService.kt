package com.sagadrive.app.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.sagadrive.app.data.AudioPermission
import com.sagadrive.app.data.MediaIds
import com.sagadrive.app.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * Single playback service for the phone UI, the media notification and Android Auto.
 * Android Auto renders its own driver-safe UI from this session; nothing custom is projected.
 *
 * Audio focus: ExoPlayer requests/handles focus itself (handleAudioFocus = true). If Spotify,
 * Maps or Waze take focus, ExoPlayer ducks or pauses; after a permanent loss (e.g. Spotify starts)
 * it stays paused instead of fighting for focus.
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: MusicRepository

    override fun onCreate() {
        super.onCreate()
        repo = MusicRepository(applicationContext)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            LibraryResult.ofItem(repo.rootItem(), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val item = if (mediaId == MediaIds.ROOT) repo.rootItem() else repo.resolve(mediaId)
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            scope.future<LibraryResult<ImmutableList<MediaItem>>> {
                if (!AudioPermission.granted(applicationContext)) {
                    return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_PERMISSION_DENIED)
                }
                val all: List<MediaItem> = when {
                    parentId == MediaIds.ROOT -> repo.categories()
                    parentId == MediaIds.SONGS -> repo.songs()
                    parentId == MediaIds.ALBUMS -> repo.albums()
                    parentId == MediaIds.ARTISTS -> repo.artists()
                    parentId.startsWith(MediaIds.ALBUM_PREFIX) ->
                        repo.songsByAlbum(parentId.removePrefix(MediaIds.ALBUM_PREFIX).toLongOrNull() ?: -1)
                    parentId.startsWith(MediaIds.ARTIST_PREFIX) ->
                        repo.songsByArtist(parentId.removePrefix(MediaIds.ARTIST_PREFIX).toLongOrNull() ?: -1)
                    else -> return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
                val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
                LibraryResult.ofItemList(ImmutableList.copyOf(all.drop(from).take(pageSize)), params)
            }

        /** Android Auto sends items with only a mediaId; resolve them to playable items. */
        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.map { repo.resolve(it.mediaId) ?: it }.toMutableList()
        }
    }
}
