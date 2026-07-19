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
        title.setText("Lisa - Passo 1\nApp installata correttamente.");
        title.setTextSize(20);
        layout.addView(title);

        Button btn = new Button(this);
        btn.setText("Apri Impostazioni Accessibilità");
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btn);

        setContentView(layout);
    }
}
