package com.lisa.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class LisaPadAzioniActivity extends LisaBaseActivity {

    private SharedPreferences prefs;
    private List<String> selezionate;
    private java.util.List<android.widget.FrameLayout> squares;
    private java.util.List<android.view.View> dots;
    private java.util.List<android.widget.TextView> previewTexts;
    private java.util.Map<String, String> idToLabel = new java.util.HashMap<>();
    private android.widget.LinearLayout previewBar;
    private int coloreBlu;
    private static final int MAX_AZIONI = 16;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("lisa_ui", MODE_PRIVATE);
        
        // Legge il blu del tema (stesso colore dei RadioButton dei modelli)
        android.util.TypedValue tvAccent = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.colorControlActivated, tvAccent, true)) {
            coloreBlu = tvAccent.data;
        } else {
            coloreBlu = 0xFF2196F3;
        }
        selezionate = new ArrayList<>();
        squares = new ArrayList<>();
        dots = new ArrayList<>();
        previewTexts = new ArrayList<>();

        String salvate = prefs.getString("pad_azioni_selezionate", "");
        if (!salvate.isEmpty()) {
            String[] arr = salvate.split(",");
            for (String s : arr) {
                if (selezionate.size() < MAX_AZIONI) {
                    selezionate.add(s);
                }
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(32), dp(20), dp(32));

        TextView titolo = new TextView(this);
        titolo.setText("Azioni e Bottoni");
        titolo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        titolo.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        layout.addView(titolo);

        TextView descrizione = new TextView(this);
        descrizione.setText("Seleziona fino a 16 comandi per il tuo Pad.");
        descrizione.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        descrizione.setPadding(0, dp(8), 0, dp(8));
        layout.addView(descrizione);

        // Preview bar verticale
        previewBar = new android.widget.LinearLayout(this);
        previewBar.setOrientation(android.widget.LinearLayout.VERTICAL);
        previewBar.setPadding(dp(8), dp(8), dp(8), dp(8));

        for (int i = 0; i < MAX_AZIONI; i++) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(2), 0, dp(2));

            android.widget.FrameLayout sqBox = new android.widget.FrameLayout(this);
            android.widget.LinearLayout.LayoutParams sqLp = new android.widget.LinearLayout.LayoutParams(dp(28), dp(28));
            sqLp.setMargins(0, 0, dp(8), 0);
            sqBox.setLayoutParams(sqLp);
            
            android.graphics.drawable.GradientDrawable boxBg = new android.graphics.drawable.GradientDrawable();
            boxBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            boxBg.setColor(0x00000000);
            boxBg.setStroke(dp(2), 0xFF888888);
            sqBox.setBackground(boxBg);

            android.view.View dot = new android.view.View(this);
            android.widget.FrameLayout.LayoutParams dotLp = new android.widget.FrameLayout.LayoutParams(dp(18), dp(18));
            dotLp.gravity = android.view.Gravity.CENTER;
            dot.setLayoutParams(dotLp);
            dot.setVisibility(android.view.View.INVISIBLE);
            
            sqBox.addView(dot);
            row.addView(sqBox);

            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setTextColor(0xFF202020);
            android.widget.LinearLayout.LayoutParams tvLp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tvLp);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            previewTexts.add(tv);
            row.addView(tv);

            squares.add(sqBox);
            dots.add(dot);
            previewBar.addView(row);
        }

        aggiungiGruppo(layout, "Navigazione");
        aggiungiAzione(layout, "home", "Home");
        aggiungiAzione(layout, "back", "Indietro");
        aggiungiAzione(layout, "recents", "Recenti");
        aggiungiAzione(layout, "notifiche", "Notifiche");
        aggiungiAzione(layout, "quick_settings", "Impostazioni Rapide");
        aggiungiAzione(layout, "lock_screen", "Blocca Schermo");
        aggiungiAzione(layout, "power_menu", "Menu Spegnimento");
        aggiungiAzione(layout, "screenshot", "Screenshot");

        aggiungiGruppo(layout, "Audio");
        aggiungiAzione(layout, "volume_up", "Volume +");
        aggiungiAzione(layout, "volume_down", "Volume -");
        aggiungiAzione(layout, "mute", "Muto ON/OFF");

        aggiungiGruppo(layout, "Schermo");
        aggiungiAzione(layout, "brightness_up", "Luminosità +");
        aggiungiAzione(layout, "brightness_down", "Luminosità -");
        aggiungiAzione(layout, "zoom_in", "Zoom +");
        aggiungiAzione(layout, "zoom_out", "Zoom -");

        aggiungiGruppo(layout, "Scroll");
        aggiungiAzione(layout, "scroll_up", "Scorri Su");
        aggiungiAzione(layout, "scroll_down", "Scorri Giù");
        aggiungiAzione(layout, "scroll_left", "Scorri Sinistra");
        aggiungiAzione(layout, "scroll_right", "Scorri Destra");
        aggiungiAzione(layout, "scroll_up_reg", "Scroll Su Regolabile");
        aggiungiAzione(layout, "scroll_down_reg", "Scroll Giù Regolabile");
        aggiungiAzione(layout, "auto_scroll", "Auto Scroll");

        aggiungiGruppo(layout, "Gesture");
        aggiungiAzione(layout, "double_tap", "Doppio Click");
        aggiungiAzione(layout, "long_press", "Pressione Prolungata");
        aggiungiAzione(layout, "drag", "Trascina");
        aggiungiAzione(layout, "context_menu", "Menu Contestuale");

        aggiungiGruppo(layout, "Gestione Dock");
        aggiungiAzione(layout, "dock_minimize", "Riduci a Icona");
        aggiungiAzione(layout, "dock_expand", "Espandi");
        aggiungiAzione(layout, "dock_move", "Sposta");
        aggiungiAzione(layout, "dock_settings", "Impostazioni Dock");

        aggiungiGruppo(layout, "Overlay Lisa");
        aggiungiAzione(layout, "overlay_grid", "Mostra Griglia");
        aggiungiAzione(layout, "overlay_numbers", "Mostra Numeri");
        aggiungiAzione(layout, "overlay_labels", "Mostra Etichette");

        aggiungiGruppo(layout, "Widget");
        aggiungiAzione(layout, "widget_pad", "Widget Pad/Dock");

        aggiungiGruppo(layout, "Lisa AI");
        aggiungiAzione(layout, "lisa_ai", "Lisa AI");

        scroll.addView(layout);

        android.widget.LinearLayout rootContainer = new android.widget.LinearLayout(this);
        rootContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);

        android.widget.LinearLayout.LayoutParams scrollLp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0.6f);
        scroll.setLayoutParams(scrollLp);
        rootContainer.addView(scroll);

        android.widget.ScrollView previewScroll = new android.widget.ScrollView(this);
        previewScroll.setFillViewport(true);
        previewBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        previewScroll.addView(previewBar);
        android.widget.LinearLayout.LayoutParams previewLp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0.4f);
        previewScroll.setLayoutParams(previewLp);
        rootContainer.addView(previewScroll);

        aggiornaPreview();

        setContentView(rootContainer);
    }

    private void aggiungiGruppo(LinearLayout layout, String nome) {
        TextView tv = new TextView(this);
        tv.setText(nome);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tv.setPadding(0, dp(16), 0, dp(8));
        layout.addView(tv);
    }

    private void aggiungiAzione(LinearLayout layout, final String id, String label) {
        idToLabel.put(id, label);

        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        android.widget.RadioButton rb = new android.widget.RadioButton(this);
        rb.setText(label);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rb.setTextColor(0xFF202020);
        rb.setChecked(selezionate.contains(id));
        rb.setFocusable(false);

        rb.setOnClickListener(v -> {
            boolean eraSelezionato = selezionate.contains(id);
            if (eraSelezionato) {
                selezionate.remove(id);
                rb.setChecked(false);
            } else {
                if (selezionate.size() >= MAX_AZIONI) {
                    rb.setChecked(false);
                    Toast.makeText(this, "Massimo " + MAX_AZIONI + " azioni selezionabili", Toast.LENGTH_SHORT).show();
                    return;
                }
                selezionate.add(id);
                rb.setChecked(true);
            }
            salvaSelezioni();
            aggiornaPreview();
        });

        row.addView(rb);

        layout.addView(row);
    }

    private void salvaSelezioni() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selezionate.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(selezionate.get(i));
        }
        prefs.edit().putString("pad_azioni_selezionate", sb.toString()).apply();
    }

    private void aggiornaPreview() {
        for (int i = 0; i < MAX_AZIONI; i++) {
            android.widget.FrameLayout sqBox = squares.get(i);
            android.view.View dot = dots.get(i);
            int blu = coloreBlu;
            android.graphics.drawable.GradientDrawable boxBg = new android.graphics.drawable.GradientDrawable();
            boxBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);

            if (i < selezionate.size()) {
                // Bordo quadrato esterno: BLU (non riempito)
                boxBg.setColor(0x00000000);
                boxBg.setStroke(dp(2), blu);
                sqBox.setBackground(boxBg);
                // Quadratino interno: BLU PIENO
                android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
                dotBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                dotBg.setColor(blu);
                dot.setBackground(dotBg);
                dot.setVisibility(android.view.View.VISIBLE);
                String id = selezionate.get(i);
                String label = idToLabel.containsKey(id) ? idToLabel.get(id) : id;
                previewTexts.get(i).setText(label);
            } else {
                // Bordo grigio, vuoto
                boxBg.setColor(0x00000000);
                boxBg.setStroke(dp(2), 0xFF888888);
                sqBox.setBackground(boxBg);
                dot.setVisibility(android.view.View.INVISIBLE);
                previewTexts.get(i).setText("");
            }
        }
    }
}
