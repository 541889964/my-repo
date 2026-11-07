package com.superrouter.app

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun WallpaperLayer(form: FormFactor, modifier: Modifier = Modifier) {
    val kind = if (form == FormFactor.Phone) UiState.phoneKind else UiState.tabletKind
    val staticPath  = if (form == FormFactor.Phone) UiState.phoneStaticPath  else UiState.tabletStaticPath
    val dynamicPath = if (form == FormFactor.Phone) UiState.phoneDynamicPath else UiState.tabletDynamicPath

    Box(modifier.fillMaxSize()) {
        key(kind, staticPath, dynamicPath) {
            when (kind) {
                1 -> if (!staticPath.isNullOrBlank() && File(staticPath).exists())
                        StaticWallpaper(staticPath) else DefaultWallpaper()
                2 -> if (!dynamicPath.isNullOrBlank() && File(dynamicPath).exists())
                        DynamicWallpaper(dynamicPath) else DefaultWallpaper()
                else -> DefaultWallpaper()
            }
        }

        val darkAlpha = when (kind) {
            2 -> 0.62f
            1 -> 0.52f
            else -> 0f
        }
        if (darkAlpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = darkAlpha)))
        }

        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.22f),
                0.18f to Color.Transparent,
                0.82f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.32f)
            )
        ))

        Box(Modifier.fillMaxSize().background(GlowGradient))
    }
}

@Composable
private fun DefaultWallpaper() {
    Box(Modifier.fillMaxSize().background(DeepGradient)) {
        Box(Modifier.fillMaxSize().background(HeroGlow))
    }
}

@Composable
private fun StaticWallpaper(path: String) {
    val bitmap = remember(path) {
        try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (_: Throwable) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else DefaultWallpaper()
}

@Composable
private fun DynamicWallpaper(path: String) {
    AndroidView(
        factory = { ctx ->
            val container = FrameLayout(ctx)
            val surfaceView = SurfaceView(ctx)
            container.addView(surfaceView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            var player: MediaPlayer? = null
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    try {
                        player = MediaPlayer().apply {
                            setDataSource(path)
                            setSurface(holder.surface)
                            isLooping = true
                            setVolume(0f, 0f)
                            setOnPreparedListener { start() }
                            setOnErrorListener { _, _, _ -> true }
                            prepareAsync()
                        }
                    } catch (_: Throwable) { }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    try { player?.release() } catch (_: Throwable) { }
                    player = null
                }
            })
            container
        },
        modifier = Modifier.fillMaxSize()
    )
}
