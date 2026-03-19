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
import android.widget.TextView;
import androidx.annotation.NonNull;

import java.io.File;
import java.util.Arrays;

public class CircleRecordActivity extends Activity {

    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final int    REQUEST_CODE     = 2001;
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
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int circleSize = Math.min(dp(240), screenW - dp(48));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        // TextureView обрезан в круг через clipToOutline
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
        tvLp.topMargin = (int)(screenH * 0.15f); // 15% от верха экрана
        root.addView(textureView, tvLp);

        // Прогресс-кольцо поверх TextureView
        progressRing = new ProgressRingView(this);
        progressRing.setVisibility(View.GONE);
        int ringSize = circleSize + dp(12);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(ringSize, ringSize);
        ringLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        ringLp.topMargin = tvLp.topMargin - dp(6);
        root.addView(progressRing, ringLp);

        // Таймер под кружком
        timerText = new TextView(this);
        timerText.setTextColor(0xFFFF3B30);
        timerText.setTextSize(16);
        timerText.setGravity(Gravity.CENTER);
        timerText.setText("0:00");
        timerText.setVisibility(View.GONE);
        FrameLayout.LayoutParams timerLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        timerLp.gravity = Gravity.TOP;
        timerLp.topMargin = tvLp.topMargin + circleSize + dp(12);
        root.addView(timerText, timerLp);

        // Кнопка отмены (X) слева снизу
        View cancelBtn = makeRoundBtn("✕", dp(52));
        FrameLayout.LayoutParams cancelLp = new FrameLayout.LayoutParams(dp(52), dp(52));
        cancelLp.gravity = Gravity.BOTTOM | Gravity.START;
        cancelLp.setMargins(dp(32), 0, 0, dp(52));
        root.addView(cancelBtn, cancelLp);
        cancelBtn.setOnClickListener(v -> cancel());

        // Переключить камеру справа снизу
        switchBtn = makeRoundBtn("⟳", dp(52));
        FrameLayout.LayoutParams switchLp = new FrameLayout.LayoutParams(dp(52), dp(52));
        switchLp.gravity = Gravity.BOTTOM | Gravity.END;
        switchLp.setMargins(0, 0, dp(32), dp(52));
        root.addView(switchBtn, switchLp);
        switchBtn.setOnClickListener(v -> switchCamera());

        // Подсказка снизу
        TextView hint = new TextView(this);
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        hint.setText("Отпустите для отправки");
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.BOTTOM;
        hintLp.bottomMargin = dp(14);
        root.addView(hint, hintLp);

        return root;
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
            Surface previewSurface  = new Surface(st);
            Surface recorderSurface = mediaRecorder.getSurface();

            if (captureSession != null) { captureSession.close(); captureSession = null; }
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
                                .i("CircleRec","Recording STARTED OK");
                            runOnUiThread(() -> onRecordingStarted());
                        } catch (Exception e) {
                            AppLogger.get(CircleRecordActivity.this)
                                .e("CircleRec","ses.configure error: "+e.getMessage());
                        }
                    }
                    @Override public void onConfigureFailed(@NonNull CameraCaptureSession s) {
                        AppLogger.get(CircleRecordActivity.this).e("CircleRec","configure FAILED");
                    }
                }, bgHandler);
        } catch (Exception e) {
            AppLogger.get(this).e("CircleRec","startRecording exc: "+e.getMessage());
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
        timerText.setVisibility(View.VISIBLE);
        if (switchBtn != null) switchBtn.setVisibility(View.INVISIBLE);

        recordSeconds = 0;
        timerRunnable = new Runnable() {
            @Override public void run() {
                recordSeconds++;
                timerText.setText(recordSeconds / 60 + ":"
                    + String.format("%02d", recordSeconds % 60));
                progressRing.setProgress((float) recordSeconds / MAX_SECONDS);
                if (recordSeconds < MAX_SECONDS) uiHandler.postDelayed(this, 1000);
                else stopRecordingAndSend();
            }
        };
        uiHandler.postDelayed(timerRunnable, 1000);
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
