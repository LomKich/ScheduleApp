package com.schedule.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * BackupManager — система резервного копирования APK и watchdog.
 *
 * Функционал:
 *  1. Перед установкой нового APK — сохраняет текущий APK как бэкап
 *  2. Хранит до MAX_BACKUPS последних версий (filesDir/apk_backups/)
 *  3. Watchdog: если JS не присылает heartbeat в течение WATCHDOG_TIMEOUT мс
 *     после старта — показывает диалог восстановления предыдущей версии
 *  4. Предоставляет интерфейс для восстановления и просмотра бэкапов
 */
public class BackupManager {

    private static final String TAG              = "BackupManager";
    private static final String PREFS_NAME       = "backup_prefs";
    private static final String KEY_LAST_BACKUP  = "last_backup_path";
    private static final String KEY_LAST_VERSION = "last_backup_version";
    private static final String KEY_LAST_TS      = "last_backup_ts";
    private static final String KEY_INSTALL_COUNT= "install_count"; // сколько раз устанавливали
    private static final int    MAX_BACKUPS      = 2;    // хранить N последних версий
    private static final long   WATCHDOG_TIMEOUT = 20_000; // 20 сек — если JS не ответил

    private static BackupManager instance;

    private final Context         ctx;
    private final AppLogger       log;
    private final SharedPreferences prefs;
    private final Handler         handler = new Handler(Looper.getMainLooper());

    // Watchdog
    private long  _lastHeartbeat    = 0;
    private boolean _watchdogActive = false;
    private Runnable _watchdogRunnable;
    private WatchdogCallback _callback;

    public interface WatchdogCallback {
        void onAppHealthy();
        void onAppUnhealthy(String reason, String backupVersion, String backupPath);
    }

    // ── Singleton ────────────────────────────────────────────────────────────

    public static synchronized BackupManager get(Context ctx) {
        if (instance == null) instance = new BackupManager(ctx);
        return instance;
    }

    private BackupManager(Context ctx) {
        this.ctx   = ctx.getApplicationContext();
        this.log   = AppLogger.get(ctx);
        this.prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Backup directory ─────────────────────────────────────────────────────

    private File getBackupDir() {
        File dir = new File(ctx.getFilesDir(), "apk_backups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ── 1. Сохранение текущего APK перед обновлением ─────────────────────────

    /**
     * Вызывать ПЕРЕД скачиванием нового APK.
     * Копирует текущий установленный APK в папку резервных копий.
     * @return путь к сохранённому файлу, или null при ошибке
     */
    public String backupCurrentApk() {
        try {
            String currentApkPath = ctx.getApplicationInfo().sourceDir;
            String versionName    = getVersionName();
            long   nowMs          = System.currentTimeMillis();

            String stamp    = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(nowMs));
            String fileName = "backup_v" + versionName + "_" + stamp + ".apk";
            File   destFile = new File(getBackupDir(), fileName);

            log.i(TAG, "Backing up current APK v" + versionName + " → " + destFile.getName());

            // Копируем
            copyFile(new File(currentApkPath), destFile);

            // Сохраняем мета
            prefs.edit()
                .putString(KEY_LAST_BACKUP,  destFile.getAbsolutePath())
                .putString(KEY_LAST_VERSION, versionName)
                .putLong  (KEY_LAST_TS,      nowMs)
                .putInt   (KEY_INSTALL_COUNT, prefs.getInt(KEY_INSTALL_COUNT, 0) + 1)
                .apply();

            log.i(TAG, "Backup saved: " + destFile.length() + " bytes");

            // Удаляем старые бэкапы (оставляем MAX_BACKUPS)
            pruneOldBackups();

            return destFile.getAbsolutePath();

        } catch (Exception e) {
            log.e(TAG, "backupCurrentApk error: " + e.getMessage());
            return null;
        }
    }

    /** Удаляет старые бэкапы, оставляя последние MAX_BACKUPS штук */
    private void pruneOldBackups() {
        File[] files = getBackupDir().listFiles(
            f -> f.getName().startsWith("backup_") && f.getName().endsWith(".apk"));
        if (files == null || files.length <= MAX_BACKUPS) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - MAX_BACKUPS; i++) {
            log.i(TAG, "Pruning old backup: " + files[i].getName());
            files[i].delete();
        }
    }

    /** Копирует файл */
    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream  in  = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int    n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    // ── 2. Информация о бэкапах ───────────────────────────────────────────────

    public String getLastBackupVersion() {
        return prefs.getString(KEY_LAST_VERSION, null);
    }

    public String getLastBackupPath() {
        String path = prefs.getString(KEY_LAST_BACKUP, null);
        if (path == null) return null;
        File f = new File(path);
        return f.exists() ? path : null; // null если файл был удалён
    }

    public long getLastBackupTimestamp() {
        return prefs.getLong(KEY_LAST_TS, 0);
    }

    public boolean hasBackup() {
        return getLastBackupPath() != null;
    }

    /** Список всех доступных бэкапов (новые первыми) */
    public File[] listBackups() {
        File[] files = getBackupDir().listFiles(
            f -> f.getName().startsWith("backup_") && f.getName().endsWith(".apk"));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /** Суммарный размер всех бэкапов в байтах */
    public long getTotalBackupSize() {
        long total = 0;
        for (File f : listBackups()) total += f.length();
        return total;
    }

    // ── 3. Восстановление из бэкапа ──────────────────────────────────────────

    /**
     * Запускает установщик с последним бэкапом.
     * Возвращает true если установщик запущен, false при ошибке.
     */
    public boolean restoreLatestBackup(android.app.Activity activity) {
        String path = getLastBackupPath();
        if (path == null) {
            log.e(TAG, "restoreLatestBackup: no backup available");
            return false;
        }
        return installApk(activity, new File(path));
    }

    public boolean restoreBackup(android.app.Activity activity, File backupFile) {
        if (!backupFile.exists()) {
            log.e(TAG, "restoreBackup: file not found: " + backupFile);
            return false;
        }
        return installApk(activity, backupFile);
    }

    private boolean installApk(android.app.Activity activity, File apkFile) {
        try {
            log.i(TAG, "Installing backup: " + apkFile.getName() + " (" + apkFile.length() + " bytes)");
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, ctx.getPackageName() + ".fileprovider", apkFile);
            android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                          | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            log.e(TAG, "installApk error: " + e.getMessage());
            return false;
        }
    }

    // ── 4. Watchdog ───────────────────────────────────────────────────────────

    /**
     * Запускает watchdog.
     * После старта приложения ждёт WATCHDOG_TIMEOUT мс.
     * Если JS не прислал heartbeat — вызывает onAppUnhealthy.
     */
    public void startWatchdog(WatchdogCallback callback) {
        this._callback       = callback;
        this._watchdogActive = true;
        this._lastHeartbeat  = 0;

        log.i(TAG, "Watchdog started (timeout=" + WATCHDOG_TIMEOUT + "ms)");

        _watchdogRunnable = () -> {
            if (!_watchdogActive) return;
            if (_lastHeartbeat > 0) {
                // Heartbeat был получен — всё ок
                log.i(TAG, "Watchdog OK: heartbeat received at +" +
                    (_lastHeartbeat - (System.currentTimeMillis() - WATCHDOG_TIMEOUT)) + "ms");
                if (_callback != null) _callback.onAppHealthy();
                _watchdogActive = false;
                return;
            }

            // Heartbeat не пришёл — приложение, возможно, сломано
            String backupVer  = getLastBackupVersion();
            String backupPath = getLastBackupPath();
            int    installCnt = prefs.getInt(KEY_INSTALL_COUNT, 0);

            log.w(TAG, "Watchdog FAIL: no heartbeat after " + WATCHDOG_TIMEOUT + "ms" +
                ", installCount=" + installCnt + ", backup=" + backupVer);

            if (_callback != null) {
                String reason = buildFailReason(installCnt);
                _callback.onAppUnhealthy(reason, backupVer, backupPath);
            }
            _watchdogActive = false;
        };

        handler.postDelayed(_watchdogRunnable, WATCHDOG_TIMEOUT);
    }

    private String buildFailReason(int installCount) {
        if (installCount > 0) {
            return "Приложение не отвечает после последнего обновления. " +
                   "Возможно, новая версия содержит ошибку.";
        }
        return "Приложение не отвечает. Возможно, повреждены данные или нет разрешений.";
    }

    /** JS вызывает при каждом успешном тике (через @JavascriptInterface) */
    public void onHeartbeat() {
        _lastHeartbeat = System.currentTimeMillis();
        if (_watchdogActive) {
            // Первый heartbeat — сразу останавливаем таймер
            log.i(TAG, "Watchdog: first heartbeat received ✅");
            if (_watchdogRunnable != null) handler.removeCallbacks(_watchdogRunnable);
            if (_callback != null) _callback.onAppHealthy();
            _watchdogActive = false;
        }
    }

    public void stopWatchdog() {
        _watchdogActive = false;
        if (_watchdogRunnable != null) {
            handler.removeCallbacks(_watchdogRunnable);
            _watchdogRunnable = null;
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private String getVersionName() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static String formatSize(long bytes) {
        if (bytes > 1024 * 1024) return String.format(Locale.getDefault(), "%.1f МБ", bytes / 1048576.0);
        if (bytes > 1024)        return String.format(Locale.getDefault(), "%d КБ", bytes / 1024);
        return bytes + " Б";
    }

    public static String formatDate(long ts) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(ts));
    }
}
