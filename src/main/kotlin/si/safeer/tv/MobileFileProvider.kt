package si.safeer.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class MobileFileProvider : ContentProvider() {

    private lateinit var authority: String

    override fun onCreate(): Boolean {
        return true
    }

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        authority = info.authority
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = getFileForUri(uri)
        val proj = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(proj, 1)
        val row = cursor.newRow()
        for (col in proj) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val file = getFileForUri(uri)
        val ext = file.extension
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = getFileForUri(uri)
        val fileMode = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, fileMode)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getFileForUri(uri: Uri): File {
        val path = uri.path ?: throw FileNotFoundException("Prazen URI")
        val ctx = context ?: throw FileNotFoundException("Context ni na voljo")
        
        val file = if (path.startsWith("/external_files/")) {
            File(ctx.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile, path.removePrefix("/external_files/"))
        } else if (path.startsWith("/cache_files/")) {
            File(ctx.cacheDir, path.removePrefix("/cache_files/"))
        } else {
            File(ctx.filesDir, path)
        }
        
        if (!file.exists()) {
            file.parentFile?.mkdirs()
        }
        return file
    }
}
