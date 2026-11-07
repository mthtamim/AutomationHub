package com.mthtamim.phonelinkflows

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.4.0 crash catcher: the launch crash leaves no visible stack trace
 * on the phone, so the default uncaught-exception handler writes the
 * full trace to Downloads/AutomationHub-crash.txt before the process
 * dies. The user opens that file and pastes the text back, which names
 * the exact crashing line. Installed first in LocalizedActivity.onCreate
 * so it covers every activity launch (MainActivity and LockActivity).
 * If no file appears after a crash, the crash happened before any
 * activity's onCreate — that is itself diagnostic.
 */
object CrashHandler {

    private const val FILE_NAME = "AutomationHub-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext, throwable)
            } catch (_: Exception) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun writeReport(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            append("AutomationHub crash report\n")
            append("time: ").append(stamp).append('\n')
            append("app: 1.4.4 (vc15)\n")
            append("android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n')
            append('\n')
            append(sw.toString())
        }
        val resolver = context.contentResolver
        // Keep one file with a stable name: delete any previous report.
        try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                arrayOf(FILE_NAME), null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns._ID + "=?",
                        arrayOf(id.toString())
                    )
                }
            }
        } catch (_: Exception) {
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return
        resolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    }
}
