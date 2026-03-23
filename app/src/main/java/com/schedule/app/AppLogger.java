package com.schedule.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Логгер — пишет все события в Downloads/расписание/<timestamp>_log.txt
 * Каждый запуск приложения создаёт новый файл с уникальным именем.
 * Поддерживает Android 10+ (MediaStore) и старые версии (прямая запись).
 */
public class AppLogger {

    private static final String TAG          = "ScheduleApp";
    private static final String SUBFOLDER    = "расписание";   // папка внутри Downloads
    private static final int    MAX_SIZE     = 512 * 1024; // 512 KB — потом очищаем
    private static final int    MAX_LOGS     = 4;          // хранить не более N файлов логов

    private static AppLogger instance;
    private final Context ctx;
    private final SimpleDateFormat sdf     = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    private final SimpleDateFormat sdFname = new SimpleDateFormat("yyyyMMdd_HHmmss",           Locale.getDefault());

    // Уникальное имя файла для этой сессии
    private final String FILE_NAME;

    // Для Android < 10
    private File legacyFile;
    // Для Android 10+
    private Uri mediaUri;

    private AppLogger(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        // Уникальное имя: 20250318_143022_log.txt
        this.FILE_NAME = sdFname.format(new Date()) + "_log.txt";
        try {
            initFile();
        } catch (Exception e) {
            Log.e(TAG, "Logger init failed, will use logcat only: " + e.getMessage());
        }
        writeRaw("════════════════════════════════════════");
        writeRaw("  ScheduleApp — сессия начата");
        writeRaw("  Файл лога: " + FILE_NAME);
        writeRaw("  Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        writeRaw("  Устройство: " + Build.MANUFACTURER + " " + Build.MODEL);
        writeRaw("════════════════════════════════════════");
    }

    public static synchronized AppLogger get(Context ctx) {
        if (instance == null) instance = new AppLogger(ctx);
        return instance;
    }

    // ── Инициализация файла ──────────────────────────────────────────────────

    private void initFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            initMediaStore();
        } else {
            initLegacy();
        }
    }

    private void initMediaStore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        // Удаляем старые логи перед созданием нового
        pruneOldLogsMediaStore();

        // Всегда создаём НОВЫЙ файл (уникальное имя на каждый запуск)
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            // Папка: Downloads/расписание/
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                   Environment.DIRECTORY_DOWNLOADS + "/" + SUBFOLDER);
            mediaUri = ctx.getContentResolver()
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        } catch (Exception e) {
            Log.w(TAG, "MediaStore insert failed, falling back to internal storage: " + e.getMessage());
            mediaUri = null;
            initInternalFallback();
        }
    }

    /** Удаляет старые логи через MediaStore (Android 10+), оставляет MAX_LOGS-1 (место для нового) */
    private void pruneOldLogsMediaStore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND "
                + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
            String[] args = {
                "%" + SUBFOLDER + "%",
                "%_log.txt"
            };
            String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED
            };
            android.database.Cursor cursor = ctx.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, args,
                MediaStore.MediaColumns.DATE_ADDED + " ASC"
            );
            if (cursor == null) return;
            java.util.List<Uri> uris = new java.util.ArrayList<>();
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                uris.add(android.content.ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id));
            }
            cursor.close();
            // Удаляем самые старые, оставляем MAX_LOGS-1 (новый ещё не создан)
            int toDelete = uris.size() - (MAX_LOGS - 1);
            for (int i = 0; i < toDelete; i++) {
                ctx.getContentResolver().delete(uris.get(i), null, null);
                Log.i(TAG, "Pruned old log (MediaStore): " + uris.get(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "pruneOldLogsMediaStore error: " + e.getMessage());
        }
    }

    /**
     * Запасной вариант: пишем лог во внутреннее хранилище приложения (getFilesDir).
     * Не требует никаких разрешений, никогда не падает.
     */
    private void initInternalFallback() {
        try {
            File dir = ctx.getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            pruneOldLogsDir(dir);
            legacyFile = new File(dir, FILE_NAME);
            Log.i(TAG, "Logger: using internal fallback at " + legacyFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Logger: all storage options failed: " + e.getMessage());
        }
    }

    private void initLegacy() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloads, SUBFOLDER);
        if (!dir.exists()) dir.mkdirs();
        pruneOldLogsDir(dir);
        legacyFile = new File(dir, FILE_NAME);
    }

    /** Удаляет старые логи в указанной папке, оставляет MAX_LOGS-1 файлов */
    private void pruneOldLogsDir(File dir) {
        try {
            File[] logs = dir.listFiles(f -> f.getName().endsWith("_log.txt"));
            if (logs == null || logs.length < MAX_LOGS) return;
            java.util.Arrays.sort(logs, java.util.Comparator.comparingLong(File::lastModified));
            int toDelete = logs.length - (MAX_LOGS - 1);
            for (int i = 0; i < toDelete; i++) {
                Log.i(TAG, "Pruned old log: " + logs[i].getName());
                logs[i].delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "pruneOldLogsDir error: " + e.getMessage());
        }
    }

    // ── Публичные методы ─────────────────────────────────────────────────────

    public void i(String tag, String msg) {
        Log.i(TAG, "[" + tag + "] " + msg);
        write("INFO ", tag, msg);
    }

    public void e(String tag, String msg) {
        Log.e(TAG, "[" + tag + "] " + msg);
        write("ERROR", tag, msg);
    }

    public void e(String tag, String msg, Throwable t) {
        Log.e(TAG, "[" + tag + "] " + msg, t);
        write("ERROR", tag, msg + " | " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    public void w(String tag, String msg) {
        Log.w(TAG, "[" + tag + "] " + msg);
        write("WARN ", tag, msg);
    }

    public void js(String level, String msg, String sourceId, int line) {
        String src = sourceId != null ? sourceId.replaceAll(".*/", "") : "?";
        String entry = msg + "  [" + src + ":" + line + "]";
        Log.d(TAG, "[JS:" + level + "] " + entry);
        write("JS:" + padRight(level.toUpperCase(), 5), "WebView", entry);
    }

    public void section(String title) {
        writeRaw("── " + title + " " + "─".repeat(Math.max(0, 38 - title.length())));
    }

    // ── Внутренняя запись ────────────────────────────────────────────────────

    private synchronized void write(String level, String tag, String msg) {
        String line = sdf.format(new Date()) + " [" + padRight(level, 5) + "] "
                + padRight(tag, 12) + " │ " + msg + "\n";
        writeRaw(line.trim());
    }

    private synchronized void writeRaw(String line) {
        String data = line + "\n";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaUri != null) {
                try (OutputStream os = ctx.getContentResolver()
                        .openOutputStream(mediaUri, "wa")) {
                    if (os != null) os.write(data.getBytes("UTF-8"));
                }
            } else if (legacyFile != null) {
                try (FileOutputStream fos = new FileOutputStream(legacyFile, true)) {
                    fos.write(data.getBytes("UTF-8"));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Logger write error: " + e.getMessage());
        }
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
    }
}
