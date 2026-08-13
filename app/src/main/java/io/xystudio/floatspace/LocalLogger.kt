package io.xystudio.floatspace

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalLogger {
    private lateinit var directory: File
    private val lock = Any()
    private val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun initialize(context: Context) {
        val media = context.externalMediaDirs.firstOrNull() ?: context.filesDir
        directory = File(media, "logs").apply { mkdirs() }
        info("Logger dimulai; folder=${directory.absolutePath}")
    }

    fun info(message: String) = append("info.txt", "INFO", message)
    fun error(message: String, throwable: Throwable? = null) =
        append("error.txt", "ERROR", message + (throwable?.let { "\n${it.stackTraceToString()}" } ?: ""))
    fun crash(throwable: Throwable) = append("crash.txt", "CRASH", throwable.stackTraceToString())

    fun path(): String = if (::directory.isInitialized) directory.absolutePath else "Belum tersedia"

    private fun append(file: String, level: String, message: String) {
        if (!::directory.isInitialized) return
        synchronized(lock) {
            runCatching {
                File(directory, file).appendText("[${date.format(Date())}] [$level] $message\n\n")
            }
        }
    }
}
