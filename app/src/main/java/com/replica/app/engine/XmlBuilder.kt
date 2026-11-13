package com.replica.app.engine

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Writes the analysis as an Alight Motion scene (XML).
 * Structure follows the community-documented Alight Motion XML format:
 * scene -> media declarations -> bookmarks -> layers (back to front).
 */
object XmlBuilder {

    class Params(
        val title: String,
        val video: VideoAnalysis,
        val audio: AudioAnalysis,
        val audioUri: String?,
        val videoUri: String?,
        val videoMime: String,
        val videoSize: Long,
        val beatMarkers: Boolean,
        val referenceLayer: Boolean
    )

    class CanvasSize(val w: Int, val h: Int)

    fun canvasFor(info: VideoInfo): CanvasSize {
        val longest = max(info.width, info.height).toFloat()
        val k = if (longest > 1920f) 1920f / longest else 1f
        fun even(v: Float): Int {
            val r = v.roundToInt()
            return max(2, r - (r % 2))
        }
        return CanvasSize(even(info.width * k), even(info.height * k))
    }

    private fun f6(v: Double): String = String.format(Locale.US, "%.6f", v)
    private fun f6(v: Float): String = String.format(Locale.US, "%.6f", v.toDouble())

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun hex(argb: Int): String = String.format(Locale.US, "#%08X", argb)

    private fun sig(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { String.format("%02x", it) }
    }

    private fun fpsString(fps: Double): String {
        val f = min(60.0, max(1.0, fps))
        return if (abs(f - f.roundToInt()) < 0.02) f.roundToInt().toString()
        else String.format(Locale.US, "%.2f", f)
    }

    fun build(p: Params): String {
        val v = p.video
        val cv = canvasFor(v.info)
        val total = v.info.durationMs
        val cx = cv.w / 2.0
        val cy = cv.h / 2.0
        val sb = StringBuilder(64 * 1024)
        var nextId = 1

        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<scene title=\"").append(esc(p.title)).append("\"")
            .append(" width=\"").append(cv.w).append("\" height=\"").append(cv.h).append("\"")
            .append(" exportWidth=\"").append(cv.w).append("\" exportHeight=\"").append(cv.h).append("\"")
            .append(" bgcolor=\"#FF000000\" totalTime=\"").append(total).append("\"")
            .append(" fps=\"").append(fpsString(v.info.fps)).append("\"")
            .append(" modifiedTime=\"").append(System.currentTimeMillis()).append("\"")
            .append(" ffver=\"106\" amplatform=\"android\">\n")

        // ---- media declarations
        val audioDur = if (p.audio.hasAudio) min(p.audio.durationMs, total) else 0L
        if (p.audio.hasAudio && p.audioUri != null) {
            sb.append("  <media uri=\"").append(esc(p.audioUri)).append("\" filename=\"")
                .append(esc("audio." + p.audio.extension)).append("\" title=\"Edit audio\" type=\"")
                .append(esc(p.audio.mime)).append("\" size=\"").append(p.audio.file?.length() ?: 0L)
                .append("\" duration=\"").append(p.audio.durationMs).append("\" sig=\"")
                .append(sig(p.audioUri)).append("\"/>\n")
        }
        if (p.referenceLayer && p.videoUri != null) {
            sb.append("  <media uri=\"").append(esc(p.videoUri)).append("\" filename=\"reference.mp4\" title=\"Reference edit\" type=\"")
                .append(esc(p.videoMime)).append("\" size=\"").append(p.videoSize)
                .append("\" duration=\"").append(total).append("\" sig=\"").append(sig(p.videoUri))
                .append("\" orientation=\"").append(v.info.rotation).append("\" width=\"").append(v.info.width)
                .append("\" height=\"").append(v.info.height).append("\"/>\n")
        }

        // ---- bookmarks (beat markers)
        if (p.beatMarkers && p.audio.hasAudio) {
            val marks = ArrayList<Long>()
            if (p.audio.bpm > 0 && p.audio.beatsMs.isNotEmpty()) {
                for (b in p.audio.beatsMs) if (b in 0..total) marks.add(b)
            } else {
                var last = -1000L
                for (o in p.audio.onsetsMs) if (o in 0..total && o - last >= 250) { marks.add(o); last = o }
            }
            val step = if (marks.size > 800) (marks.size / 800) + 1 else 1
            var i = 0
            while (i < marks.size) {
                sb.append("  <bookmark t=\"").append(marks[i]).append("\" audio=\"1\"/>\n")
                i += step
            }
        }

        // ---- layer helpers
        fun sizeProp() = "    <property name=\"size\" type=\"vec2\" value=\"${cv.w}.000000,${cv.h}.000000\"/>\n"

        fun staticLoc(): String = "      <location value=\"${f6(cx)},${f6(cy)},0.000000\"/>\n"

        fun rectLayer(
            label: String, start: Long, end: Long, color: Int, transform: String, hidden: Boolean = false
        ) {
            sb.append("  <shape id=\"").append(nextId++).append("\" label=\"").append(esc(label))
                .append("\" startTime=\"").append(start).append("\" endTime=\"").append(end).append("\"")
            if (hidden) sb.append(" hidden=\"true\"")
            sb.append(" fillType=\"color\" mediaFillMode=\"stretch\" s=\".rect\">\n")
            sb.append("    <transform>\n").append(transform).append("    </transform>\n")
            sb.append("    <fillColor value=\"").append(hex(color)).append("\"/>\n")
            sb.append(sizeProp())
            sb.append("  </shape>\n")
        }

        // ---- reference layer (hidden, muted) at the very back
        if (p.referenceLayer && p.videoUri != null) {
            sb.append("  <shape id=\"").append(nextId++)
                .append("\" label=\"Reference edit (hidden)\" startTime=\"0\" endTime=\"").append(total)
                .append("\" hidden=\"true\" fillType=\"media\" fillVideo=\"").append(esc(p.videoUri))
                .append("\" mediaFillMode=\"stretch\" inTime=\"0\" outTime=\"").append(total).append("\" s=\".rect\">\n")
            sb.append("    <transform>\n").append(staticLoc()).append("    </transform>\n")
            sb.append("    <gain value=\"0.000000\"/>\n")
            sb.append(sizeProp())
            sb.append("  </shape>\n")
        }

        // ---- clips (placeholders carrying the measured motion)
        for (c in v.clips) {
            val m = c.motion
            val t = StringBuilder()
            if (m == null) {
                t.append(staticLoc())
            } else {
                t.append("      <location>\n")
                for (k in m.timesMs.indices) {
                    val ox = if (m.offX != null) m.offX[k] * cv.w else 0f
                    val oy = if (m.offY != null) m.offY[k] * cv.h else 0f
                    t.append("        <kf t=\"").append(f6(m.timesMs[k] / 1000.0)).append("\" v=\"")
                        .append(f6(cx + ox)).append(",").append(f6(cy + oy)).append(",0.000000\"/>\n")
                }
                t.append("      </location>\n")
                if (m.scale != null) {
                    t.append("      <scale>\n")
                    for (k in m.timesMs.indices) {
                        val s = m.scale[k]
                        t.append("        <kf t=\"").append(f6(m.timesMs[k] / 1000.0)).append("\" v=\"")
                            .append(f6(s)).append(",").append(f6(s)).append("\"/>\n")
                    }
                    t.append("      </scale>\n")
                }
            }
            val label = "Clip " + String.format(Locale.US, "%02d", c.index + 1) + " (replace media)"
            rectLayer(label, c.startMs, c.endMs, c.color, t.toString())
        }

        // ---- overlays: dips to black first, flashes on top
        fun overlayTransform(o: Overlay): String {
            val t = StringBuilder()
            t.append(staticLoc())
            t.append("      <opacity>\n")
            for (k in o.timesMs.indices) {
                t.append("        <kf t=\"").append(f6((o.timesMs[k] - o.startMs) / 1000.0)).append("\" v=\"")
                    .append(f6(o.alphas[k])).append("\"/>\n")
            }
            t.append("      </opacity>\n")
            return t.toString()
        }
        var nDark = 0
        var nFlash = 0
        for (o in v.overlays) if (!o.isFlash) {
            nDark++
            val end = max(o.endMs, o.startMs + 1)
            rectLayer("Fade to black $nDark", o.startMs, end, o.color, overlayTransform(o))
        }
        for (o in v.overlays) if (o.isFlash) {
            nFlash++
            val end = max(o.endMs, o.startMs + 1)
            rectLayer("Flash $nFlash", o.startMs, end, o.color, overlayTransform(o))
        }

        // ---- the edit's own song
        if (p.audio.hasAudio && p.audioUri != null && audioDur > 0) {
            sb.append("  <audio id=\"").append(nextId++).append("\" label=\"Edit audio\" startTime=\"0\" endTime=\"")
                .append(audioDur).append("\" src=\"").append(esc(p.audioUri))
                .append("\" inTime=\"0\" outTime=\"").append(audioDur).append("\"/>\n")
        }

        sb.append("</scene>\n")
        return sb.toString()
    }
}
