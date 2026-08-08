package com.lisa.app;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;

public final class ScreenSnapshot {

    private static final int MASSIMO_ELEMENTI = 180;

    private ScreenSnapshot() {}

    private static class Elemento {
        final AccessibilityNodeInfo nodo;
        final int profondita;

        Elemento(AccessibilityNodeInfo nodo, int profondita) {
            this.nodo = nodo;
            this.profondita = profondita;
        }
    }

    public static String acquisisci(AccessibilityNodeInfo root) {
        JSONObject schermata = new JSONObject();

        try {
            if (root == null) {
                schermata.put("ok", false);
                schermata.put("errore", "schermata_non_disponibile");
                schermata.put("elementi", new JSONArray());
                return schermata.toString();
            }

            schermata.put("ok", true);
            schermata.put(
                "pacchetto",
                root.getPackageName() == null
                    ? ""
                    : root.getPackageName().toString()
            );

            JSONArray elementi = new JSONArray();
            ArrayDeque<Elemento> coda = new ArrayDeque<>();
            coda.add(new Elemento(root, 0));

            int identificatore = 1;

            while (!coda.isEmpty() &&
                   elementi.length() < MASSIMO_ELEMENTI) {

                Elemento corrente = coda.removeFirst();
                AccessibilityNodeInfo nodo = corrente.nodo;

                if (nodo == null) continue;

                String testo = valore(nodo.getText());
                String descrizione =
                    valore(nodo.getContentDescription());

                String suggerimento = "";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    suggerimento = valore(nodo.getHintText());
                }

                boolean interattivo =
                    nodo.isClickable() ||
                    nodo.isLongClickable() ||
                    nodo.isEditable() ||
                    nodo.isScrollable() ||
                    nodo.isCheckable() ||
                    nodo.isFocusable();

                boolean significativo =
                    interattivo ||
                    !testo.isEmpty() ||
                    !descrizione.isEmpty() ||
                    !suggerimento.isEmpty();

                if (nodo.isVisibleToUser() && significativo) {
                    JSONObject elemento = new JSONObject();
                    Rect limiti = new Rect();
                    nodo.getBoundsInScreen(limiti);

                    elemento.put("id", identificatore++);
                    elemento.put("testo", testo);
                    elemento.put("descrizione", descrizione);
                    elemento.put("suggerimento", suggerimento);
                    elemento.put(
                        "classe",
                        valore(nodo.getClassName())
                    );
                    elemento.put(
                        "view_id",
                        nodo.getViewIdResourceName() == null
                            ? ""
                            : nodo.getViewIdResourceName()
                    );

                    elemento.put("cliccabile", nodo.isClickable());
                    elemento.put(
                        "pressione_lunga",
                        nodo.isLongClickable()
                    );
                    elemento.put("modificabile", nodo.isEditable());
                    elemento.put("scorrevole", nodo.isScrollable());
                    elemento.put("selezionato", nodo.isSelected());
                    elemento.put("abilitato", nodo.isEnabled());
                    elemento.put("profondita", corrente.profondita);

                    JSONObject posizione = new JSONObject();
                    posizione.put("sinistra", limiti.left);
                    posizione.put("alto", limiti.top);
                    posizione.put("destra", limiti.right);
                    posizione.put("basso", limiti.bottom);
                    posizione.put("centro_x", limiti.centerX());
                    posizione.put("centro_y", limiti.centerY());

                    elemento.put("posizione", posizione);
                    elementi.put(elemento);
                }

                for (int i = 0; i < nodo.getChildCount(); i++) {
                    AccessibilityNodeInfo figlio = nodo.getChild(i);

                    if (figlio != null) {
                        coda.addLast(
                            new Elemento(
                                figlio,
                                corrente.profondita + 1
                            )
                        );
                    }
                }
            }

            schermata.put("numero_elementi", elementi.length());
            schermata.put("elementi", elementi);

        } catch (Exception errore) {
            try {
                schermata.put("ok", false);
                schermata.put(
                    "errore",
                    errore.getClass().getSimpleName() +
                    ": " + errore.getMessage()
                );
            } catch (Exception ignored) {}
        }

        return schermata.toString();
    }

    private static String valore(CharSequence valore) {
        return valore == null ? "" : valore.toString().trim();
    }
}
