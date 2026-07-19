package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class LisaAccessibilityService extends AccessibilityService {

    private static final String TAG = "LisaAccessibility";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Per ora non facciamo nulla con gli eventi, solo verifichiamo che il servizio sia attivo
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Servizio interrotto");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Lisa Accessibility Service connesso e attivo");
    }
}
