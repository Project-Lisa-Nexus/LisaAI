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
        titolo.setTextColor(0xFF000000);
        titolo.setTextColor(0xFF000000);
        titolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(titolo);

        TextView descrizione = new TextView(this);
        descrizione.setText("Configura le impostazioni del pad.");
        descrizione.setTextSize(16);
        descrizione.setTextColor(0xFF000000);
        descrizione.setTextColor(0xFF000000);
        
        LinearLayout.LayoutParams descrizioneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descrizioneLp.bottomMargin = dp(20);
        layout.addView(descrizione, descrizioneLp);

        TextView titoloModello = new TextView(this);
        titoloModello.setText("Modello");
        titoloModello.setTextSize(20);
        titoloModello.setTextColor(0xFF000000);
        titoloModello.setTextColor(0xFF000000);
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
        rowAzioni.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        android.view.View.OnClickListener apriAzioni = new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                startActivity(new android.content.Intent(LisaPadSettingsActivity.this, LisaPadAzioniActivity.class));
            }
        };

        android.widget.TextView tvAzioni = new android.widget.TextView(this);
        tvAzioni.setText("Gesti e Azioni");
        tvAzioni.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        tvAzioni.setTextColor(0xFF000000);
        tvAzioni.setTextColor(0xFF000000);
        tvAzioni.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvAzioni.setPadding(dp(4), dp(8), dp(38), dp(8));
        tvAzioni.setClickable(true);
        tvAzioni.setFocusable(true);
        tvAzioni.setOnClickListener(apriAzioni);
        android.widget.LinearLayout.LayoutParams tvLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tvAzioni.setLayoutParams(tvLp);
        rowAzioni.addView(tvAzioni);

        // Spacer: spinge la freccia a destra senza essere cliccabile
        android.view.View spacerAzioni = new android.view.View(this);
        spacerAzioni.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, 1, 1f));
        spacerAzioni.setClickable(false);
        spacerAzioni.setFocusable(false);
        rowAzioni.addView(spacerAzioni);

        android.widget.TextView tvFreccia = new android.widget.TextView(this);
        tvFreccia.setText("›");
        tvFreccia.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24);
        tvFreccia.setTextColor(0xFF000000);
        tvFreccia.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvFreccia.setPadding(dp(16), dp(20), dp(32), dp(20));
        tvFreccia.setClickable(true);
        tvFreccia.setFocusable(true);
        tvFreccia.setOnClickListener(apriAzioni);
        rowAzioni.addView(tvFreccia);

        layout.addView(rowAzioni);

        // --- SEZIONE PERSONALIZZAZIONE ---
        android.widget.TextView tvPers = new android.widget.TextView(this);
        tvPers.setText("Personalizzazione");
        tvPers.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        tvPers.setTextColor(0xFF000000);
        tvPers.setTextColor(0xFF000000);
        tvPers.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvPers.setPadding(0, dp(12), 0, dp(12));
        layout.addView(tvPers);

        String[] opzioni = {"Dimensione", "Trasparenza", "Arrotondamento", "Distanza tra pulsanti", "Colore"};
        for (String op : opzioni) {
            android.widget.TextView tvOp = new android.widget.TextView(this);
            tvOp.setText(op);
            tvOp.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            tvOp.setTextColor(0xFF000000);
            tvOp.setTextColor(0xFF000000);
            
            // REGOLA: click solo sulla scritta + 1cm (38dp) a destra. Niente click sulla riga intera.
            tvOp.setPadding(0, dp(12), dp(38), dp(12));
            tvOp.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            tvOp.setClickable(true);
            tvOp.setFocusable(true);

            tvOp.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) {
                    mostraPopupPersonalizzazione(op);
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
        
        root.setMinimumWidth(dp(280));

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
        tvTitolo.setTextColor(0xFF000000);
        tvTitolo.setTextColor(0xFF000000); 
        tvTitolo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvTitolo.setPadding(0, 0, 0, dp(16));
        root.addView(tvTitolo);

        RadioGroup rg = new RadioGroup(this);
        
        for (int i = 0; i < testi.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(testi[i]);
            rb.setId(baseId + i);
            // Testo grande come richiesto
            rb.setTextSize(16); 
            rb.setTextColor(0xFF000000);
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

    private void mostraPopupPersonalizzazione(final String titoloVoce) {
        if (popupAperto) return;
        popupAperto = true;

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumWidth(dp(280));
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF2F2F2);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        android.widget.TextView tvTitolo = new android.widget.TextView(this);
        tvTitolo.setText(titoloVoce);
        tvTitolo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        tvTitolo.setTextColor(0xFF000000);
        tvTitolo.setTextColor(0xFF000000);
        tvTitolo.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);

        if (!"Colore".equals(titoloVoce)) {
            // Riga Titolo + Valore
            android.widget.LinearLayout rowTitolo = new android.widget.LinearLayout(this);
            rowTitolo.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            rowTitolo.setGravity(android.view.Gravity.CENTER_VERTICAL);

            android.widget.LinearLayout.LayoutParams lpTitle = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rowTitolo.addView(tvTitolo, lpTitle);

            android.widget.TextView tvValore = new android.widget.TextView(this);
            tvValore.setText("100"); // Default
            tvValore.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
            tvValore.setTextColor(0xFF000000);
            tvValore.setTextColor(0xFF000000); // Nero
            rowTitolo.addView(tvValore);

            root.addView(rowTitolo);

            // --- INIZIO MIXER ---
            final android.widget.SeekBar seekBar = new android.widget.SeekBar(this);
            seekBar.setMax(100);
            seekBar.setProgress(50);

            int bluAccent;
            android.util.TypedValue tvAccent = new android.util.TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.colorControlActivated, tvAccent, true)) {
                bluAccent = tvAccent.data;
            } else {
                bluAccent = 0xFF2196F3;
            }

            android.graphics.drawable.GradientDrawable bgTrack = new android.graphics.drawable.GradientDrawable();
            bgTrack.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bgTrack.setColor(0xFFD0D0D0);
            bgTrack.setCornerRadius(dp(6));
            bgTrack.setSize(0, dp(12));

            android.graphics.drawable.GradientDrawable progressRect = new android.graphics.drawable.GradientDrawable();
            progressRect.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            progressRect.setColor(bluAccent);
            progressRect.setCornerRadius(dp(6));
            progressRect.setSize(0, dp(12));

            android.graphics.drawable.ClipDrawable progressClip = new android.graphics.drawable.ClipDrawable(
                    progressRect, android.view.Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);

            android.graphics.drawable.LayerDrawable layered = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{bgTrack, progressClip});
            layered.setId(0, android.R.id.background);
            layered.setId(1, android.R.id.progress);
            layered.setLayerInset(0, 0, 0, 0, 0);
            layered.setLayerInset(1, 0, 0, 0, 0);
            seekBar.setProgressDrawable(layered);

            android.graphics.drawable.GradientDrawable thumb = new android.graphics.drawable.GradientDrawable();
            thumb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            thumb.setColor(bluAccent);
            thumb.setSize(dp(20), dp(20));
            seekBar.setThumb(thumb);
            seekBar.setSplitTrack(false);
            seekBar.setPadding(dp(10), 0, dp(10), 0);

            final android.widget.FrameLayout frameSeek = new android.widget.FrameLayout(this);
            android.widget.LinearLayout.LayoutParams lpFrame = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(60));
            lpFrame.leftMargin = dp(16);
            lpFrame.rightMargin = dp(16);
            lpFrame.topMargin = dp(16);
            lpFrame.bottomMargin = dp(24);
            frameSeek.setLayoutParams(lpFrame);

            // 1. SeekBar (sotto le tacche)
            android.widget.FrameLayout.LayoutParams seekLp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            seekLp.gravity = android.view.Gravity.CENTER_VERTICAL;
            frameSeek.addView(seekBar, seekLp);

            root.addView(frameSeek);

            seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                    int valore;
                    if (progress <= 50) {
                        valore = 40 + Math.round(progress * 60f / 50f);
                    } else {
                        valore = 100 + Math.round((progress - 50) * 100f / 50f);
                    }
                    tvValore.setText(String.valueOf(valore));
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
            });
            // --- FINE MIXER ---

        } else {
            tvTitolo.setPadding(0, 0, 0, dp(16));
            root.addView(tvTitolo);
        }

        // Recupera il blu esatto dei RadioButton (colorControlActivated)
        int bluAccent;
        android.util.TypedValue tvAccent = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.colorControlActivated, tvAccent, true)) {
            bluAccent = tvAccent.data;
        } else {
            bluAccent = 0xFF2196F3; // Fallback estremo
        }

        android.widget.TextView tvFatto = new android.widget.TextView(this);
        tvFatto.setText("Fatto");
        tvFatto.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        tvFatto.setTextColor(0xFF000000);
        tvFatto.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvFatto.setTextColor(bluAccent);
        tvFatto.setPadding(dp(16), dp(12), dp(16), dp(12));
        
        // Cliccabile solo sulla scritta (WRAP_CONTENT), allineata a destra con wrapper
        android.widget.LinearLayout.LayoutParams lpFatto = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tvFatto.setLayoutParams(lpFatto);
        
        tvFatto.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) { dialog.dismiss(); }
        });
        
        // Aggiungo un minimo di spazio verticale sopra il tasto per non farlo attaccare al titolo se il popup è piccolo
        android.widget.LinearLayout.LayoutParams lpSpazio = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24));
        android.view.View spazio = new android.view.View(this);
        spazio.setLayoutParams(lpSpazio);
        root.addView(spazio);
        
        android.widget.LinearLayout rowFatto = new android.widget.LinearLayout(this);
        rowFatto.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rowFatto.setGravity(android.view.Gravity.END);
        rowFatto.addView(tvFatto);
        root.addView(rowFatto);

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> popupAperto = false);
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0f);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
