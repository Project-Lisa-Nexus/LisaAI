package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;

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

    public LisaDockView(AccessibilityService context) {
        this.ctx = context;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.root = new LinearLayout(context);
        this.root.setOrientation(LinearLayout.VERTICAL);
        this.root.setBackgroundColor(COLORE_SFONDO);
        int pad = dp(PAD_DP);
        this.root.setPadding(pad, pad, pad, pad);
        applicaDimensioni("sx");
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

    public void setLato(String lato) {
        String valido = normalizzaLato(lato);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LATO, valido).apply();
        applicaGravity(valido);
    }

    private String leggiLato() {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return normalizzaLato(p.getString(KEY_LATO, "sx"));
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
        root.setMinimumWidth(dp(orizzontale
                ? LATO_ORIZZONTALE_LARGHEZZA_DP
                : LATO_VERTICALE_LARGHEZZA_DP));
        root.setMinimumHeight(dp(orizzontale
                ? LATO_ORIZZONTALE_ALTEZZA_DP
                : LATO_VERTICALE_ALTEZZA_DP));
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
