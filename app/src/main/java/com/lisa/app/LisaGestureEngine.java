package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.LinkedList;
import java.util.Queue;

public final class LisaGestureEngine {

    private static final String TAG = "LisaGestureEngine";

    public enum Risultato { SUCCESS, FAILED, CANCELLED }
    public interface Callback { void onComplete(Risultato r); }
    public enum ModoScroll { AUTO, SEMANTICO, FISICO }

    public static final class GesturePoint {
        public final float x, y;
        public final boolean normalized;
        private GesturePoint(float x, float y, boolean n) {
            this.x = x; this.y = y; this.normalized = n;
        }
        public static GesturePoint norm(float nx, float ny) {
            return new GesturePoint(nx, ny, true);
        }
        public static GesturePoint px(float px, float py) {
            return new GesturePoint(px, py, false);
        }
    }

    private static LisaGestureEngine instance;
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Queue<Runnable> coda = new LinkedList<>();
    private volatile boolean occupato = false;
    private volatile long generation = 0L;

    private volatile boolean dragInCorso = false;
    private volatile boolean dragAttivo = false;
    private GestureDescription.StrokeDescription dragStroke = null;
    private volatile int dragX = -1, dragY = -1;

    private LisaGestureEngine(AccessibilityService s) { this.service = s; }

    public static void init(AccessibilityService s) {
        if (instance == null || instance.service != s)
            instance = new LisaGestureEngine(s);
    }
    public static LisaGestureEngine get() { return instance; }
    public static void release() {
        if (instance != null) { instance.cancelAll(); instance = null; }
    }
    public boolean isDragging() { return dragInCorso || dragAttivo; }

    public void cancelAll() {
        synchronized (coda) {
            generation++;
            coda.clear();
            occupato = false;
        }
        if (dragInCorso || dragAttivo) rilascioDragForzato();
        Log.i(TAG, "CANCEL_ALL gen=" + generation);
    }

    private void rilascioDragForzato() {
        if (dragStroke == null) {
            dragInCorso = false;
            dragAttivo = false;
            return;
        }
        try {
            int y2 = dragY > 0 ? dragY - 1 : dragY + 1;
            Path p = new Path();
            p.moveTo(dragX, dragY);
            p.lineTo(dragX, y2);
            GestureDescription.StrokeDescription last =
                    dragStroke.continueStroke(p, 0L, 50L, false);
            GestureDescription gd = new GestureDescription.Builder()
                    .addStroke(last).build();
            dispatchGrezzo(gd, null);
        } catch (Throwable t) {
            Log.e(TAG, "RILASCIO_FORZATO_FAIL", t);
        }
        dragStroke = null;
        dragInCorso = false;
        dragAttivo = false;
    }

    public void tap(GesturePoint p, Callback cb) { execSingolo(p, 50L, cb); }

    public void doubleTap(GesturePoint p, Callback cb) {
        synchronized (coda) {
            if (dragAttivo) { safe(cb, Risultato.CANCELLED); return; }
            final long gen = generation;
            coda.add(() -> {
                if (gen != generation) {
                    safe(cb, Risultato.CANCELLED);
                    handler.post(this::eseguiProssima);
                    return;
                }
                dispatchGrezzo(creaGestureTap(p, 40L), r1 -> {
                    if (gen != generation) {
                        safe(cb, Risultato.CANCELLED);
                        handler.post(this::eseguiProssima);
                        return;
                    }
                    if (r1 != Risultato.SUCCESS) {
                        safe(cb, r1);
                        handler.post(this::eseguiProssima);
                        return;
                    }
                    handler.postDelayed(() -> {
                        if (gen != generation) {
                            safe(cb, Risultato.CANCELLED);
                            handler.post(this::eseguiProssima);
                            return;
                        }
                        dispatchGrezzo(creaGestureTap(p, 40L), r2 -> {
                            safe(cb, r2);
                            handler.post(this::eseguiProssima);
                        });
                    }, 120L);
                });
            });
            if (!occupato) { occupato = true; handler.post(this::eseguiProssima); }
        }
    }

    public void longPress(GesturePoint p, long durataMs, Callback cb) {
        execSingolo(p, durataMs, cb);
    }

    public void swipe(GesturePoint p1, GesturePoint p2, long durataMs, Callback cb) {
        int[] a = px(p1), b = px(p2);
        Path path = new Path();
        path.moveTo(a[0], a[1]);
        path.lineTo(b[0], b[1]);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        path, 0L, Math.max(50L, durataMs))).build();
        enqueue(gd, cb);
    }

    public void pinch(GesturePoint centro, int dxyIniziale, int dxyFinale,
                      long durataMs, Callback cb) {
        int[] c = px(centro);
        Path p1 = new Path();
        p1.moveTo(c[0], c[1] - dxyIniziale);
        p1.lineTo(c[0], c[1] - dxyFinale);
        Path p2 = new Path();
        p2.moveTo(c[0], c[1] + dxyIniziale);
        p2.lineTo(c[0], c[1] + dxyFinale);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p1, 0L, durataMs))
                .addStroke(new GestureDescription.StrokeDescription(p2, 0L, durataMs))
                .build();
        enqueue(gd, cb);
    }

    public void scroll(int dxContenuto, int dyContenuto, int distanzaPx,
                       GesturePoint origine, ModoScroll modo, Callback cb) {
        synchronized (coda) {
            if (dragAttivo) { safe(cb, Risultato.CANCELLED); return; }
            final long gen = generation;
            coda.add(() -> {
                if (gen != generation || dragAttivo) {
                    safe(cb, Risultato.CANCELLED);
                    handler.post(this::eseguiProssima);
                    return;
                }
                boolean keyguardLocked = isKeyguardLocked();
                if (keyguardLocked) {
                    Log.i(TAG, "KEYGUARD_FORCE_FISICO dir=("
                            + dxContenuto + "," + dyContenuto + ")");
                }
                if (!keyguardLocked && modo != ModoScroll.FISICO) {
                    if (scrollSemantico(dxContenuto, dyContenuto, origine)) {
                        safe(cb, Risultato.SUCCESS);
                        handler.post(LisaGestureEngine.this::eseguiProssima);
                        return;
                    }
                    if (modo == ModoScroll.SEMANTICO) {
                        safe(cb, Risultato.FAILED);
                        handler.post(LisaGestureEngine.this::eseguiProssima);
                        return;
                    }
                }
                dispatchFisicoDiretto(dxContenuto, dyContenuto, distanzaPx, origine, cb);
            });
            if (!occupato) { occupato = true; handler.post(this::eseguiProssima); }
        }
    }

    public void dragStart(GesturePoint p, Callback cb) {
        if (dragInCorso || dragAttivo || occupato) {
            safe(cb, Risultato.FAILED); return;
        }
        dragAttivo = true;
        int[] a = px(p);
        dragX = a[0]; dragY = a[1];
        int y2 = dragY > 0 ? dragY - 1 : dragY + 1;
        Path path = new Path();
        path.moveTo(dragX, dragY);
        path.lineTo(dragX, y2);
        dragStroke = new GestureDescription.StrokeDescription(
                path, 0L, 600L, true);
        Log.i(TAG, "DRAG_START " + dragX + "," + dragY);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(dragStroke).build();
        dispatchGrezzo(gd, r -> {
            if (r == Risultato.SUCCESS) {
                dragInCorso = true;
            } else {
                dragStroke = null;
                dragInCorso = false;
                dragAttivo = false;
                Log.i(TAG, "DRAG_START_FAIL cleanup");
            }
            safe(cb, r);
        });
    }

    public void dragMove(GesturePoint p, Callback cb) {
        if (!dragInCorso || dragStroke == null) { safe(cb, Risultato.FAILED); return; }
        int[] a = px(p);
        Path path = new Path();
        path.moveTo(dragX, dragY);
        path.lineTo(a[0], a[1]);
        GestureDescription.StrokeDescription next =
                dragStroke.continueStroke(path, 0L, 400L, true);
        dragStroke = next;
        dragX = a[0]; dragY = a[1];
        Log.i(TAG, "DRAG_MOVE " + dragX + "," + dragY);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(next).build();
        dispatchGrezzo(gd, cb);
    }

    public void dragEnd(Callback cb) {
        if (!dragInCorso || dragStroke == null) { safe(cb, Risultato.FAILED); return; }
        int y2 = dragY > 0 ? dragY - 1 : dragY + 1;
        Path path = new Path();
        path.moveTo(dragX, dragY);
        path.lineTo(dragX, y2);
        GestureDescription.StrokeDescription last =
                dragStroke.continueStroke(path, 0L, 50L, false);
        Log.i(TAG, "DRAG_END " + dragX + "," + dragY);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(last).build();
        dispatchGrezzo(gd, r -> {
            dragStroke = null;
            dragInCorso = false;
            dragAttivo = false;
            safe(cb, r);
        });
    }

    public void dragCancel() {
        if (dragInCorso || dragAttivo) rilascioDragForzato();
    }

    private void safe(Callback cb, Risultato r) { if (cb != null) cb.onComplete(r); }

    private GestureDescription creaGestureTap(GesturePoint p, long durataMs) {
        int[] a = px(p);
        Path path = new Path();
        path.moveTo(a[0], a[1]);
        return new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        path, 0L, durataMs)).build();
    }

    private void execSingolo(GesturePoint p, long durataMs, Callback cb) {
        enqueue(creaGestureTap(p, durataMs), cb);
    }

    private void enqueue(GestureDescription gd, Callback cb) {
        synchronized (coda) {
            if (dragAttivo) { safe(cb, Risultato.CANCELLED); return; }
            final long gen = generation;
            coda.add(() -> {
                if (gen != generation || dragAttivo) {
                    safe(cb, Risultato.CANCELLED);
                    handler.post(this::eseguiProssima);
                    return;
                }
                dispatchDiretto(gd, cb);
            });
            if (!occupato) { occupato = true; handler.post(this::eseguiProssima); }
        }
    }

    private void eseguiProssima() {
        Runnable r;
        synchronized (coda) {
            r = coda.poll();
            if (r == null) { occupato = false; return; }
        }
        r.run();
    }

    // Unico punto di dispatchGesture() del motore.
    private void dispatchGrezzo(GestureDescription gd, Callback cb) {
        boolean ok;
        try {
            ok = service.dispatchGesture(gd,
                    new AccessibilityService.GestureResultCallback() {
                        @Override public void onCompleted(GestureDescription d) {
                            safe(cb, Risultato.SUCCESS);
                        }
                        @Override public void onCancelled(GestureDescription d) {
                            safe(cb, Risultato.CANCELLED);
                        }
                    }, null);
        } catch (Throwable t) {
            Log.e(TAG, "dispatch_grezzo_fail", t); ok = false;
        }
        if (!ok) safe(cb, Risultato.FAILED);
    }

    private void dispatchDiretto(GestureDescription gd, Callback cb) {
        final long gen = generation;
        boolean ok;
        try {
            ok = service.dispatchGesture(gd,
                    new AccessibilityService.GestureResultCallback() {
                        @Override public void onCompleted(GestureDescription d) {
                            safe(cb, Risultato.SUCCESS);
                            if (gen == generation) handler.post(
                                    LisaGestureEngine.this::eseguiProssima);
                        }
                        @Override public void onCancelled(GestureDescription d) {
                            safe(cb, Risultato.CANCELLED);
                            if (gen == generation) handler.post(
                                    LisaGestureEngine.this::eseguiProssima);
                        }
                    }, null);
        } catch (Throwable t) { Log.e(TAG, "dispatch_fail", t); ok = false; }
        if (!ok) {
            safe(cb, Risultato.FAILED);
            if (gen == generation) handler.post(this::eseguiProssima);
        }
    }

    private boolean scrollSemantico(int dxC, int dyC, GesturePoint origine) {
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo target = trovaScrollabilePerOrigine(
                    root, origine, dxC, dyC);
            if (target == null) return false;
            int azione = scegliAzioneScroll(target, dxC, dyC);
            if (azione == 0) return false;
            boolean ok = target.performAction(azione);
            Log.i(TAG, "SCROLL_SEMANTICO ok=" + ok + " act=" + azione);
            return ok;
        } catch (Throwable t) { Log.e(TAG, "SCROLL_SEM_FAIL", t); return false; }
    }

    private void dispatchFisicoDiretto(int dxC, int dyC, int distanzaPx,
                                       GesturePoint origine, Callback cb) {
        Rect b = boundsSchermo();
        int cx, cy;
        if (origine != null) {
            int[] p = px(origine);
            cx = p[0]; cy = p[1];
        } else {
            cx = b.centerX(); cy = b.centerY();
        }
        int x2 = cx, y2 = cy;
        if (Math.abs(dyC) >= Math.abs(dxC)) {
            y2 = cy + (dyC > 0 ? -distanzaPx : distanzaPx);
        } else {
            x2 = cx + (dxC > 0 ? -distanzaPx : distanzaPx);
        }
        Path path = new Path();
        path.moveTo(cx, cy);
        path.lineTo(x2, y2);
        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 400L))
                .build();
        Log.i(TAG, "SCROLL_FISICO " + cx + "," + cy + " -> " + x2 + "," + y2);
        dispatchDiretto(gd, cb);
    }

    private AccessibilityNodeInfo trovaScrollabilePerOrigine(
            AccessibilityNodeInfo root, GesturePoint origine, int dx, int dy) {
        boolean verticale = Math.abs(dy) >= Math.abs(dx);
        java.util.List<AccessibilityNodeInfo> candidati =
                new java.util.ArrayList<>();
        raccogliScrollabili(root, candidati, 0);

        AccessibilityNodeInfo best = null;
        long bestArea = -1;
        int cx = -1, cy = -1;
        if (origine != null) {
            int[] p = px(origine);
            cx = p[0]; cy = p[1];
        }

        for (AccessibilityNodeInfo n : candidati) {
            if (!haAsseCorretto(n, verticale, dx, dy)) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            long area = (long) r.width() * r.height();
            // Bonus se contiene il punto origine
            if (cx >= 0 && r.contains(cx, cy)) area += 10_000_000L;
            if (area > bestArea) {
                bestArea = area;
                best = n;
            }
        }
        return best;
    }

    private boolean haAsseCorretto(AccessibilityNodeInfo n,
            boolean verticale, int dx, int dy) {
        java.util.List<AccessibilityNodeInfo.AccessibilityAction> acts =
                n.getActionList();
        if (acts == null) return false;
        int up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId();
        int down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId();
        int left = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId();
        int right = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId();
        for (AccessibilityNodeInfo.AccessibilityAction a : acts) {
            if (a == null) continue;
            int id = a.getId();
            if (verticale) {
                if (dy > 0 && id == down) return true;
                if (dy < 0 && id == up) return true;
            } else {
                if (dx > 0 && id == right) return true;
                if (dx < 0 && id == left) return true;
            }
        }
        return false;
    }

    private void raccogliScrollabili(AccessibilityNodeInfo n,
            java.util.List<AccessibilityNodeInfo> out, int lvl) {
        if (n == null || lvl > 8) return;
        if (n.isScrollable()) out.add(n);
        int figli = Math.min(n.getChildCount(), 20);
        for (int i = 0; i < figli; i++) {
            raccogliScrollabili(n.getChild(i), out, lvl + 1);
        }
    }

    private AccessibilityNodeInfo nodoInPunto(AccessibilityNodeInfo n,
            int x, int y, int lvl) {
        if (n == null || lvl > 12) return null;
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        if (!r.contains(x, y)) return null;
        int figli = Math.min(n.getChildCount(), 15);
        for (int i = 0; i < figli; i++) {
            AccessibilityNodeInfo t = nodoInPunto(n.getChild(i), x, y, lvl + 1);
            if (t != null) return t;
        }
        return n;
    }

    private AccessibilityNodeInfo risaliScrollabile(AccessibilityNodeInfo n, int max) {
        int lvl = 0;
        while (n != null && lvl < max) {
            if (n.isScrollable()) return n;
            n = n.getParent();
            lvl++;
        }
        return null;
    }

    private AccessibilityNodeInfo trovaScrollabile(AccessibilityNodeInfo n, int lvl) {
        if (n == null || lvl > 6) return null;
        if (n.isScrollable()) return n;
        int figli = Math.min(n.getChildCount(), 12);
        for (int i = 0; i < figli; i++) {
            AccessibilityNodeInfo t = trovaScrollabile(n.getChild(i), lvl + 1);
            if (t != null) return t;
        }
        return null;
    }

    private int scegliAzioneScroll(AccessibilityNodeInfo n, int dx, int dy) {
        java.util.List<AccessibilityNodeInfo.AccessibilityAction> acts = n.getActionList();
        int scrollUp = AccessibilityNodeInfo.AccessibilityAction
                .ACTION_SCROLL_UP.getId();
        int scrollDown = AccessibilityNodeInfo.AccessibilityAction
                .ACTION_SCROLL_DOWN.getId();
        int scrollLeft = AccessibilityNodeInfo.AccessibilityAction
                .ACTION_SCROLL_LEFT.getId();
        int scrollRight = AccessibilityNodeInfo.AccessibilityAction
                .ACTION_SCROLL_RIGHT.getId();
        boolean up=false, down=false, left=false, right=false;
        if (acts != null) {
            for (AccessibilityNodeInfo.AccessibilityAction a : acts) {
                if (a == null) continue;
                int id = a.getId();
                if (id == scrollUp) up = true;
                else if (id == scrollDown) down = true;
                else if (id == scrollLeft) left = true;
                else if (id == scrollRight) right = true;
            }
        }
        // Solo azioni direzionali esplicite.
        // FORWARD/BACKWARD non definiscono l'asse: esclusi.
        if (Math.abs(dy) >= Math.abs(dx)) {
            if (dy > 0) return down ? scrollDown : 0;
            else return up ? scrollUp : 0;
        } else {
            if (dx > 0) return right ? scrollRight : 0;
            else return left ? scrollLeft : 0;
        }
    }

    private Rect boundsSchermo() {
        Rect r = new Rect();
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                    service.getSystemService(android.content.Context.WINDOW_SERVICE);
            if (wm != null) {
                android.view.Display d = wm.getDefaultDisplay();
                android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                d.getRealMetrics(dm);
                r.set(0, 0, dm.widthPixels, dm.heightPixels);
            }
        } catch (Throwable ignored) {}
        if (r.width() == 0) {
            android.util.DisplayMetrics dm = service.getResources().getDisplayMetrics();
            r.set(0, 0, dm.widthPixels, dm.heightPixels);
        }
        return r;
    }

    public boolean isKeyguardLocked() {
        try {
            android.app.KeyguardManager km =
                    (android.app.KeyguardManager) service.getSystemService(
                            android.content.Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            Log.e(TAG, "KEYGUARD_CHECK_FAIL", t);
            return false;
        }
    }

    private int[] px(GesturePoint p) {
        Rect b = boundsSchermo();
        int w = b.width(), h = b.height();
        int x, y;
        if (p.normalized) {
            x = (int) (Math.max(0f, Math.min(1f, p.x)) * (w - 1));
            y = (int) (Math.max(0f, Math.min(1f, p.y)) * (h - 1));
        } else {
            x = (int) Math.max(0, Math.min(w - 1, p.x));
            y = (int) Math.max(0, Math.min(h - 1, p.y));
        }
        return new int[]{x, y};
    }
}
