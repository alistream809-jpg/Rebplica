package com.replica.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.replica.app.data.MediaUtil
import com.replica.app.data.Prefs
import com.replica.app.data.ProjectMeta
import com.replica.app.data.ProjectStore
import com.replica.app.data.t
import com.replica.app.engine.CancelledException
import com.replica.app.engine.Pipeline
import com.replica.app.engine.ProgressState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class Screen { MAIN, SETTINGS, PROGRESS, RESULT }

class Picked(
    val uri: Uri,
    val name: String,
    val size: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumb: ImageBitmap?
)

object AppState {
    lateinit var app: Context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var screen by mutableStateOf(Screen.MAIN)
    var tab by mutableStateOf(1)            // 0 = Projects, 1 = Converter
    var picked by mutableStateOf<Picked?>(null)
    var progress by mutableStateOf(ProgressState())
    var error by mutableStateOf<String?>(null)
    var resultId by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    val projects = mutableStateListOf<ProjectMeta>()
    private var job: Job? = null
    private var toastJob: Job? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Prefs.init(app)
        ProjectStore.init(app)
        refreshProjects()
    }

    fun refreshProjects() {
        val l = ProjectStore.list()
        projects.clear()
        projects.addAll(l)
    }

    fun showToast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(3200)
            toast = null
        }
    }

    fun pick(uri: Uri) {
        scope.launch {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(app, uri)
                val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { val x = w; w = h; h = x }
                if (dur <= 0 || w <= 0 || h <= 0) throw IllegalStateException("bad video")
                var thumb: ImageBitmap? = null
                val bmp = r.getFrameAtTime(minOf(1_000_000L, dur * 500L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    val tw = 480
                    val th = maxOf(1, bmp.height * tw / bmp.width)
                    thumb = Bitmap.createScaledBitmap(bmp, tw, th, true).asImageBitmap()
                }
                picked = Picked(
                    uri, MediaUtil.queryName(app, uri), MediaUtil.querySize(app, uri), dur, w, h, thumb
                )
            } catch (e: Exception) {
                showToast(t("pick_failed"))
            } finally {
                r.release()
            }
        }
    }

    fun startConversion() {
        val p = picked ?: return
        error = null
        progress = ProgressState()
        screen = Screen.PROGRESS
        job = scope.launch {
            val self = coroutineContext[Job]!!
            try {
                val meta = Pipeline.run(
                    app, p.uri, p.name, p.size, p.durationMs,
                    Prefs.sensitivity, Prefs.beatMarkers, Prefs.referenceLayer,
                    { !self.isActive }, { progress = it }
                )
                refreshProjects()
                resultId = meta.id
                screen = Screen.RESULT
            } catch (e: CancelledException) {
                screen = Screen.MAIN
            } catch (e: CancellationException) {
                screen = Screen.MAIN
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun cancel() {
        job?.cancel()
        screen = Screen.MAIN
    }

    fun deleteProject(id: String) {
        ProjectStore.delete(id)
        refreshProjects()
    }
}
