package com.replica.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes every frame of the edit, reduces it to a tiny thumbnail and derives:
 *  - hard cuts (frame-difference spikes)
 *  - light flashes and dips to black (brightness excursions)
 *  - global motion per frame (shake via translation, zoom via scale)
 */
object VideoAnalyzer {
    private const val ML = 64            // motion lattice (ML x ML luma)
    private const val TW = 16            // thumbnail luma grid
    private const val CW = 8             // thumbnail chroma grid
    private const val THUMB = TW * TW + 2 * CW * CW
    private val SC = floatArrayOf(0.95f, 0.9667f, 0.9833f, 1f, 1.0167f, 1.0333f, 1.05f)

    // ---------------------------------------------------------------- frame sink

    private class Sink {
        val pts = LongList()
        val my = FloatList()
        val mu = FloatList()
        val mv = FloatList()
        val diff = FloatList()
        val mdx = FloatList()
        val mdy = FloatList()
        val mds = FloatList()
        val thumbs = ByteList()
        var prevThumb: ByteArray? = null
        var prevLat: FloatArray? = null

        fun add(ptsMs: Long, lat: FloatArray, cu: FloatArray, cv: FloatArray) {
            val thumb = ByteArray(THUMB)
            var sumY = 0f
            for (j in 0 until TW) {
                for (i in 0 until TW) {
                    var s = 0f
                    for (dy in 0 until 4) {
                        val row = (j * 4 + dy) * ML + i * 4
                        for (dx in 0 until 4) s += lat[row + dx]
                    }
                    val v = s / 16f
                    thumb[j * TW + i] = v.toInt().coerceIn(0, 255).toByte()
                    sumY += v
                }
            }
            var sumU = 0f
            var sumV = 0f
            for (k in 0 until CW * CW) {
                thumb[TW * TW + k] = cu[k].toInt().coerceIn(0, 255).toByte()
                thumb[TW * TW + CW * CW + k] = cv[k].toInt().coerceIn(0, 255).toByte()
                sumU += cu[k]
                sumV += cv[k]
            }
            pts.add(ptsMs)
            my.add(sumY / (TW * TW))
            mu.add(sumU / (CW * CW))
            mv.add(sumV / (CW * CW))
            val pt = prevThumb
            diff.add(if (pt != null) VideoAnalyzer.thumbDiff(pt, 0, thumb, 0) else 0f)
            val pl = prevLat
            if (pl != null) {
                val m = VideoAnalyzer.motion(pl, lat)
                mdx.add(m[0]); mdy.add(m[1]); mds.add(m[2])
            } else {
                mdx.add(0f); mdy.add(0f); mds.add(1f)
            }
            thumbs.addAll(thumb)
            prevThumb = thumb
            prevLat = lat.copyOf()
        }
    }

    private fun thumbDiff(a: ByteArray, ao: Int, b: ByteArray, bo: Int): Float {
        var dy = 0
        for (k in 0 until TW * TW) dy += abs((a[ao + k].toInt() and 0xFF) - (b[bo + k].toInt() and 0xFF))
        var dc = 0
        for (k in TW * TW until THUMB) dc += abs((a[ao + k].toInt() and 0xFF) - (b[bo + k].toInt() and 0xFF))
        return dy / (TW * TW).toFloat() + 0.5f * dc / (2 * CW * CW).toFloat()
    }

    // ---------------------------------------------------------------- global motion

    private fun sad(prev: FloatArray, cur: FloatArray, sx: Float, sy: Float, s: Float): Float {
        val c = 31.5f
        val inv = 1f / s
        var acc = 0f
        var n = 0
        var y = 8
        while (y < 56) {
            val py = c + (y - c - sy) * inv
            val iy = (py + 0.5f).toInt()
            var x = 8
            if (py >= 0f && iy < ML) {
                while (x < 56) {
                    val px = c + (x - c - sx) * inv
                    val ix = (px + 0.5f).toInt()
                    if (px >= 0f && ix < ML) {
                        acc += abs(cur[y * ML + x] - prev[iy * ML + ix])
                        n++
                    }
                    x += 2
                }
            }
            y += 2
        }
        return if (n > 60) acc / n else 1e9f
    }

    private fun parab(l: Float, c: Float, r: Float): Float {
        val d = l - 2f * c + r
        if (d < 1e-6f) return 0f
        return (0.5f * (l - r) / d).coerceIn(-0.5f, 0.5f)
    }

    /** Returns [shiftX, shiftY (lattice cells), scale] describing how content moved prev -> cur. */
    private fun motion(prev: FloatArray, cur: FloatArray): FloatArray {
        val identity = floatArrayOf(0f, 0f, 1f)
        val base = sad(prev, cur, 0f, 0f, 1f)
        if (base < 1.5f || base > 1e8f) return identity
        var best = base
        var bx = 0
        var by = 0
        var bi = 3
        for (si in SC.indices) {
            for (sy in -4..4) {
                for (sx in -4..4) {
                    val v = sad(prev, cur, sx.toFloat(), sy.toFloat(), SC[si])
                    if (v < best) {
                        best = v; bx = sx; by = sy; bi = si
                    }
                }
            }
        }
        if (best > base * 0.88f) return identity
        val s0 = SC[bi]
        var fx = bx.toFloat()
        var fy = by.toFloat()
        var fs = s0
        if (abs(bx) < 4) {
            fx += parab(sad(prev, cur, bx - 1f, by.toFloat(), s0), best, sad(prev, cur, bx + 1f, by.toFloat(), s0))
        }
        if (abs(by) < 4) {
            fy += parab(sad(prev, cur, bx.toFloat(), by - 1f, s0), best, sad(prev, cur, bx.toFloat(), by + 1f, s0))
        }
        if (bi in 1..5) {
            val o = parab(
                sad(prev, cur, bx.toFloat(), by.toFloat(), SC[bi - 1]), best,
                sad(prev, cur, bx.toFloat(), by.toFloat(), SC[bi + 1])
            )
            fs = s0 + o * (SC[bi + 1] - SC[bi - 1]) / 2f
        }
        return floatArrayOf(fx, fy, fs)
    }

    // ---------------------------------------------------------------- decoding

    private fun decodeWithCodec(
        ex: MediaExtractor, track: Int, fmt: MediaFormat, durUs: Long, sink: Sink,
        progress: (Float) -> Unit, cancelled: () -> Boolean
    ) {
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        ex.selectTrack(track)
        val dec = MediaCodec.createDecoderByType(mime)
        try {
            fmt.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            dec.configure(fmt, null, null, 0)
            dec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastProgressMs = 0L
            while (!outputDone) {
                if (cancelled()) throw CancelledException()
                if (!inputDone) {
                    val ii = dec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = dec.getInputBuffer(ii)!!
                        val sz = ex.readSampleData(buf, 0)
                        if (sz < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000)
                if (oi >= 0) {
                    if (info.size > 0) {
                        val image = dec.getOutputImage(oi)
                        if (image != null) {
                            try {
                                if (image.format != ImageFormat.YUV_420_888) {
                                    throw IllegalStateException("Unsupported pixel format")
                                }
                                sampleImage(image, info.presentationTimeUs / 1000, sink)
                            } finally {
                                image.close()
                            }
                        }
                    }
                    dec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    val nowMs = info.presentationTimeUs / 1000
                    if (durUs > 0 && nowMs - lastProgressMs > 100) {
                        lastProgressMs = nowMs
                        progress(min(1f, info.presentationTimeUs / durUs.toFloat()))
                    }
                }
            }
        } finally {
            try { dec.stop() } catch (ignored: Exception) {}
            dec.release()
        }
    }

    private fun sampleImage(image: android.media.Image, ptsMs: Long, sink: Sink) {
        var cx = 0
        var cy = 0
        var cw = image.width
        var ch = image.height
        val crop = image.cropRect
        if (crop != null && crop.width() > 0 && crop.height() > 0) {
            cx = crop.left; cy = crop.top; cw = crop.width(); ch = crop.height()
        }
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        val yb = yP.buffer
        val ub = uP.buffer
        val vb = vP.buffer
        val yRs = yP.rowStride
        val yPs = yP.pixelStride
        val uRs = uP.rowStride
        val uPs = uP.pixelStride
        val vRs = vP.rowStride
        val vPs = vP.pixelStride
        val maxX = cx + cw - 2
        val maxY = cy + ch - 2

        val lat = FloatArray(ML * ML)
        for (j in 0 until ML) {
            val y = min(cy + ((2 * j + 1) * ch) / (2 * ML), maxY)
            val r0 = y * yRs
            val r1 = (y + 1) * yRs
            for (i in 0 until ML) {
                val x = min(cx + ((2 * i + 1) * cw) / (2 * ML), maxX)
                val p = x * yPs
                val q = (x + 1) * yPs
                val s = (yb.get(r0 + p).toInt() and 0xFF) + (yb.get(r0 + q).toInt() and 0xFF) +
                    (yb.get(r1 + p).toInt() and 0xFF) + (yb.get(r1 + q).toInt() and 0xFF)
                lat[j * ML + i] = s * 0.25f
            }
        }
        val cu = FloatArray(CW * CW)
        val cv = FloatArray(CW * CW)
        for (j in 0 until CW) {
            val y = min(cy + ((2 * j + 1) * ch) / (2 * CW), cy + ch - 1)
            for (i in 0 until CW) {
                val x = min(cx + ((2 * i + 1) * cw) / (2 * CW), cx + cw - 1)
                cu[j * CW + i] = (ub.get((y shr 1) * uRs + (x shr 1) * uPs).toInt() and 0xFF).toFloat()
                cv[j * CW + i] = (vb.get((y shr 1) * vRs + (x shr 1) * vPs).toInt() and 0xFF).toFloat()
            }
        }
        sink.add(ptsMs, lat, cu, cv)
    }

    private fun decodeWithRetriever(
        ctx: Context, uri: Uri, durMs: Long, sink: Sink,
        progress: (Float) -> Unit, cancelled: () -> Boolean
    ): Double {
        val r = MediaMetadataRetriever()
        r.setDataSource(ctx, uri)
        val step = max(66L, durMs / 1500L)
        try {
            var t = 0L
            val px = IntArray(ML * ML)
            while (t < durMs) {
                if (cancelled()) throw CancelledException()
                val bmp: Bitmap? = r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST, ML, ML)
                if (bmp != null) {
                    val bw = bmp.width
                    val bh = bmp.height
                    val lat = FloatArray(ML * ML)
                    val u64 = FloatArray(ML * ML)
                    val v64 = FloatArray(ML * ML)
                    val src = IntArray(bw * bh)
                    bmp.getPixels(src, 0, bw, 0, 0, bw, bh)
                    for (j in 0 until ML) for (i in 0 until ML) {
                        val c = src[min(bh - 1, j * bh / ML) * bw + min(bw - 1, i * bw / ML)]
                        val rr = ((c shr 16) and 0xFF).toFloat()
                        val gg = ((c shr 8) and 0xFF).toFloat()
                        val bb = (c and 0xFF).toFloat()
                        lat[j * ML + i] = 0.299f * rr + 0.587f * gg + 0.114f * bb
                        u64[j * ML + i] = 128f - 0.168736f * rr - 0.331264f * gg + 0.5f * bb
                        v64[j * ML + i] = 128f + 0.5f * rr - 0.418688f * gg - 0.081312f * bb
                    }
                    px.fill(0)
                    val cu = FloatArray(CW * CW)
                    val cv = FloatArray(CW * CW)
                    val blk = ML / CW
                    for (j in 0 until CW) for (i in 0 until CW) {
                        var su = 0f
                        var sv = 0f
                        for (dy in 0 until blk) for (dx in 0 until blk) {
                            val idx = (j * blk + dy) * ML + i * blk + dx
                            su += u64[idx]; sv += v64[idx]
                        }
                        cu[j * CW + i] = su / (blk * blk)
                        cv[j * CW + i] = sv / (blk * blk)
                    }
                    bmp.recycle()
                    sink.add(t, lat, cu, cv)
                }
                progress(min(1f, t / durMs.toFloat()))
                t += step
            }
        } finally {
            r.release()
        }
        return 1000.0 / step
    }

    // ---------------------------------------------------------------- public entry

    fun analyze(
        ctx: Context, uri: Uri, sensitivity: Int,
        progress: (Float) -> Unit, cancelled: () -> Boolean
    ): VideoAnalysis {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val m = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("video/")) {
                track = i; fmt = f; break
            }
        }
        if (track < 0 || fmt == null) {
            ex.release()
            throw IllegalStateException("No video track found")
        }
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val w = fmt.getInteger(MediaFormat.KEY_WIDTH)
        val h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
        val rot = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
        var durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durUs <= 0) {
            val mr = MediaMetadataRetriever()
            try {
                mr.setDataSource(ctx, uri)
                durUs = (mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            } finally {
                mr.release()
            }
        }
        val durMs = durUs / 1000

        var sink = Sink()
        var fallback = false
        var sampledFps = 0.0
        try {
            decodeWithCodec(ex, track, fmt, durUs, sink, progress, cancelled)
        } catch (e: CancelledException) {
            throw e
        } catch (e: Exception) {
            fallback = true
        } finally {
            ex.release()
        }
        if (fallback || sink.pts.size < 2) {
            sink = Sink()
            fallback = true
            sampledFps = decodeWithRetriever(ctx, uri, durMs, sink, progress, cancelled)
        }
        if (sink.pts.size < 2) throw IllegalStateException("Could not read frames from this video")

        val swap = rot == 90 || rot == 270
        return detect(sink, if (swap) h else w, if (swap) w else h, rot, durMs, mime, sensitivity, fallback, sampledFps)
    }

    // ---------------------------------------------------------------- detection

    private fun rangeMed(a: FloatArray, from: Int, to: Int, tmp: FloatArray): Float {
        val f = max(0, from)
        val t = min(a.size - 1, to)
        if (t < f) return -1f
        var c = 0
        for (i in f..t) tmp[c++] = a[i]
        java.util.Arrays.sort(tmp, 0, c)
        return tmp[c / 2]
    }

    private fun runs(flag: BooleanArray, minLen: Int, maxLen: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        val n = flag.size
        var i = 0
        while (i < n) {
            if (!flag[i]) { i++; continue }
            val s = i
            var e = i
            var j = i + 1
            while (j < n) {
                if (flag[j]) { e = j; j++ }
                else if (j + 1 < n && flag[j + 1]) j++
                else break
            }
            val len = e - s + 1
            if (len in minLen..maxLen) out.add(intArrayOf(s, e))
            i = e + 1
        }
        return out
    }

    private fun rgbOf(y: Float, u: Float, v: Float): IntArray {
        val r = (y + 1.402f * (v - 128f)).roundToInt().coerceIn(0, 255)
        val g = (y - 0.344136f * (u - 128f) - 0.714136f * (v - 128f)).roundToInt().coerceIn(0, 255)
        val b = (y + 1.772f * (u - 128f)).roundToInt().coerceIn(0, 255)
        return intArrayOf(r, g, b)
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun detect(
        sink: Sink, width: Int, height: Int, rot: Int, durMsIn: Long, mime: String,
        sens: Int, fallback: Boolean, sampledFps: Double
    ): VideoAnalysis {
        val n = sink.pts.size
        // frame rate
        val deltas = FloatArray(n - 1) { (sink.pts[it + 1] - sink.pts[it]).toFloat() }
        java.util.Arrays.sort(deltas)
        val medDelta = max(1f, deltas[deltas.size / 2])
        val fps = if (fallback && sampledFps > 0) sampledFps else (1000.0 / medDelta).coerceIn(1.0, 240.0)
        val lastPts = sink.pts[n - 1]
        val durationMs = max(durMsIn, lastPts + medDelta.toLong())

        val s = sens.coerceIn(0, 2)
        val minAbs = floatArrayOf(22f, 16f, 11f)[s]
        val ratio = floatArrayOf(3.2f, 2.6f, 2.1f)[s]

        val my = sink.my.toArray()
        val mu = sink.mu.toArray()
        val mv = sink.mv.toArray()
        val dArr = sink.diff.toArray()
        val tmp = FloatArray(64)

        val bl = FloatArray(n)
        val ref2 = FloatArray(n)
        for (i in 0 until n) {
            val l = rangeMed(my, i - 10, i - 3, tmp)
            val r = rangeMed(my, i + 3, i + 10, tmp)
            bl[i] = if (l < 0 && r < 0) my[i] else if (l < 0) r else if (r < 0) l else (l + r) / 2f
            val l2 = rangeMed(my, i - 30, i - 12, tmp)
            val r2 = rangeMed(my, i + 12, i + 30, tmp)
            ref2[i] = if (l2 < 0 && r2 < 0) my[i] else max(l2, r2)
        }

        val flashFlag = BooleanArray(n) { my[it] - bl[it] > 38f && my[it] > 140f }
        val darkFlag = BooleanArray(n) {
            (ref2[it] > 30f && my[it] < 0.5f * ref2[it]) || (my[it] < 10f && ref2[it] > 25f)
        }
        val flashRuns = runs(flashFlag, 1, max(6, (0.4 * fps).roundToInt()))
        val darkRuns = runs(darkFlag, 3, max(6, (2.5 * fps).roundToInt()))

        val blocked = BooleanArray(n)
        val overlays = ArrayList<Overlay>()
        val eventCuts = ArrayList<Int>()

        fun addOverlay(isFlash: Boolean, s0: Int, e0: Int) {
            val refRun = if (isFlash) (bl[s0] + bl[e0]) / 2f else max(ref2[s0], ref2[e0])
            fun alpha(i: Int): Float =
                if (isFlash) ((my[i] - refRun) / max(1f, 255f - refRun)).coerceIn(0f, 1f)
                else (1f - my[i] / max(1f, refRun)).coerceIn(0f, 1f)
            val thr = if (isFlash) 0.05f else 0.04f
            val maxExt = if (isFlash) 4 else 12
            var s1 = s0
            var e1 = e0
            while (s1 > 0 && s0 - s1 < maxExt && alpha(s1 - 1) > thr) s1--
            while (e1 < n - 1 && e1 - e0 < maxExt && alpha(e1 + 1) > thr) e1++
            val from = max(0, s1 - 1)
            val to = min(n - 1, e1 + 1)
            val times = LongArray(to - from + 1)
            val alphas = FloatArray(to - from + 1)
            var peak = s1
            var peakA = -1f
            for (k in from..to) {
                times[k - from] = sink.pts[k]
                alphas[k - from] = if (k < s1 || k > e1) 0f else alpha(k)
                if (k in s1..e1 && alphas[k - from] > peakA) { peakA = alphas[k - from]; peak = k }
            }
            if (times.size < 3 || peakA < 0.05f) return
            for (k in max(0, s1 - 1)..min(n - 1, e1 + 1)) blocked[k] = true
            val color = if (isFlash) {
                val rgb = rgbOf(my[peak], mu[peak], mv[peak])
                val mx = max(rgb[0], max(rgb[1], rgb[2]))
                val mn = min(rgb[0], min(rgb[1], rgb[2]))
                if (mx - mn < 45 || mx == 0) argb(255, 255, 255)
                else argb(rgb[0] * 255 / mx, rgb[1] * 255 / mx, rgb[2] * 255 / mx)
            } else argb(0, 0, 0)
            overlays.add(Overlay(isFlash, color, times, alphas))
            // content change across the event => a cut at its peak
            if (s0 > 2 && e0 < n - 3) {
                val a = max(0, s1 - 2)
                val b = min(n - 1, e1 + 2)
                val d = thumbDiff(sink.thumbs.data, a * THUMB, sink.thumbs.data, b * THUMB)
                if (d >= minAbs * 0.9f) eventCuts.add(peak)
            }
        }
        for (r in flashRuns) addOverlay(true, r[0], r[1])
        for (r in darkRuns) addOverlay(false, r[0], r[1])

        // hard cuts
        val cuts = ArrayList<Int>()
        var last = -100
        for (i in 1 until n) {
            if (blocked[i]) continue
            val d = dArr[i]
            if (d < minAbs) continue
            var nb = 0f
            var cnt = 0
            for (k in -4..4) {
                if (k == 0) continue
                val j = i + k
                if (j in 1 until n && !blocked[j]) { nb += dArr[j]; cnt++ }
            }
            val avg = if (cnt > 0) nb / cnt else 0f
            if (d < max(avg * ratio, minAbs)) continue
            if (i - 1 >= 1 && d < dArr[i - 1]) continue
            if (i + 1 < n && d < dArr[i + 1]) continue
            if (i - last < 3 && cuts.isNotEmpty()) {
                if (d > dArr[cuts[cuts.size - 1]]) { cuts[cuts.size - 1] = i; last = i }
                continue
            }
            cuts.add(i)
            last = i
        }
        cuts.addAll(eventCuts)
        cuts.sort()
        val finalCuts = ArrayList<Int>()
        for (c in cuts) {
            if (c <= 0 || c >= n) continue
            if (finalCuts.isEmpty() || c - finalCuts[finalCuts.size - 1] >= 3) finalCuts.add(c)
        }

        // clips
        val bounds = ArrayList<Int>()
        bounds.add(0)
        bounds.addAll(finalCuts)
        bounds.add(n)
        val mdx = sink.mdx.toArray()
        val mdy = sink.mdy.toArray()
        val mds = sink.mds.toArray()
        val clips = ArrayList<Clip>()
        val strideOut = max(1, (fps / 30.0).roundToInt())
        val hw = max(2, (fps * 0.15).roundToInt())

        for (ci in 0 until bounds.size - 1) {
            val fs = bounds[ci]
            val fe = bounds[ci + 1] - 1
            val startMs = if (ci == 0) 0L else sink.pts[fs]
            var endMs = if (ci == bounds.size - 2) durationMs else sink.pts[bounds[ci + 1]]
            if (endMs <= startMs) endMs = startMs + 1

            var sy = 0f; var su = 0f; var sv = 0f
            for (f in fs..fe) { sy += my[f]; su += mu[f]; sv += mv[f] }
            val cntF = (fe - fs + 1).toFloat()
            val rgb = rgbOf(sy / cntF, su / cntF, sv / cntF)

            var motionOut: ClipMotion? = null
            val m = fe - fs + 1
            if (m >= 4) {
                val tx = FloatArray(m)
                val ty = FloatArray(m)
                val sc = FloatArray(m)
                sc[0] = 1f
                var cx = 0f
                var cy = 0f
                var cs = 1f
                for (k in 1 until m) {
                    val f = fs + k
                    val ok = !blocked[f]
                    val dx = if (ok) mdx[f] else 0f
                    val dy = if (ok) mdy[f] else 0f
                    val s2 = if (ok) mds[f].coerceIn(0.9f, 1.1f) else 1f
                    cx = s2 * cx + dx
                    cy = s2 * cy + dy
                    cs *= s2
                    tx[k] = cx / ML
                    ty[k] = cy / ML
                    sc[k] = cs.coerceIn(0.7f, 1.6f)
                }
                val hx = FloatArray(m)
                val hy = FloatArray(m)
                for (k in 0 until m) {
                    val a = max(0, k - hw)
                    val b = min(m - 1, k + hw)
                    var mx = 0f
                    var my2 = 0f
                    for (q in a..b) { mx += tx[q]; my2 += ty[q] }
                    val c = (b - a + 1).toFloat()
                    hx[k] = tx[k] - mx / c
                    hy[k] = ty[k] - my2 / c
                }
                fun p2p(a: FloatArray): Float {
                    var lo = Float.MAX_VALUE
                    var hi = -Float.MAX_VALUE
                    for (v in a) { if (v < lo) lo = v; if (v > hi) hi = v }
                    return hi - lo
                }
                val shake = max(p2p(hx), p2p(hy)) > 0.012f
                val zoom = p2p(sc) > 0.03f
                if (shake || zoom) {
                    val idx = ArrayList<Int>()
                    for (k in 0 until m) if (k % strideOut == 0 || k == m - 1) idx.add(k)
                    val times = LongArray(idx.size) { max(0L, sink.pts[fs + idx[it]] - startMs) }
                    motionOut = ClipMotion(
                        times,
                        if (shake) FloatArray(idx.size) { hx[idx[it]] } else null,
                        if (shake) FloatArray(idx.size) { hy[idx[it]] } else null,
                        if (zoom) FloatArray(idx.size) { sc[idx[it]] } else null
                    )
                }
            }
            clips.add(Clip(ci, startMs, endMs, argb(rgb[0], rgb[1], rgb[2]), motionOut))
        }

        val info = VideoInfo(width, height, rot, durationMs, fps, mime, n, fallback)
        return VideoAnalysis(info, clips, overlays.sortedBy { it.startMs })
    }
}
