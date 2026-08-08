package com.lisa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class BridgeReceiver extends BroadcastReceiver {

    private static final String TAG = "LisaBridge";

    @Override
    public void onReceive(Context context, Intent intent) {
        String azione = intent.getStringExtra("azione");
        String pacchetto = intent.getStringExtra("pacchetto");

        Log.i(TAG, "Ricevuto: azione=" + azione + ", pacchetto=" + pacchetto);

        if ("apri_app".equals(azione) && pacchetto != null && !pacchetto.isEmpty()) {
            LisaAccessibilityService.apriAppStatic(context, pacchetto);

            Toast.makeText(
                context,
                "Lisa apre: " + pacchetto,
                Toast.LENGTH_SHORT
            ).show();
        }
    }
}
