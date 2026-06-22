package com.subscheck.apk.output

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Saves output to the Download folder on Android.
 */
object OutputWriter {

    private const val TAG = "OutputWriter"
    private const val DIR_NAME = "subs-check"

    /**
     * Save the TXT file to the Download/subs-check folder.
     * Automatically overwrites existing file.
     * Returns the file path.
     */
    fun saveToFile(context: Context, content: String, fileName: String? = null): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val subsDir = File(downloadsDir, DIR_NAME)
            if (!subsDir.exists()) {
                subsDir.mkdirs()
            }

            val actualFileName = fileName ?: "subs-check-${getTimeStamp()}.txt"
            val file = File(subsDir, actualFileName)

            // Overwrite if exists
            file.writeText(content)

            Log.i(TAG, "File saved to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file: ${e.message}", e)
            null
        }
    }

    /**
     * Get the subs-check output directory path.
     */
    fun getOutputDir(context: Context): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, DIR_NAME)
    }

    private fun getTimeStamp(): String {
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    }
}
