package com.lisa.app;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AccessibilityUtils {

    public static AccessibilityNodeInfo trovaPerTesto(
            AccessibilityNodeInfo root, String testo) {

        if (root == null || testo == null || testo.trim().isEmpty()) return null;

        List<AccessibilityNodeInfo> risultati =
                root.findAccessibilityNodeInfosByText(testo);

        if (risultati != null && !risultati.isEmpty()) {
            return risultati.get(0);
        }

        return cercaRicorsivo(root, testo.trim().toLowerCase());
    }

    private static AccessibilityNodeInfo cercaRicorsivo(
            AccessibilityNodeInfo nodo, String testo) {

        if (nodo == null) return null;

        CharSequence valore = nodo.getText();
        CharSequence descrizione = nodo.getContentDescription();

        if (valore != null &&
                valore.toString().toLowerCase().contains(testo)) {
            return nodo;
        }

        if (descrizione != null &&
                descrizione.toString().toLowerCase().contains(testo)) {
            return nodo;
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo trovato =
                    cercaRicorsivo(nodo.getChild(i), testo);

            if (trovato != null) return trovato;
        }

        return null;
    }

    public static boolean click(AccessibilityNodeInfo nodo) {
        AccessibilityNodeInfo corrente = nodo;

        while (corrente != null) {
            if (corrente.isClickable()) {
                return corrente.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK);
            }
            corrente = corrente.getParent();
        }

        return false;
    }

    public static boolean scrivi(
            AccessibilityNodeInfo nodo, String testo) {

        if (nodo == null || testo == null) return false;

        nodo.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

        Bundle dati = new Bundle();
        dati.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                testo);

        return nodo.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT, dati);
    }

    public static AccessibilityNodeInfo trovaCampoTesto(
            AccessibilityNodeInfo nodo) {

        if (nodo == null) return null;

        if (nodo.isEditable()) return nodo;

        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo trovato =
                    trovaCampoTesto(nodo.getChild(i));

            if (trovato != null) return trovato;
        }

        return null;
    }

    public static boolean scorri(
            AccessibilityNodeInfo nodo, boolean avanti) {

        if (nodo == null) return false;

        if (nodo.isScrollable()) {
            return nodo.performAction(
                    avanti
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {
            if (scorri(nodo.getChild(i), avanti)) return true;
        }

        return false;
    }
}
