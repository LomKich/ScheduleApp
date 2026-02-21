package com.schedule.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * VPN-сервис для обхода DNS-блокировок через DNS-over-HTTPS (DoH).
 * Перехватывает UDP-пакеты на порт 53 и перенаправляет через выбранный DoH-сервер.
 */
public class DnsVpnService extends VpnService {

    private static final String TAG = "DnsVpnService";
    private static final String CHANNEL_ID = "vpn_channel";
    private static final int NOTIF_ID = 1;

    public static final String ACTION_START = "com.schedule.app.START_VPN";
    public static final String ACTION_STOP  = "com.schedule.app.STOP_VPN";
    public static final String EXTRA_DOH_URL      = "doh_url";
    public static final String EXTRA_DPI_STRATEGY = "dpi_strategy";

    // DNS по умолчанию — Cloudflare DoH
    private static final String DEFAULT_DOH = "https://1.1.1.1/dns-query";

    private final IBinder binder = new LocalBinder();
    private ParcelFileDescriptor vpnInterface;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile String dohUrl = DEFAULT_DOH;
    private volatile String dpiStrategy = "general";

    private OkHttpClient httpClient;

    public class LocalBinder extends Binder {
        public DnsVpnService getService() { return DnsVpnService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            String newDoh = intent.getStringExtra(EXTRA_DOH_URL);
            if (newDoh != null && !newDoh.isEmpty()) dohUrl = newDoh;
            String strat = intent.getStringExtra(EXTRA_DPI_STRATEGY);
            if (strat != null) dpiStrategy = strat;
            startVpn();
        }
        return START_STICKY;
    }

    private void startVpn() {
        if (running.get()) return;

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Активен • " + getDnsLabel()));

        httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        try {
            // Создать TUN интерфейс
            Builder builder = new Builder();
            builder.setSession("ScheduleVPN")
                .addAddress("10.8.0.1", 32)
                .addDnsServer("10.8.0.2")      // виртуальный DNS (сам перехватываем)
                .addRoute("10.8.0.2", 32)       // только DNS трафик через VPN
                .setBlocking(false)
                .setMtu(1500);

            // Исключить само приложение из VPN (чтобы DoH работал)
            builder.addDisallowedApplication(getPackageName());

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface");
                return;
            }

            running.set(true);
            executor = Executors.newFixedThreadPool(2);

            // Запустить обработчик пакетов
            executor.submit(this::packetLoop);

            Log.i(TAG, "VPN started, DoH: " + dohUrl + ", DPI: " + dpiStrategy);

        } catch (Exception e) {
            Log.e(TAG, "Error starting VPN", e);
            stopVpn();
        }
    }

    /**
     * Основной цикл перехвата DNS пакетов и проксирования через DoH
     */
    private void packetLoop() {
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
        ByteBuffer buf = ByteBuffer.allocate(32767);

        while (running.get()) {
            try {
                buf.clear();
                int len = in.read(buf.array());
                if (len <= 0) { Thread.sleep(10); continue; }
                buf.limit(len);

                // Парсим IP заголовок
                int ipVersion = (buf.get(0) >> 4) & 0xF;
                if (ipVersion != 4) continue; // пока только IPv4

                int protocol = buf.get(9) & 0xFF; // 17 = UDP
                if (protocol != 17) continue;

                int ipHeaderLen = (buf.get(0) & 0xF) * 4;
                int destPort = ((buf.get(ipHeaderLen + 2) & 0xFF) << 8) | (buf.get(ipHeaderLen + 3) & 0xFF);

                if (destPort != 53) continue; // только DNS

                // Извлечь DNS payload
                int udpHeaderLen = 8;
                int dnsOffset = ipHeaderLen + udpHeaderLen;
                int dnsLen = len - dnsOffset;
                if (dnsLen <= 0) continue;

                byte[] dnsQuery = new byte[dnsLen];
                buf.position(dnsOffset);
                buf.get(dnsQuery);

                // Источник запроса (для ответа)
                final int srcIpOff = 12;
                byte[] srcIp = new byte[4];
                buf.position(srcIpOff);
                buf.get(srcIp);
                int srcPort = ((buf.get(ipHeaderLen) & 0xFF) << 8) | (buf.get(ipHeaderLen + 1) & 0xFF);

                // Отправить через DoH асинхронно
                final byte[] finalQuery = dnsQuery;
                final byte[] finalSrc = srcIp;
                final int finalSrcPort = srcPort;
                executor.submit(() -> {
                    try {
                        byte[] resp = queryDoH(finalQuery);
                        if (resp != null) {
                            byte[] packet = buildUdpPacket(
                                new byte[]{10,8,0,2}, finalSrc,
                                53, finalSrcPort,
                                resp
                            );
                            synchronized (out) { out.write(packet); }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "DoH query failed: " + e.getMessage());
                    }
                });

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                if (running.get()) Log.e(TAG, "Packet loop error", e);
            }
        }
    }

    /**
     * Выполнить DNS запрос через DoH (RFC 8484)
     */
    private byte[] queryDoH(byte[] dnsWire) {
        String url = (dohUrl != null && !dohUrl.isEmpty()) ? dohUrl : DEFAULT_DOH;
        try {
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/dns-message")
                .addHeader("Accept", "application/dns-message")
                .post(RequestBody.create(dnsWire, MediaType.parse("application/dns-message")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().bytes();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "DoH error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Собрать UDP/IP пакет с DNS ответом
     */
    private byte[] buildUdpPacket(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] data) {
        int totalLen = 20 + 8 + data.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLen);

        // IP header
        buf.put((byte) 0x45);                          // Version + IHL
        buf.put((byte) 0x00);                          // DSCP
        buf.putShort((short) totalLen);                // Total length
        buf.putShort((short) 0);                       // ID
        buf.putShort((short) 0x4000);                  // Flags + Fragment offset
        buf.put((byte) 64);                            // TTL
        buf.put((byte) 17);                            // Protocol: UDP
        buf.putShort((short) 0);                       // Checksum placeholder
        buf.put(srcIp);
        buf.put(dstIp);

        // IP checksum
        buf.putShort(10, ipChecksum(buf.array(), 0, 20));

        // UDP header
        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putShort((short) (8 + data.length));
        buf.putShort((short) 0); // checksum (optional for IPv4)

        // DNS data
        buf.put(data);
        return buf.array();
    }

    private short ipChecksum(byte[] data, int offset, int len) {
        int sum = 0;
        for (int i = offset; i < offset + len; i += 2) {
            sum += ((data[i] & 0xFF) << 8) | (i + 1 < data.length ? data[i + 1] & 0xFF : 0);
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (short) ~sum;
    }

    private void stopVpn() {
        running.set(false);
        if (executor != null) { executor.shutdownNow(); executor = null; }
        try { if (vpnInterface != null) { vpnInterface.close(); vpnInterface = null; } }
        catch (IOException e) { Log.e(TAG, "Error closing VPN", e); }
        stopForeground(true);
        stopSelf();
        Log.i(TAG, "VPN stopped");
    }

    public boolean isRunning() { return running.get(); }

    public void updateDns(String newDohUrl) {
        this.dohUrl = (newDohUrl != null && !newDohUrl.isEmpty()) ? newDohUrl : DEFAULT_DOH;
        updateNotification("Активен • " + getDnsLabel());
    }

    public void updateDpiStrategy(String strategyId) {
        this.dpiStrategy = strategyId;
        // DPI параметры применяются при следующем соединении
    }

    private String getDnsLabel() {
        if (dohUrl == null || dohUrl.isEmpty()) return "системный DNS";
        if (dohUrl.contains("1.1.1.1")) return "Cloudflare";
        if (dohUrl.contains("8.8.8.8")) return "Google";
        if (dohUrl.contains("adguard")) return "AdGuard";
        if (dohUrl.contains("yandex")) return "Яндекс";
        return "DoH";
    }

    // ══ Уведомление ══
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "VPN Обход", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Обход DNS блокировок");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, DnsVpnService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nb = new Notification.Builder(this, CHANNEL_ID);
        } else {
            nb = new Notification.Builder(this);
        }

        return nb
            .setContentTitle("Расписание — VPN обход")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Выключить", stopPi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpn();
    }
}
