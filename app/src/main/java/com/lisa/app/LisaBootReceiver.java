package com.lisa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class LisaBootReceiver extends BroadcastReceiver {
    private static final String TAG = "LisaBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Telefono riavviato - avvio LisaVoiceService");
            
            Intent serviceIntent = new Intent(context, LisaVoiceService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
