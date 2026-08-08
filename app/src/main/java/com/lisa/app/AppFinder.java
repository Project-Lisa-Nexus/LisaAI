package com.lisa.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class AppFinder {

    public static boolean apriAppPerNome(Context context, String nomeApp) {
        if (nomeApp == null || nomeApp.trim().isEmpty()) return false;

        PackageManager pm = context.getPackageManager();

        Intent ricerca = new Intent(Intent.ACTION_MAIN);
        ricerca.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> applicazioni =
            pm.queryIntentActivities(ricerca, PackageManager.MATCH_ALL);

        ResolveInfo migliore = null;
        int punteggioMigliore = -1;

        for (ResolveInfo info : applicazioni) {
            CharSequence etichetta = info.loadLabel(pm);
            if (etichetta == null) continue;

            int punteggio = calcolaPunteggio(
                nomeApp,
                etichetta.toString()
            );

            if (punteggio > punteggioMigliore) {
                punteggioMigliore = punteggio;
                migliore = info;
            }
        }

        if (migliore == null || punteggioMigliore < 450) {
            return false;
        }

        Intent avvio = new Intent(Intent.ACTION_MAIN);
        avvio.addCategory(Intent.CATEGORY_LAUNCHER);
        avvio.setClassName(
            migliore.activityInfo.packageName,
            migliore.activityInfo.name
        );
        avvio.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        );

        context.startActivity(avvio);
        return true;
    }

    private static int calcolaPunteggio(String cercato, String etichetta) {
        String q = normalizza(cercato);
        String l = normalizza(etichetta);

        if (q.equals(l)) return 2000;

        String qCompatto = q.replace(" ", "");
        String lCompatto = l.replace(" ", "");

        if (qCompatto.equals(lCompatto)) return 1900;
        if (l.contains(q)) return 1700 - Math.abs(l.length() - q.length());
        if (lCompatto.contains(qCompatto)) return 1600;

        String[] paroleQ = q.split(" ");
        String[] paroleL = l.split(" ");

        int corrispondenze = 0;
        int punti = 0;

        for (String parolaQ : paroleQ) {
            int migliore = 0;

            for (String parolaL : paroleL) {
                if (parolaQ.equals(parolaL)) {
                    migliore = Math.max(migliore, 120);
                } else if (
                    parolaQ.length() >= 3 &&
                    parolaL.length() >= 3 &&
                    (parolaQ.startsWith(parolaL) ||
                     parolaL.startsWith(parolaQ))
                ) {
                    migliore = Math.max(migliore, 90);
                } else if (prefissoComune(parolaQ, parolaL) >= 3) {
                    migliore = Math.max(migliore, 65);
                }
            }

            if (migliore > 0) {
                corrispondenze++;
                punti += migliore;
            }
        }

        if (corrispondenze == paroleQ.length) {
            return 900 + punti;
        }

        if (
            paroleQ.length >= 2 &&
            corrispondenze >= paroleQ.length - 1
        ) {
            return 450 + punti;
        }

        return -1;
    }

    private static int prefissoComune(String a, String b) {
        int limite = Math.min(a.length(), b.length());
        int i = 0;

        while (i < limite && a.charAt(i) == b.charAt(i)) {
            i++;
        }

        return i;
    }

    private static String normalizza(String testo) {
        String risultato = Normalizer.normalize(
            testo.toLowerCase(Locale.ITALIAN),
            Normalizer.Form.NFD
        );

        risultato = risultato.replaceAll("\\p{M}", "");
        risultato = risultato.replaceAll("[^a-z0-9]+", " ");
        return risultato.trim().replaceAll("\\s+", " ");
    }
}
