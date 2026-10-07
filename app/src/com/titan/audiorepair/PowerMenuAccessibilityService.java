package com.titan.audiorepair;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class PowerMenuAccessibilityService extends AccessibilityService {
    private static volatile PowerMenuAccessibilityService instance;

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

    public static boolean openPowerMenu() {
        PowerMenuAccessibilityService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }
}
