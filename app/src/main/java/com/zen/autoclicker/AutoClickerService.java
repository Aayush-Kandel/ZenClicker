package com.zen.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class AutoClickerService extends AccessibilityService {

    public static final String ACTION_SHOW_OVERLAY = "com.zen.autoclicker.ACTION_SHOW";
    public static final String ACTION_HIDE_OVERLAY = "com.zen.autoclicker.ACTION_HIDE";
    public static final String ACTION_STOP = "com.zen.autoclicker.ACTION_STOP";
    public static final String ACTION_UPDATE_CONFIG = "com.zen.autoclicker.ACTION_UPDATE_CONFIG";

    private static final String ACTION_NOTIF_STOP = "com.zen.autoclicker.NOTIF_STOP";
    private static final String ACTION_NOTIF_HIDE = "com.zen.autoclicker.NOTIF_HIDE";

    private static final String CHANNEL_ID = "zen_clicker_controls";
    private static final int NOTIFICATION_ID = 1001;

    static volatile AutoClickerService instance;

    private WindowManager windowManager;
    private NotificationManager notificationManager;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TargetPinView pin;
    private FloatingControlBarView bar;
    private boolean overlayShown = false;
    private volatile boolean clicking = false;
    private boolean isMoveMode = false;

    private static int lastPinX = -1;
    private static int lastPinY = -1;
    private static int lastBarX = -1;
    private static int lastBarY = -1;

    public static boolean isOverlayShowing() {
        return instance != null && instance.overlayShown;
    }

    private int intervalMs = 100;
    private boolean gestureInFlight = false;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (clicking && gestureInFlight) {
                gestureInFlight = false;
                scheduleNextClick(10);
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (clicking) {
                    stopClicking();
                }
            }
        }
    };

    private final BroadcastReceiver notificationActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (ACTION_NOTIF_STOP.equals(intent.getAction())) {
                stopClicking();
                vibrateTick(true);
            } else if (ACTION_NOTIF_HIDE.equals(intent.getAction())) {
                hideOverlay();
            }
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.os.VibratorManager vm = (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        createNotificationChannel();

        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, screenFilter);

        IntentFilter notifFilter = new IntentFilter();
        notifFilter.addAction(ACTION_NOTIF_STOP);
        notifFilter.addAction(ACTION_NOTIF_HIDE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(notificationActionReceiver, notifFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationActionReceiver, notifFilter);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        reloadConfig();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_SHOW_OVERLAY:
                    reloadConfig();
                    showOverlay();
                    break;
                case ACTION_HIDE_OVERLAY:
                    hideOverlay();
                    break;
                case ACTION_STOP:
                    stopClicking();
                    break;
                case ACTION_UPDATE_CONFIG:
                    reloadConfig();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Physical volume key emergency stop
        if (clicking && event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP) {
                stopClicking();
                vibrateTick(true);
                return true;
            }
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onDestroy() {
        instance = null;
        stopClicking();
        hideOverlay();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(notificationActionReceiver); } catch (Exception ignored) {}
        cancelNotification();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (overlayShown) {
            clampViewsToScreen();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        stopClicking();
    }

    public void reloadConfig() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        intervalMs = Math.max(40, prefs.getInt(MainActivity.KEY_INTERVAL, 100));
    }

    public void setMoveMode(boolean enabled) {
        isMoveMode = enabled;
        if (pin != null) {
            pin.setMoveMode(enabled);
        }
        if (bar != null) {
            bar.invalidate();
        }
    }

    // ---------- overlay management ----------

    public void showOverlay() {
        if (overlayShown) return;
        overlayShown = true;
        isMoveMode = false; // Default to Pinned mode

        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();

        pin = new TargetPinView(this);
        bar = new FloatingControlBarView(this);

        int pinSize = dp(52);
        WindowManager.LayoutParams pinParams = new WindowManager.LayoutParams(
                pinSize, pinSize,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        pinParams.gravity = Gravity.TOP | Gravity.START;
        pinParams.x = (lastPinX >= 0) ? Math.min(screenWidth - pinSize, lastPinX) : (screenWidth - pinSize) / 2;
        pinParams.y = (lastPinY >= 0) ? Math.min(screenHeight - pinSize, lastPinY) : (screenHeight - pinSize) / 2;

        int barWidth = dp(148);
        int barHeight = dp(52);
        WindowManager.LayoutParams barParams = new WindowManager.LayoutParams(
                barWidth, barHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        barParams.gravity = Gravity.TOP | Gravity.START;
        barParams.x = (lastBarX >= 0) ? Math.min(screenWidth - barWidth, lastBarX) : Math.max(0, screenWidth - barWidth - dp(16));
        barParams.y = (lastBarY >= 0) ? Math.min(screenHeight - barHeight, lastBarY) : (screenHeight - barHeight) / 2;

        try {
            windowManager.addView(pin, pinParams);
            windowManager.addView(bar, barParams);
            updateNotification();
        } catch (Exception e) {
            overlayShown = false;
        }
    }

    public void hideOverlay() {
        if (!overlayShown) return;
        overlayShown = false;
        stopClicking();

        if (pin != null && pin.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) pin.getLayoutParams();
            lastPinX = p.x;
            lastPinY = p.y;
        }
        if (bar != null && bar.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) bar.getLayoutParams();
            lastBarX = p.x;
            lastBarY = p.y;
        }

        try {
            if (pin != null) windowManager.removeView(pin);
        } catch (Exception ignored) {}
        try {
            if (bar != null) windowManager.removeView(bar);
        } catch (Exception ignored) {}

        pin = null;
        bar = null;
        cancelNotification();
    }

    private void clampViewsToScreen() {
        int screenW = getScreenWidth();
        int screenH = getScreenHeight();

        if (pin != null && pin.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) pin.getLayoutParams();
            p.x = Math.max(0, Math.min(screenW - pin.getWidth(), p.x));
            p.y = Math.max(0, Math.min(screenH - pin.getHeight(), p.y));
            try { windowManager.updateViewLayout(pin, p); } catch (Exception ignored) {}
        }
        if (bar != null && bar.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) bar.getLayoutParams();
            p.x = Math.max(0, Math.min(screenW - bar.getWidth(), p.x));
            p.y = Math.max(0, Math.min(screenH - bar.getHeight(), p.y));
            try { windowManager.updateViewLayout(bar, p); } catch (Exception ignored) {}
        }
    }

    // ---------- click loop & dispatch (backlog-free) ----------

    public void startClicking() {
        if (clicking) return;
        if (isMoveMode) {
            setMoveMode(false); // Automatically lock in place when clicking starts
        }
        reloadConfig();
        clicking = true;
        gestureInFlight = false;
        setPinPassThrough(true);
        if (bar != null) bar.invalidate();
        updateNotification();

        handler.removeCallbacksAndMessages(null);
        scheduleNextClick(0);
    }

    public void stopClicking() {
        if (!clicking) return;
        clicking = false;
        gestureInFlight = false;

        handler.removeCallbacksAndMessages(null);

        setPinPassThrough(false);
        if (bar != null) bar.invalidate();
        updateNotification();
    }

    private void scheduleNextClick(long delayMs) {
        if (!clicking) return;
        handler.postDelayed(() -> {
            if (!clicking) return;
            performClick();
        }, delayMs);
    }

    private void performClick() {
        if (!clicking || pin == null || gestureInFlight) return;

        int screenW = getScreenWidth();
        int screenH = getScreenHeight();

        int x = Math.max(1, Math.min(screenW - 1, pin.centerX()));
        int y = Math.max(1, Math.min(screenH - 1, pin.centerY()));

        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);

        int strokeDuration = 10;
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, strokeDuration));

        gestureInFlight = true;

        handler.removeCallbacks(watchdogRunnable);
        handler.postDelayed(watchdogRunnable, Math.max(intervalMs + 100, 200));

        try {
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    handler.removeCallbacks(watchdogRunnable);
                    gestureInFlight = false;
                    if (clicking) {
                        long delay = Math.max(10, intervalMs - strokeDuration);
                        scheduleNextClick(delay);
                    }
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    handler.removeCallbacks(watchdogRunnable);
                    gestureInFlight = false;
                    if (clicking) {
                        scheduleNextClick(15);
                    }
                }
            }, handler);
        } catch (Exception e) {
            gestureInFlight = false;
            stopClicking();
        }
    }

    private void setPinPassThrough(boolean passThrough) {
        if (pin == null || !overlayShown) return;
        try {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) pin.getLayoutParams();
            if (passThrough) {
                p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            } else {
                p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            windowManager.updateViewLayout(pin, p);
        } catch (Exception ignored) {}
    }

    private void vibrateTick(boolean longBuzz) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            long duration = longBuzz ? 90 : 25;
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    // ---------- notifications ----------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.enableVibration(false);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotification() {
        if (!overlayShown || notificationManager == null) return;

        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pContent = PendingIntent.getActivity(this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent stopIntent = new Intent(ACTION_NOTIF_STOP).setPackage(getPackageName());
        PendingIntent pStop = PendingIntent.getBroadcast(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent hideIntent = new Intent(ACTION_NOTIF_HIDE).setPackage(getPackageName());
        PendingIntent pHide = PendingIntent.getBroadcast(this, 2, hideIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        String intervalText = intervalMs < 1000 ? intervalMs + "ms" : String.format(java.util.Locale.US, "%.1fs", intervalMs / 1000f);
        String msg = clicking
                ? getString(R.string.notification_msg_running, intervalText)
                : getString(R.string.notification_msg_paused);

        Notification.Builder nb = new Notification.Builder(this, CHANNEL_ID);
        nb.setSmallIcon(R.drawable.ic_app)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(msg)
                .setContentIntent(pContent)
                .setOngoing(true)
                .setAutoCancel(false);

        android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_app);
        if (clicking) {
            nb.addAction(new Notification.Action.Builder(icon, getString(R.string.action_stop), pStop).build());
        }
        nb.addAction(new Notification.Action.Builder(icon, getString(R.string.action_hide), pHide).build());

        notificationManager.notify(NOTIFICATION_ID, nb.build());
    }

    private void cancelNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    private int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    static int dp(int v) {
        return (int) (v * Resources.getSystem().getDisplayMetrics().density);
    }

    static float dpF(float v) {
        return v * Resources.getSystem().getDisplayMetrics().density;
    }

    // ---------- views ----------

    private final class TargetPinView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint moveIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float downRawX, downRawY;
        private int initialParamX, initialParamY;
        private boolean isDragging = false;

        TargetPinView(Context c) {
            super(c);
            ringPaint.setStyle(Paint.Style.STROKE);
            fillPaint.setStyle(Paint.Style.FILL);
            dotPaint.setStyle(Paint.Style.FILL);
            centerPointPaint.setStyle(Paint.Style.FILL);
            centerPointPaint.setColor(Color.WHITE);

            moveIndicatorPaint.setStyle(Paint.Style.STROKE);
            moveIndicatorPaint.setStrokeWidth(dpF(2.0f));
            moveIndicatorPaint.setColor(Color.argb(250, 79, 157, 255));

            int touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

            setOnTouchListener((v, e) -> {
                // If not in Move Mode, touches pass through and do NOT move the pin! (PINNED)
                if (!isMoveMode) {
                    return false;
                }

                WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams();
                int screenW = getScreenWidth();
                int screenH = getScreenHeight();

                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        initialParamX = p.x;
                        initialParamY = p.y;
                        isDragging = false;
                        setScaleX(1.06f);
                        setScaleY(1.06f);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downRawX;
                        float dy = e.getRawY() - downRawY;
                        if (!isDragging && Math.hypot(dx, dy) > touchSlop) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            p.x = Math.max(0, Math.min(screenW - getWidth(), (int) (initialParamX + dx)));
                            p.y = Math.max(0, Math.min(screenH - getHeight(), (int) (initialParamY + dy)));
                            try { windowManager.updateViewLayout(v, p); } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        setScaleX(1.0f);
                        setScaleY(1.0f);
                        return true;
                }
                return false;
            });
        }

        void setMoveMode(boolean enabled) {
            invalidate();
        }

        int centerX() {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            return loc[0] + getWidth() / 2;
        }

        int centerY() {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            return loc[1] + getHeight() / 2;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float c = getWidth() / 2f;
            float r = c - dpF(4.5f);

            if (isMoveMode) {
                // Glow & Move styling when in Move Mode
                ringPaint.setStrokeWidth(dpF(2.5f));
                ringPaint.setColor(Color.argb(255, 79, 157, 255));
                fillPaint.setColor(Color.argb(70, 79, 157, 255));
                dotPaint.setColor(Color.argb(255, 79, 157, 255));

                canvas.drawCircle(c, c, r, fillPaint);
                canvas.drawCircle(c, c, r, ringPaint);
                canvas.drawCircle(c, c, dpF(4f), dotPaint);
                canvas.drawCircle(c, c, dpF(1.2f), centerPointPaint);

                // Move directional tick indicators
                float tickOut = c - dpF(1.5f);
                float tickIn = c - dpF(5.5f);
                canvas.drawLine(c, tickIn, c, tickOut, moveIndicatorPaint);
                canvas.drawLine(c, c * 2 - tickIn, c, c * 2 - tickOut, moveIndicatorPaint);
                canvas.drawLine(tickIn, c, tickOut, c, moveIndicatorPaint);
                canvas.drawLine(c * 2 - tickIn, c, c * 2 - tickOut, c, moveIndicatorPaint);
            } else {
                // Clean Minimal Pinned Reticle
                ringPaint.setStrokeWidth(dp(2));
                ringPaint.setColor(Color.argb(210, 79, 157, 255));
                fillPaint.setColor(Color.argb(40, 79, 157, 255));
                dotPaint.setColor(Color.argb(240, 79, 157, 255));

                canvas.drawCircle(c, c, r, fillPaint);
                canvas.drawCircle(c, c, r, ringPaint);
                canvas.drawCircle(c, c, dpF(3.5f), dotPaint);
                canvas.drawCircle(c, c, dpF(1f), centerPointPaint);

                // Crosshair markers
                canvas.drawLine(c - dp(8), c, c - dpF(3.5f), c, ringPaint);
                canvas.drawLine(c + dpF(3.5f), c, c + dp(8), c, ringPaint);
                canvas.drawLine(c, c - dp(8), c, c - dpF(3.5f), ringPaint);
                canvas.drawLine(c, c + dpF(3.5f), c, c + dp(8), ringPaint);
            }
        }
    }

    private final class FloatingControlBarView extends View {
        private final Paint barBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint btnBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint padlockShacklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path playPath = new Path();
        private final RectF pauseBar1 = new RectF();
        private final RectF pauseBar2 = new RectF();
        private final RectF padlockBody = new RectF();
        private final RectF padlockArch = new RectF();

        private float downRawX, downRawY;
        private int initialParamX, initialParamY;
        private boolean isDragging = false;
        private int touchedButton = -1; // 0=Play, 1=Move, 2=Close
        private boolean wasClickingOnDown = false;

        FloatingControlBarView(Context c) {
            super(c);
            barBgPaint.setStyle(Paint.Style.FILL);
            barBgPaint.setColor(Color.parseColor("#171920"));

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dpF(1.5f));
            borderPaint.setColor(Color.parseColor("#2C313E"));

            dividerPaint.setStyle(Paint.Style.STROKE);
            dividerPaint.setStrokeWidth(dpF(1.0f));
            dividerPaint.setColor(Color.argb(35, 255, 255, 255));

            btnBgPaint.setStyle(Paint.Style.FILL);
            iconPaint.setStyle(Paint.Style.FILL);

            padlockShacklePaint.setStyle(Paint.Style.STROKE);
            padlockShacklePaint.setStrokeWidth(dpF(1.8f));

            int touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

            setOnTouchListener((v, e) -> {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams();
                int screenW = getScreenWidth();
                int screenH = getScreenHeight();

                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        initialParamX = p.x;
                        initialParamY = p.y;
                        isDragging = false;

                        float touchX = e.getX();
                        if (touchX < dp(52)) {
                            touchedButton = 0; // Play/Pause
                            wasClickingOnDown = clicking;
                            if (clicking) {
                                stopClicking();
                                vibrateTick(false);
                                return true;
                            }
                        } else if (touchX < dp(100)) {
                            touchedButton = 1; // Move/Lock
                        } else {
                            touchedButton = 2; // Close
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (touchedButton == 0 && wasClickingOnDown) {
                            return true; // Stop touch event: ignore move
                        }

                        // Dragging the bar is only active in Move Mode!
                        if (isMoveMode) {
                            float dx = e.getRawX() - downRawX;
                            float dy = e.getRawY() - downRawY;
                            if (!isDragging && Math.hypot(dx, dy) > touchSlop) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                p.x = Math.max(0, Math.min(screenW - getWidth(), (int) (initialParamX + dx)));
                                p.y = Math.max(0, Math.min(screenH - getHeight(), (int) (initialParamY + dy)));
                                try { windowManager.updateViewLayout(v, p); } catch (Exception ignored) {}
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (touchedButton == 0) {
                            if (wasClickingOnDown) {
                                wasClickingOnDown = false;
                                touchedButton = -1;
                                isDragging = false;
                                return true;
                            }
                            if (!isDragging) {
                                if (isMoveMode) setMoveMode(false);
                                startClicking();
                                vibrateTick(false);
                            }
                        } else if (touchedButton == 1) {
                            if (!isDragging) {
                                setMoveMode(!isMoveMode);
                                vibrateTick(false);
                            }
                        } else if (touchedButton == 2) {
                            if (!isDragging) {
                                hideOverlay();
                                vibrateTick(false);
                            }
                        }
                        touchedButton = -1;
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        touchedButton = -1;
                        wasClickingOnDown = false;
                        isDragging = false;
                        return true;
                }
                return false;
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float radius = h / 2f;

            // Bar background & border
            canvas.drawRoundRect(0, 0, w, h, radius, radius, barBgPaint);
            canvas.drawRoundRect(0, 0, w, h, radius, radius, borderPaint);

            // Vertical Dividers between the 3 buttons
            canvas.drawLine(dp(52), dp(13), dp(52), h - dp(13), dividerPaint);
            canvas.drawLine(dp(100), dp(13), dp(100), h - dp(13), dividerPaint);

            // ----------------------------------------------------
            // BUTTON 1: Play / Pause (Center cx=26, cy=26)
            // ----------------------------------------------------
            float c1x = dp(26);
            float c1y = h / 2f;
            int playBgColor = clicking ? Color.argb(245, 43, 213, 118) : Color.argb(245, 79, 157, 255);
            btnBgPaint.setColor(playBgColor);
            canvas.drawCircle(c1x, c1y, dpF(19f), btnBgPaint);

            iconPaint.setColor(Color.argb(250, 11, 13, 16));
            if (clicking) {
                // Pause bars
                float barW = dpF(3.5f);
                float barH = dp(14);
                float sp = dp(3);
                pauseBar1.set(c1x - sp - barW, c1y - barH / 2f, c1x - sp, c1y + barH / 2f);
                pauseBar2.set(c1x + sp, c1y - barH / 2f, c1x + sp + barW, c1y + barH / 2f);
                canvas.drawRoundRect(pauseBar1, dp(2), dp(2), iconPaint);
                canvas.drawRoundRect(pauseBar2, dp(2), dp(2), iconPaint);
            } else {
                // Play triangle
                playPath.reset();
                float offset = dpF(1f);
                playPath.moveTo(c1x - dp(5) + offset, c1y - dp(8));
                playPath.lineTo(c1x + dp(8) + offset, c1y);
                playPath.lineTo(c1x - dp(5) + offset, c1y + dp(8));
                playPath.close();
                canvas.drawPath(playPath, iconPaint);
            }

            // ----------------------------------------------------
            // BUTTON 2: Move / Pin Toggle (Center cx=76, cy=26)
            // ----------------------------------------------------
            float c2x = dp(76);
            float c2y = h / 2f;

            if (isMoveMode) {
                // Active Move Mode: Highlighted blue with 4-way crosshair arrows
                btnBgPaint.setColor(Color.argb(255, 79, 157, 255));
                canvas.drawCircle(c2x, c2y, dpF(17f), btnBgPaint);

                iconPaint.setColor(Color.argb(250, 11, 13, 16));
                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(dpF(2.0f));

                // 4-way arrows
                canvas.drawLine(c2x - dp(8), c2y, c2x + dp(8), c2y, iconPaint);
                canvas.drawLine(c2x, c2y - dp(8), c2x, c2y + dp(8), iconPaint);

                iconPaint.setStyle(Paint.Style.FILL);
                // Arrow tips
                Path arrowPath = new Path();
                // Left
                arrowPath.moveTo(c2x - dp(8), c2y);
                arrowPath.lineTo(c2x - dp(5), c2y - dp(3));
                arrowPath.lineTo(c2x - dp(5), c2y + dp(3));
                arrowPath.close();
                // Right
                arrowPath.moveTo(c2x + dp(8), c2y);
                arrowPath.lineTo(c2x + dp(5), c2y - dp(3));
                arrowPath.lineTo(c2x + dp(5), c2y + dp(3));
                arrowPath.close();
                // Top
                arrowPath.moveTo(c2x, c2y - dp(8));
                arrowPath.lineTo(c2x - dp(3), c2y - dp(5));
                arrowPath.lineTo(c2x + dp(3), c2y - dp(5));
                arrowPath.close();
                // Bottom
                arrowPath.moveTo(c2x, c2y + dp(8));
                arrowPath.lineTo(c2x - dp(3), c2y + dp(5));
                arrowPath.lineTo(c2x + dp(3), c2y + dp(5));
                arrowPath.close();
                canvas.drawPath(arrowPath, iconPaint);
            } else {
                // Pinned Mode: Dark circular button with Padlock icon
                btnBgPaint.setColor(Color.parseColor("#222630"));
                canvas.drawCircle(c2x, c2y, dpF(17f), btnBgPaint);

                iconPaint.setColor(Color.parseColor("#8A8F98"));
                iconPaint.setStyle(Paint.Style.FILL);

                // Padlock Body
                padlockBody.set(c2x - dp(5), c2y - dp(1), c2x + dp(5), c2y + dp(7));
                canvas.drawRoundRect(padlockBody, dp(2), dp(2), iconPaint);

                // Padlock Arch/Shackle
                padlockShacklePaint.setColor(Color.parseColor("#8A8F98"));
                padlockArch.set(c2x - dpF(3.5f), c2y - dpF(6.5f), c2x + dpF(3.5f), c2y);
                canvas.drawArc(padlockArch, 180, 180, false, padlockShacklePaint);
            }

            // ----------------------------------------------------
            // BUTTON 3: Close [X] (Center cx=124, cy=26)
            // ----------------------------------------------------
            float c3x = dp(124);
            float c3y = h / 2f;
            btnBgPaint.setColor(Color.parseColor("#1D2028"));
            canvas.drawCircle(c3x, c3y, dpF(15f), btnBgPaint);

            iconPaint.setColor(Color.parseColor("#8A8F98"));
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(dpF(2.0f));

            float xRadius = dpF(4.5f);
            canvas.drawLine(c3x - xRadius, c3y - xRadius, c3x + xRadius, c3y + xRadius, iconPaint);
            canvas.drawLine(c3x + xRadius, c3y - xRadius, c3x - xRadius, c3y + xRadius, iconPaint);
        }
    }
}
