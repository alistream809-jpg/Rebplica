package com.replica.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

data class ProjectMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val durationMs: Long,
    val clips: Int,
    val flashes: Int,
    val fades: Int,
    val motionClips: Int,
    val bpm: Double,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hasAudio: Boolean
)

object ProjectStore {
    private lateinit var root: File

    fun init(ctx: Context) {
        root = File(ctx.filesDir, "projects")
        root.mkdirs()
    }

    fun dir(id: String) = File(root, id)
    fun xmlFile(id: String) = File(dir(id), "project.xml")
    fun thumbFile(id: String) = File(dir(id), "thumb.jpg")

    fun save(m: ProjectMeta) {
        val o = JSONObject()
        o.put("id", m.id); o.put("name", m.name); o.put("createdAt", m.createdAt)
        o.put("durationMs", m.durationMs); o.put("clips", m.clips); o.put("flashes", m.flashes)
        o.put("fades", m.fades); o.put("motionClips", m.motionClips); o.put("bpm", m.bpm)
        o.put("width", m.width); o.put("height", m.height); o.put("fps", m.fps)
        o.put("hasAudio", m.hasAudio)
        File(dir(m.id), "meta.json").writeText(o.toString())
    }

    fun list(): List<ProjectMeta> {
        val out = ArrayList<ProjectMeta>()
        val dirs = root.listFiles() ?: return out
        for (d in dirs) {
            val f = File(d, "meta.json")
            if (!f.exists()) continue
            try {
                val o = JSONObject(f.readText())
                out.add(
                    ProjectMeta(
                        o.getString("id"), o.getString("name"), o.getLong("createdAt"),
                        o.getLong("durationMs"), o.getInt("clips"), o.getInt("flashes"),
                        o.getInt("fades"), o.optInt("motionClips", 0), o.getDouble("bpm"),
                        o.getInt("width"), o.getInt("height"), o.getDouble("fps"),
                        o.getBoolean("hasAudio")
                    )
                )
            } catch (ignored: Exception) {
            }
        }
        return out.sortedByDescending { it.createdAt }
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }
}
