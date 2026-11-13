package com.replica.app.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Extracts the audio track of the edit as a standalone file and analyses it:
 * spectral-flux onsets -> tempo (autocorrelation) -> phase-aligned beat grid.
 */
object AudioAnalyzer {
    private const val N = 1024
    private const val HOP = 512

    private class Fft {
        val window = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }
        val cosT = FloatArray(N / 2) { cos(2.0 * PI * it / N).toFloat() }
        val sinT = FloatArray(N / 2) { (-sin(2.0 * PI * it / N)).toFloat() }
        val re = FloatArray(N)
        val im = FloatArray(N)

        fun run() {
            var j = 0
            for (i in 1 until N) {
                var bit = N shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= N) {
                val half = len / 2
                val step = N / len
                var i = 0
                while (i < N) {
                    for (k in 0 until half) {
                        val wr = cosT[k * step]
                        val wi = sinT[k * step]
                        val a = i + k
                        val b = a + half
                        val xr = re[b] * wr - im[b] * wi
                        val xi = re[b] * wi + im[b] * wr
                        re[b] = re[a] - xr
                        im[b] = im[a] - xi
                        re[a] += xr
                        im[a] += xi
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }

    private class Flux {
        val fft = Fft()
        val buf = FloatArray(N)
        var fill = 0
        val prevMag = FloatArray(N / 2)
        var first = true
        val env = FloatList(4096)

        fun push(sample: Float) {
            buf[fill++] = sample
            if (fill == N) {
                process()
                System.arraycopy(buf, HOP, buf, 0, N - HOP)
                fill = N - HOP
            }
        }

        private fun process() {
            for (i in 0 until N) { fft.re[i] = buf[i] * fft.window[i]; fft.im[i] = 0f }
            fft.run()
            var flux = 0f
            for (k in 1 until N / 2) {
                val mag = ln(1.0 + 20.0 * sqrt((fft.re[k] * fft.re[k] + fft.im[k] * fft.im[k]).toDouble())).toFloat()
                if (!first) {
                    val d = mag - prevMag[k]
                    if (d > 0f) flux += d
                }
                prevMag[k] = mag
            }
            first = false
            env.add(flux)
        }
    }

    fun analyze(
        ctx: Context, uri: Uri, outDir: File, progress: (Float) -> Unit, cancelled: () -> Boolean
    ): AudioAnalysis {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val m = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("audio/")) { track = i; fmt = f; break }
        }
        if (track < 0 || fmt == null) {
            ex.release()
            return AudioAnalysis(false, 0, 0.0, 0f, LongArray(0), LongArray(0), null, "", "")
        }
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        ex.release()

        // 1) lossless extraction of the original audio stream when possible (AAC -> .m4a)
        var outFile: File? = null
        var outMime = "audio/mp4"
        var ext = "m4a"
        if (mime == "audio/mp4a-latm") {
            val f = File(outDir, "audio.m4a")
            if (remux(ctx, uri, track, f)) outFile = f
        }

        // 2) decode to PCM for analysis (and as WAV if remux was not possible)
        val wavFile = File(outDir, "audio.wav")
        val wantWav = outFile == null
        val ex2 = MediaExtractor()
        ex2.setDataSource(ctx, uri, null)
        ex2.selectTrack(track)
        val dec = MediaCodec.createDecoderByType(mime)
        val flux = Flux()
        var sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        var channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        var raf: RandomAccessFile? = null
        var pcmBytes = 0L
        var totalFrames = 0L
        try {
            if (wantWav) {
                if (wavFile.exists()) wavFile.delete()
                raf = RandomAccessFile(wavFile, "rw")
                raf.write(ByteArray(44))
            }
            dec.configure(fmt, null, null, 0)
            dec.start()
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var lastProg = 0L
            while (!outDone) {
                if (cancelled()) throw CancelledException()
                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val b = dec.getInputBuffer(ii)!!
                        val sz = ex2.readSampleData(b, 0)
                        if (sz < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            dec.queueInputBuffer(ii, 0, sz, ex2.sampleTime, 0)
                            ex2.advance()
                        }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (oi >= 0) {
                    if (info.size > 0) {
                        val ob = dec.getOutputBuffer(oi)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        if (raf != null) {
                            val bytes = ByteArray(info.size)
                            ob.duplicate().get(bytes)
                            raf.write(bytes)
                            pcmBytes += bytes.size
                        }
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val ch = max(1, channels)
                        val frames = sb.remaining() / ch
                        for (f in 0 until frames) {
                            var acc = 0f
                            for (c in 0 until ch) acc += sb.get(f * ch + c)
                            flux.push(acc / ch / 32768f)
                        }
                        totalFrames += frames
                    }
                    dec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    val nowMs = info.presentationTimeUs / 1000
                    if (durUs > 0 && nowMs - lastProg > 200) {
                        lastProg = nowMs
                        progress(min(1f, info.presentationTimeUs / durUs.toFloat()))
                    }
                }
            }
            if (raf != null) {
                val r = raf
                val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                hdr.put("RIFF".toByteArray()); hdr.putInt((36 + pcmBytes).toInt())
                hdr.put("WAVE".toByteArray()); hdr.put("fmt ".toByteArray())
                hdr.putInt(16); hdr.putShort(1); hdr.putShort(channels.toShort())
                hdr.putInt(sampleRate); hdr.putInt(sampleRate * channels * 2)
                hdr.putShort((channels * 2).toShort()); hdr.putShort(16)
                hdr.put("data".toByteArray()); hdr.putInt(pcmBytes.toInt())
                r.seek(0)
                r.write(hdr.array())
                outFile = wavFile
                outMime = "audio/x-wav"
                ext = "wav"
            }
        } finally {
            try { raf?.close() } catch (ignored: Exception) {}
            try { dec.stop() } catch (ignored: Exception) {}
            dec.release()
            ex2.release()
        }

        val env = flux.env.toArray()
        val hopSec = HOP.toDouble() / sampleRate
        val durationMs = if (totalFrames > 0) (totalFrames * 1000L / sampleRate) else durUs / 1000
        val res = analyzeEnvelope(env, hopSec)
        return AudioAnalysis(
            true, durationMs, res.bpm, res.confidence, res.beats, res.onsets, outFile, outMime, ext
        )
    }

    private fun remux(ctx: Context, uri: Uri, track: Int, out: File): Boolean {
        val ex = MediaExtractor()
        var mux: MediaMuxer? = null
        var started = false
        return try {
            ex.setDataSource(ctx, uri, null)
            ex.selectTrack(track)
            if (out.exists()) out.delete()
            mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val ti = mux.addTrack(ex.getTrackFormat(track))
            mux.start()
            started = true
            val buf = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            var firstPts = -1L
            while (true) {
                val sz = ex.readSampleData(buf, 0)
                if (sz < 0) break
                val t = ex.sampleTime
                if (firstPts < 0) firstPts = t
                info.offset = 0
                info.size = sz
                info.presentationTimeUs = t - firstPts
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
                mux.writeSampleData(ti, buf, info)
                ex.advance()
            }
            mux.stop()
            started = false
            out.length() > 0
        } catch (e: Exception) {
            try { if (started) mux?.stop() } catch (ignored: Exception) {}
            out.delete()
            false
        } finally {
            try { mux?.release() } catch (ignored: Exception) {}
            ex.release()
        }
    }

    private class EnvResult(val bpm: Double, val confidence: Float, val beats: LongArray, val onsets: LongArray)

    private fun analyzeEnvelope(env: FloatArray, hopSec: Double): EnvResult {
        val n = env.size
        if (n < 64) return EnvResult(0.0, 0f, LongArray(0), LongArray(0))

        // onset strength: flux minus local mean
        val o = FloatArray(n)
        val w = 8
        for (i in 0 until n) {
            val a = max(0, i - w)
            val b = min(n - 1, i + w)
            var s = 0f
            for (k in a..b) s += env[k]
            o[i] = max(0f, env[i] - 1.05f * s / (b - a + 1))
        }
        var mean = 0f
        for (v in o) mean += v
        mean /= n
        var varSum = 0f
        for (v in o) varSum += (v - mean) * (v - mean)
        val sd = sqrt(varSum / n)

        // peak picking
        val onsetIdx = ArrayList<Int>()
        val thr = mean + 0.6f * sd
        var lastPeak = -100
        val minGap = max(1, (0.08 / hopSec).roundToInt())
        for (i in 2 until n - 2) {
            if (o[i] < thr) continue
            var isMax = true
            for (k in -3..3) {
                val j = i + k
                if (j in 0 until n && o[j] > o[i]) { isMax = false; break }
            }
            if (!isMax) continue
            if (i - lastPeak < minGap) continue
            onsetIdx.add(i)
            lastPeak = i
        }
        val onsetsMs = LongArray(onsetIdx.size) { (onsetIdx[it] * hopSec * 1000).toLong() }

        // tempo by autocorrelation
        val rate = 1.0 / hopSec
        val lagMin = max(2, (rate * 60.0 / 200.0).roundToInt())
        val lagMax = min(n / 2, (rate * 60.0 / 60.0).roundToInt())
        if (lagMax <= lagMin + 2) return EnvResult(0.0, 0f, LongArray(0), onsetsMs)
        val ac = FloatArray(lagMax + 2)
        for (lag in 0..lagMax + 1) {
            if (lag >= n) break
            var s = 0f
            for (i in 0 until n - lag) s += o[i] * o[i + lag]
            ac[lag] = s / (n - lag)
        }
        var bestLag = -1
        var bestScore = 0f
        for (lag in lagMin..lagMax) {
            val bpm = 60.0 * rate / lag
            val prior = exp(-0.5 * (log2(bpm / 125.0) / 0.9).let { it * it }).toFloat()
            val sc = ac[lag] * prior
            if (sc > bestScore && ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1]) {
                bestScore = sc; bestLag = lag
            }
        }
        if (bestLag < 0 || ac[0] <= 0f) return EnvResult(0.0, 0f, LongArray(0), onsetsMs)
        val l = ac[bestLag - 1]
        val c = ac[bestLag]
        val r = ac[bestLag + 1]
        val d = l - 2f * c + r
        val frac = if (d < -1e-9f) (0.5f * (l - r) / d).coerceIn(-0.5f, 0.5f) else 0f
        val period = bestLag + frac.toDouble()
        val bpm = 60.0 * rate / period
        val confidence = (ac[bestLag] / ac[0]).coerceIn(0f, 1f)
        if (confidence < 0.08f) return EnvResult(0.0, confidence, LongArray(0), onsetsMs)

        // beat phase
        var bestPhase = 0.0
        var bestPS = -1f
        var ph = 0.0
        while (ph < period) {
            var s = 0f
            var t = ph
            while (t < n - 1) {
                val i0 = t.toInt()
                val fr = (t - i0).toFloat()
                s += o[i0] * (1f - fr) + o[min(n - 1, i0 + 1)] * fr
                t += period
            }
            if (s > bestPS) { bestPS = s; bestPhase = ph }
            ph += 0.5
        }
        // grid, each beat snapped to the closest onset within +-45 ms
        val snap = 0.045 / hopSec
        val beats = ArrayList<Long>()
        var t = bestPhase
        var oi = 0
        while (t < n) {
            var bestT = t
            var bestD = snap + 1
            while (oi > 0 && onsetIdx[oi - 1] > t - snap) oi--
            var k = oi
            while (k < onsetIdx.size && onsetIdx[k] <= t + snap) {
                val dd = abs(onsetIdx[k] - t)
                if (dd <= snap && dd < bestD) { bestD = dd; bestT = onsetIdx[k].toDouble() }
                k++
            }
            beats.add((bestT * hopSec * 1000).toLong())
            t += period
        }
        return EnvResult(bpm, confidence, beats.toLongArray(), onsetsMs)
    }
}
