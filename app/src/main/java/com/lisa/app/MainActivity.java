package com.lisa.app;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 100, 40, 40);

        TextView title = new TextView(this);
        title.setText("Lisa - Passo 2\nSblocca e apri EVA.");
        title.setTextSize(20);
        layout.addView(title);

        Button btnAccess = new Button(this);
        btnAccess.setText("Apri Impostazioni Accessibilità");
        btnAccess.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btnAccess);

        Button btnUnlock = new Button(this);
        btnUnlock.setText("Sblocca e apri EVA");
        btnUnlock.setOnClickListener(v -> {
            LisaAccessibilityService service = LisaAccessibilityService.getInstance();
            if (service != null) {
                service.sbloccaSchermo();
                service.apriEva();
            }
        });
        layout.addView(btnUnlock);

        setContentView(layout);
    }
}
