package com.lisa.app;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

public class MainActivity extends Activity {
    
    private static final int PERM_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Richiedi permessi
        richiediPermessi();

        // Avvia servizio voce
        Intent voiceIntent = new Intent(this, LisaVoiceService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(voiceIntent);
        } else {
            startService(voiceIntent);
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 100, 40, 40);

        TextView title = new TextView(this);
        title.setText("Lisa - Passo 4\nVoce sempre attiva");
        title.setTextSize(20);
        layout.addView(title);

        Button btnAccess = new Button(this);
        btnAccess.setText("Apri Impostazioni Accessibilita");
        btnAccess.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btnAccess);

        Button btnTestYoutube = new Button(this);
        btnTestYoutube.setText("TEST WHATSAPP 123");
        btnTestYoutube.setOnClickListener(v -> {
            LisaAccessibilityService service = LisaAccessibilityService.getInstance();
            if (service != null) {
                service.apriAppPerNome("whatsapp");
            }
        });
        layout.addView(btnTestYoutube);

        setContentView(layout);
    }

    private void richiediPermessi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permessi = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            for (String permesso : permessi) {
                if (checkSelfPermission(permesso) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(permessi, PERM_REQUEST);
                    break;
                }
            }
        }
    }
}
