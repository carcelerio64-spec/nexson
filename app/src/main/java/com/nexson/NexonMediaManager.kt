package com.nexson

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

data class Track(val title: String, val artist: String, val rawId: Int? = null)

class NexonMediaManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    val playlist = listOf(
        Track("Neon Mirage", "Nexon Cyber Orchestra"),
        Track("Analog Drift", "The Retro Wave"),
        Track("Neural Resonance", "Bio-Electric Dream"),
        Track("Synthetic Horizon", "AI Pioneer")
    )
    private var currentTrackIndex = 0

    val currentTrack: Track
        get() = playlist[currentTrackIndex]

    init {
        setupMediaSession()
        preparePlayer()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(context, "NexonMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            setPlaybackState(stateBuilder.build())
            isActive = true
        }
    }

    private fun preparePlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        }
    }

    fun play() {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .build()
        )
    }

    fun pause() {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0.0f)
                .build()
        )
    }

    fun isPlaying(): Boolean {
        val state = mediaSession?.controller?.playbackState?.state
        return state == PlaybackStateCompat.STATE_PLAYING
    }

    fun next() {
        currentTrackIndex = (currentTrackIndex + 1) % playlist.size
        play()
    }

    fun previous() {
        currentTrackIndex = if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
        play()
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.isActive = false
        mediaSession?.release()
    }
}
