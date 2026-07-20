
package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class LisaAccessibilityService extends AccessibilityService {

    private static final String TAG = "LisaAccessibility";
    private static LisaAccessibilityService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Servizio interrotto");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Lisa Accessibility Service connesso e attivo");
    }

    public static LisaAccessibilityService getInstance() {
        return instance;
    }

    public void sbloccaSchermo() {
        Path path = new Path();
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        path.moveTo(width / 2f, height * 0.8f);
        path.lineTo(width / 2f, height * 0.2f);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 300);
        GestureDescription gesture =
                new GestureDescription.Builder().addStroke(stroke).build();

        dispatchGesture(gesture, null, null);
        Log.i(TAG, "Swipe di sblocco eseguito");
    }

    public void apriEva() {
        try {
            Intent intent = getPackageManager()
                    .getLaunchIntentForPackage("com.crea_si.eva_facial_mouse");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.i(TAG, "EVA Facemouse aperto");
            } else {
                Log.w(TAG, "EVA Facemouse non trovato sul dispositivo");
            }
        } catch (Exception e) {
            Log.e(TAG, "Errore apertura EVA: " + e.getMessage());
        }
    }public void apriApp(String packageName) {
    try {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.i(TAG, "App aperta: " + packageName);
        } else {
            Log.w(TAG, "App non trovata: " + packageName);
        }
    } catch (Exception e) {
        Log.e(TAG, "Errore apertura app: " + e.getMessage());
    }
}
}
