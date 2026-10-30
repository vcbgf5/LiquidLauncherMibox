package com.liquidglass.tvlauncher.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.liquidglass.tvlauncher.data.Edition
import com.liquidglass.tvlauncher.data.Performance
import java.io.File

/**
 * The clip the phone sent, looping and muted, cropped to fill its box the way a photo with
 * `ContentScale.Crop` would (the sides or top/bottom that don't fit the screen's shape are cut
 * off, never squeezed). Never used in the light performance mode or the Lite edition: decoding
 * video the whole time the home screen is up is too much for a weak box.
 */
@Composable
fun VideoWallpaper(file: File, modifier: Modifier = Modifier) {
    if (Performance.lite || Edition.lite) return
    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(file) {
        onDispose { runCatching { mediaPlayer.release() } }
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TextureView(context).apply {
                var videoW = 0
                var videoH = 0
                fun centerCrop() {
                    if (videoW <= 0 || videoH <= 0 || width <= 0 || height <= 0) return
                    val scale = maxOf(width.toFloat() / videoW, height.toFloat() / videoH)
                    val matrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate((width - videoW * scale) / 2f, (height - videoH * scale) / 2f)
                    }
                    setTransform(matrix)
                }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                        try {
                            mediaPlayer.reset() // a surface can be torn down and rebuilt more than once
                            mediaPlayer.setDataSource(file.path)
                            mediaPlayer.setSurface(Surface(surface))
                            mediaPlayer.isLooping = true
                            mediaPlayer.setVolume(0f, 0f)
                            mediaPlayer.setOnVideoSizeChangedListener { _, vw, vh ->
                                videoW = vw
                                videoH = vh
                                centerCrop()
                            }
                            mediaPlayer.setOnPreparedListener { it.start() }
                            mediaPlayer.prepareAsync()
                        } catch (_: Throwable) {
                            // a clip Android can't decode: the still poster frame underneath keeps showing
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) = centerCrop()

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        runCatching { mediaPlayer.stop() }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
    )
}
