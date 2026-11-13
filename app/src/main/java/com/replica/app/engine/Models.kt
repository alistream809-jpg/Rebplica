package com.replica.app.engine

class CancelledException : RuntimeException("cancelled")

class FloatList(cap: Int = 1024) {
    var data = FloatArray(cap)
    var size = 0
    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    operator fun get(i: Int): Float = data[i]
    fun toArray(): FloatArray = data.copyOf(size)
}

class LongList(cap: Int = 1024) {
    var data = LongArray(cap)
    var size = 0
    fun add(v: Long) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
    operator fun get(i: Int): Long = data[i]
}

class ByteList(cap: Int = 1 shl 16) {
    var data = ByteArray(cap)
    var size = 0
    fun addAll(a: ByteArray) {
        if (size + a.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + a.size))
        System.arraycopy(a, 0, data, size, a.size)
        size += a.size
    }
}

class VideoInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationMs: Long,
    val fps: Double,
    val mime: String,
    val framesAnalyzed: Int,
    val usedFallback: Boolean
)

/** Motion measured for one clip. Offsets are fractions of frame size, scale is a plain factor. */
class ClipMotion(
    val timesMs: LongArray,        // relative to clip start
    val offX: FloatArray?,         // null when no shake detected
    val offY: FloatArray?,
    val scale: FloatArray?         // null when no zoom detected
)

class Clip(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val color: Int,                // ARGB
    val motion: ClipMotion?
)

class Overlay(
    val isFlash: Boolean,          // true: light flash, false: dip to black
    val color: Int,                // ARGB
    val timesMs: LongArray,        // absolute
    val alphas: FloatArray
) {
    val startMs: Long get() = timesMs.first()
    val endMs: Long get() = timesMs.last()
}

class VideoAnalysis(
    val info: VideoInfo,
    val clips: List<Clip>,
    val overlays: List<Overlay>
)

class AudioAnalysis(
    val hasAudio: Boolean,
    val durationMs: Long,
    val bpm: Double,
    val confidence: Float,
    val beatsMs: LongArray,
    val onsetsMs: LongArray,
    val file: java.io.File?,
    val mime: String,
    val extension: String
)
