package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

public final class LisaCapabilitySystem {

    private static final String TAG = "LisaCapabilitySystem";

    public enum Source {
        VOICE, PAD, HOTSPOT, GRID, AGENT,
        POINTER, SWITCH, EXTERNAL_INPUT
    }

    public enum ActionResult {
        SUCCESS, FAILED, UNSUPPORTED, NEED_CONFIRMATION,
        IN_PROGRESS, CANCELLED
    }

    public interface Callback {
        void onResult(ActionResult result, String detail);
    }

    public static final String PARAM_DISTANCE   = "distance";
    public static final String PARAM_DURATION   = "duration";
    public static final String PARAM_TARGET     = "target";
    public static final String PARAM_VALUE      = "value";
    public static final String PARAM_APP_NAME   = "app_name";
    public static final String PARAM_CAPABILITY = "capability";
    public static final String PARAM_X          = "x";
    public static final String PARAM_Y          = "y";

    public enum Cap {
        GESTURE_TAP, GESTURE_DOUBLE_TAP, GESTURE_LONG_PRESS,
        GESTURE_SCROLL_UP, GESTURE_SCROLL_DOWN,
        GESTURE_SCROLL_LEFT, GESTURE_SCROLL_RIGHT,
        GESTURE_SWIPE_UP, GESTURE_SWIPE_DOWN,
        GESTURE_SWIPE_LEFT, GESTURE_SWIPE_RIGHT,
        GESTURE_DRAG_START, GESTURE_DRAG_MOVE, GESTURE_DRAG_END,
        GESTURE_PINCH_IN, GESTURE_PINCH_OUT, GESTURE_CANCEL,

        NAV_BACK, NAV_HOME, NAV_RECENTS,
        NAV_NOTIFICATIONS, NAV_QUICK_SETTINGS,
        NAV_LOCK_SCREEN, NAV_SCREENSHOT,

        APP_OPEN,

        SYSTEM_ENABLE, SYSTEM_DISABLE,
        SYSTEM_OPEN_SETTINGS, SYSTEM_QUERY_STATE,

        TEXT_COPY, TEXT_CUT, TEXT_PASTE, TEXT_SELECT_ALL,

        OVERLAY_NUMBERS, OVERLAY_LABELS,
        OVERLAY_GRID, OVERLAY_HIDE
    }

    public static final class ActionBinding {
        public final Cap action;
        public final Source source;
        public final LisaGestureEngine.GesturePoint targetPoint;
        public final Bundle params;
        public final boolean requiresConfirmation;

        private ActionBinding(Builder b) {
            if (b.action == null || b.source == null) {
                throw new IllegalArgumentException(
                        "action/source null");
            }
            this.action = b.action;
            this.source = b.source;
            this.targetPoint = b.targetPoint;
            this.params = (b.params == null)
                    ? new Bundle()
                    : new Bundle(b.params);
            this.requiresConfirmation = b.requiresConfirmation;
        }

        public static final class Builder {
            private final Cap action;
            private final Source source;
            private LisaGestureEngine.GesturePoint targetPoint;
            private Bundle params;
            private boolean requiresConfirmation;

            public Builder(Cap action, Source source) {
                if (action == null || source == null) {
                    throw new IllegalArgumentException(
                            "action/source null");
                }
                this.action = action;
                this.source = source;
            }
            public Builder point(LisaGestureEngine.GesturePoint p) {
                this.targetPoint = p; return this;
            }
            public Builder params(Bundle b) {
                this.params = b; return this;
            }
            public Builder confirmation(boolean v) {
                this.requiresConfirmation = v; return this;
            }
            public ActionBinding build() { return new ActionBinding(this); }
        }
    }

    private LisaCapabilitySystem() {}

    /**
     * Entry point del bus azioni.
     * Il caller costruisce ActionBinding e chiama esegui().
     */
    public static ActionResult esegui(ActionBinding b, Callback cb) {
        if (b == null) return ActionResult.FAILED;

        // Conferme obbligatorie: eventuale richiesta dal caller
        // puo' solo alzare la soglia, non abbassarla.
        boolean confermaEffettiva = b.requiresConfirmation
                || richiedeConfermaObbligatoria(b);

        if (confermaEffettiva) {
            if (cb != null) cb.onResult(
                    ActionResult.NEED_CONFIRMATION,
                    "azione_critica");
            return ActionResult.NEED_CONFIRMATION;
        }

        try {
            switch (b.action) {
                // --- NAV (global actions) ---
                case NAV_BACK:
                    return global(AccessibilityService.GLOBAL_ACTION_BACK, cb);
                case NAV_HOME:
                    return global(AccessibilityService.GLOBAL_ACTION_HOME, cb);
                case NAV_RECENTS:
                    return global(AccessibilityService.GLOBAL_ACTION_RECENTS, cb);
                case NAV_NOTIFICATIONS:
                    return global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, cb);
                case NAV_QUICK_SETTINGS:
                    return global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, cb);
                case NAV_LOCK_SCREEN:
                    return global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, cb);
                case NAV_SCREENSHOT:
                    return global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, cb);

                // --- GESTURE SCROLL ---
                case GESTURE_SCROLL_UP:
                    return scroll(0, -1, b, cb);
                case GESTURE_SCROLL_DOWN:
                    return scroll(0, +1, b, cb);
                case GESTURE_SCROLL_LEFT:
                    return scroll(-1, 0, b, cb);
                case GESTURE_SCROLL_RIGHT:
                    return scroll(+1, 0, b, cb);

                // --- GESTURE TAP (targetPoint obbligatorio) ---
                case GESTURE_TAP:
                    return tapTarget(b, cb);
                case GESTURE_DOUBLE_TAP:
                    return doubleTapTarget(b, cb);
                case GESTURE_LONG_PRESS:
                    return longPressTarget(b, cb);

                // --- GESTURE CANCEL ---
                case GESTURE_CANCEL: {
                    LisaGestureEngine eng = LisaGestureEngine.get();
                    if (eng == null) return unsupported(cb, "engine_null");
                    eng.cancelAll();
                    if (cb != null) cb.onResult(ActionResult.SUCCESS, "cancel");
                    return ActionResult.SUCCESS;
                }

                // Tutto il resto: ancora non implementato in questo blocco
                default:
                    return unsupported(cb, "cap_non_implementata:" + b.action);
            }
        } catch (Throwable t) {
            Log.e(TAG, "errore su " + b.action, t);
            if (cb != null) cb.onResult(ActionResult.FAILED, t.toString());
            return ActionResult.FAILED;
        }
    }

    // ---------------------------------------------------------
    // Helper interni
    // ---------------------------------------------------------

    private static boolean richiedeConfermaObbligatoria(ActionBinding b) {
        // Solo SYSTEM_DISABLE di BT/Wi-Fi deve passare da SafetyGuard.
        // Verra' implementato completamente quando aggiungeremo SYSTEM_*.
        return false;
    }

    private static ActionResult global(int action, Callback cb) {
        LisaAccessibilityService s = LisaAccessibilityService.getInstance();
        if (s == null) return unsupported(cb, "accessibility_off");
        boolean ok = s.performGlobalAction(action);
        ActionResult r = ok ? ActionResult.SUCCESS : ActionResult.FAILED;
        if (cb != null) cb.onResult(r, "global_" + action);
        return r;
    }

    private static ActionResult scroll(int dirX, int dirY,
                                       ActionBinding b, Callback cb) {
        LisaGestureEngine eng = LisaGestureEngine.get();
        if (eng == null) return unsupported(cb, "engine_null");

        int distanza = 0;
        if (b.params != null) {
            int d = b.params.getInt(PARAM_DISTANCE, 0);
            if (d > 0) distanza = d;
        }
        LisaAccessibilityService svc = LisaAccessibilityService.getInstance();
        if (svc == null) return unsupported(cb, "accessibility_off");
        android.util.DisplayMetrics dm = svc.getResources().getDisplayMetrics();
        LisaGestureEngine.GesturePoint origine = b.targetPoint;
        if (Math.abs(dirY) >= Math.abs(dirX)) {
            if (distanza <= 0) distanza = Math.round(dm.heightPixels * 0.50f);
            if (origine == null) {
                float py = (dirY > 0) ? 0.75f : 0.25f;
                origine = LisaGestureEngine.GesturePoint.norm(0.50f, py);
            }
        } else {
            if (distanza <= 0) distanza = Math.round(dm.widthPixels * 0.50f);
            if (origine == null) {
                float px = (dirX > 0) ? 0.75f : 0.25f;
                origine = LisaGestureEngine.GesturePoint.norm(px, 0.50f);
            }
        }

        eng.scroll(dirX, dirY, distanza,
                origine,
                LisaGestureEngine.ModoScroll.AUTO,
                r -> {
                    if (cb != null) {
                        cb.onResult(
                                r == LisaGestureEngine.Risultato.SUCCESS
                                        ? ActionResult.SUCCESS
                                        : ActionResult.FAILED,
                                "scroll");
                    }
                });
        return ActionResult.IN_PROGRESS;
    }

    private static ActionResult tapTarget(ActionBinding b, Callback cb) {
        LisaGestureEngine eng = LisaGestureEngine.get();
        if (eng == null) return unsupported(cb, "engine_null");
        if (b.targetPoint == null) return unsupported(cb, "no_target");
        eng.tap(b.targetPoint, r -> {
            if (cb != null) cb.onResult(mapRis(r), "tap_gesto_consegnato");
        });
        return ActionResult.IN_PROGRESS;
    }

    private static ActionResult doubleTapTarget(ActionBinding b, Callback cb) {
        LisaGestureEngine eng = LisaGestureEngine.get();
        if (eng == null) return unsupported(cb, "engine_null");
        if (b.targetPoint == null) return unsupported(cb, "no_target");
        eng.doubleTap(b.targetPoint, r -> {
            if (cb != null) cb.onResult(mapRis(r), "doubleTap_gesto_consegnato");
        });
        return ActionResult.IN_PROGRESS;
    }

    private static ActionResult longPressTarget(ActionBinding b, Callback cb) {
        LisaGestureEngine eng = LisaGestureEngine.get();
        if (eng == null) return unsupported(cb, "engine_null");
        if (b.targetPoint == null) return unsupported(cb, "no_target");
        eng.longPress(b.targetPoint, 600L, r -> {
            if (cb != null) cb.onResult(mapRis(r), "longPress_gesto_consegnato");
        });
        return ActionResult.IN_PROGRESS;
    }

    private static ActionResult mapRis(LisaGestureEngine.Risultato r) {
        if (r == LisaGestureEngine.Risultato.SUCCESS) return ActionResult.SUCCESS;
        if (r == LisaGestureEngine.Risultato.CANCELLED) return ActionResult.CANCELLED;
        return ActionResult.FAILED;
    }

    private static ActionResult unsupported(Callback cb, String detail) {
        if (cb != null) cb.onResult(ActionResult.UNSUPPORTED, detail);
        return ActionResult.UNSUPPORTED;
    }
}
