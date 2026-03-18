package com.schedule.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Получает BOOT_COMPLETED и QUICKBOOT_POWERON — перезапускает фоновый сервис
 * если пользователь был зарегистрирован (есть username в prefs).
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) return;

        SharedPreferences prefs = context.getSharedPreferences(
            BackgroundPollService.PREFS, Context.MODE_PRIVATE);
        String username = prefs.getString(BackgroundPollService.KEY_USERNAME, null);
        if (username == null || username.isEmpty()) return; // не зарегистрирован

        // Запускаем фоновый сервис — уведомления придут даже после перезагрузки
        try {
            Intent svc = new Intent(context, BackgroundPollService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            // Игнорируем — возможно ограничения запуска
        }
    }
}
