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
import android.widget.RadioGroup;
import android.widget.RadioButton;

public class LisaSettingsActivity extends LisaBaseActivity {

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
        titolo.setTextColor(0xFF000000);
        titolo.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        layout.addView(titolo);

        TextView descrizione = new TextView(this);
        descrizione.setText(
                "Configura Lisa Assist e controlla lo stato dei suoi servizi."
        );
        descrizione.setTextSize(16);
        descrizione.setTextColor(0xFF000000);

        LinearLayout.LayoutParams descrizioneLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        descrizioneLp.bottomMargin = dp(12);
        layout.addView(descrizione, descrizioneLp);

        // Rimosso per evitare il doppione (spostato in MainActivity)

        aggiungiSezione(
                layout,
                "● Bolla Lisa",
                "Dimensione, posizione, trasparenza e comportamento"
        );

        aggiungiSezioneBolle(layout);
        aggiungiSezioneMotoreAsr(layout);
        aggiungiSezioneMotoreLisa(layout);

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

        aggiungiSezioneAffidabilita(layout);

        aggiungiSezione(
                layout,
                "🛠 Avanzate",
                "LisaOS, diagnostica, test e strumenti sviluppatore"
        );


        LinearLayout padRow = new LinearLayout(this);
        padRow.setOrientation(LinearLayout.HORIZONTAL);
        padRow.setPadding(dp(0), dp(12), dp(0), dp(12));

        LinearLayout testoPadContainer = new LinearLayout(this);
        testoPadContainer.setOrientation(LinearLayout.VERTICAL);
        testoPadContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvPad = new TextView(this);
        tvPad.setText("🎮 Lisa Pad");
        tvPad.setTextSize(20);
        tvPad.setTextColor(0xFF000000);
        tvPad.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        testoPadContainer.addView(tvPad);

        TextView descPad = new TextView(this);
        descPad.setText("Attiva o disattiva il pad personalizzabile");
        descPad.setTextSize(16);
        descPad.setTextColor(0xFF000000);
        testoPadContainer.addView(descPad);

        padRow.addView(testoPadContainer);
        testoPadContainer.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                startActivity(new android.content.Intent(LisaSettingsActivity.this, LisaPadSettingsActivity.class));
            }
        });
        android.widget.Switch swPad = new android.widget.Switch(this);
        boolean padAttivo = getSharedPreferences("lisa_ui", MODE_PRIVATE).getBoolean("lisa_pad_attivo", false);
        swPad.setChecked(padAttivo);
        swPad.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                getSharedPreferences("lisa_ui", MODE_PRIVATE).edit().putBoolean("lisa_pad_attivo", isChecked).apply();
                if (isChecked) {
                    startActivity(new android.content.Intent(LisaSettingsActivity.this, LisaPadSettingsActivity.class));
                }
            }
        });
        padRow.addView(swPad);
        layout.addView(padRow, 3);


        scrollView.addView(layout);
        
        

        setContentView(scrollView);
    }

    private void aggiungiSezioneMotoreAsr(LinearLayout layout) {
        TextView titolo = new TextView(this);
        titolo.setText("Motore ASR");
        titolo.setTextSize(20);
        titolo.setTextColor(0xFF000000);
        titolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams titoloLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titoloLp.topMargin = 0;
        titoloLp.bottomMargin = dp(4);
        layout.addView(titolo, titoloLp);

        TextView descrizione = new TextView(this);
        descrizione.setText(
                "Scegli il motore di riconoscimento vocale di Lisa"
        );
        descrizione.setTextSize(16);
        descrizione.setTextColor(0xFF000000);
        LinearLayout.LayoutParams lpDescAsr = new LinearLayout.LayoutParams(-1, -2);
        lpDescAsr.bottomMargin = dp(12);
        layout.addView(descrizione, lpDescAsr);

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

        android.widget.RadioGroup.LayoutParams lpGoogle = new android.widget.RadioGroup.LayoutParams(-1, -2);
        lpGoogle.bottomMargin = dp(12);
        gruppo.addView(google, lpGoogle);

        android.widget.RadioGroup.LayoutParams lpWhisper = new android.widget.RadioGroup.LayoutParams(-1, -2);
        lpWhisper.bottomMargin = 0;
        gruppo.addView(whisper, lpWhisper);

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

        LinearLayout.LayoutParams lpGruppo = new LinearLayout.LayoutParams(-1, -2);
        lpGruppo.bottomMargin = dp(12);
        layout.addView(gruppo, lpGruppo);
    }

    private void aggiungiSezioneMotoreLisa(LinearLayout layout) {
        android.content.SharedPreferences prefs =
                getSharedPreferences("lisa_ui", MODE_PRIVATE);
        String cur = prefs.getString("lisa_engine", "v1");

        TextView tv = new TextView(this);
        tv.setText("Motore Lisa");
        tv.setTextSize(20);
        tv.setTextColor(0xFF000000);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 0;
        lp.bottomMargin = dp(12);
        layout.addView(tv, lp);

        RadioGroup rg = new RadioGroup(this);
        int idV1 = android.view.View.generateViewId();
        int idV2 = android.view.View.generateViewId();
        RadioButton r1 = new RadioButton(this);
        r1.setId(idV1);
        r1.setText("Lisa V1 - Stabile (attuale)");
        RadioButton r2 = new RadioButton(this);
        r2.setId(idV2);
        r2.setText("Lisa V2 - Sperimentale (gesti vocali griglia in verifica)");
        android.widget.RadioGroup.LayoutParams lpR1 = new android.widget.RadioGroup.LayoutParams(-1, -2);
        lpR1.bottomMargin = dp(12);
        rg.addView(r1, lpR1);

        android.widget.RadioGroup.LayoutParams lpR2 = new android.widget.RadioGroup.LayoutParams(-1, -2);
        lpR2.bottomMargin = 0;
        rg.addView(r2, lpR2);
        if ("v2".equals(cur)) rg.check(idV2); else rg.check(idV1);

        rg.setOnCheckedChangeListener((g, id) -> {
            if (id == idV1) {
                prefs.edit().putString("lisa_engine", "v1").apply();
            } else if (id == idV2) {
                prefs.edit().putString("lisa_engine", "v2").apply();
            }
        });

        LinearLayout.LayoutParams lpRg = new LinearLayout.LayoutParams(-1, -2);
        lpRg.bottomMargin = dp(12);
        layout.addView(rg, lpRg);
    }

    private void aggiungiSezioneBolle(LinearLayout layout) {
        TextView titolo = new TextView(this);
        titolo.setText("Bolle/Vignette");
        titolo.setTextSize(20);
        titolo.setTextColor(0xFF000000);
        titolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams titoloLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titoloLp.topMargin = 0;
        titoloLp.bottomMargin = dp(12);
        layout.addView(titolo, titoloLp);

        android.content.SharedPreferences prefs =
                getSharedPreferences("lisa_ui", MODE_PRIVATE);

        Switch pallinoFlottante = new Switch(this);
        pallinoFlottante.setText("Pallino flottante");
        pallinoFlottante.setChecked(
                prefs.getBoolean("pallino_attivo", true)
        );
        pallinoFlottante.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    prefs.edit()
                            .putBoolean("pallino_attivo", isChecked)
                            .apply();

                    LisaAccessibilityService
                            .aggiornaVisibilitaPallino();
                }
        );
        LinearLayout.LayoutParams lpPallino = new LinearLayout.LayoutParams(-1, -2);
        lpPallino.bottomMargin = dp(12);
        layout.addView(pallinoFlottante, lpPallino);

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
        LinearLayout.LayoutParams lpVigS = new LinearLayout.LayoutParams(-1, -2);
        lpVigS.bottomMargin = dp(12);
        layout.addView(vignettaSemplice, lpVigS);

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
        LinearLayout.LayoutParams lpVigD = new LinearLayout.LayoutParams(-1, -2);
        lpVigD.bottomMargin = dp(12);
        layout.addView(vignettaDiagnosi, lpVigD);
    }

    private void aggiungiSezioneAffidabilita(
            LinearLayout layout) {

        TextView titolo = new TextView(this);
        titolo.setText("🛡 Affidabilità");
        titolo.setTextSize(20);
        titolo.setTextColor(0xFF000000);
        titolo.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams titoloLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titoloLp.topMargin = 0;
        titoloLp.bottomMargin = dp(4);

        layout.addView(
                titolo,
                titoloLp
        );

        TextView descrizione = new TextView(this);
        descrizione.setText(
                "Temperatura, batteria e sicurezza del telefono"
        );
        descrizione.setTextSize(16);
        descrizione.setTextColor(0xFF000000);
        LinearLayout.LayoutParams lpDescAff = new LinearLayout.LayoutParams(-1, -2);
        lpDescAff.bottomMargin = dp(12);
        layout.addView(descrizione, lpDescAff);

        android.content.SharedPreferences prefs =
                getSharedPreferences(
                        "lisa_ui",
                        MODE_PRIVATE
                );

        Button statoTelefono = new Button(this);
        statoTelefono.setText("📊 Stato telefono");

        statoTelefono.setOnClickListener(v -> {

            ThermalSafetyMonitor monitor =
                    LisaAccessibilityService
                            .getThermalSafetyMonitor();

            String stato;
            String statoVoce;

            if (monitor != null) {

                stato =
                        monitor.getStatoDettagliato();

                statoVoce =
                        monitor.getStatoVocale();

            } else {

                stato =
                        "Monitor temperatura non disponibile";

                statoVoce =
                        "Monitor temperatura non disponibile.";
            }

            LisaAccessibilityService
                    .aggiungiRigaDiagnosi(
                            "🌡️",
                            stato,
                            7000
                    );

            boolean avvisoVocaleAttivo =
                    prefs.getBoolean(
                            "avviso_vocale_attivo",
                            true
                    );

            if (avvisoVocaleAttivo) {

                LisaSpeaker.parla(
                        LisaSettingsActivity.this,
                        statoVoce,
                        null
                );
            }
        });

        LinearLayout.LayoutParams statoLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        statoLp.topMargin = 0;
        statoLp.bottomMargin = dp(12);

        layout.addView(
                statoTelefono,
                statoLp
        );

        Switch monitorTemperatura =
                new Switch(this);

        monitorTemperatura.setText(
                "Monitor temperatura"
        );

        monitorTemperatura.setChecked(
                prefs.getBoolean(
                        "monitor_temperatura_attivo",
                        true
                )
        );

        monitorTemperatura
                .setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {

                            prefs.edit()
                                    .putBoolean(
                                            "monitor_temperatura_attivo",
                                            isChecked
                                    )
                                    .apply();

                            ThermalSafetyMonitor monitor =
                                    LisaAccessibilityService
                                            .getThermalSafetyMonitor();

                            if (monitor != null) {

                                if (isChecked) {
                                    monitor.start();
                                } else {
                                    monitor.stop();
                                }
                            }
                        }
                );

        LinearLayout.LayoutParams lpMonitor = new LinearLayout.LayoutParams(-1, -2);
        lpMonitor.bottomMargin = dp(12);
        layout.addView(monitorTemperatura, lpMonitor);

        Switch avvisoVocale =
                new Switch(this);

        avvisoVocale.setText(
                "Avviso vocale"
        );

        avvisoVocale.setChecked(
                prefs.getBoolean(
                        "avviso_vocale_attivo",
                        true
                )
        );

        avvisoVocale
                .setOnCheckedChangeListener(
                        (buttonView, isChecked) ->
                                prefs.edit()
                                        .putBoolean(
                                                "avviso_vocale_attivo",
                                                isChecked
                                        )
                                        .apply()
                );

        LinearLayout.LayoutParams lpAvviso = new LinearLayout.LayoutParams(-1, -2);
        lpAvviso.bottomMargin = dp(12);
        layout.addView(avvisoVocale, lpAvviso);

        Switch notificaAndroid =
                new Switch(this);

        notificaAndroid.setText(
                "Notifica Android"
        );

        notificaAndroid.setChecked(
                prefs.getBoolean(
                        "notifica_android_attiva",
                        true
                )
        );

        notificaAndroid
                .setOnCheckedChangeListener(
                        (buttonView, isChecked) ->
                                prefs.edit()
                                        .putBoolean(
                                                "notifica_android_attiva",
                                                isChecked
                                        )
                                        .apply()
                );

        LinearLayout.LayoutParams lpNotifica = new LinearLayout.LayoutParams(-1, -2);
        lpNotifica.bottomMargin = dp(12);
        layout.addView(notificaAndroid, lpNotifica);
    }


    private void aggiungiSezione(
            LinearLayout layout,
            String titolo,
            String descrizione) {

        LinearLayout contenitore = new LinearLayout(this);
        contenitore.setOrientation(LinearLayout.VERTICAL);
        contenitore.setPadding(
                0,
                dp(12),
                0,
                dp(12)
        );

        TextView nome = new TextView(this);
        nome.setText(titolo);
        nome.setTextSize(20);
        nome.setTextColor(0xFF000000);
        nome.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        contenitore.addView(nome);

        TextView dettaglio = new TextView(this);
        dettaglio.setText(descrizione);
        dettaglio.setTextSize(16);
        dettaglio.setTextColor(0xFF000000);
        contenitore.addView(dettaglio);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        lp.bottomMargin = dp(12);

        layout.addView(contenitore, lp);
    }
}
