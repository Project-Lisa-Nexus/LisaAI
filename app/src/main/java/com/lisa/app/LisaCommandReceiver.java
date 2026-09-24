package com.lisa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;

public class LisaCommandReceiver extends BroadcastReceiver {
public static final String ACTION_LISA_COMMAND = "com.lisa.app.COMMAND";

    private static final String TAG = "LisaCommandReceiver";

    private static boolean bloccoAdbCanaleCritico(
            String verbo,
            String target) {

        if (verbo == null || target == null) {
            return false;
        }

        String v =
                java.text.Normalizer.normalize(
                        verbo,
                        java.text.Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ITALIAN)
                .replaceAll("[^\\p{L}\\p{N}]+", "");

        String t =
                java.text.Normalizer.normalize(
                        target,
                        java.text.Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ITALIAN)
                .replaceAll("[^\\p{L}\\p{N}]+", "");

        boolean spegne =
                v.equals("spegni")
                || v.equals("disattiva")
                || v.equals("disabilita")
                || v.equals("chiudi");

        boolean critico =
                t.equals("bluetooth")
                || t.equals("wifi");

        return spegne && critico;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_LISA_COMMAND.equals(intent.getAction())) return;

        String azione = intent.getStringExtra("azione");
        String pacchetto = intent.getStringExtra("pacchetto");
        String nomeApp = intent.getStringExtra("nome_app");
        String testo = intent.getStringExtra("testo");
        String testoB64 = intent.getStringExtra("testo_b64");

        if (testoB64 != null &&
            !testoB64.trim().isEmpty()) {
            try {
                testo = new String(
                    Base64.decode(
                        testoB64,
                        Base64.DEFAULT
                    ),
                    StandardCharsets.UTF_8
                );
            } catch (Exception errore) {
                Log.e(
                    TAG,
                    "Testo Base64 non valido",
                    errore
                );
            }
        }


        if ("asr_probe_status".equals(azione)) {

            if (isOrderedBroadcast()) setResultCode(0);
            setResultData(
                    LisaVoiceService.asrProbeStatusStatic()
            );

            return;
        }

        if ("asr_probe".equals(azione)) {

            android.content.Intent servizio =
                    new android.content.Intent(
                            context,
                            LisaVoiceService.class
                    );

            servizio.setAction(
                    LisaVoiceService.ACTION_ASR_PROBE
            );

            try {

                context.startForegroundService(servizio);

                if (isOrderedBroadcast()) setResultCode(0);

            } catch (Exception errore) {

                android.util.Log.e(
                        "LisaCommandReceiver",
                        "Errore avvio ASR probe",
                        errore
                );

                if (isOrderedBroadcast()) setResultCode(1);
            }

            return;
        }

        if ("wake_status".equals(azione)) {

            if (isOrderedBroadcast()) setResultCode(0);
            setResultData(
                    LisaVoiceService.wakeStatusStatic()
            );

            return;
        }

        if ("wake_test".equals(azione)) {

            android.content.Intent servizio =
                    new android.content.Intent(
                            context,
                            LisaVoiceService.class
                    );

            servizio.setAction(
                    LisaVoiceService.ACTION_WAKE_TEST
            );

            try {

                context.startForegroundService(servizio);

                if (isOrderedBroadcast()) setResultCode(0);

                android.util.Log.i(
                        "LisaCommandReceiver",
                        "Wake test richiesto"
                );

            } catch (Exception errore) {

                android.util.Log.e(
                        "LisaCommandReceiver",
                        "Errore avvio wake test",
                        errore
                );

                if (isOrderedBroadcast()) setResultCode(1);
            }

            return;
        }

        if ("apri_app".equals(azione)) {
            boolean riuscito = false;

            if (nomeApp != null && !nomeApp.trim().isEmpty()) {
                riuscito = AppFinder.apriAppPerNome(
                    context,
                    nomeApp.trim()
                );
            }

            if (!riuscito &&
                pacchetto != null &&
                !pacchetto.trim().isEmpty()) {

                LisaAccessibilityService.apriAppStatic(
                    context,
                    pacchetto.trim()
                );
                riuscito = true;
            }

            if (isOrderedBroadcast()) setResultCode(riuscito ? 0 : 1);
            return;
        }


        if ("apri_url".equals(azione)) {
            String url = intent.getStringExtra("url");

            if (url == null || url.trim().isEmpty()) {
                if (isOrderedBroadcast()) setResultCode(1);
                setResultData("url_mancante");
                return;
            }

            try {
                Intent browser = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url.trim())
                );
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(browser);
                if (isOrderedBroadcast()) setResultCode(0);
            } catch (Exception errore) {
                Log.e(TAG, "Impossibile aprire URL", errore);
                if (isOrderedBroadcast()) setResultCode(1);
                setResultData("errore_apertura_url");
            }

            return;
        }

        if ("parla".equals(azione)) {
            BroadcastReceiver.PendingResult attesa = goAsync();

            LisaSpeaker.parla(
                context,
                testo,
                attesa::finish
            );

            return;
        }

        LisaAccessibilityService servizio =
            LisaAccessibilityService.getInstance();

        if (servizio == null) {
            Log.e(TAG, "Servizio accessibilità non attivo");
            if (isOrderedBroadcast()) setResultCode(1);
            setResultData("servizio_accessibilita_non_attivo");
            return;
        }

        if ("leggi_schermo".equals(azione)) {
            String json = ScreenSnapshot.acquisisci(
                servizio.getRootInActiveWindow()
            );

            String codificato = Base64.encodeToString(
                json.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            );

            if (isOrderedBroadcast()) setResultCode(0);
            setResultData(codificato);
            return;
        }

        boolean azioneInterattiva =
            "esegui_ui_generica".equals(azione) ||
            "imposta_toggle".equals(azione) ||
            "clicca".equals(azione) ||
            "scrivi_testo".equals(azione) ||
            "scorri_giu".equals(azione) ||
            "scorri_su".equals(azione);

        boolean autorizzazioneEsplicita =
            "true".equalsIgnoreCase(
                intent.getStringExtra("consenti_protetto")
            );

        if (azioneInterattiva &&
            !autorizzazioneEsplicita &&
            SafetyGuard.schermataProtetta(
                context,
                servizio.getRootInActiveWindow()
            )) {

            Log.w(TAG, "Azione bloccata su app di accessibilità");
            if (isOrderedBroadcast()) setResultCode(2);
            setResultData("schermata_protetta");
            return;
        }

        if ("esegui_ui_generica".equals(azione)) {

            String verbo =
                    intent.getStringExtra("verbo");

            String target =
                    intent.getStringExtra("target");

            if (verbo == null
                    || verbo.trim().isEmpty()
                    || target == null
                    || target.trim().isEmpty()) {

                if (isOrderedBroadcast()) {
                    setResultCode(1);
                    setResultData(
                            "parametri_ui_mancanti"
                    );
                }

                return;
            }

            if (bloccoAdbCanaleCritico(
                    verbo,
                    target)) {

                Log.w(
                        TAG,
                        "SAFETY ADB: spegnimento bloccato: "
                                + target
                );

                if (isOrderedBroadcast()) {
                    setResultCode(1);
                    setResultData(
                            "safety_guard_bloccato|stato=nessuno"
                    );
                }

                return;
            }

            final boolean ordinato =
                    isOrderedBroadcast();

            BroadcastReceiver.PendingResult pending =
                    goAsync();

            servizio.eseguiAzioneUIGenerica(
                    verbo.trim(),
                    target.trim(),
                    (ok, statoFinale, dettaglio) -> {

                        if (ordinato) {

                            pending.setResultCode(
                                    ok ? 0 : 1
                            );

                            String stato =
                                    statoFinale == null
                                            ? "nessuno"
                                            : statoFinale
                                                    ? "on"
                                                    : "off";

                            pending.setResultData(
                                    dettaglio
                                            + "|stato="
                                            + stato
                            );
                        }

                        pending.finish();
                    }
            );

            return;
        }

        if ("imposta_toggle".equals(azione)) {

            String etichetta =
                    intent.getStringExtra("etichetta");

            String stato =
                    intent.getStringExtra("stato");

            if (etichetta == null
                    || etichetta.trim().isEmpty()
                    || stato == null
                    || stato.trim().isEmpty()) {

                if (isOrderedBroadcast()) {
                    setResultCode(1);
                    setResultData("parametri_toggle_mancanti");
                }

                return;
            }

            String valore =
                    stato.trim()
                            .toLowerCase(java.util.Locale.ITALIAN);

            final boolean desiderato;

            if ("on".equals(valore)
                    || "true".equals(valore)
                    || "1".equals(valore)
                    || "acceso".equals(valore)
                    || "attivo".equals(valore)) {

                desiderato = true;

            } else if ("off".equals(valore)
                    || "false".equals(valore)
                    || "0".equals(valore)
                    || "spento".equals(valore)
                    || "disattivato".equals(valore)) {

                desiderato = false;

            } else {

                if (isOrderedBroadcast()) {
                    setResultCode(1);
                    setResultData("stato_toggle_non_valido");
                }

                return;
            }

            if (!desiderato
                    && bloccoAdbCanaleCritico(
                            "spegni",
                            etichetta)) {

                Log.w(
                        TAG,
                        "SAFETY ADB toggle: spegnimento bloccato: "
                                + etichetta
                );

                if (isOrderedBroadcast()) {
                    setResultCode(1);
                    setResultData(
                            "safety_guard_bloccato|stato=nessuno"
                    );
                }

                return;
            }

            final boolean ordinato = isOrderedBroadcast();

            BroadcastReceiver.PendingResult pending = goAsync();

            servizio.impostaTogglePerEtichetta(
                    etichetta.trim(),
                    desiderato,
                    (ok, statoFinale, dettaglio) -> {

                        if (ordinato) {

                            pending.setResultCode(ok ? 0 : 1);

                            String risultato = dettaglio;

                            if (statoFinale != null) {
                                risultato += "|stato="
                                        + (statoFinale ? "on" : "off");
                            }

                            pending.setResultData(risultato);
                        }

                        pending.finish();
                    }
            );

            return;
        }

        boolean riuscito = false;

        if ("clicca".equals(azione)) {
            riuscito = servizio.cliccaTesto(testo);

        } else if ("scrivi_testo".equals(azione)) {
            riuscito = servizio.scriviTesto(testo);

        } else if ("messaggio".equals(azione)) {
            String contatto = intent.getStringExtra("contatto");
            String testoMessaggio = intent.getStringExtra("testo_messaggio");
            String appMessaggio = intent.getStringExtra("app");
            String pacchettoMessaggio = intent.getStringExtra("pacchetto");

            riuscito = servizio.cercaContattoEInvia(
                    contatto,
                    testoMessaggio,
                    appMessaggio,
                    pacchettoMessaggio
            );

            if (riuscito) {
                LisaSpeaker.parla(
                    context,
                    "Messaggio pronto per " + contatto + ". Vuoi inviarlo?",
                    () -> servizio.ascoltaConfermaInvio()
            );
            } else {
                String domanda = servizio.domandaContattoAmbiguo();

                if (domanda != null) {
                    LisaSpeaker.parla(context, domanda, () -> {});
                }
            }

        } else if ("conferma_invio".equals(azione)) {
            riuscito = servizio.confermaInvio();

        } else if ("annulla_invio".equals(azione)) {
            servizio.annullaInvio();
            riuscito = true;

        } else if ("impostazione".equals(azione)) {

            String tipo = intent.getStringExtra("tipo");
            String operazione = intent.getStringExtra("operazione");
            String valore = intent.getStringExtra("valore");

            riuscito = SystemController.regola(
                    context,
                    tipo,
                    operazione,
                    valore
            );

        } else if ("scorri_giu".equals(azione)) {
            riuscito = servizio.scorriAvanti();

        } else if ("scorri_su".equals(azione)) {
            riuscito = servizio.scorriIndietro();

        } else if ("home".equals(azione)) {
            riuscito = servizio.performGlobalAction(
                android.accessibilityservice.AccessibilityService
                    .GLOBAL_ACTION_HOME
            );

        } else if ("indietro".equals(azione)) {
            riuscito = servizio.performGlobalAction(
                android.accessibilityservice.AccessibilityService
                    .GLOBAL_ACTION_BACK
            );

        } else if ("recenti".equals(azione)) {
            riuscito = servizio.performGlobalAction(
                android.accessibilityservice.AccessibilityService
                    .GLOBAL_ACTION_RECENTS
            );

        } else if ("notifiche".equals(azione)) {
            riuscito = servizio.performGlobalAction(
                android.accessibilityservice.AccessibilityService
                    .GLOBAL_ACTION_NOTIFICATIONS
            );

        } else if ("screenshot".equals(azione)) {
            riuscito = servizio.performGlobalAction(
                android.accessibilityservice.AccessibilityService
                    .GLOBAL_ACTION_TAKE_SCREENSHOT
            );

        } else if ("blocca".equals(azione)) {
            riuscito = servizio.performGlobalAction(
                android.accessibilityservice.AccessibilityService
                    .GLOBAL_ACTION_LOCK_SCREEN
            );

        } else if (azione != null && azione.startsWith("VOLUME")) {
            String operazione = azione.contains("DOWN") ? "diminuisci" : "aumenta";
            riuscito = SystemController.regola(context, "volume", operazione, null);

        } else if (azione != null &&
                (azione.startsWith("BRIGHTNESS") || azione.startsWith("LUMINOSITA"))) {
            String operazione = azione.contains("DOWN") ? "diminuisci" : "aumenta";
            riuscito = SystemController.regola(context, "luminosita", operazione, null);

        } else if ("TOGGLE_FLASHLIGHT".equals(azione) || "torcia".equals(azione)) {
            riuscito = SystemController.toggleTorcia(context);
        }

        if (isOrderedBroadcast()) setResultCode(riuscito ? 0 : 1);
    }
}
