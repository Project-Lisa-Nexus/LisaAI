package com.lisa.app;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AccessibilityUtils {

    public static AccessibilityNodeInfo trovaPerTesto(
            AccessibilityNodeInfo root, String testo) {

        if (root == null || testo == null || testo.trim().isEmpty())
            return null;

        String cercato = testo.trim().toLowerCase();

        List<AccessibilityNodeInfo> risultati =
                root.findAccessibilityNodeInfosByText(testo.trim());

        if (risultati != null) {
            AccessibilityNodeInfo migliore = null;

            for (AccessibilityNodeInfo nodo : risultati) {
                if (nodo == null || !nodo.isVisibleToUser() || !nodo.isEnabled())
                    continue;

                CharSequence valore = nodo.getText();
                CharSequence descrizione = nodo.getContentDescription();

                String t = valore == null ? "" : valore.toString().trim().toLowerCase();
                String d = descrizione == null ? "" : descrizione.toString().trim().toLowerCase();

                if (t.equals(cercato) || d.equals(cercato)) {
                    if (nodo.isClickable() || nodo.isFocusable())
                        return nodo;

                    if (migliore == null)
                        migliore = nodo;
                }
            }

            if (migliore != null)
                return migliore;
        }

        return cercaRicorsivoIntelligente(root, cercato);
    }

    private static AccessibilityNodeInfo cercaRicorsivoIntelligente(
            AccessibilityNodeInfo nodo, String testo) {

        if (nodo == null) return null;

        if (nodo.isVisibleToUser() && nodo.isEnabled()) {
            CharSequence valore = nodo.getText();
            CharSequence descrizione = nodo.getContentDescription();

            String t = valore == null ? "" : valore.toString().trim().toLowerCase();
            String d = descrizione == null ? "" : descrizione.toString().trim().toLowerCase();

            if (t.equals(testo) || d.equals(testo) ||
                    t.contains(testo) || d.contains(testo)) {

                if (nodo.isClickable() || nodo.isFocusable())
                    return nodo;

                AccessibilityNodeInfo padre = nodo.getParent();
                while (padre != null) {
                    if (padre.isVisibleToUser() && padre.isEnabled() &&
                            padre.isClickable())
                        return padre;
                    padre = padre.getParent();
                }

                return nodo;
            }
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo trovato =
                    cercaRicorsivoIntelligente(nodo.getChild(i), testo);

            if (trovato != null)
                return trovato;
        }

        return null;
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

    public static AccessibilityNodeInfo trovaPerTestoNonEditabile(
            AccessibilityNodeInfo root, String testo) {

        if (root == null || testo == null || testo.trim().isEmpty())
            return null;

        return cercaNonEditabile(
                root,
                testo.trim().toLowerCase()
        );
    }

    private static AccessibilityNodeInfo cercaNonEditabile(
            AccessibilityNodeInfo nodo, String testo) {

        if (nodo == null) return null;

        CharSequence valore = nodo.getText();
        CharSequence descrizione = nodo.getContentDescription();

        if (!nodo.isEditable()) {
            if (valore != null &&
                    valore.toString().toLowerCase().contains(testo)) {
                return nodo;
            }

            if (descrizione != null &&
                    descrizione.toString().toLowerCase().contains(testo)) {
                return nodo;
            }
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo trovato =
                    cercaNonEditabile(nodo.getChild(i), testo);

            if (trovato != null) return trovato;
        }

        return null;
    }


    public static AccessibilityNodeInfo trovaPerTestoVisibile(
            AccessibilityNodeInfo root, String testo) {

        if (root == null || testo == null || testo.trim().isEmpty())
            return null;

        return cercaSoloTesto(
                root,
                testo.trim().toLowerCase()
        );
    }

    private static AccessibilityNodeInfo cercaSoloTesto(
            AccessibilityNodeInfo nodo, String testo) {

        if (nodo == null)
            return null;

        CharSequence valore = nodo.getText();

        if (!nodo.isEditable()
                && valore != null
                && valore.toString().trim().equalsIgnoreCase(testo)) {

            return nodo;
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {

            AccessibilityNodeInfo trovato =
                    cercaSoloTesto(nodo.getChild(i), testo);

            if (trovato != null)
                return trovato;
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
