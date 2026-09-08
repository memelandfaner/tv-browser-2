package com.example.safeerbrowser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast

class DownloadHandler(private val context: Context) {

    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        try {
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                if (userAgent != null) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setDescription(UiText.get(R.string.ui_downloading))
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                allowScanningByMediaScanner()
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)
            Toast.makeText(context, UiText.get(R.string.ui_download_file , filename), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, UiText.get(R.string.ui_download_error , e.message), Toast.LENGTH_LONG).show()
        }
    }
}
