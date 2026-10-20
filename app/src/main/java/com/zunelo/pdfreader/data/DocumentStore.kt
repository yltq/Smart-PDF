package com.zunelo.pdfreader.data

import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.*
import com.zunelo.pdfreader.common.*
import com.zunelo.pdfreader.data.*
import com.zunelo.pdfreader.dialog.*
import com.zunelo.pdfreader.home.*
import com.zunelo.pdfreader.onboarding.*
import com.zunelo.pdfreader.pdf.*
import com.zunelo.pdfreader.permission.*
import com.zunelo.pdfreader.startup.*
import com.zunelo.pdfreader.tools.*
import com.zunelo.pdfreader.viewer.*

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class DocumentType(val label: String) {
    PDF("PDF"), WORD("Word"), EXCEL("Excel"), PPT("PPT"), OTHER("Other")
}

data class DocumentItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    val mime: String
) {
    val type: DocumentType
        get() = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "pdf" -> DocumentType.PDF
            "doc", "docx" -> DocumentType.WORD
            "xls", "xlsx" -> DocumentType.EXCEL
            "ppt", "pptx" -> DocumentType.PPT
            else -> DocumentType.OTHER
        }
}

object DocumentStore {
    private const val PREFS = "pdf_reader_store"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_RECENTS = "recents"

    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun scan(context: Context): List<DocumentItem> {
        if (!hasAccess(context)) return emptyList()
        return runCatching {
            Environment.getExternalStorageDirectory().walkTopDown()
                .onEnter { directory -> directory == Environment.getExternalStorageDirectory() || (!directory.isHidden && directory.name != "Android") }
                .filter { it.isFile && isSupported(it.name, inferredMime(it.name)) }
                .map { file -> DocumentItem(Uri.fromFile(file), file.name, file.length(), file.lastModified(), inferredMime(file.name)) }
                .sortedByDescending { it.modified }
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun isSupported(name: String, mime: String): Boolean {
        if (mime == "application/pdf") return true
        return name.substringAfterLast('.', "").lowercase(Locale.US) in
            setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
    }

    fun isFavorite(context: Context, item: DocumentItem): Boolean =
        context.getSharedPreferences(PREFS, 0).getStringSet(KEY_FAVORITES, emptySet())
            ?.contains(item.uri.toString()) == true

    fun toggleFavorite(context: Context, item: DocumentItem): Boolean {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val values = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val added = if (values.remove(item.uri.toString())) false else {
            values.add(item.uri.toString())
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, values).apply()
        return added
    }

    fun markRecent(context: Context, item: DocumentItem) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val values = prefs.getStringSet(KEY_RECENTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        values.removeAll { it.substringAfter('|') == item.uri.toString() }
        values += "${System.currentTimeMillis()}|${item.uri}"
        prefs.edit().putStringSet(KEY_RECENTS, values.sortedDescending().take(50).toSet()).apply()
    }

    fun recentUris(context: Context): List<String> = context.getSharedPreferences(PREFS, 0)
        .getStringSet(KEY_RECENTS, emptySet()).orEmpty().sortedDescending().map { it.substringAfter('|') }

    fun favoriteUris(context: Context): Set<String> = context.getSharedPreferences(PREFS, 0)
        .getStringSet(KEY_FAVORITES, emptySet()).orEmpty()

    fun renameItem(context: Context, item: DocumentItem, newName: String): DocumentItem? = runCatching {
        if (item.uri.scheme == "file") {
            val source = File(item.uri.path!!)
            val target = File(source.parentFile, newName)
            if (source.absolutePath == target.absolutePath) return@runCatching item
            if (target.exists()) return@runCatching null
            if (!source.renameTo(target)) return@runCatching null
            val renamed = item.copy(uri = Uri.fromFile(target), name = newName, size = target.length(), modified = target.lastModified(), mime = inferredMime(newName))
            replaceStoredUri(context, item.uri.toString(), renamed.uri.toString())
            renamed
        } else null
    }.getOrNull()

    fun nameExists(item: DocumentItem, newName: String): Boolean {
        if (item.uri.scheme != "file") return false
        val source = File(item.uri.path ?: return false)
        val target = File(source.parentFile, newName)
        return source.absolutePath != target.absolutePath && target.exists()
    }

    fun delete(context: Context, item: DocumentItem): Boolean = runCatching {
        if (item.uri.scheme != "file" || !File(item.uri.path!!).delete()) return@runCatching false
        removeStoredUri(context, item.uri.toString())
        true
    }.getOrDefault(false)

    private fun replaceStoredUri(context: Context, oldUri: String, newUri: String) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toMutableSet()
        if (favorites.remove(oldUri)) favorites += newUri
        val recents = prefs.getStringSet(KEY_RECENTS, emptySet()).orEmpty().mapTo(mutableSetOf()) {
            if (it.substringAfter('|') == oldUri) "${it.substringBefore('|')}|$newUri" else it
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).putStringSet(KEY_RECENTS, recents).apply()
    }

    private fun removeStoredUri(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().filterTo(mutableSetOf()) { it != uri }
        val recents = prefs.getStringSet(KEY_RECENTS, emptySet()).orEmpty().filterTo(mutableSetOf()) { it.substringAfter('|') != uri }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).putStringSet(KEY_RECENTS, recents).apply()
    }

    fun cacheFile(context: Context, item: DocumentItem): File {
        if (item.uri.scheme == "file") return File(item.uri.path!!)
        val extension = item.name.substringAfterLast('.', "bin")
        val file = File(context.cacheDir, "open_${item.uri.toString().hashCode()}.$extension")
        context.contentResolver.openInputStream(item.uri).use { input ->
            file.outputStream().use { output -> input?.copyTo(output) }
        }
        return file
    }

    fun openFileDescriptor(context: Context, item: DocumentItem): ParcelFileDescriptor? =
        if (item.uri.scheme == "file") ParcelFileDescriptor.open(File(item.uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        else context.contentResolver.openFileDescriptor(item.uri, "r")

    fun shareUri(context: Context, item: DocumentItem): Uri = if (item.uri.scheme == "file") {
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(item.uri.path!!))
    } else item.uri

    fun inferredMime(name: String): String = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.US))
        ?: "application/octet-stream"

    fun subtitle(context: Context, item: DocumentItem): String {
        val date = if (item.modified > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.modified)) else context.getString(R.string.unknown_date)
        return "$date  ${formatSize(item.size)}"
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
