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
 * Логгер — пишет все события в Downloads/ScheduleApp_logs.txt
 * Поддерживает Android 10+ (MediaStore) и старые версии (прямая запись).
 */
public class AppLogger {

    private static final String TAG      = "ScheduleApp";
    private static final String FILE_NAME = "ScheduleApp_logs.txt";
    private static final int    MAX_SIZE  = 512 * 1024; // 512 KB — потом очищаем

    private static AppLogger instance;
    private final Context ctx;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    // Для Android < 10
    private File legacyFile;
    // Для Android 10+
    private Uri mediaUri;

    private AppLogger(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        try {
            initFile();
        } catch (Exception e) {
            // Ни при каких обстоятельствах логгер не должен крашить приложение
            Log.e(TAG, "Logger init failed, will use logcat only: " + e.getMessage());
        }
        writeRaw("════════════════════════════════════════");
        writeRaw("  ScheduleApp — сессия начата");
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

        // Шаг 1: ищем существующий файл в MediaStore
        android.database.Cursor cursor = null;
        try {
            cursor = ctx.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE},
                MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[]{FILE_NAME},
                null
            );
            if (cursor != null && cursor.moveToFirst()) {
                long id   = cursor.getLong(0);
                long size = cursor.getLong(1);
                Uri found = Uri.withAppendedPath(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(id));
                if (size > MAX_SIZE) {
                    // Файл слишком большой — удаляем, создадим ниже заново
                    ctx.getContentResolver().delete(found, null, null);
                } else {
                    mediaUri = found; // переиспользуем существующий
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaStore query failed: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        // Шаг 2: если нет рабочего URI — создаём новый файл
        if (mediaUri == null) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                mediaUri = ctx.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            } catch (Exception e) {
                // IllegalStateException: "Failed to build unique file" — файл с таким именем
                // уже существует физически на диске, но MediaStore его не видит (рассинхрон).
                // Это главная причина краша. Решение: откатываемся на внутреннее хранилище.
                Log.w(TAG, "MediaStore insert failed, falling back to internal storage: " + e.getMessage());
                mediaUri = null;
                initInternalFallback();
            }
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
            legacyFile = new File(dir, FILE_NAME);
            if (legacyFile.exists() && legacyFile.length() > MAX_SIZE) {
                legacyFile.delete();
            }
            Log.i(TAG, "Logger: using internal fallback at " + legacyFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Logger: all storage options failed: " + e.getMessage());
            // legacyFile остаётся null — writeRaw() просто пропустит запись, без краша
        }
    }

    private void initLegacy() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        legacyFile = new File(dir, FILE_NAME);
        if (legacyFile.exists() && legacyFile.length() > MAX_SIZE) {
            legacyFile.delete();
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
