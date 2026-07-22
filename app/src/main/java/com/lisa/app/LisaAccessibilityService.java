package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class LisaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LisaAccessibility";
    private static LisaAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Servizio accessibilita connesso");
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Servizio interrotto");
    }

    public void apriApp(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Aperta app: " + packageName);
        } else {
            Log.e(TAG, "App non trovata: " + packageName);
        }
    }

    public boolean cliccaTesto(String testo) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo nodo = AccessibilityUtils.trovaPerTesto(root, testo);
        return AccessibilityUtils.click(nodo);
    }

    public static void apriAppStatic(Context context, String packageName) {
        if (instance != null) {
            instance.apriApp(packageName);
        } else {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }

    public static void chiamaStatic(Context context, String numero) {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(android.net.Uri.parse("tel:" + numero));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void scriviStatic(Context context, String destinatario, String messaggio) {
        Log.d("LisaAccessibility", "Scrivi a " + destinatario + ": " + messaggio);
    }
}
