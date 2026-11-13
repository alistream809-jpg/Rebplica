package com.replica.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

object MediaUtil {
    const val AM_PACKAGE = "com.alightcreative.motion"

    fun queryName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "video"
            }
        } catch (ignored: Exception) {}
        return uri.lastPathSegment ?: "video"
    }

    fun querySize(ctx: Context, uri: Uri): Long {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getLong(0)
            }
        } catch (ignored: Exception) {}
        return 0L
    }

    /** Turns a document-picker URI into the stable MediaStore URI when possible. */
    fun resolveVideoUri(ctx: Context, uri: Uri): String {
        try {
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                if (id.startsWith("video:")) {
                    val n = id.substringAfter(':').toLong()
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, n).toString()
                }
            }
        } catch (ignored: Exception) {}
        return uri.toString()
    }

    /** Copies the extracted song to Music/Replica so other apps (Alight Motion) can read it. */
    fun saveAudio(ctx: Context, file: File, displayName: String, mime: String): String? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Replica")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri.toString()
        } catch (ignored: Exception) {
            null
        }
    }

    fun saveXmlToDownloads(ctx: Context, file: File, displayName: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "text/xml")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Replica")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        } catch (ignored: Exception) {
            false
        }
    }

    fun fileUri(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)

    fun isAlightInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(AM_PACKAGE, 0); true
    } catch (ignored: PackageManager.NameNotFoundException) { false }

    fun share(ctx: Context, file: File) {
        val uri = fileUri(ctx, file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 0 = handed to Alight Motion, 1 = Alight Motion launched, file saved for manual import, 2 = not installed */
    fun openInAlight(ctx: Context, file: File, displayName: String): Int {
        if (!isAlightInstalled(ctx)) return 2
        val uri = fileUri(ctx, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/xml")
            setPackage(AM_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(view); return 0 } catch (ignored: Exception) {}
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage(AM_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(send); return 0 } catch (ignored: Exception) {}
        saveXmlToDownloads(ctx, file, displayName)
        val launch = ctx.packageManager.getLaunchIntentForPackage(AM_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(launch) } catch (ignored: Exception) {}
        }
        return 1
    }
}
