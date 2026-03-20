package com.schedule.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;

import java.io.File;
import java.util.Arrays;

public class CircleRecordActivity extends Activity {

    public static final String EXTRA_VIDEO_PATH   = "video_path";
    public static final String EXTRA_FRONT_CAMERA = "front_camera";
    public static final int    REQUEST_CODE       = 2001;
    public static final int    MAX_SECONDS      = 60;

    // Флаги для связи JS → Activity через static volatile
    public static volatile boolean isActive     = false;
    public static volatile boolean shouldStop   = false;
    public static volatile boolean shouldCancel = false;

    private TextureView           textureView;
    private CameraManager         cameraManager;
    private CameraDevice          cameraDevice;
    private CameraCaptureSession  captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private MediaRecorder         mediaRecorder;
    private File                  videoFile;
    private boolean               isRecording   = false;
    private boolean               isFrontCamera = true;
    private String                currentCameraId;
    private Handler               bgHandler;
    private HandlerThread         bgThread;
    private final Handler         uiHandler     = new Handler();
    private int                   recordSeconds = 0;
    private Runnable              timerRunnable;
    private Runnable              stopPollRunnable;
    private Size                  videoSize;

    private ProgressRingView  progressRing;
    private TextView          timerText;
    private View              switchBtn;
    private View              bottomBar;       // нижняя панель (скрыта до начала записи)
    private View              lockBtn;         // кнопка замка справа

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isActive = true; shouldStop = false; shouldCancel = false;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(0x99000000));
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(buildUi());
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBgThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceListener);
        }
        startStopPoll();
    }

    @Override
    protected void onPause() {
        stopStopPoll();
        closeCamera();
        stopBgThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        isActive = false;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() { cancel(); }

    // Поллинг флага остановки от JS каждые 80мс
    private void startStopPoll() {
        stopStopPoll();
        stopPollRunnable = new Runnable() {
            @Override public void run() {
                if (shouldCancel) { cancel(); return; }
                if (shouldStop)   { stopRecordingAndSend(); return; }
                uiHandler.postDelayed(this, 80);
            }
        };
        uiHandler.postDelayed(stopPollRunnable, 80);
    }

    private void stopStopPoll() {
        if (stopPollRunnable != null) {
            uiHandler.removeCallbacks(stopPollRunnable);
            stopPollRunnable = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View buildUi() {
        int screenW = getResources().getDisplayMetrics().widthPixels;

        // Круг — почти во всю ширину экрана (как в Telegram)
        int circleSize = screenW - dp(32);

        // ── Root: чёрный фон на весь экран ──────────────────────
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xCC000000); // ~80% затемнение, фон просвечивает

        // ── TextureView (кружок) — горизонтально центрирован, от верха ──
        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(surfaceListener);
        textureView.setClipToOutline(true);
        textureView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(circleSize, circleSize);
        tvLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        tvLp.topMargin = dp(52);
        tvLp.leftMargin = dp(16);
        tvLp.rightMargin = dp(16);
        root.addView(textureView, tvLp);

        // ── Прогресс-кольцо поверх TextureView ──────────────────
        progressRing = new ProgressRingView(this);
        progressRing.setVisibility(View.GONE);
        int ringSize = circleSize + dp(10);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(ringSize, ringSize);
        ringLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        ringLp.topMargin = tvLp.topMargin - dp(5);
        root.addView(progressRing, ringLp);

        // ── Кнопка замка справа от кружка (вертикально по центру) ──
        lockBtn = makeLockBtn();
        FrameLayout.LayoutParams lockLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        lockLp.gravity = Gravity.END | Gravity.TOP;
        lockLp.topMargin = tvLp.topMargin + circleSize / 2 - dp(22);
        lockLp.rightMargin = dp(8);
        root.addView(lockBtn, lockLp);
        lockBtn.setVisibility(View.GONE);

        // ── Нижняя панель (появляется после начала записи) ────────
        bottomBar = buildBottomBar();
        FrameLayout.LayoutParams bbLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(72));
        bbLp.gravity = Gravity.BOTTOM;
        bbLp.bottomMargin = dp(12);
        root.addView(bottomBar, bbLp);
        bottomBar.setVisibility(View.GONE);

        return root;
    }

    /** Нижняя панель в стиле Telegram: [flip+flash] | [●timer ← отмена] | [📷] */
    private View buildBottomBar() {
        FrameLayout wrap = new FrameLayout(this);

        // Пилюля с flip и flash слева
        LinearLayout leftPill = new LinearLayout(this);
        leftPill.setOrientation(LinearLayout.HORIZONTAL);
        leftPill.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable pillBg =
            new android.graphics.drawable.GradientDrawable();
        pillBg.setCornerRadius(dp(24));
        pillBg.setColor(0xCC1C1C1E);
        leftPill.setBackground(pillBg);
        leftPill.setPadding(dp(6), dp(6), dp(6), dp(6));

        switchBtn = makePillIconBtn("⟳");
        switchBtn.setOnClickListener(v -> switchCamera());
        leftPill.addView(switchBtn,
            new LinearLayout.LayoutParams(dp(44), dp(44)));

        View flashBtn = makePillIconBtn("⚡");
        leftPill.addView(flashBtn,
            new LinearLayout.LayoutParams(dp(44), dp(44)));

        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, dp(56));
        pillLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        pillLp.leftMargin = dp(12);
        wrap.addView(leftPill, pillLp);

        // Центральная пилюля: ● таймер  ‹ Влево — отмена
        android.graphics.drawable.GradientDrawable centerBg =
            new android.graphics.drawable.GradientDrawable();
        centerBg.setCornerRadius(dp(24));
        centerBg.setColor(0xCC1C1C1E);

        LinearLayout centerPill = new LinearLayout(this);
        centerPill.setOrientation(LinearLayout.HORIZONTAL);
        centerPill.setGravity(Gravity.CENTER_VERTICAL);
        centerPill.setBackground(centerBg);
        centerPill.setPadding(dp(14), 0, dp(14), 0);

        View dot = new View(this);
        dot.setBackground(makeCircleDrawable(0xFFFF3B30));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(8);
        centerPill.addView(dot, dotLp);

        timerText = new TextView(this);
        timerText.setTextColor(0xFFFFFFFF);
        timerText.setTextSize(15);
        timerText.setTypeface(android.graphics.Typeface.MONOSPACE);
        timerText.setText("0:00,0");
        timerText.setPadding(0, 0, dp(14), 0);
        centerPill.addView(timerText,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hintTv = new TextView(this);
        hintTv.setTextColor(0xAAFFFFFF);
        hintTv.setTextSize(13);
        hintTv.setText("‹ Влево — отмена");
        centerPill.addView(hintTv,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams ctrLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, dp(44));
        ctrLp.gravity = Gravity.CENTER;
        wrap.addView(centerPill, ctrLp);

        // Кнопка 📷 справа — большой серый круг
        FrameLayout camBtn = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable camBg =
            new android.graphics.drawable.GradientDrawable();
        camBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        camBg.setColor(0xCC3A3A3C);
        camBtn.setBackground(camBg);
        camBtn.setClickable(true); camBtn.setFocusable(true);
        TextView camIco = new TextView(this);
        camIco.setText("📷");
        camIco.setTextSize(22);
        camIco.setGravity(Gravity.CENTER);
        camBtn.addView(camIco, new FrameLayout.LayoutParams(dp(60), dp(60)));
        camBtn.setOnClickListener(v -> stopRecordingAndSend());

        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(dp(60), dp(60));
        camLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        camLp.rightMargin = dp(12);
        wrap.addView(camBtn, camLp);

        return wrap;
    }

    private View makePillIconBtn(String emoji) {
        TextView tv = new TextView(this);
        tv.setText(emoji);
        tv.setTextSize(20);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(0xFFFFFFFF);
        tv.setClickable(true); tv.setFocusable(true);
        android.graphics.drawable.RippleDrawable rip =
            new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                null, makeCircleDrawable(0xFFFFFFFF));
        tv.setBackground(rip);
        return tv;
    }

    private View makeLockBtn() {
        FrameLayout fl = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable bg =
            new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(0xCC1C1C1E);
        fl.setBackground(bg); fl.setClickable(true); fl.setFocusable(true);
        TextView tv = new TextView(this);
        tv.setText("🔒"); tv.setTextSize(18); tv.setGravity(Gravity.CENTER);
        fl.addView(tv, new FrameLayout.LayoutParams(dp(44), dp(44)));
        return fl;
    }

    private void startBgThread() {
        bgThread = new HandlerThread("CamBG");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    private void stopBgThread() {
        if (bgThread != null) {
            bgThread.quitSafely();
            try { bgThread.join(); } catch (InterruptedException ignored) {}
            bgThread = null; bgHandler = null;
        }
    }

    private String getCameraId(boolean front) {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics ch = cameraManager.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (front  && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
                if (!front && facing == CameraCharacteristics.LENS_FACING_BACK)  return id;
            }
        } catch (CameraAccessException e) {
            AppLogger.get(this).e("CircleRec", "getCameraId: " + e.getMessage());
        }
        return null;
    }

    private Size chooseVideoSize(String cameraId) {
        try {
            CameraCharacteristics ch = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = ch.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return new Size(640, 480);
            Size[] sizes = map.getOutputSizes(MediaRecorder.class);
            Size best = null; int bestScore = Integer.MAX_VALUE;
            for (Size s : sizes) {
                int w = s.getWidth(), h = s.getHeight();
                if (Math.min(w,h) < 320 || Math.max(w,h) > 1280) continue;
                // Предпочитаем близкие к квадрату и к 720px
                int score = Math.abs(w - h) * 3 + Math.abs(Math.min(w,h) - 720);
                if (score < bestScore) { bestScore = score; best = s; }
            }
            return best != null ? best : new Size(640, 480);
        } catch (CameraAccessException e) { return new Size(640, 480); }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int w, int h) {
        currentCameraId = getCameraId(isFrontCamera);
        if (currentCameraId == null) currentCameraId = getCameraId(!isFrontCamera);
        if (currentCameraId == null) { finish(); return; }

        videoSize = chooseVideoSize(currentCameraId);
        AppLogger.get(this).i("CircleRec",
            "openCamera size=" + videoSize.getWidth() + "x" + videoSize.getHeight());
        configureTransform(w, h);

        try {
            cameraManager.openCamera(currentCameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(@NonNull CameraDevice cam) {
                    cameraDevice = cam;
                    // Сразу начинаем запись — не ждём нажатия кнопки
                    startRecording();
                }
                @Override public void onDisconnected(@NonNull CameraDevice cam) {
                    cam.close(); cameraDevice = null;
                }
                @Override public void onError(@NonNull CameraDevice cam, int err) {
                    cam.close(); cameraDevice = null;
                    AppLogger.get(CircleRecordActivity.this).e("CircleRec","cam err="+err);
                    finish();
                }
            }, bgHandler);
        } catch (CameraAccessException e) {
            AppLogger.get(this).e("CircleRec", "openCamera exc: " + e.getMessage());
        }
    }

    private void closeCamera() {
        if (isRecording) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            isRecording = false;
        }
        if (captureSession != null) { captureSession.close(); captureSession = null; }
        if (cameraDevice   != null) { cameraDevice.close();   cameraDevice   = null; }
        if (mediaRecorder  != null) { mediaRecorder.release(); mediaRecorder  = null; }
        if (timerRunnable  != null) {
            uiHandler.removeCallbacks(timerRunnable); timerRunnable = null;
        }
    }

    private void startRecording() {
        if (cameraDevice == null) return;
        try {
            videoFile = File.createTempFile("circle_", ".mp4", getCacheDir());
            setupMediaRecorder();

            SurfaceTexture st = textureView.getSurfaceTexture();
            st.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
            final Surface previewSurface  = new Surface(st);
            final Surface recorderSurface = mediaRecorder.getSurface();

            if (captureSession != null) { captureSession.close(); captureSession = null; }

            // Пробуем preview + recorder (основной путь)
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(recorderSurface);

            cameraDevice.createCaptureSession(
                Arrays.asList(previewSurface, recorderSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(@NonNull CameraCaptureSession ses) {
                        captureSession = ses;
                        try {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                CaptureRequest.CONTROL_MODE_AUTO);
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON);
                            ses.setRepeatingRequest(
                                previewRequestBuilder.build(), null, bgHandler);
                            mediaRecorder.start();
                            isRecording = true;
                            AppLogger.get(CircleRecordActivity.this)
                                .i("CircleRec","Recording STARTED OK (preview+recorder)");
                            runOnUiThread(() -> {
                                configureTransform(textureView.getWidth(), textureView.getHeight());
                                onRecordingStarted();
                            });
                        } catch (Exception e) {
                            AppLogger.get(CircleRecordActivity.this)
                                .e("CircleRec","ses.configure error: "+e.getMessage());
                        }
                    }
                    @Override public void onConfigureFailed(@NonNull CameraCaptureSession s) {
                        // Некоторые устройства (vivo, OPPO) не поддерживают
                        // одновременно preview + recorder → fallback: только recorder
                        AppLogger.get(CircleRecordActivity.this)
                            .w("CircleRec","preview+recorder FAILED, retrying recorder-only");
                        startRecordingRecorderOnly(recorderSurface);
                    }
                }, bgHandler);
        } catch (Exception e) {
            AppLogger.get(this).e("CircleRec","startRecording exc: "+e.getMessage());
            runOnUiThread(this::finish);
        }
    }

    /** Fallback для устройств, не поддерживающих одновременно preview+recorder */
    private void startRecordingRecorderOnly(Surface recorderSurface) {
        if (cameraDevice == null) return;
        try {
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewRequestBuilder.addTarget(recorderSurface);
            // Скрываем TextureView — нет превью
            runOnUiThread(() -> textureView.setVisibility(View.INVISIBLE));

            cameraDevice.createCaptureSession(
                Arrays.asList(recorderSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(@NonNull CameraCaptureSession ses) {
                        captureSession = ses;
                        try {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                CaptureRequest.CONTROL_MODE_AUTO);
                            ses.setRepeatingRequest(
                                previewRequestBuilder.build(), null, bgHandler);
                            mediaRecorder.start();
                            isRecording = true;
                            AppLogger.get(CircleRecordActivity.this)
                                .i("CircleRec","Recording STARTED OK (recorder-only fallback)");
                            runOnUiThread(CircleRecordActivity.this::onRecordingStarted);
                        } catch (Exception e) {
                            AppLogger.get(CircleRecordActivity.this)
                                .e("CircleRec","recorder-only configure error: "+e.getMessage());
                            runOnUiThread(CircleRecordActivity.this::finish);
                        }
                    }
                    @Override public void onConfigureFailed(@NonNull CameraCaptureSession s) {
                        AppLogger.get(CircleRecordActivity.this)
                            .e("CircleRec","recorder-only configure FAILED — giving up");
                        runOnUiThread(CircleRecordActivity.this::cancel);
                    }
                }, bgHandler);
        } catch (Exception e) {
            AppLogger.get(this).e("CircleRec","startRecordingRecorderOnly exc: "+e.getMessage());
            runOnUiThread(this::cancel);
        }
    }

    /**
     * ПРАВИЛЬНЫЙ порядок MediaRecorder:
     * setAudioSource + setVideoSource → setOutputFormat →
     * setVideoSize + setVideoEncoder + setAudioEncoder →
     * setOutputFile → prepare()
     */
    private void setupMediaRecorder() throws Exception {
        mediaRecorder = new MediaRecorder();
        // 1. Источники — ДО setOutputFormat
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        // 2. Формат
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        // 3. Параметры кодека — ПОСЛЕ setOutputFormat
        int side = Math.min(videoSize.getWidth(), videoSize.getHeight());
        if (side > 720) side = 720;
        mediaRecorder.setVideoSize(side, side);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(2_500_000);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncodingBitRate(128_000);
        // 4. Файл и лимит
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
        int rot    = getWindowManager().getDefaultDisplay().getRotation();
        int sensor = getSensorOrientation(currentCameraId);
        mediaRecorder.setOrientationHint(getRecordOrientation(sensor, rot, isFrontCamera));
        mediaRecorder.setMaxDuration(MAX_SECONDS * 1000);
        mediaRecorder.setOnInfoListener((mr, what, extra) -> {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)
                runOnUiThread(this::stopRecordingAndSend);
        });
        // 5. Prepare
        mediaRecorder.prepare();
        AppLogger.get(this).i("CircleRec",
            "MediaRecorder prepared side="+side+" file="+videoFile.getAbsolutePath());
    }

    private int getSensorOrientation(String camId) {
        try {
            Integer o = cameraManager.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
            return o != null ? o : 90;
        } catch (Exception e) { return 90; }
    }

    private int getRecordOrientation(int sensor, int devRot, boolean front) {
        int[] D = {0, 90, 180, 270};
        int dev = D[devRot % 4];
        return front ? (sensor + dev + 360) % 360 : (sensor - dev + 360) % 360;
    }

    private void onRecordingStarted() {
        progressRing.setVisibility(View.VISIBLE);
        progressRing.setProgress(0f);
        // Показываем нижнюю панель с таймером
        if (bottomBar != null) bottomBar.setVisibility(View.VISIBLE);
        if (lockBtn   != null) lockBtn.setVisibility(View.VISIBLE);
        // Скрываем переключение камеры во время записи (как в Telegram)
        if (switchBtn != null) switchBtn.setEnabled(false);

        recordSeconds = 0;
        // Тик каждые 100мс — для отображения десятых долей секунды
        final int[] tenths = {0};
        timerRunnable = new Runnable() {
            @Override public void run() {
                tenths[0]++;
                int total = tenths[0];
                int secs  = total / 10;
                int dec   = total % 10;
                if (timerText != null)
                    timerText.setText(secs / 60 + ":"
                        + String.format("%02d", secs % 60) + "," + dec);
                progressRing.setProgress((float) secs / MAX_SECONDS);
                recordSeconds = secs;
                if (secs < MAX_SECONDS) uiHandler.postDelayed(this, 100);
                else stopRecordingAndSend();
            }
        };
        uiHandler.postDelayed(timerRunnable, 100);
    }

    private void stopRecordingAndSend() {
        stopStopPoll();
        if (!isRecording) { setResult(RESULT_CANCELED); finish(); return; }
        isRecording = false;
        if (timerRunnable != null) {
            uiHandler.removeCallbacks(timerRunnable); timerRunnable = null;
        }
        if (captureSession != null) { captureSession.close(); captureSession = null; }
        try { mediaRecorder.stop(); } catch (Exception e) {
            AppLogger.get(this).e("CircleRec","stop: "+e.getMessage());
        }
        if (mediaRecorder != null) { mediaRecorder.release(); mediaRecorder = null; }
        AppLogger.get(this).i("CircleRec",
            "Saved path="+videoFile.getAbsolutePath()+" len="+videoFile.length());
        Intent res = new Intent();
        res.putExtra(EXTRA_VIDEO_PATH, videoFile.getAbsolutePath());
        res.putExtra(EXTRA_FRONT_CAMERA, isFrontCamera);
        setResult(RESULT_OK, res);
        finish();
    }

    private void cancel() {
        stopStopPoll();
        if (isRecording) {
            isRecording = false;
            if (timerRunnable != null) uiHandler.removeCallbacks(timerRunnable);
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            try { if (mediaRecorder != null) mediaRecorder.stop(); } catch (Exception ignored) {}
            if (mediaRecorder != null) { mediaRecorder.release(); mediaRecorder = null; }
            if (videoFile != null) videoFile.delete();
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void switchCamera() {
        isFrontCamera = !isFrontCamera;
        closeCamera();
        startBgThread();
        openCamera(textureView.getWidth(), textureView.getHeight());
    }

    private final TextureView.SurfaceTextureListener surfaceListener =
        new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(
                @NonNull SurfaceTexture s, int w, int h) { openCamera(w, h); }
            @Override public void onSurfaceTextureSizeChanged(
                @NonNull SurfaceTexture s, int w, int h) { configureTransform(w, h); }
            @Override public boolean onSurfaceTextureDestroyed(
                @NonNull SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        };

    /**
     * Center-crop transform: камера заполняет квадратный TextureView без полос.
     */
    private void configureTransform(int vw, int vh) {
        if (videoSize == null || vw == 0 || vh == 0) return;
        android.graphics.Matrix m = new android.graphics.Matrix();
        int rot = getWindowManager().getDefaultDisplay().getRotation();

        float bufW, bufH;
        if (rot == Surface.ROTATION_0 || rot == Surface.ROTATION_180) {
            bufW = videoSize.getHeight(); bufH = videoSize.getWidth();
        } else {
            bufW = videoSize.getWidth(); bufH = videoSize.getHeight();
        }
        float scale = Math.max((float)vw / bufW, (float)vh / bufH);
        m.setScale(scale * bufW / vw, scale * bufH / vh, vw / 2f, vh / 2f);

        if (rot == Surface.ROTATION_90)  m.postRotate(270, vw/2f, vh/2f);
        if (rot == Surface.ROTATION_270) m.postRotate(90,  vw/2f, vh/2f);
        if (rot == Surface.ROTATION_180) m.postRotate(180, vw/2f, vh/2f);
        if (isFrontCamera) m.postScale(-1, 1, vw/2f, vh/2f);

        runOnUiThread(() -> textureView.setTransform(m));
    }

    private class ProgressRingView extends View {
        private float progress = 0f;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        ProgressRingView(android.content.Context ctx) {
            super(ctx);
            bg.setStyle(Paint.Style.STROKE); bg.setStrokeWidth(dp(4)); bg.setColor(0x44FFFFFF);
            fg.setStyle(Paint.Style.STROKE); fg.setStrokeWidth(dp(4));
            fg.setColor(0xFFFF3B30); fg.setStrokeCap(Paint.Cap.ROUND);
        }
        void setProgress(float p) { progress = p; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            int cx = getWidth()/2, cy = getHeight()/2;
            int r = Math.min(cx, cy) - dp(4);
            oval.set(cx-r, cy-r, cx+r, cy+r);
            canvas.drawArc(oval, 0, 360, false, bg);
            if (progress > 0) canvas.drawArc(oval, -90, progress*360, false, fg);
        }
    }

    private android.graphics.drawable.GradientDrawable makeCircleDrawable(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color); return d;
    }

    private View makeRoundBtn(String label, int size) {
        FrameLayout fl = new FrameLayout(this);
        fl.setBackground(makeCircleDrawable(0x77000000));
        fl.setClickable(true); fl.setFocusable(true);
        TextView tv = new TextView(this);
        tv.setText(label); tv.setTextColor(Color.WHITE);
        tv.setTextSize(18); tv.setGravity(Gravity.CENTER);
        fl.addView(tv, new FrameLayout.LayoutParams(size, size));
        fl.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        return fl;
    }
}
