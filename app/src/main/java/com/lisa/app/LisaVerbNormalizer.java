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

        if (canonico.equals(verbo)) return t;
        return (resto.isEmpty()) ? canonico : canonico + " " + resto;
    }
}
