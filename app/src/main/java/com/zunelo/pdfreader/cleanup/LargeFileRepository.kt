package com.zunelo.pdfreader.cleanup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.media.MediaScannerConnection
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.data.DocumentStore
import java.io.File
import java.util.Locale

private const val MEGABYTE = 1024L * 1024L

enum class LargeFileCategory(val labelRes: Int) {
    DOCUMENT(R.string.large_file_type_documents),
    IMAGE(R.string.large_file_type_images),
    VIDEO(R.string.large_file_type_videos),
    AUDIO(R.string.large_file_type_audio),
    ARCHIVE(R.string.large_file_type_archives),
    OTHER(R.string.large_file_type_other)
}

data class LargeFileItem(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val category: LargeFileCategory
)

data class LargeFileDeleteResult(
    val deletedPaths: Set<String>,
    val failedCount: Int,
    val freedBytes: Long
)

object LargeFileRepository {
    const val MINIMUM_FILE_SIZE = 10L * MEGABYTE

    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "dng")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk")

    fun scan(context: Context): List<LargeFileItem> {
        if (!DocumentStore.hasAccess(context)) return emptyList()
        val root = Environment.getExternalStorageDirectory()
        val rootPath = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
        val rootPrefix = rootPath + File.separator
        val pending = ArrayDeque<File>().apply { add(root) }
        val visited = hashSetOf<String>()
        val result = mutableListOf<LargeFileItem>()

        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            val canonicalDirectory = runCatching { directory.canonicalPath }.getOrNull() ?: continue
            if (canonicalDirectory != rootPath && !canonicalDirectory.startsWith(rootPrefix)) continue
            if (!visited.add(canonicalDirectory)) continue
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
            children.forEach { child ->
                when {
                    child.isDirectory && canEnter(directory, child) -> pending.add(child)
                    child.isFile -> {
                        val length = runCatching { child.length() }.getOrDefault(0L)
                        if (length >= MINIMUM_FILE_SIZE) {
                            result += LargeFileItem(
                                path = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath),
                                name = child.name,
                                size = length,
                                modified = runCatching { child.lastModified() }.getOrDefault(0L),
                                category = categoryOf(child.name)
                            )
                        }
                    }
                }
            }
        }

        return result.sortedWith(compareByDescending<LargeFileItem> { it.size }.thenBy { it.path.lowercase(Locale.US) })
    }

    fun delete(context: Context, items: Collection<LargeFileItem>): LargeFileDeleteResult {
        if (!DocumentStore.canModifyStorage(context)) {
            return LargeFileDeleteResult(emptySet(), items.size, 0L)
        }
        val storageRoot = runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull()
            ?: return LargeFileDeleteResult(emptySet(), items.size, 0L)
        val rootPrefix = storageRoot + File.separator
        val deleted = linkedSetOf<String>()
        var failed = 0
        var freed = 0L

        items.sortedBy { it.path }.forEach { item ->
            val file = File(item.path)
            val canonical = runCatching { file.canonicalPath }.getOrNull()
            val safeTarget = canonical != null && canonical.startsWith(rootPrefix) && file.isFile
            if (safeTarget && runCatching { file.delete() }.getOrDefault(false)) {
                deleted += item.path
                freed += item.size
                DocumentStore.forgetUri(context, Uri.fromFile(file).toString())
            } else {
                failed++
            }
        }
        if (deleted.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, deleted.toTypedArray(), null, null)
        }
        return LargeFileDeleteResult(deleted, failed, freed)
    }

    private fun canEnter(parent: File, directory: File): Boolean {
        if (directory.isHidden || directory.name.startsWith('.')) return false
        if (parent.name.equals("Android", true) && directory.name.lowercase(Locale.US) in setOf("data", "obb")) return false
        return true
    }

    fun categoryOf(name: String): LargeFileCategory = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        in documentExtensions -> LargeFileCategory.DOCUMENT
        in imageExtensions -> LargeFileCategory.IMAGE
        in videoExtensions -> LargeFileCategory.VIDEO
        in audioExtensions -> LargeFileCategory.AUDIO
        in archiveExtensions -> LargeFileCategory.ARCHIVE
        else -> LargeFileCategory.OTHER
    }
}
