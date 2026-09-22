package com.lisa.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;

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

        aggiungiSezioneBolle(layout);
        aggiungiSezioneMotoreAsr(layout);

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

    private void aggiungiSezioneMotoreAsr(LinearLayout layout) {
        TextView titolo = new TextView(this);
        titolo.setText("Motore ASR");
        titolo.setTextSize(18);
        titolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams titoloLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titoloLp.topMargin = dp(8);
        titoloLp.bottomMargin = dp(4);
        layout.addView(titolo, titoloLp);

        TextView descrizione = new TextView(this);
        descrizione.setText(
                "Scegli il motore di riconoscimento vocale di Lisa"
        );
        descrizione.setTextSize(14);
        layout.addView(descrizione);

        android.content.SharedPreferences prefs =
                getSharedPreferences("lisa_ui", MODE_PRIVATE);

        android.widget.RadioGroup gruppo =
                new android.widget.RadioGroup(this);
        gruppo.setOrientation(android.widget.RadioGroup.VERTICAL);

        android.widget.RadioButton google =
                new android.widget.RadioButton(this);
        google.setId(android.view.View.generateViewId());
        google.setText("Google / Android on-device (consigliato)");

        android.widget.RadioButton whisper =
                new android.widget.RadioButton(this);
        whisper.setId(android.view.View.generateViewId());
        whisper.setText("Whisper Small locale");

        gruppo.addView(google);
        gruppo.addView(whisper);

        String motore =
                prefs.getString("motore_asr", "google");

        if ("whisper".equals(motore)) {
            whisper.setChecked(true);
        } else {
            google.setChecked(true);
        }

        gruppo.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    String valore =
                            checkedId == whisper.getId()
                                    ? "whisper"
                                    : "google";

                    prefs.edit()
                            .putString("motore_asr", valore)
                            .apply();
                }
        );

        layout.addView(gruppo);
    }

    private void aggiungiSezioneBolle(LinearLayout layout) {
        TextView titolo = new TextView(this);
        titolo.setText("Bolle/Vignette");
        titolo.setTextSize(18);
        titolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams titoloLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titoloLp.topMargin = dp(8);
        titoloLp.bottomMargin = dp(8);
        layout.addView(titolo, titoloLp);

        android.content.SharedPreferences prefs =
                getSharedPreferences("lisa_ui", MODE_PRIVATE);

        Switch vignettaSemplice = new Switch(this);
        vignettaSemplice.setText("Vignetta semplice");
        vignettaSemplice.setChecked(
                prefs.getBoolean("vignetta_semplice_attiva", true)
        );
        vignettaSemplice.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        prefs.edit()
                                .putBoolean("vignetta_semplice_attiva", isChecked)
                                .apply()
        );
        layout.addView(vignettaSemplice);

        Switch vignettaDiagnosi = new Switch(this);
        vignettaDiagnosi.setText("Vignetta diagnosi");
        vignettaDiagnosi.setChecked(
                prefs.getBoolean("telemetria_diagnosi_attiva", false)
        );
        vignettaDiagnosi.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    prefs.edit()
                            .putBoolean(
                                    "telemetria_diagnosi_attiva",
                                    isChecked
                            )
                            .apply();

                    if (!isChecked) {
                        LisaAccessibilityService.nascondiTelemetria();
                    }
                }
        );
        layout.addView(vignettaDiagnosi);
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
