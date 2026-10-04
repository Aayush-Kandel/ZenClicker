package com.zen.autoclicker;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS = "zen";
    public static final String KEY_INTERVAL = "interval_ms";

    private TextView intervalLabel;
    private TextView cpsLabel;
    private TextView permStatus;
    private TextView permBadge;
    private LinearLayout permCard;
    private Button startButton;
    private SeekBar seek;

    private TextView[] presetChips;
    private final int[] presetValues = {50, 100, 200, 500, 1000};

    private int intervalMs = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intervalLabel = findViewById(R.id.intervalLabel);
        cpsLabel = findViewById(R.id.cpsLabel);
        permStatus = findViewById(R.id.permStatus);
        permBadge = findViewById(R.id.permBadge);
        permCard = findViewById(R.id.permCard);
        startButton = findViewById(R.id.startButton);
        seek = findViewById(R.id.intervalSeek);

        presetChips = new TextView[]{
                findViewById(R.id.preset50),
                findViewById(R.id.preset100),
                findViewById(R.id.preset200),
                findViewById(R.id.preset500),
                findViewById(R.id.preset1000)
        };

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        intervalMs = prefs.getInt(KEY_INTERVAL, 100);

        // Setup slider & display
        seek.setProgress(intervalToProgress(intervalMs));
        updateSpeedDisplay();
        highlightMatchingPreset();

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (fromUser) {
                    intervalMs = progressToInterval(v);
                    updateSpeedDisplay();
                    highlightMatchingPreset();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                saveConfig();
            }
        });

        for (int i = 0; i < presetChips.length; i++) {
            final int value = presetValues[i];
            presetChips[i].setOnClickListener(v -> {
                intervalMs = value;
                seek.setProgress(intervalToProgress(intervalMs));
                updateSpeedDisplay();
                highlightMatchingPreset();
                saveConfig();
            });
        }

        permCard.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        findViewById(R.id.restrictedHelpText).setOnClickListener(v -> showRestrictedGuideDialog());

        startButton.setOnClickListener(v -> {
            if (!isServiceRunning()) {
                Toast.makeText(this, R.string.toast_permission_required, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }

            saveConfig();
            if (AutoClickerService.isOverlayShowing()) {
                if (AutoClickerService.instance != null) {
                    AutoClickerService.instance.hideOverlay();
                }
                updateServiceStatus();
            } else {
                if (AutoClickerService.instance != null) {
                    AutoClickerService.instance.showOverlay();
                } else {
                    try {
                        Intent intent = new Intent(this, AutoClickerService.class);
                        intent.setAction(AutoClickerService.ACTION_SHOW_OVERLAY);
                        startService(intent);
                    } catch (Exception ignored) {}
                }
                updateServiceStatus();
                moveTaskToBack(true);
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
    }

    private void updateServiceStatus() {
        boolean running = isServiceRunning();
        boolean showing = AutoClickerService.isOverlayShowing();
        if (running) {
            permStatus.setText(R.string.perm_enabled);
            permStatus.setTextColor(getColor(R.color.accentOn));
            permBadge.setTextColor(getColor(R.color.accentOn));
            startButton.setText(showing ? R.string.btn_hide_controls : R.string.btn_show_controls);
        } else {
            permStatus.setText(R.string.perm_required);
            permStatus.setTextColor(getColor(R.color.textDim));
            permBadge.setTextColor(getColor(R.color.accentWarning));
            startButton.setText(R.string.btn_show_controls);
        }
    }

    private void saveConfig() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_INTERVAL, intervalMs)
                .apply();

        if (AutoClickerService.instance != null) {
            AutoClickerService.instance.reloadConfig();
        } else {
            try {
                Intent updateIntent = new Intent(this, AutoClickerService.class);
                updateIntent.setAction(AutoClickerService.ACTION_UPDATE_CONFIG);
                startService(updateIntent);
            } catch (Exception ignored) {}
        }
    }

    private void updateSpeedDisplay() {
        if (intervalMs < 1000) {
            intervalLabel.setText(String.format(Locale.US, "%d ms", intervalMs));
        } else {
            intervalLabel.setText(String.format(Locale.US, "%.2f s", intervalMs / 1000.0));
        }

        double cps = 1000.0 / intervalMs;
        if (cps >= 10.0 || Math.abs(cps - Math.round(cps)) < 0.05) {
            cpsLabel.setText(String.format(Locale.US, "(%d clicks / sec)", Math.round(cps)));
        } else {
            cpsLabel.setText(String.format(Locale.US, "(%.1f clicks / sec)", cps));
        }
    }

    private void highlightMatchingPreset() {
        for (int i = 0; i < presetChips.length; i++) {
            boolean matches = (presetValues[i] == intervalMs);
            presetChips[i].setBackgroundResource(matches ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            presetChips[i].setTextColor(getColor(matches ? R.color.accent : R.color.text));
        }
    }

    private int progressToInterval(int progress) {
        if (progress <= 20) {
            return 40 + (progress * 3); // 40ms to 100ms
        } else if (progress <= 60) {
            return 100 + ((progress - 20) * 10); // 100ms to 500ms
        } else {
            return 500 + ((progress - 60) * 38); // 500ms to ~2020ms
        }
    }

    private int intervalToProgress(int ms) {
        if (ms <= 100) {
            return Math.max(0, (ms - 40) / 3);
        } else if (ms <= 500) {
            return 20 + ((ms - 100) / 10);
        } else {
            return Math.min(100, 60 + ((ms - 500) / 38));
        }
    }

    private boolean isServiceRunning() {
        if (AutoClickerService.instance != null) {
            return true;
        }
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabledServices) {
            String id = info.getId();
            if (id != null && id.contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void showRestrictedGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_restricted_title)
                .setMessage(R.string.dialog_restricted_msg)
                .setPositiveButton(R.string.btn_open_app_info, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.btn_open_accessibility, (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .setNeutralButton(R.string.btn_close, null)
                .show();
    }
}
