package com.lisa.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LisaSettingsActivity extends Activity {

    private int dp(int valore) {
        return Math.round(
                valore * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                dp(20),
                dp(32),
                dp(20),
                dp(32)
        );

        TextView titolo = new TextView(this);
        titolo.setText("Impostazioni Lisa");
        titolo.setTextSize(26);
        titolo.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        layout.addView(titolo);

        TextView descrizione = new TextView(this);
        descrizione.setText(
                "Configura Lisa Assist e controlla lo stato dei suoi servizi."
        );
        descrizione.setTextSize(15);

        LinearLayout.LayoutParams descrizioneLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        descrizioneLp.bottomMargin = dp(20);
        layout.addView(descrizione, descrizioneLp);

        aggiungiSezione(
                layout,
                "♿ Accessibilità",
                "Servizio Lisa, autorizzazioni e stato del sistema"
        );

        aggiungiSezione(
                layout,
                "● Bolla Lisa",
                "Dimensione, posizione, trasparenza e comportamento"
        );

        aggiungiSezione(
                layout,
                "🎤 Voce",
                "Whisper, microfono e sintesi vocale"
        );

        aggiungiSezione(
                layout,
                "📱 Controlli Android",
                "Home, Indietro, notifiche, volume e luminosità"
        );

        aggiungiSezione(
                layout,
                "💬 Comunicazioni",
                "WhatsApp e funzioni di messaggistica"
        );

        aggiungiSezione(
                layout,
                "🎨 Aspetto",
                "Interfaccia, dimensioni e tema"
        );

        aggiungiSezione(
                layout,
                "🛡 Affidabilità",
                "Batteria, esecuzione in background e autorizzazioni"
        );

        aggiungiSezione(
                layout,
                "🛠 Avanzate",
                "LisaOS, diagnostica, test e strumenti sviluppatore"
        );

        Button tornaDashboard = new Button(this);
        tornaDashboard.setText("← Torna alla Dashboard");

        LinearLayout.LayoutParams backLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        backLp.topMargin = dp(24);

        tornaDashboard.setOnClickListener(v -> finish());
        layout.addView(tornaDashboard, backLp);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void aggiungiSezione(
            LinearLayout layout,
            String titolo,
            String descrizione) {

        LinearLayout contenitore = new LinearLayout(this);
        contenitore.setOrientation(LinearLayout.VERTICAL);
        contenitore.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );

        TextView nome = new TextView(this);
        nome.setText(titolo);
        nome.setTextSize(18);
        nome.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        contenitore.addView(nome);

        TextView dettaglio = new TextView(this);
        dettaglio.setText(descrizione);
        dettaglio.setTextSize(14);
        contenitore.addView(dettaglio);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        lp.bottomMargin = dp(8);

        layout.addView(contenitore, lp);
    }
}
