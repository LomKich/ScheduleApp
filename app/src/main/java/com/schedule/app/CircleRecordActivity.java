package com.schedule.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Нативная запись кружка (видеосообщения) — точно как в Telegram.
 *
 * Показывает круговой превью с фронтальной камеры, прогресс-кольцо,
 * кнопки записи / переключения камеры / отмены.
 *
 * Результат: Intent с EXTRA_VIDEO_PATH = абсолютный путь к .mp4 файлу.
 */
public class CircleRecordActivity extends Activity {

    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final int    REQUEST_CODE     = 2001;
    public static final int    MAX_SECONDS      = 60;

    // ── State ────────────────────────────────────────────────────────────────
    private TextureView     textureView;
    private CameraManager   cameraManager;
    private CameraDevice    cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private MediaRecorder   mediaRecorder;
    private File            videoFile;
    private boolean         isRecording   = false;
    private boolean         isFrontCamera = true;
    private String          currentCameraId;
    private Handler         bgHandler;
    private HandlerThread   bgThread;
    private Handler         uiHandler     = new Handler();
    private int             recordSeconds = 0;
    private Runnable        timerRunnable;
    private Size            videoSize;

    // ── UI refs ──────────────────────────────────────────────────────────────
    private CircleOverlayView overlayView;
    private ProgressRingView  progressRing;
    private TextView          timerText;
    private View              recordBtn;
    private View              recordBtnInner;
    private View              cancelBtn;
    private View              switchBtn;
    private TextView          hintText;

    // ── dp helper ────────────────────────────────────────────────────────────
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Прозрачное окно — кружок поверх всего без чёрного фона
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setContentView(buildUi());
        setupCameraManager();
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
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBgThread();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    // ── UI build ─────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private View buildUi() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        // Кружок 240dp — как в Telegram
        int circleSize = dp(240);

        // Root: полностью прозрачный, поверх всего
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        // ── Полупрозрачный dim-слой (не закрывает весь экран, только фон за кружком) ──
        View dimView = new View(this);
        dimView.setBackgroundColor(0x55000000);
        root.addView(dimView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        // ── Camera TextureView — обрезается в круг через overlayView ─────────
        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(surfaceListener);
        // Кружок располагаем в нижней части экрана (выше кнопки отмены)
        FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(circleSize, circleSize);
        tvLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tvLp.bottomMargin = dp(140);
        root.addView(textureView, tvLp);

        // ── Circular mask overlay — поверх textureView ─────────────────────
        overlayView = new CircleOverlayView(this);
        overlayView.setCircleSize(circleSize);
        FrameLayout.LayoutParams ovLp = new FrameLayout.LayoutParams(circleSize, circleSize);
        ovLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        ovLp.bottomMargin = dp(140);
        root.addView(overlayView, ovLp);

        // ── Progress ring вокруг кружка ──────────────────────────────────────
        progressRing = new ProgressRingView(this);
        int ringSize = circleSize + dp(8);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(ringSize, ringSize);
        ringLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        ringLp.bottomMargin = dp(140) - dp(4);
        root.addView(progressRing, ringLp);

        // ── Timer text — над кружком ─────────────────────────────────────────
        timerText = new TextView(this);
        timerText.setTextColor(Color.WHITE);
        timerText.setTextSize(15);
        timerText.setGravity(Gravity.CENTER);
        timerText.setText("0:00");
        timerText.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams timerLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        timerLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        timerLp.bottomMargin = dp(140) + circleSize + dp(10);
        root.addView(timerText, timerLp);

        // ── Кнопка отмены — крестик снизу слева ────────────────────────────
        cancelBtn = makeRoundBtn("✕", dp(48));
        FrameLayout.LayoutParams cancelLp = new FrameLayout.LayoutParams(dp(48), dp(48));
        cancelLp.gravity = Gravity.BOTTOM | Gravity.START;
        cancelLp.setMargins(dp(32), 0, 0, dp(52));
        root.addView(cancelBtn, cancelLp);
        cancelBtn.setOnClickListener(v -> cancel());

        // ── Кнопка переключения камеры — снизу справа ───────────────────────
        switchBtn = makeRoundBtn("⟳", dp(48));
        FrameLayout.LayoutParams switchLp = new FrameLayout.LayoutParams(dp(48), dp(48));
        switchLp.gravity = Gravity.BOTTOM | Gravity.END;
        switchLp.setMargins(0, 0, dp(32), dp(52));
        root.addView(switchBtn, switchLp);
        switchBtn.setOnClickListener(v -> switchCamera());

        // ── Hint text — под кружком ──────────────────────────────────────────
        hintText = new TextView(this);
        hintText.setTextColor(0xCCFFFFFF);
        hintText.setTextSize(13);
        hintText.setGravity(Gravity.CENTER);
        hintText.setText("Удерживайте для записи");
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hintLp.bottomMargin = dp(110);
        root.addView(hintText, hintLp);

        // ── Большая кнопка записи — снизу по центру ─────────────────────────
        FrameLayout recordBtnFrame = new FrameLayout(this);
        recordBtnFrame.setClickable(true);
        recordBtnFrame.setFocusable(true);

        View recordBtnOuter = new View(this);
        recordBtnOuter.setBackground(makeCircleDrawable(0x33FFFFFF));
        FrameLayout.LayoutParams outerLp = new FrameLayout.LayoutParams(dp(76), dp(76));
        outerLp.gravity = Gravity.CENTER;
        recordBtnFrame.addView(recordBtnOuter, outerLp);

        recordBtnInner = new View(this);
        recordBtnInner.setBackground(makeCircleDrawable(0xFFFF3B30));
        FrameLayout.LayoutParams innerLp = new FrameLayout.LayoutParams(dp(58), dp(58));
        innerLp.gravity = Gravity.CENTER;
        recordBtnFrame.addView(recordBtnInner, innerLp);

        recordBtn = recordBtnFrame;
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(dp(76), dp(76));
        btnLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        btnLp.bottomMargin = dp(18);
        root.addView(recordBtn, btnLp);

        recordBtn.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecordingAndSend();
            }
        });

        return root;
    }

    // ── Camera setup ──────────────────────────────────────────────────────────

    private void setupCameraManager() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
    }

    private void startBgThread() {
        bgThread  = new HandlerThread("CameraBG");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    private void stopBgThread() {
        if (bgThread != null) {
            bgThread.quitSafely();
            try { bgThread.join(); } catch (InterruptedException ignored) {}
            bgThread  = null;
            bgHandler = null;
        }
    }

    private String getCameraId(boolean front) {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics ch = cameraManager.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (front && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
                if (!front && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
            }
        } catch (CameraAccessException e) {
            AppLogger.get(this).e("CircleRec", "getCameraId error: " + e.getMessage());
        }
        return null;
    }

    private Size chooseVideoSize(String cameraId) {
        try {
            CameraCharacteristics ch = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return new Size(480, 480);
            Size[] sizes = map.getOutputSizes(MediaRecorder.class);
            // Ищем размер близкий к квадрату (мин. сторона >= 480, макс. <= 1280)
            Size best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Size s : sizes) {
                int w = s.getWidth(), h = s.getHeight();
                if (Math.min(w, h) < 480 || Math.max(w, h) > 1280) continue;
                // Предпочитаем квадратные, потом близкие к квадрату
                int diff = Math.abs(w - h);
                int side = Math.min(w, h);
                int score = diff * 10 - side; // меньше разница — лучше, больше сторона — лучше
                if (score < bestScore) { bestScore = score; best = s; }
            }
            if (best != null) return best;
            // Fallback — любой ≤ 1280 с обеими сторонами ≥ 480
            for (Size s : sizes) {
                if (Math.min(s.getWidth(), s.getHeight()) >= 480 && Math.max(s.getWidth(), s.getHeight()) <= 1280)
                    return s;
            }
            return new Size(640, 480);
        } catch (CameraAccessException e) {
            return new Size(640, 480);
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        currentCameraId = getCameraId(isFrontCamera);
        if (currentCameraId == null) {
            currentCameraId = getCameraId(!isFrontCamera);
        }
        if (currentCameraId == null) {
            AppLogger.get(this).e("CircleRec", "No camera found");
            finish();
            return;
        }
        videoSize = chooseVideoSize(currentCameraId);
        configureTransform(width, height);
        try {
            cameraManager.openCamera(currentCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }
                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close(); cameraDevice = null;
                }
                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close(); cameraDevice = null;
                    AppLogger.get(CircleRecordActivity.this).e("CircleRec", "Camera error: " + error);
                    finish();
                }
            }, bgHandler);
        } catch (CameraAccessException e) {
            AppLogger.get(this).e("CircleRec", "openCamera: " + e.getMessage());
        }
    }

    private void closeCamera() {
        if (isRecording) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            isRecording = false;
        }
        if (captureSession != null) {
            captureSession.close(); captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close(); cameraDevice = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release(); mediaRecorder = null;
        }
        if (timerRunnable != null) {
            uiHandler.removeCallbacks(timerRunnable); timerRunnable = null;
        }
    }

    private void startPreview() {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
            Surface previewSurface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(
                Collections.singletonList(previewSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                CaptureRequest.CONTROL_MODE_AUTO);
                            captureSession.setRepeatingRequest(
                                previewRequestBuilder.build(), null, bgHandler);
                        } catch (CameraAccessException e) {
                            AppLogger.get(CircleRecordActivity.this)
                                .e("CircleRec", "startPreview setRepeating: " + e.getMessage());
                        }
                    }
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        AppLogger.get(CircleRecordActivity.this)
                            .e("CircleRec", "Preview configure failed");
                    }
                }, bgHandler);
        } catch (CameraAccessException e) {
            AppLogger.get(this).e("CircleRec", "startPreview: " + e.getMessage());
        }
    }

    // ── Recording ────────────────────────────────────────────────────────────

    private void startRecording() {
        if (cameraDevice == null) return;
        try {
            videoFile = File.createTempFile("circle_", ".mp4", getCacheDir());
            setupMediaRecorder();

            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
            Surface previewSurface  = new Surface(texture);
            Surface recorderSurface = mediaRecorder.getSurface();

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(recorderSurface);

            if (captureSession != null) { captureSession.close(); captureSession = null; }

            cameraDevice.createCaptureSession(
                Arrays.asList(previewSurface, recorderSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                CaptureRequest.CONTROL_MODE_AUTO);
                            captureSession.setRepeatingRequest(
                                previewRequestBuilder.build(), null, bgHandler);
                            mediaRecorder.start();
                            isRecording = true;
                            runOnUiThread(() -> onRecordingStarted());
                        } catch (Exception e) {
                            AppLogger.get(CircleRecordActivity.this)
                                .e("CircleRec", "record configure error: " + e.getMessage());
                        }
                    }
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        AppLogger.get(CircleRecordActivity.this).e("CircleRec", "Record configure failed");
                    }
                }, bgHandler);

        } catch (Exception e) {
            AppLogger.get(this).e("CircleRec", "startRecording error: " + e.getMessage());
        }
    }

    private void setupMediaRecorder() throws Exception {
        mediaRecorder = new MediaRecorder();
        // ВАЖНО: audioSource должен быть установлен ДО videoSource
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());

        // Используем реальный размер, поддерживаемый камерой
        int w = videoSize.getWidth(), h = videoSize.getHeight();
        // Квадратное видео для кружка — берём меньшую сторону
        int side = Math.min(w, h);
        // Ограничиваем до 720 для совместимости
        if (side > 720) side = 720;
        mediaRecorder.setVideoSize(side, side);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(2_000_000);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setMaxDuration(MAX_SECONDS * 1000);
        mediaRecorder.setOnInfoListener((mr, what, extra) -> {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                runOnUiThread(() -> stopRecordingAndSend());
            }
        });

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int hint = getSensorOrientation(currentCameraId);
        mediaRecorder.setOrientationHint(getRecordOrientation(hint, rotation, isFrontCamera));
        mediaRecorder.prepare();
    }

    private int getSensorOrientation(String camId) {
        try {
            return cameraManager.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (Exception e) { return 90; }
    }

    private int getRecordOrientation(int sensorOri, int deviceRot, boolean front) {
        int[] ORIENTATIONS = {0, 90, 180, 270};
        int deviceDeg = ORIENTATIONS[deviceRot % 4];
        if (front) {
            return (sensorOri + deviceDeg + 360) % 360;
        } else {
            return (sensorOri - deviceDeg + 360) % 360;
        }
    }

    private void onRecordingStarted() {
        // Анимируем кнопку: красный круг → красный квадрат (как в Telegram)
        recordBtnInner.animate().scaleX(0.5f).scaleY(0.5f).setDuration(150).start();
        recordBtnInner.setBackground(makeRoundedRectDrawable(0xFFFF3B30, dp(10)));

        timerText.setVisibility(View.VISIBLE);
        timerText.setTextColor(0xFFFF3B30);
        hintText.setText("Нажмите ещё раз для отправки");
        progressRing.setProgress(0);
        progressRing.setVisibility(View.VISIBLE);
        switchBtn.setVisibility(View.INVISIBLE); // нельзя переключить во время записи

        recordSeconds = 0;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                recordSeconds++;
                int m = recordSeconds / 60, s = recordSeconds % 60;
                timerText.setText(m + ":" + String.format("%02d", s));
                float progress = (float) recordSeconds / MAX_SECONDS;
                progressRing.setProgress(progress);
                if (recordSeconds < MAX_SECONDS) {
                    uiHandler.postDelayed(this, 1000);
                } else {
                    stopRecordingAndSend();
                }
            }
        };
        uiHandler.postDelayed(timerRunnable, 1000);
    }

    private void stopRecordingAndSend() {
        if (!isRecording) return;
        isRecording = false;

        if (timerRunnable != null) {
            uiHandler.removeCallbacks(timerRunnable); timerRunnable = null;
        }
        if (captureSession != null) {
            captureSession.close(); captureSession = null;
        }
        try { mediaRecorder.stop(); } catch (Exception ignored) {}
        mediaRecorder.release(); mediaRecorder = null;

        // Возвращаем путь к файлу в MainActivity
        Intent result = new Intent();
        result.putExtra(EXTRA_VIDEO_PATH, videoFile.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    private void cancel() {
        if (isRecording) {
            isRecording = false;
            if (timerRunnable != null) uiHandler.removeCallbacks(timerRunnable);
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
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

    // ── TextureView listener ──────────────────────────────────────────────────

    private final TextureView.SurfaceTextureListener surfaceListener =
        new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int w, int h) {
                openCamera(w, h);
            }
            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {
                configureTransform(w, h);
            }
            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return true; }
            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        };

    private void configureTransform(int viewWidth, int viewHeight) {
        if (videoSize == null) return;
        Matrix matrix = new Matrix();
        int rotation  = getWindowManager().getDefaultDisplay().getRotation();
        RectF viewRect   = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, videoSize.getHeight(), videoSize.getWidth());
        float cx = viewRect.centerX(), cy = viewRect.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                (float) viewHeight / videoSize.getHeight(),
                (float) viewWidth  / videoSize.getWidth());
            matrix.postScale(scale, scale, cx, cy);
            matrix.postRotate(90 * (rotation - 2), cx, cy);
        } else {
            // Portrait — масштабируем чтобы заполнить квадрат
            float scaleX = (float) viewWidth  / videoSize.getWidth();
            float scaleY = (float) viewHeight / videoSize.getHeight();
            float scale  = Math.max(scaleX, scaleY);
            matrix.setScale(scale, scale, cx, cy);
            if (rotation == Surface.ROTATION_180) {
                matrix.postRotate(180, cx, cy);
            }
        }
        if (isFrontCamera) {
            matrix.postScale(-1, 1, cx, cy); // mirror front camera
        }
        runOnUiThread(() -> textureView.setTransform(matrix));
    }

    // ── Custom Views ──────────────────────────────────────────────────────────

    /** Накладывает маску-кольцо: показывает только круговое видео, скрывает углы. */
    private class CircleOverlayView extends View {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int circleSize = 0;

        CircleOverlayView(android.content.Context ctx) {
            super(ctx);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void setCircleSize(int size) { circleSize = size; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            // Прозрачный фон
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            // Рисуем белый круг-маску (только внутри кружка видно видео)
            // Сначала заливаем всё непрозрачным чёрным
            canvas.drawColor(Color.BLACK);
            // Затем вырезаем круг
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - dp(1), paint);
            paint.setXfermode(null);
        }
    }

    /** Кольцо прогресса вокруг круга (анимируется при записи). */
    private class ProgressRingView extends View {
        private float progress = 0f;
        private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval     = new RectF();

        ProgressRingView(android.content.Context ctx) {
            super(ctx);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(dp(3));
            bgPaint.setColor(0x33FFFFFF);

            fgPaint.setStyle(Paint.Style.STROKE);
            fgPaint.setStrokeWidth(dp(3));
            fgPaint.setColor(0xFFFF3B30); // Telegram red
            fgPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cx = getWidth() / 2, cy = getHeight() / 2;
            int r  = Math.min(cx, cy) - dp(6);
            oval.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(oval, 0, 360, false, bgPaint);
            if (progress > 0) {
                canvas.drawArc(oval, -90, progress * 360, false, fgPaint);
            }
        }
    }

    // ── Drawable helpers ──────────────────────────────────────────────────────

    private android.graphics.drawable.Drawable makeCircleDrawable(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private android.graphics.drawable.Drawable makeRoundedRectDrawable(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(color);
        return d;
    }

    private View makeRoundBtn(String symbol, int size) {
        FrameLayout fl = new FrameLayout(this);
        fl.setBackground(makeCircleDrawable(0x66000000));
        fl.setClickable(true);
        fl.setFocusable(true);
        TextView tv = new TextView(this);
        tv.setText(symbol);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        fl.addView(tv, new FrameLayout.LayoutParams(size, size));
        fl.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        return fl;
    }
}
