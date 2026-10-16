package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.View;

/**
 * Lisa Dock - struttura base.
 * Patch 1: overlay vuoto, agganciabile su 4 lati (sx/dx/alto/basso).
 * Nessun pulsante. Solo visualizzazione.
 *
 * NOTA MAIN THREAD: mostra(), nascondi(), setLato() devono essere chiamati
 * dal main thread. La responsabilita' e' del chiamante (LisaAccessibilityService).
 *
 * NOTA TEST: la barra vuota non e' cliccabile. Il test di Patch 1 verifica
 * solo comparsa, spostamento, scomparsa.
 */
public final class LisaDockView {

    private static final String TAG = "LisaDockView";
    private static final String PREFS = "lisa_ui";
    private static final String KEY_LATO = "dock_lato";

    // Costanti di stile (unico punto di verita')
    private static final int COLORE_SFONDO = 0xCC202020;
    private static final int PAD_DP = 8;
    private static final int LATO_VERTICALE_LARGHEZZA_DP = 48;
    private static final int LATO_VERTICALE_ALTEZZA_DP = 140;
    private static final int LATO_ORIZZONTALE_LARGHEZZA_DP = 140;
    private static final int LATO_ORIZZONTALE_ALTEZZA_DP = 48;

    private final AccessibilityService ctx;
    private final WindowManager wm;
    private final LinearLayout root;
    private WindowManager.LayoutParams lp;
    private boolean visibile = false;
    private int dimPulsanteDp = 48;

    public LisaDockView(AccessibilityService context) {
        this.ctx = context;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.root = new LinearLayout(context);
        this.root.setOrientation(LinearLayout.VERTICAL);
        this.root.setBackgroundColor(0x00000000);
        this.root.setPadding(0, 0, 0, 0);
        applicaDimensioni("dx");
        creaPulsanti();
    }

    public boolean isVisibile() {
        return visibile;
    }

    public void mostra() {
        if (visibile) {
            Log.w(TAG, "DOCK_ADD_SKIP gia_visibile");
            return;
        }
        if (wm == null) {
            Log.e(TAG, "DOCK_ADD_FAIL wm_null");
            return;
        }

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        String lato = leggiLato();
        applicaGravity(lato);

        try {
            wm.addView(root, lp);
            visibile = true;
            Log.i(TAG, "DOCK_ADD_OK lato=" + lato);
        } catch (Throwable t) {
            visibile = false;
            Log.e(TAG, "DOCK_ADD_FAIL", t);
        }
    }

    public void nascondi() {
        if (!visibile || wm == null) return;
        try {
            wm.removeViewImmediate(root);
            visibile = false;
            Log.i(TAG, "DOCK_REMOVE_OK");
        } catch (Throwable t) {
            Log.e(TAG, "DOCK_REMOVE_FAIL stato_invariato", t);
        }
    }

    private void creaPulsanti() {
        nav("⏺️", "Home", LisaCapabilitySystem.Cap.NAV_HOME);
        navRuotata("⏏️", "Indietro", LisaCapabilitySystem.Cap.NAV_BACK, -90f);
        nav("⏹️", "Recenti", LisaCapabilitySystem.Cap.NAV_RECENTS);
        nav("🔔", "Notifiche", LisaCapabilitySystem.Cap.NAV_NOTIFICATIONS);
        nav("⚡", "Quick Settings", LisaCapabilitySystem.Cap.NAV_QUICK_SETTINGS);
        nav("🔒", "Blocca schermo", LisaCapabilitySystem.Cap.NAV_LOCK_SCREEN);

        ctrl("🔊", "Volume su", "aumenta");
        ctrl("🔉", "Volume giu", "diminuisci");

        nav("🔼", "Scorri su", LisaCapabilitySystem.Cap.GESTURE_SCROLL_UP);
        nav("🔽", "Scorri giu", LisaCapabilitySystem.Cap.GESTURE_SCROLL_DOWN);
        nav("◀️", "Scorri sinistra", LisaCapabilitySystem.Cap.GESTURE_SCROLL_LEFT);
        nav("▶️", "Scorri destra", LisaCapabilitySystem.Cap.GESTURE_SCROLL_RIGHT);

        ctrlSis("🔆", "Luminosita su", "luminosita", "aumenta");
        ctrlSis("🔅", "Luminosita giu", "luminosita", "diminuisci");
        screenshot("📸", "Screenshot");
        overlay("⊞", "Griglia");

    }

    private void navRuotata(String icona, String descrizione,
                            final LisaCapabilitySystem.Cap cap,
                            final float gradi) {
        FrameLayout frame = new FrameLayout(ctx);
        int size = dp(dimPulsanteDp);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(0, 0, 0, 0);
        frame.setLayoutParams(p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(0);
        bg.setStroke(dp(1), 0x00000000);
        frame.setBackground(bg);
        TextView tv = new TextView(ctx);
        tv.setText(icona);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                dp(dimPulsanteDp) * 0.60f);
        tv.setIncludeFontPadding(false);
        tv.setTextColor(0xFF202020);
        tv.setGravity(Gravity.CENTER);
        tv.setRotation(gradi);
        frame.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        frame.setContentDescription(descrizione);
        frame.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { eseguiNav(cap); }
        });
        root.addView(frame);
    }

    private void ctrlSis(String icona, String descrizione,
                        final String tipo, final String op) {
        aggiungiPulsante(icona, descrizione, new Runnable() {
            public void run() {
                SystemController.regola(ctx, tipo, op, null);
            }
        });
    }

    private void screenshot(String icona, String descrizione) {
        aggiungiPulsante(icona, descrizione, new Runnable() {
            public void run() {
                try {
                    LisaAccessibilityService.eseguiScreenshotConRipristino();
                } catch (Throwable t) {
                    Log.e(TAG, "DOCK_SCREENSHOT_FAIL", t);
                }
            }
        });
    }

    private void overlay(String icona, String descrizione) {
        aggiungiPulsante(icona, descrizione, new Runnable() {
            public void run() {
                try {
                    LisaAccessibilityService s =
                            LisaAccessibilityService.getInstance();
                    if (s != null) s.mostraGriglia();
                } catch (Throwable t) {
                    Log.e(TAG, "DOCK_OVERLAY_FAIL", t);
                }
            }
        });
    }

    private void vuoto() {
        aggiungiPulsante("", "", null);
    }

    private void nav(String icona, String descrizione, final LisaCapabilitySystem.Cap cap) {
        aggiungiPulsante(icona, descrizione, new Runnable() {
            public void run() { eseguiNav(cap); }
        });
    }

    private void ctrl(String icona, String descrizione, final String op) {
        aggiungiPulsante(icona, descrizione, new Runnable() {
            public void run() {
                SystemController.regola(ctx, "volume", op, null);
            }
        });
    }

    private void placeholder(String icona, final String descrizione) {
        aggiungiPulsante(icona, descrizione, null);
    }

    private void aggiungiPulsante(String testo, String descrizione, final Runnable azione) {
        TextView b = new TextView(ctx);
        b.setText(testo);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                dp(dimPulsanteDp) * 0.60f);
        b.setIncludeFontPadding(false);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setContentDescription(descrizione);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x00000000);
        bg.setCornerRadius(0);
        bg.setStroke(dp(1), 0x00000000);
        b.setBackground(bg);
        b.setForeground(null);
        b.setStateListAnimator(null);
        b.setTextColor(0xFF202020);
        int size = dp(dimPulsanteDp);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(0, 0, 0, 0);
        b.setLayoutParams(p);
        if (azione != null) {
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { azione.run(); }
            });
        } else {
            b.setEnabled(false);
            b.setAlpha(0.4f);
        }
        root.addView(b);
    }

    private void eseguiNav(LisaCapabilitySystem.Cap c) {
        try {
            LisaCapabilitySystem.ActionBinding b =
                    new LisaCapabilitySystem.ActionBinding.Builder(
                            c, LisaCapabilitySystem.Source.PAD).build();
            LisaCapabilitySystem.esegui(b, null);
        } catch (Throwable t) {
            Log.e(TAG, "DOCK_NAV_FAIL " + c, t);
        }
    }

    public void setLato(String lato) {
        String valido = normalizzaLato(lato);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LATO, valido).apply();
        applicaGravity(valido);
    }

    private String leggiLato() {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return normalizzaLato(p.getString(KEY_LATO, "dx"));
    }

    private String normalizzaLato(String lato) {
        if ("dx".equals(lato) || "alto".equals(lato)
                || "basso".equals(lato) || "sx".equals(lato)) {
            return lato;
        }
        return "sx";
    }

    private void applicaGravity(String lato) {
        applicaDimensioni(lato);

        if (lp == null) {
            Log.w(TAG, "DOCK_GRAVITY_SKIP lp_null (salvato per prossima mostra)");
            return;
        }

        if ("dx".equals(lato)) {
            lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            root.setOrientation(LinearLayout.VERTICAL);
        } else if ("alto".equals(lato)) {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            root.setOrientation(LinearLayout.HORIZONTAL);
        } else if ("basso".equals(lato)) {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            root.setOrientation(LinearLayout.HORIZONTAL);
        } else {
            lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            root.setOrientation(LinearLayout.VERTICAL);
        }
        lp.x = 0;
        lp.y = 0;

        if (visibile && wm != null) {
            try {
                wm.updateViewLayout(root, lp);
                Log.i(TAG, "DOCK_UPDATE_OK lato=" + lato);
            } catch (Throwable t) {
                Log.e(TAG, "DOCK_UPDATE_FAIL", t);
            }
        }
    }

    private void applicaDimensioni(String lato) {
        boolean orizzontale = "alto".equals(lato) || "basso".equals(lato);

        int nPulsanti = 16;
        android.util.DisplayMetrics dm =
                ctx.getResources().getDisplayMetrics();
        int dimSchermoDp = Math.round(
                (orizzontale ? dm.widthPixels : dm.heightPixels)
                        / dm.density);
        int spazioUtile = Math.max(120, dimSchermoDp - 100);
        int dim = spazioUtile / nPulsanti;
        dim = dim * 145 / 100;  // LARGE 145%
        dimPulsanteDp = Math.max(30, Math.min(85, dim));

        root.setMinimumWidth(dp(orizzontale
                ? dimPulsanteDp * nPulsanti
                : dimPulsanteDp));
        root.setMinimumHeight(dp(orizzontale
                ? dimPulsanteDp
                : dimPulsanteDp * nPulsanti));
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
