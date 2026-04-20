package com.schedule.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * Получает BOOT_COMPLETED и запускает фоновый сервис если он был включён.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) &&
            !"android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences("sapp_prefs", Context.MODE_PRIVATE);
        boolean bgEnabled = prefs.getBoolean(MessagingForegroundService.PREF_BG_ENABLED, false);
        String  username  = prefs.getString(MessagingForegroundService.PREF_SB_USER, "");

        if (bgEnabled && username != null && !username.isEmpty()) {
            Log.i("BootReceiver", "Boot complete — запускаю BgService для @" + username);
            Intent svcIntent = new Intent(context, MessagingForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent);
            } else {
                context.startService(svcIntent);
            }
        } else {
            Log.i("BootReceiver", "Boot complete — BgService отключён, пропускаю");
        }

        // ── Джарвис: голосовой режим (Протокол Астра) ─────────────────────────
        SharedPreferences jarvisPrefs = context.getSharedPreferences(
                "jarvis_relay_prefs", Context.MODE_PRIVATE);
        boolean jarvisActive = jarvisPrefs.getBoolean("jarvis_voice_active", false);

        if (jarvisActive) {
            Log.i("BootReceiver", "Boot complete — перезапускаю JarvisVoiceService");
            Intent jarvisIntent = new Intent(context,
                    com.schedule.app.jarvis.JarvisVoiceService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(jarvisIntent);
            } else {
                context.startService(jarvisIntent);
            }
        } else {
            Log.i("BootReceiver", "Boot complete — Джарвис не активирован, пропускаю");
        }
    }
}
