package com.replica.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.replica.app.data.MediaUtil
import com.replica.app.data.ProjectMeta
import com.replica.app.data.ProjectStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class ProgressState(
    val fraction: Float = 0f,
    val stage: Int = 0,          // 0 video, 1 audio, 2 xml
    val etaSec: Long = -1,
    val elapsedSec: Long = 0,
    val fallback: Boolean = false
)

object Pipeline {
    private const val W_VIDEO = 0.62f
    private const val W_AUDIO = 0.30f

    fun run(
        ctx: Context, uri: Uri, displayName: String, videoSize: Long, durationHintMs: Long,
        sensitivity: Int, beatMarkers: Boolean, referenceLayer: Boolean,
        cancelled: () -> Boolean, onProgress: (ProgressState) -> Unit
    ): ProjectMeta {
        val id = System.currentTimeMillis().toString()
        val dir = ProjectStore.dir(id)
        dir.mkdirs()
        val t0 = System.nanoTime()
        var smooth = -1.0
        fun report(frac: Float, stage: Int) {
            val el = (System.nanoTime() - t0) / 1e9
            val initial = durationHintMs / 1000.0 * 0.5 + 4.0
            val raw = if (frac < 0.05f) initial * (1 - frac) else el * (1 - frac) / frac
            smooth = if (smooth < 0) raw else 0.8 * smooth + 0.2 * raw
            onProgress(ProgressState(frac.coerceIn(0f, 1f), stage, smooth.toLong(), el.toLong()))
        }
        try {
            report(0f, 0)
            val video = VideoAnalyzer.analyze(ctx, uri, sensitivity, { report(it * W_VIDEO, 0) }, cancelled)
            report(W_VIDEO, 1)
            val audio = AudioAnalyzer.analyze(ctx, uri, dir, { report(W_VIDEO + it * W_AUDIO, 1) }, cancelled)
            if (cancelled()) throw CancelledException()
            report(W_VIDEO + W_AUDIO, 2)

            val baseName = displayName.substringBeforeLast('.').ifBlank { "Edit" }
            var audioUri: String? = null
            if (audio.hasAudio && audio.file != null) {
                audioUri = MediaUtil.saveAudio(ctx, audio.file, "Replica_" + id + "." + audio.extension, audio.mime)
                    ?: android.net.Uri.fromFile(audio.file).toString()
            }
            val videoUri = MediaUtil.resolveVideoUri(ctx, uri)
            val xml = XmlBuilder.build(
                XmlBuilder.Params(
                    title = "Replica - $baseName",
                    video = video, audio = audio, audioUri = audioUri, videoUri = videoUri,
                    videoMime = "video/mp4", videoSize = videoSize,
                    beatMarkers = beatMarkers, referenceLayer = referenceLayer
                )
            )
            File(dir, "project.xml").writeText(xml)
            report(0.97f, 2)
            makeThumb(ctx, uri, ProjectStore.thumbFile(id), video.info.durationMs)

            val meta = ProjectMeta(
                id = id, name = baseName, createdAt = System.currentTimeMillis(),
                durationMs = video.info.durationMs, clips = video.clips.size,
                flashes = video.overlays.count { it.isFlash }, fades = video.overlays.count { !it.isFlash },
                motionClips = video.clips.count { it.motion != null }, bpm = audio.bpm,
                width = video.info.width, height = video.info.height, fps = video.info.fps,
                hasAudio = audio.hasAudio
            )
            ProjectStore.save(meta)
            report(1f, 2)
            return meta
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun makeThumb(ctx: Context, uri: Uri, out: File, durMs: Long) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val t = min(1_000_000L, durMs * 500L)
            val bmp = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return
            val w = 360
            val h = maxOf(1, bmp.height * w / bmp.width)
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (ignored: Exception) {
        } finally {
            r.release()
        }
    }
}
