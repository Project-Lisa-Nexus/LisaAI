package com.lisa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class LisaCommandReceiver extends BroadcastReceiver {
    private static final String TAG = "LisaCommandReceiver";
    public static final String ACTION_LISA_COMMAND = "com.lisa.app.COMMAND";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_LISA_COMMAND.equals(intent.getAction())) {
            String azione = intent.getStringExtra("azione");
            String pacchetto = intent.getStringExtra("pacchetto");
            String testo = intent.getStringExtra("testo");

            Log.d(TAG, "Comando ricevuto: azione=" + azione + " pacchetto=" + pacchetto);

            if ("apri_app".equals(azione) && pacchetto != null) {
                LisaAccessibilityService.apriAppStatic(context, pacchetto);
            }
            else if ("chiama".equals(azione) && testo != null) {
                LisaAccessibilityService.chiamaStatic(context, testo);
            }
            else if ("scrivi".equals(azione)) {
                String destinatario = intent.getStringExtra("destinatario");
                String messaggio = intent.getStringExtra("messaggio");
                LisaAccessibilityService.scriviStatic(context, destinatario, messaggio);
            }
        }
    }
}
