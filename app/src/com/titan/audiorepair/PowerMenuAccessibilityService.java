package com.titan.audiorepair;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.os.Handler;
import android.os.Looper;

public class PowerMenuAccessibilityService extends AccessibilityService {
    private static volatile PowerMenuAccessibilityService instance;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally ignored. This service does not inspect UI content.
    }

    @Override public void onInterrupt() {
        // Nothing to interrupt.
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isConnected() {
        return instance != null;
    }

    /** Refresh Android's notification and quick-settings panels; NOT a SystemUI restart. */
    public static boolean softRefreshUi() {
        PowerMenuAccessibilityService s = instance;
        if (s == null || !s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) return false;
        s.uiHandler.postDelayed(() -> s.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE), 650);
        s.uiHandler.postDelayed(() -> s.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS), 1050);
        s.uiHandler.postDelayed(() -> s.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE), 1800);
        return true;
    }

    public static boolean openPowerMenu() {
        PowerMenuAccessibilityService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }
}
