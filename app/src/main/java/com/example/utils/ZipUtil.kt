package com.example.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtil {
    fun createZipFromUris(context: Context, uris: List<Uri>, zipFileName: String): File? {
        if (uris.isEmpty()) return null
        
        val zipFile = File(context.cacheDir, "$zipFileName.zip")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for (uri in uris) {
                    val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                    context.contentResolver.openInputStream(uri)?.use { fis ->
                        BufferedInputStream(fis).use { bis ->
                            val entry = ZipEntry(fileName)
                            zos.putNextEntry(entry)
                            bis.copyTo(zos, 1024 * 8)
                            zos.closeEntry()
                        }
                    }
                }
            }
            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
