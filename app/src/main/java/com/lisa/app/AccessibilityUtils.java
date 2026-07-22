package com.lisa.app;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AccessibilityUtils {

    public static AccessibilityNodeInfo trovaPerTesto(AccessibilityNodeInfo root, String testo) {
        if (root == null || testo == null) return null;

        List<AccessibilityNodeInfo> lista = root.findAccessibilityNodeInfosByText(testo);

        if (lista != null && !lista.isEmpty()) {
            return lista.get(0);
        }

        return null;
    }

    public static boolean click(AccessibilityNodeInfo nodo) {
        if (nodo == null) return false;

        AccessibilityNodeInfo corrente = nodo;

        while (corrente != null) {
            if (corrente.isClickable()) {
                return corrente.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            corrente = corrente.getParent();
        }

        return false;
    }
}
