package com.lisa.app;

import java.util.Locale;

// Normalizza i verbi vocali di Lisa verso forme canoniche.
// Non tocca i dizionari esistenti: riscrive solo la frase in ingresso.
public final class LisaVerbNormalizer {

    private LisaVerbNormalizer() {}

    // Verbi canonici: attiva / disattiva / apri / chiudi / aumenta /
    // diminuisci / premi / vai
    public static String normalizza(String frase) {
        if (frase == null) return "";
        String t = frase.toLowerCase(Locale.ITALIAN).trim();
        if (t.isEmpty()) return t;

        // Frasi brevi (risposte contestuali) non si toccano
        String[] parole = t.split(" ");
        if (parole.length <= 1) return t;
        if (parole.length == 2
                && (parole[0].equals("no")
                    || parole[0].equals("si")
                    || parole[0].equals("sì")
                    || parole[0].equals("ok"))) return t;

        // Rimuovi prefisso "lisa" / "ciao lisa" / "ehi lisa"
        if (t.startsWith("ciao lisa ")) t = t.substring(10).trim();
        else if (t.startsWith("ehi lisa ")) t = t.substring(9).trim();
        else if (t.startsWith("ok lisa ")) t = t.substring(8).trim();
        else if (t.startsWith("lisa ")) t = t.substring(5).trim();
        if (t.isEmpty()) return frase.toLowerCase(Locale.ITALIAN).trim();

        String[] p = t.split(" ", 2);
        String verbo = p[0];
        String resto = p.length > 1 ? p[1] : "";

        String canonico = verbo;
        if (verbo.equals("accendi") || verbo.equals("avvia")
                || verbo.equals("lancia") || verbo.equals("avvia")
                || verbo.equals("attiva") || verbo.equals("apri")
                || verbo.equals("mostra") || verbo.equals("mostrami")
                || verbo.equals("visualizza")
                || verbo.equals("fammi") || verbo.equals("metti")) {
            canonico = "apri";
        } else if (verbo.equals("spegni") || verbo.equals("disattiva")
                || verbo.equals("chiudi") || verbo.equals("nascondi")
                || verbo.equals("togli") || verbo.equals("leva")
                || verbo.equals("disabilita")) {
            canonico = "chiudi";
        } else if (verbo.equals("premi") || verbo.equals("clicca")
                || verbo.equals("tocca") || verbo.equals("seleziona")) {
            canonico = "premi";
        } else if (verbo.equals("alza") || verbo.equals("aumenta")
                || verbo.equals("ingrandisci") || verbo.equals("piu")
                || verbo.equals("più")) {
            canonico = "aumenta";
        } else if (verbo.equals("abbassa") || verbo.equals("riduci")
                || verbo.equals("diminuisci") || verbo.equals("meno")
                || verbo.equals("rimpicciolisci")) {
            canonico = "diminuisci";
        }

        // 🟢 GRUPPO SCROLL: solo se il resto e' UNA DIREZIONE PURA
        if (verbo.equals("scorri") || verbo.equals("corri")
                || verbo.equals("muovi") || verbo.equals("sposta")
                || verbo.equals("vai") || verbo.equals("scivola")
                || verbo.equals("sali") || verbo.equals("scendi")) {
            String dir = normalizzaDirezione(resto);
            if (dir != null) {
                return "scorri " + dir;
            }
            // altrimenti: NON toccare la frase originale
        }

        if (canonico.equals(verbo)) return t;
        return (resto.isEmpty()) ? canonico : canonico + " " + resto;
    }

    // Restituisce direzione canonica SOLO se resto e' interamente
    // una direzione pura. Altrimenti null (non toccare la frase).
    private static String normalizzaDirezione(String resto) {
        if (resto == null) return null;
        String r = resto.toLowerCase(Locale.ITALIAN)
                .trim()
                .replaceAll("\\s+", " ");
        if (r.isEmpty()) return null;

        if (r.matches("^(?:in |verso |a )?(?:basso|giu|giu'|giù|down)$"))
            return "in basso";
        if (r.matches("^(?:in |verso )?(?:alto|su|up)$"))
            return "in alto";
        if (r.matches("^(?:a |verso )?(?:destra|right)$"))
            return "a destra";
        if (r.matches("^(?:a |verso )?(?:sinistra|left)$"))
            return "a sinistra";
        return null;
    }
}
