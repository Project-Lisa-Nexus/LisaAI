package com.lisa.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.SharedPreferences;

public class LisaPadSettingsActivity extends LisaBaseActivity {

    private int dp(int valore) {
        return Math.round(valore * getResources().getDisplayMetrics().density);
    }

    private SharedPreferences prefs;
    private RadioGroup radioGroupModello;
    private boolean popupAperto = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(32), dp(20), dp(32));

        TextView titolo = new TextView(this);
        titolo.setText("Impostazioni Lisa Pad");
        titolo.setTextSize(26);
        titolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(titolo);

        TextView descrizione = new TextView(this);
        descrizione.setText("Configura le impostazioni del pad.");
        descrizione.setTextSize(16);
        
        LinearLayout.LayoutParams descrizioneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descrizioneLp.bottomMargin = dp(20);
        layout.addView(descrizione, descrizioneLp);

        TextView titoloModello = new TextView(this);
        titoloModello.setText("Modello");
        titoloModello.setTextSize(20);
        titoloModello.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titoloModelloLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titoloModelloLp.bottomMargin = dp(10);
        layout.addView(titoloModello, titoloModelloLp);

        prefs = getSharedPreferences("lisa_ui", MODE_PRIVATE);

        radioGroupModello = new RadioGroup(this);
        radioGroupModello.setOrientation(RadioGroup.VERTICAL);
        
        RadioButton rbFissa = new RadioButton(this);
        rbFissa.setText("Dock Fissa");
        rbFissa.setId(1001);
        rbFissa.setPadding(0, dp(12), 0, dp(12));
        
        RadioButton rbFluttuante = new RadioButton(this);
        rbFluttuante.setText("Dock Fluttuante");
        rbFluttuante.setId(1002);
        rbFluttuante.setPadding(0, dp(12), 0, dp(12));
        
        RadioButton rbRaggruppata = new RadioButton(this);
        rbRaggruppata.setText("Dock Raggruppata");
        rbRaggruppata.setId(1003);
        rbRaggruppata.setPadding(0, dp(12), 0, dp(12));

        radioGroupModello.addView(rbFissa);
        radioGroupModello.addView(rbFluttuante);
        radioGroupModello.addView(rbRaggruppata);
        layout.addView(radioGroupModello);

        // --- PULSANTE AZIONI E BOTTONI (stile EVA, senza banda grigia) ---
        android.widget.LinearLayout rowAzioni = new android.widget.LinearLayout(this);
        rowAzioni.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rowAzioni.setPadding(0, dp(12), 0, dp(12));
        rowAzioni.setClickable(true);
        rowAzioni.setFocusable(true);
        rowAzioni.setBackgroundColor(android.graphics.Color.TRANSPARENT); // Nessuno sfondo

        android.widget.TextView tvAzioni = new android.widget.TextView(this);
        tvAzioni.setText("Gesti e Azioni");
        tvAzioni.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        tvAzioni.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams tvLp = new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvAzioni.setLayoutParams(tvLp);
        rowAzioni.addView(tvAzioni);

        android.widget.TextView tvFreccia = new android.widget.TextView(this);
        tvFreccia.setText(">");
        tvFreccia.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        rowAzioni.addView(tvFreccia);

        rowAzioni.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                startActivity(new android.content.Intent(LisaPadSettingsActivity.this, LisaPadAzioniActivity.class));
            }
        });
        layout.addView(rowAzioni);

        // --- SEZIONE PERSONALIZZAZIONE ---
        android.widget.TextView tvPers = new android.widget.TextView(this);
        tvPers.setText("Personalizzazione");
        tvPers.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        tvPers.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvPers.setPadding(0, dp(12), 0, dp(12));
        layout.addView(tvPers);

        String[] opzioni = {"Dimensione", "Arrotondamento", "Distanza tra pulsanti", "Colore"};
        for (String op : opzioni) {
            android.widget.TextView tvOp = new android.widget.TextView(this);
            tvOp.setText(op);
            tvOp.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            tvOp.setTextColor(0xFF202020);
            
            // REGOLA: click solo sulla scritta + 1cm (38dp) a destra. Niente click sulla riga intera.
            tvOp.setPadding(0, dp(12), dp(38), dp(12));
            tvOp.setClickable(true);
            tvOp.setFocusable(true);

            final String nome = op;
            tvOp.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) {
                    android.widget.Toast.makeText(LisaPadSettingsActivity.this, nome + " premuto", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            layout.addView(tvOp);
        }
        // --- FINE PERSONALIZZAZIONE ---
        // --- FINE PULSANTE AZIONI ---


        syncRadioGroupWithPrefs();

        rbFissa.setOnClickListener(v -> {
            radioGroupModello.check(1001);
            prefs.edit().putString("pad_modello", "fissa").apply();
            if (!popupAperto) mostraPopup("Posizione", "pad_posizione", "sinistra", 
                new String[]{"Destra", "Sinistra", "Alto", "Basso"}, 
                new String[]{"destra", "sinistra", "alto", "basso"}, 2001);
        });

        rbFluttuante.setOnClickListener(v -> {
            radioGroupModello.check(1002);
            prefs.edit().putString("pad_modello", "fluttuante").apply();
            if (!popupAperto) mostraPopup("Orientamento", "pad_orientamento", "orizzontale", 
                new String[]{"Orizzontale", "Verticale"}, 
                new String[]{"orizzontale", "verticale"}, 3001);
        });

        rbRaggruppata.setOnClickListener(v -> {
            radioGroupModello.check(1003);
            prefs.edit().putString("pad_modello", "raggruppata").apply();
        });

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void syncRadioGroupWithPrefs() {
        String modelloAttuale = prefs.getString("pad_modello", "fissa");
        if ("fluttuante".equals(modelloAttuale)) radioGroupModello.check(1002);
        else if ("raggruppata".equals(modelloAttuale)) radioGroupModello.check(1003);
        else radioGroupModello.check(1001);
    }

    private void mostraPopup(String titolo, String chiavePref, String defaultVal, String[] testi, String[] valori, int baseId) {
        popupAperto = true;
        String attuale = prefs.getString(chiavePref, defaultVal);
        
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        
        // Padding ridotto per farla stare più compatta
        int pad = dp(24); 
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF2F2F2);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        TextView tvTitolo = new TextView(this);
        tvTitolo.setText(titolo);
        // Testo grande come richiesto
        tvTitolo.setTextSize(20); 
        tvTitolo.setTextColor(0xFF202020);
        tvTitolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvTitolo.setPadding(0, 0, 0, dp(16));
        root.addView(tvTitolo);

        RadioGroup rg = new RadioGroup(this);
        
        for (int i = 0; i < testi.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(testi[i]);
            rb.setId(baseId + i);
            rb.setTextColor(0xFF202020);
            // Testo grande come richiesto
            rb.setTextSize(16); 
            if (valori[i].equals(attuale)) rb.setChecked(true);
            
            final int index = i;
            rb.setOnClickListener(v -> {
                prefs.edit().putString(chiavePref, valori[index]).apply();
                dialog.dismiss();
            });
            
            rg.addView(rb);
        }

        root.addView(rg);
        dialog.setContentView(root);
        
        dialog.setOnDismissListener(d -> popupAperto = false);

        dialog.show();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0f);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // WRAP_CONTENT: la vignetta si stringe intorno al testo, niente muro nero
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

}