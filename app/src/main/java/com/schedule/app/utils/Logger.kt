package com.schedule.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

object Logger {

    private const val TAG = "ScheduleApp"
    private const val LOG_FOLDER_NAME = "расписаниеnative"
    private const val LOG_FILE_PREFIX = "sapp_log_"

    // Настройки
    var enabled = true
    var fileLogEnabled = false
    var logLevel = LogLevel.VERBOSE
    private var context: Context? = null
    private var logFile: File? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    enum class LogLevel(val value: Int) {
        VERBOSE(2), DEBUG(3), INFO(4), WARN(5), ERROR(6)
    }

    // Инициализация: передаём Context
    fun init(context: Context) {
        this.context = context.applicationContext
        prepareLogFile()
    }

    private fun prepareLogFile() {
        if (!fileLogEnabled) return

        val targetFile = getTargetLogFile() ?: run {
            // Если не удалось получить файл, отключаем запись
            fileLogEnabled = false
            Log.e(TAG, "Failed to create log file, file logging disabled")
            return
        }

        logFile = targetFile
        logFile?.parentFile?.mkdirs()
        writeToFile("=== Log started at ${getCurrentTime()} ===")
    }

    private fun getTargetLogFile(): File? {
        val ctx = context ?: return null

        // Проверяем разрешения для Android 10 и ниже
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ – нет разрешения на прямой доступ к Download, используем MediaStore или SAF.
            // Для простоты будем использовать внутреннюю папку приложения, но уведомим пользователя.
            // Можно запросить разрешение через SAF, но это сложнее.
            false
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        return if (hasPermission) {
            // Используем устаревший метод, но он работает на многих устройствах
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, LOG_FOLDER_NAME)
            if (!targetDir.exists()) targetDir.mkdirs()
            File(targetDir, "${LOG_FILE_PREFIX}${System.currentTimeMillis()}.txt")
        } else {
            // Запасной вариант: внутренняя папка приложения
            Log.w(TAG, "No permission to write to Download, using internal app folder")
            val internalDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            File(internalDir, "${LOG_FILE_PREFIX}${System.currentTimeMillis()}.txt")
        }
    }

    private fun getCurrentTime(): String = dateFormat.format(Date())

    private fun shouldLog(level: LogLevel): Boolean = enabled && level.value >= logLevel.value

    private fun formatMessage(level: String, msg: String): String =
        "${getCurrentTime()} [$level] ${Thread.currentThread().name} : $msg"

    private fun writeToFile(message: String) {
        if (!fileLogEnabled || logFile == null) return
        executor.execute {
            try {
                FileWriter(logFile, true).use { it.write("$message\n") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log to file", e)
            }
        }
    }

    // Основные методы
    fun v(tag: String = TAG, message: String) {
        if (shouldLog(LogLevel.VERBOSE)) {
            val formatted = formatMessage("VERBOSE", "$tag: $message")
            Log.v(tag, message)
            writeToFile(formatted)
        }
    }

    fun d(tag: String = TAG, message: String) {
        if (shouldLog(LogLevel.DEBUG)) {
            val formatted = formatMessage("DEBUG", "$tag: $message")
            Log.d(tag, message)
            writeToFile(formatted)
        }
    }

    fun i(tag: String = TAG, message: String) {
        if (shouldLog(LogLevel.INFO)) {
            val formatted = formatMessage("INFO", "$tag: $message")
            Log.i(tag, message)
            writeToFile(formatted)
        }
    }

    fun w(tag: String = TAG, message: String) {
        if (shouldLog(LogLevel.WARN)) {
            val formatted = formatMessage("WARN", "$tag: $message")
            Log.w(tag, message)
            writeToFile(formatted)
        }
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (shouldLog(LogLevel.ERROR)) {
            val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
            val formatted = formatMessage("ERROR", "$tag: $fullMsg")
            Log.e(tag, message, throwable)
            writeToFile(formatted)
        }
    }

    // Утилиты
    fun getCurrentLogFile(): File? = logFile

    fun clearLogFile() {
        executor.execute {
            logFile?.delete()
            logFile = null
            prepareLogFile()
        }
    }
}
