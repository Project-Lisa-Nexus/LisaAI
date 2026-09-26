package com.lisa.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class LisaVoiceService extends Service {



    private static final String TAG = "LisaVoiceSession";

    public static final String ACTION_WAKE_TEST =
            "com.lisa.app.WAKE_TEST";
    public static final String ACTION_ASR_PROBE = "com.lisa.app.ASR_PROBE";

    private WakeWordManager wakeWordManager;
    private AsrProbeManager asrProbeManager;

    private static final String CHANNEL_ID = "LisaVoiceChannel";
    private static final String API_LISA =
            "http://127.0.0.1:5000/api/invoca";

    private static volatile boolean sessioneAttiva = false;
    private static volatile boolean whisperLocaleAttivo = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private final VoiceController voiceController = VoiceController.getInstance();
    private boolean ascoltoInCorso = false;
    private int errorSilenzioConsecutivi = 0;
    private boolean inAttesaVuoiFareAltro = false;
    private Runnable ascoltoProgrammato;

    // Risposta reale prodotta dai comandi deterministici locali.
    // Viene consumata subito dal chiamante Whisper.
    private String rispostaComandoLocale = null;

    // P4.2B: l'ultima esecuzione locale e' stata demandata
    // al Generic UI Resolver asincrono.
    private boolean ultimaAzioneUiGenerica = false;

    // SAFETY VOCE: richiesta critica in attesa di conferma.
    private String pendingCriticalTarget = null;
    private long pendingCriticalTimestamp = 0L;

    public static boolean isSessioneAttiva() {
        return sessioneAttiva;
    }


    private static volatile LisaVoiceService instance;
    private volatile boolean sospesoPerTts = false;

    public static void sospendiPerTts() {

        LisaVoiceService servizio = instance;

        if (servizio == null) return;

        servizio.handler.post(() -> {

            servizio.sospesoPerTts = true;
if (servizio.recognizer != null) {

                try {
                    servizio.recognizer.cancel();
                } catch (Exception ignored) {
                }
            }

            servizio.ascoltoInCorso = false;

            servizio.chiediAudioFocus(
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            );

            Log.d(TAG,
                    "ASR sospeso: Lisa sta parlando");
        });
    }

    public static void riprendiDopoTts() {

        LisaVoiceService servizio = instance;

        if (servizio == null) return;

        servizio.handler.postDelayed(() -> {

            servizio.rilasciaAudioFocus();

            servizio.sospesoPerTts = false;

            if (sessioneAttiva && !whisperLocaleAttivo) {
                servizio.programmaAscolto(250);
            }

        }, 300);
    }

    private android.media.AudioManager audioManager;
    private android.media.AudioFocusRequest focusRequest;

    private void chiediAudioFocus(int gain) {
        if (audioManager == null)
            audioManager = (android.media.AudioManager)
                    getSystemService(android.content.Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        rilasciaAudioFocus();

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.media.AudioAttributes attrs =
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
            focusRequest = new android.media.AudioFocusRequest.Builder(gain)
                    .setAudioAttributes(attrs).build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(
                    null, android.media.AudioManager.STREAM_MUSIC, gain);
        }
    }

    private void rilasciaAudioFocus() {
        if (audioManager == null) return;

        if (android.os.Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        } else if (android.os.Build.VERSION.SDK_INT < 26) {
            audioManager.abandonAudioFocus(null);
        }
    }

    public static final String ACTION_LOCAL_WHISPER_SESSION =
            "com.lisa.nexus.ACTION_LOCAL_WHISPER_SESSION";

    private AndroidAsrPipeProbeManager androidAsrPipeProbeManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        creaCanaleNotifica();
        Log.i(TAG, "Lisa Voice Session creata");
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        String azione =
                intent != null
                        ? intent.getAction()
                        : null;

        startForeground(
                1,
                creaNotifica("Lisa attiva"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        );

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "RECORD_AUDIO non concesso");
            terminaSessione(false);
            return START_NOT_STICKY;
        }

        if (!accessibilitaLisaAttiva()) {
            Log.i(TAG, "Avvio voce rifiutato: Accessibility Lisa OFF");
            sessioneAttiva = false;

            LisaAccessibilityService.aggiornaIndicatoreAscolto(false);
            MainActivity.aggiornaStatoPulsante();

            voiceController.reset();

            try {
                stopForeground(true);
            } catch (Exception ignored) {
            }

            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_LOCAL_WHISPER_SESSION.equals(azione)) {

            // Whisper locale possiede il microfono.
            // Il Service resta foreground per mantenere viva
            // la sessione anche quando viene aperta un'altra app.
            sessioneAttiva = true;

            LisaAccessibilityService.aggiornaIndicatoreAscolto(true);
            MainActivity.aggiornaStatoPulsante();

            fermaRecognizer();

            whisperLocaleAttivo = true;
            voiceController.startSession();

            LisaAccessibilityService.aggiornaVignettaSemplice(
                    "🎤 Lisa Voice Control attivo"
            );

            if (wakeWordManager != null) {
                wakeWordManager.stopListening();
            }

            aggiornaNotifica("Lisa - ascolto locale attivo");
            Log.i(TAG, "SESSIONE WHISPER LOCALE AGGANCIATA AL SERVICE");

            return START_STICKY;
        }

        if (ACTION_WAKE_TEST.equals(azione)) {

            avviaWakeTest();

            return START_STICKY;
        }

        if ("com.lisa.nexus.ACTION_ANDROID_ASR_PIPE_PROBE".equals(azione)) {

            avviaAndroidAsrPipeProbe();

            return START_STICKY;
        }

        if (ACTION_ASR_PROBE.equals(azione)) {

            avviaAsrProbe();

            return START_STICKY;
        }

        // Se stiamo entrando manualmente nella sessione comandi,
        // il wake deve rilasciare il microfono.
        if (wakeWordManager != null) {
            wakeWordManager.stopListening();
        }

        // Nuova attivazione manuale: il controller deve uscire
        // da eventuale STOPPING rimasto dalla sessione precedente.
        voiceController.startSession();
        sessioneAttiva = true;

        LisaAccessibilityService.aggiornaIndicatoreAscolto(true);
        MainActivity.aggiornaStatoPulsante();

        aggiornaNotifica(
                "Lisa pronta ad ascoltare"
        );

        programmaAscolto(250);

        return START_NOT_STICKY;
    }



    public static void eseguiComandoWhisperLocale(
            String frase) {

        LisaVoiceService servizio = instance;

        if (servizio == null
                || frase == null
                || frase.trim().isEmpty()) {

            Log.w(TAG,
                    "Comando Whisper non eseguito: Service non disponibile");
            return;
        }

        new android.os.Handler(
                android.os.Looper.getMainLooper()
        ).post(() -> {

            String comando =
                    servizio.rimuoviRichiamoLisa(frase)
                            .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                            .replaceAll("\\s+", " ")
                            .trim();

            Log.i(TAG, "DEBUG WHISPER COMANDO=[" + comando + "]");

            LisaAccessibilityService.aggiornaVignettaSemplice(
                    comando
            );

        servizio.rispostaComandoLocale = null;

        boolean eseguito =
                    servizio.eseguiLocaleRapido(comando);

            String comandoUsato = comando;

            // Se il comando esatto non viene riconosciuto,
            // prova una correzione conservativa SOLO sui comandi
            // Android locali conosciuti.
            if (!eseguito) {

                String comandoCorretto =
                        servizio.correggiComandoWhisperLocale(
                                comando
                        );

                if (!comandoCorretto.equals(comando)) {

                    comandoUsato = comandoCorretto;

                    eseguito =
                            servizio.eseguiLocaleRapido(
                                    comandoCorretto
                            );
                }
            }

            // Un comando UI generico produce risultato/feedback
            // nella propria callback, anche se il click e' asincrono.
            // Non generare qui un secondo feedback locale.
            if (eseguito
                    && servizio.ultimaAzioneUiGenerica) {

                servizio.rispostaComandoLocale = null;

                Log.i(
                        TAG,
                        "WHISPER -> P4 UI GENERICA: feedback delegato alla callback"
                );

                return;
            }

            final String rispostaLocaleFinale =
                    servizio.rispostaComandoLocale;

            // Consumata qui: nessuna risposta può finire
            // accidentalmente sul comando successivo.
            servizio.rispostaComandoLocale = null;

            final String azioneFinale =
                    eseguito
                            ? (rispostaLocaleFinale != null
                                    ? rispostaLocaleFinale
                                    : fraseInPrimaPersona(comandoUsato))
                            : null;

            LisaAccessibilityService.aggiungiRigaDiagnosi(
                    "🧠",
                    "Interpretato: " + comandoUsato
            );

            if (eseguito) {
                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "⚙️",
                        "Azione: " + azioneFinale
                );
            }

            Log.i(
                    TAG,
                    "WHISPER -> COMANDO LOCALE: "
                            + comando
                            + " | interpretato="
                            + comandoUsato
                            + " | eseguito="
                            + eseguito
            );

            if (eseguito) {
                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "✅",
                        "Risultato: eseguito=true"
                );
            }

            // Blocca il rumore ASR evidente prima di programmare
            // la risposta semplice e prima di inviare a LisaOS.
            if (!eseguito
                    && java.util.Arrays.asList("a","e","i","o","u","ah","eh","ih","oh","uh","mm","mh","hmm","hm","mmm","ehm","boh","mah","niente","nulla").contains(comandoUsato.trim().toLowerCase())) {

                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "⚠️",
                        "Non ho capito"
                );

                LisaAccessibilityService.aggiornaVignettaSemplice(
                        "⚠️ Non ho capito"
                );

                Log.i(
                        TAG,
                        "WHISPER -> RUMORE EVIDENTE BLOCCATO: "
                                + comandoUsato
                );

                return;
            }

            // La vignetta semplice non deve mostrare
            // "Invio a LisaOS".
            // Per i comandi locali mostra la risposta finale
            // dopo 1,5 secondi. Per il fallback LisaOS resta
            // invece sul comando iniziale fino alla risposta.
            if (eseguito) {

                final String rispostaSemplice =
                        azioneFinale;

                new android.os.Handler(
                        android.os.Looper.getMainLooper()
                ).postDelayed(
                        () -> LisaAccessibilityService
                                .aggiornaVignettaSemplice(
                                        rispostaSemplice
                                ),
                        1500L
                );
            }

            // Se non è un comando Android locale,
            // passa la frase ORIGINALE a tutto il cervello Lisa:
            // WhatsApp, contesto, LisaOS, messaggi, ecc.
            if (!eseguito) {

                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "🌐",
                        "LisaOS: elaborazione richiesta"
                );

                Log.i(
                        TAG,
                        "WHISPER -> CERVELLO COMPLETO: "
                                + frase
                );

                servizio.gestisciFrase(frase);
            }
        });
    }

    private static String fraseInPrimaPersona(String comando) {
        if (comando == null) return "";
        String c = comando.toLowerCase(java.util.Locale.ITALIAN).trim();

        if (c.equals("vai alla home") || c.equals("home")
                || c.equals("torna alla home")
                || c.equals("portami alla home")
                || c.equals("schermata principale")
                || c.equals("vai alla schermata principale")
                || c.equals("torna alla schermata principale")) {
            return "Apro la schermata Home";
        }
        if (c.equals("indietro") || c.equals("torna indietro")
                || c.equals("vai indietro")) {
            return "Torno indietro";
        }
        if (c.equals("recenti") || c.equals("app recenti")
                || c.equals("mostra recenti")) {
            return "Apro le app recenti";
        }
        if (c.equals("notifiche") || c.equals("apri notifiche")
                || c.equals("mostra notifiche")) {
            return "Apro le notifiche";
        }
        if (c.equals("screenshot") || c.equals("fai screenshot")
                || c.equals("fai uno screenshot")) {
            return "Faccio uno screenshot";
        }
        if (c.equals("blocca schermo") || c.equals("blocca lo schermo")
                || c.equals("spegni schermo")) {
            return "Blocco lo schermo";
        }
        if (c.startsWith("alza volume")
                || c.startsWith("aumenta volume")
                || c.equals("volume su")) {
            return "Alzo il volume";
        }
        if (c.startsWith("abbassa volume")
                || c.startsWith("diminuisci volume")
                || c.equals("volume giu") || c.equals("volume giù")) {
            return "Abbasso il volume";
        }
        if (c.startsWith("alza luminosita")
                || c.startsWith("aumenta luminosita")
                || c.startsWith("luminosita su")) {
            return "Alzo la luminosità";
        }
        if (c.startsWith("abbassa luminosita")
                || c.startsWith("luminosita giu")
                || c.startsWith("luminosita giù")) {
            return "Abbasso la luminosità";
        }
        if (c.startsWith("apri ")) {
            String nome = comando.substring(5).trim();
            if (!nome.isEmpty()) return "Apro " + nome;
        }
        if (c.startsWith("avvia ")) {
            String nome = comando.substring(6).trim();
            if (!nome.isEmpty()) return "Avvio " + nome;
        }
        if (c.startsWith("aprimi ")) {
            String nome = comando.substring(7).trim();
            if (!nome.isEmpty()) return "Apro " + nome;
        }
        return "Eseguo: " + comando;
    }

        private String correggiComandoWhisperLocale(
            String frase) {

        if (frase == null) {
            return "";
        }

        String originale = frase.trim();

        String testo =
                java.text.Normalizer.normalize(
                        originale.toLowerCase(Locale.ITALIAN),
                        java.text.Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // DEDUP WHISPER:
        // corregge una sola ripetizione identica consecutiva X X.
        // La prima metà deve avere almeno 5 caratteri.
        String[] partiDedup = testo.split(" ", -1);

        if (partiDedup.length >= 2
                && partiDedup.length % 2 == 0) {

            int meta = partiDedup.length / 2;
            StringBuilder primaMeta = new StringBuilder();
            StringBuilder secondaMeta = new StringBuilder();

            for (int i = 0; i < meta; i++) {
                if (i > 0) primaMeta.append(" ");
                primaMeta.append(partiDedup[i]);
            }

            for (int i = meta; i < partiDedup.length; i++) {
                if (i > meta) secondaMeta.append(" ");
                secondaMeta.append(partiDedup[i]);
            }

            String prima = primaMeta.toString();
            String seconda = secondaMeta.toString();

            if (prima.length() >= 5 && prima.equals(seconda)) {
                Log.i(
                        TAG,
                        "DEDUP WHISPER: "
                                + originale
                                + " -> "
                                + prima
                );
                testo = prima;
            }
        }

        // Correzioni ASR mirate SOLO nei comandi volume/luminosità.
        // Mantiene invariata tutta la parte variabile della frase
        // (es. "al 50%", "del 10%", ecc.).
        String testoPrimaCorrezioneAsr = testo;

        testo = testo
                .replaceAll(
                        "(^|\\s)a bassa(?=\\s+(?:il\\s+)?(?:volume|luminosita|luminosità)\\b)",
                        "$1abbassa"
                )
                .replaceAll(
                        "(^|\\s)bassa(?=\\s+(?:il\\s+)?(?:volume|luminosita|luminosità)\\b)",
                        "$1abbassa"
                );

        // Se la correzione ASR strutturale è intervenuta,
        // restituisce subito il comando corretto prima del Levenshtein.
        if (!testo.equals(testoPrimaCorrezioneAsr)) {
            Log.i(TAG, "NORMALIZZAZIONE WHISPER: " + testoPrimaCorrezioneAsr + " -> " + testo);
            return testo;
        }

        // NORMALIZZAZIONE CONTESTUALE HOME:
        // corregge SOLO le sei deformazioni ASR osservate
        // quando sono precedute da una struttura Home conosciuta.
        // Nessuna sostituzione globale di "arm", "om", ecc.
        String testoPrimaCorrezioneHome = testo;
        testo = testo.replaceAll(
                "(vai|via|torna|portami)\\s+(alla|all|al|la|l)\\s+(oma|om|omm|aum|arm|hom)\\b",
                "$1 alla home"
        );

        if (!testo.equals(testoPrimaCorrezioneHome)) {

            if (testo.startsWith("niente ")) {
                testo = testo.substring("niente ".length()).trim();
            } else if (testo.startsWith("nulla ")) {
                testo = testo.substring("nulla ".length()).trim();
            }

            Log.i(
                    TAG,
                    "NORMALIZZAZIONE HOME WHISPER: "
                            + testoPrimaCorrezioneHome
                            + " -> "
                            + testo
            );
            return testo;
        }

        // Non correggere frasi troppo corte:
        // riduce il rischio di falsi comandi.
        // Comandi preceduti da formule naturali in modalità conversazione.
        String[] prefissiConversazione = {
                "va bene ",
                "ok ",
                "certo ",
                "sì ",
                "si ",
                "niente ",
                "nulla "
        };

        for (String prefisso : prefissiConversazione) {
            if (testo.startsWith(prefisso) && testo.length() > prefisso.length()) {
                String comandoDopoPrefisso = testo.substring(prefisso.length()).trim();

                Log.i(TAG, "COMANDO CONVERSAZIONE: " + testo
                        + " -> " + comandoDopoPrefisso);
                return comandoDopoPrefisso;
            }
        }

        if (testo.length() < 7) {
            return originale;
        }

        String[] candidati = {
                "vai alla home",
                "torna alla home",
                "portami alla home",
                "schermata principale",

                "torna indietro",
                "vai indietro",
                "indietro",

                "abbassa volume",
                "abbassa il volume",
                "volume giu",
                "diminuisci volume",

                "alza volume",
                "alza il volume",
                "volume su",
                "aumenta volume",

                "luminosita su",
                "luminosità su",
                "aumenta luminosita",
                "aumenta luminosità",

                "luminosita giu",
                "luminosità giù",
                "abbassa luminosita",
                "abbassa luminosità"
        };

        String migliore = null;
        double migliorPunteggio = 1.0;
        double secondoPunteggio = 1.0;

        for (String candidato : candidati) {

            int distanza =
                    distanzaLevenshtein(
                            testo,
                            candidato
                    );

            int lunghezza =
                    Math.max(
                            testo.length(),
                            candidato.length()
                    );

            double punteggio =
                    lunghezza == 0
                            ? 1.0
                            : distanza / (double) lunghezza;

            if (punteggio < migliorPunteggio) {

                secondoPunteggio = migliorPunteggio;
                migliorPunteggio = punteggio;
                migliore = candidato;

            } else if (punteggio < secondoPunteggio) {

                secondoPunteggio = punteggio;
            }
        }

        // 0.31 cattura gli errori realmente osservati,
        // ma richiede anche un margine netto dal secondo candidato.
        if (migliore != null
                && migliorPunteggio <= 0.31
                && (secondoPunteggio - migliorPunteggio) >= 0.07) {

            Log.i(
                    TAG,
                    "FUZZY WHISPER: "
                            + originale
                            + " -> "
                            + migliore
                            + " score="
                            + String.format(
                                    Locale.US,
                                    "%.2f",
                                    migliorPunteggio
                            )
            );

            return migliore;
        }

        return originale;
    }


    private static int distanzaLevenshtein(
            String a,
            String b) {

        int[] precedente =
                new int[b.length() + 1];

        int[] corrente =
                new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            precedente[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {

            corrente[0] = i;

            for (int j = 1; j <= b.length(); j++) {

                int costo =
                        a.charAt(i - 1) == b.charAt(j - 1)
                                ? 0
                                : 1;

                corrente[j] =
                        Math.min(
                                Math.min(
                                        corrente[j - 1] + 1,
                                        precedente[j] + 1
                                ),
                                precedente[j - 1] + costo
                        );
            }

            int[] tmp = precedente;
            precedente = corrente;
            corrente = tmp;
        }

        return precedente[b.length()];
    }


    public static void fermaSessioneWhisperLocale() {

        LisaVoiceService servizio = instance;

        if (servizio == null) {
            return;
        }

        new android.os.Handler(
                android.os.Looper.getMainLooper()
        ).post(() -> {

            LisaSpeaker.interrompi();
            whisperLocaleAttivo = false;
            sessioneAttiva = false;

            LisaAccessibilityService.aggiornaIndicatoreAscolto(false);
            MainActivity.aggiornaStatoPulsante();

            servizio.rilasciaAudioFocus();
            servizio.voiceController.reset();

            try {
                servizio.stopForeground(true);
            } catch (Exception ignored) {
            }

            LisaAccessibilityService.nascondiTelemetria();

            LisaAccessibilityService.aggiornaVignettaSemplice(
                    "⏹ Lisa Voice Control disattivato"
            );

            servizio.stopSelf();

            Log.i(
                    TAG,
                    "SERVICE WHISPER LOCALE TERMINATO"
            );
        });
    }


    private void avviaAndroidAsrPipeProbe() {

        sessioneAttiva = false;

        LisaAccessibilityService.aggiornaIndicatoreAscolto(false);
        MainActivity.aggiornaStatoPulsante();

        fermaRecognizer();
        voiceController.reset();

        if (wakeWordManager != null) {
            wakeWordManager.stopListening();
        }

        if (androidAsrPipeProbeManager != null) {
            androidAsrPipeProbeManager.release();
        }

        aggiornaNotifica(
                "Test ASR AudioSource"
        );

        androidAsrPipeProbeManager =
                new AndroidAsrPipeProbeManager(
                        this,
                        () -> handler.post(() -> {
                            Log.i(
                                    TAG,
                                    "Android ASR Pipe probe completato"
                            );

                            try {
                                stopForeground(true);
                            } catch (Exception ignored) {}

                            stopSelf();
                        }),
                        frase -> handler.post(() -> {
                            Log.i(
                                    TAG,
                                    "PIPE SEGMENT: " + frase
                            );

                            gestisciFrase(frase);
                        })
                );

        androidAsrPipeProbeManager.start();
    }


    private void avviaAsrProbe() {

        if (!accessibilitaLisaAttiva()) {
            Log.i(TAG, "ASR probe rifiutato: Accessibility Lisa OFF");
            return;
        }

        sessioneAttiva = false;

        LisaAccessibilityService.aggiornaIndicatoreAscolto(false);
        MainActivity.aggiornaStatoPulsante();

        fermaRecognizer();
        voiceController.reset();

        if (wakeWordManager != null) {
            wakeWordManager.stopListening();
        }

        if (asrProbeManager != null) {
            asrProbeManager.release();
        }

        asrProbeManager =
                new AsrProbeManager(this);

        aggiornaNotifica(
                "Diagnostica ASR in ascolto"
        );

        asrProbeManager.start();

        Log.i(
                TAG,
                "ASR probe: " + asrProbeManager.getStatus()
        );
    }

    public static String asrProbeStatusStatic() {

        LisaVoiceService servizio = instance;

        if (servizio == null) {
            return "SERVICE_NULL";
        }

        if (servizio.asrProbeManager == null) {
            return "PROBE_NULL";
        }

        return servizio.asrProbeManager.getStatus();
    }


    private void assicuraWakeManager() {

        if (wakeWordManager != null) {
            return;
        }

        wakeWordManager =
                new WakeWordManager(
                        this,
                        new WakeWordManager.Listener() {

                            @Override
                            public void onWakeWord(String keyword) {

                                handler.post(() -> {

                                    Log.i(
                                            TAG,
                                            "WAKE ricevuta dal manager: "
                                                    + keyword
                                    );

                                    if (!accessibilitaLisaAttiva()) {
                                        return;
                                    }

                                    if (wakeWordManager != null) {
                                        wakeWordManager.stopListening();
                                    }

                                    voiceController.startSession();
                                    sessioneAttiva = true;

                                    LisaAccessibilityService.aggiornaIndicatoreAscolto(true);
                                    MainActivity.aggiornaStatoPulsante();

                                    aggiornaNotifica(
                                            "Lisa pronta ad ascoltare"
                                    );

                                    LisaSpeaker.parla(
                                            LisaVoiceService.this,
                                            "Ti ascolto",
                                            () -> programmaAscolto(100)
                                    );
                                });
                            }

                            @Override
                            public void onWakeError(Throwable error) {

                                Log.e(
                                        TAG,
                                        "Wake manager errore",
                                        error
                                );

                                handler.post(() ->
                                        aggiornaNotifica(
                                                "Errore wake word"
                                        )
                                );
                            }
                        }
                );
    }


    private void avviaWakeTest() {

        if (!accessibilitaLisaAttiva()) {
            Log.i(TAG, "Wake test rifiutato: Accessibility OFF");
            return;
        }

        sessioneAttiva = false;

        LisaAccessibilityService.aggiornaIndicatoreAscolto(false);
        MainActivity.aggiornaStatoPulsante();

        fermaRecognizer();

        voiceController.reset();

        assicuraWakeManager();

        aggiornaNotifica(
                "Lisa standby — dì Ciao Lisa"
        );

        wakeWordManager.start();

        Log.i(TAG, "Wake test avviato");
    }

    public static String wakeStatusStatic() {

        LisaVoiceService servizio = instance;

        if (servizio == null) {
            return "SERVICE_NULL";
        }

        if (servizio.wakeWordManager == null) {
            return "MANAGER_NULL";
        }

        return servizio.wakeWordManager.getStatus();
    }

    private boolean accessibilitaLisaAttiva() {
        return LisaAccessibilityService.getInstance() != null;
    }

    private void programmaAscolto(long ritardoMs) {

        // Whisper + VAD possiedono già il microfono.
        if (whisperLocaleAttivo) {
            return;
        }


        if (!sessioneAttiva
                || !accessibilitaLisaAttiva()
                || sospesoPerTts
                || LisaSpeaker.isParlando()
                || !voiceController.canStartListening()) {

            if (sessioneAttiva && !accessibilitaLisaAttiva()) {
                Log.i(TAG, "Accessibility Lisa OFF: chiudo Voice Engine");
                terminaSessione(true);
            }

            return;
        }

        if (ascoltoProgrammato != null) {
            handler.removeCallbacks(ascoltoProgrammato);
        }

        ascoltoProgrammato = () -> {

            ascoltoProgrammato = null;

            if (!sessioneAttiva
                    || !accessibilitaLisaAttiva()
                    || sospesoPerTts
                    || LisaSpeaker.isParlando()
                    || !voiceController.canStartListening()) {

                if (sessioneAttiva && !accessibilitaLisaAttiva()) {
                    terminaSessione(true);
                }

                return;
            }

            if (!ascoltoInCorso) {
                avviaAscolto();
            }
        };

        handler.postDelayed(
                ascoltoProgrammato,
                Math.max(0, ritardoMs)
        );
    }

    private void avviaAscolto() {

        if (ascoltoInCorso) {
            Log.d(TAG, "ASR start ignorato: ascolto già in corso");
            return;
        }

        if (!voiceController.canStartListening()) {
            Log.d(
                    TAG,
                    "ASR start ignorato: stato=" + voiceController.getState()
            );
            return;
        }

        if (!sessioneAttiva
                || !accessibilitaLisaAttiva()
                || sospesoPerTts
                || LisaSpeaker.isParlando()
                || !voiceController.canStartListening()) {
            return;
        }

        // Durante il solo ascolto Lisa NON richiede audio focus.
        // Musica/radio/video devono continuare normalmente.
        if (!sessioneAttiva) return;

        if (!SpeechRecognizer
                .isRecognitionAvailable(this)) {

            Log.e(TAG,
                    "Riconoscimento vocale non disponibile");

            aggiornaNotifica(
                    "Riconoscimento vocale non disponibile"
            );

            return;
        }

        try {

            if (recognizer == null) {

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {

                    recognizer =
                            SpeechRecognizer.createOnDeviceSpeechRecognizer(this);

                    Log.i(TAG, "ASR Android ON-DEVICE attivo");

                } else {

                    recognizer =
                            SpeechRecognizer.createSpeechRecognizer(this);

                    Log.i(TAG, "ASR Android standard attivo (fallback)");
                }

                recognizer.setRecognitionListener(
                        creaListener()
                );
            }

            Intent voce =
                    new Intent(
                            RecognizerIntent
                                    .ACTION_RECOGNIZE_SPEECH
                    );

            voce.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent
                            .LANGUAGE_MODEL_FREE_FORM
            );

            voce.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "it-IT"
            );

            voce.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
            );

            voce.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
            );

            ascoltoInCorso = true;
            voiceController.listeningStarted();

            aggiornaNotifica(
                    "Lisa sta ascoltando…"
            );

            recognizer.startListening(voce);

        } catch (Exception errore) {

            ascoltoInCorso = false;

            voiceController.reset();

                Log.e(
                    TAG,
                    "Errore avvio riconoscimento",
                    errore
            );

            ricreaRecognizer();
        }
    }

    private RecognitionListener creaListener() {
        return new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {

                aggiornaNotifica(
                        "Lisa sta ascoltando…"
                );
            }

            @Override
            public void onBeginningOfSpeech() {

                LisaAccessibilityService.resetDiagnosi();
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {

                voiceController.listeningFinished();

                aggiornaNotifica(
                        "Lisa sta elaborando…"
                );
            }

            @Override
            public void onError(int error) {

                ascoltoInCorso = false;
                voiceController.reset();

                if (!sessioneAttiva) {
                    return;
                }

                if (sospesoPerTts || LisaSpeaker.isParlando()) {
                    return;
                }

                Log.d(TAG, "SpeechRecognizer error=" + error);

                boolean silenzioONessunaCorrispondenza =
                        error == SpeechRecognizer.ERROR_NO_MATCH
                                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;

                if (silenzioONessunaCorrispondenza) {

                    errorSilenzioConsecutivi++;

                    Log.i(
                            TAG,
                            "ASR silenzio/no-match consecutivo="
                                    + errorSilenzioConsecutivi
                    );

                    // Un solo tentativo automatico.
                    // Al secondo errore consecutivo niente loop di click.
                    if (errorSilenzioConsecutivi >= 2) {

                        LisaAccessibilityService servizio =
                                LisaAccessibilityService.getInstance();

                        if (servizio != null) {
                            servizio.annullaInvio();
                        }

                        Log.i(
                                TAG,
                                "ASR: limite silenzio raggiunto, sessione chiusa"
                        );

                        terminaSessione(true);
                        return;
                    }

                    programmaAscolto(1200);
                    return;
                }

                errorSilenzioConsecutivi = 0;
                ricreaRecognizer();

                if (sessioneAttiva) {
                    programmaAscolto(500);
                }
            }

            @Override
            public void onResults(Bundle results) {

                ascoltoInCorso = false;
                errorSilenzioConsecutivi = 0;
                voiceController.processingStarted();

                if (!sessioneAttiva) {
                    return;
                }

                ArrayList<String> frasi =
                        results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                if (frasi == null || frasi.isEmpty()) {
                    programmaAscolto(250);
                    return;
                }

                Log.i(TAG, "ASR ipotesi: " + frasi);

                String frase = frasi.get(0).trim();

                // STOP di sicurezza:
                // SpeechRecognizer può restituire più ipotesi.
                // Se una di esse è chiaramente uno STOP, ha priorità.
                for (String candidata : frasi) {
                    if (candidata == null) continue;

                    String pulita = candidata.trim();

                    if (!pulita.isEmpty() && richiestaStop(pulita)) {
                        frase = pulita;
                        Log.i(TAG, "STOP scelto da ipotesi ASR: " + frase);
                        break;
                    }
                }

                if (frase.isEmpty()) {
                    programmaAscolto(250);
                    return;
                }

                Log.i(TAG, "Hai detto: " + frase);

                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "\uD83D\uDCDD",
                        "Sentito: " + frase
                );

                LisaAccessibilityService.aggiornaVignettaSemplice(
                        frase
                );

                gestisciFrase(frase);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        };
    }

    private void gestisciFrase(String frase) {
        String testo = frase.toLowerCase(Locale.ITALIAN).trim()
                .replaceAll("[,;:!?\\.]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // STOP ASSOLUTO: priorità massima.
        if (richiestaStop(testo)) {
            String risposta = testo.contains("buonanotte")
                    ? "Buonanotte."
                    : "Va bene.";

            sessioneAttiva = false;
            inAttesaVuoiFareAltro = false;
            voiceController.stopSession();

            LisaAccessibilityService.aggiornaIndicatoreAscolto(false);

            LisaAccessibilityService servizioStop =
                    LisaAccessibilityService.getInstance();

            if (servizioStop != null) {
                servizioStop.annullaInvio();
            }

            fermaRecognizer();
            try {
                stopForeground(true);
            } catch (Exception ignored) {
            }
            stopSelf();

            // La sessione è realmente terminata:
            // aggiorna anche il pulsante visibile.
            MainActivity.aggiornaStatoPulsante();

            LisaSpeaker.parla(
                    getApplicationContext(),
                    risposta,
                    () -> {}
            );

            Log.i(TAG, "Lisa fermata dalla voce: " + testo);
            return;
        }

        // RISPOSTA CONTESTUALE A "VUOI FARE ALTRO?"
        try {
    java.io.File f = new java.io.File(getFilesDir(), "runtime_no_debug.txt");
    java.io.FileWriter w = new java.io.FileWriter(f, true);
    w.write("testo=[" + testo
            + "] stato=" + inAttesaVuoiFareAltro
            + " riscrittura=" + (LisaAccessibilityService.getInstance() != null
            && LisaAccessibilityService.getInstance().inAttesaDiNuovoTesto())
            + "\\n");
    w.close();
} catch (Exception e) {
    Log.e(TAG, "ERRORE RUNTIME DEBUG", e);
}

if (inAttesaVuoiFareAltro) {

            boolean rispostaNo =
                    testo.equals("no")
                    || testo.equals("no grazie")
                    || testo.equals("niente")
                    || testo.equals("nulla")
                    || testo.equals("basta così")
                    || testo.equals("basta cosi");

            boolean rispostaSi =
                    testo.equals("sì")
                    || testo.equals("si")
                    || testo.equals("ok")
                    || testo.equals("va bene")
                    || testo.equals("certo");

            if (rispostaNo) {
                inAttesaVuoiFareAltro = false;

                // "No" significa no alla domanda "Vuoi fare altro?",
                // ma Lisa resta attiva in modalità conversazione.
                LisaSpeaker.parla(
                        getApplicationContext(),
                        "Va bene, sono in ascolto.",
                        () -> programmaAscolto(250)
                );
                aggiornaNotifica("Lisa pronta");
                return;
            }

            if (rispostaSi) {
                inAttesaVuoiFareAltro = false;

                LisaSpeaker.parla(
                        this,
                        "Dimmi pure.",
                        () -> programmaAscolto(250)
                );

                aggiornaNotifica("Lisa pronta");
                return;
            }

            // Se l'utente risponde direttamente con un comando
            // ("apri YouTube", "vai alla home"...), non richiediamo
            // un sì intermedio: usciamo dallo stato e processiamo normalmente.
            inAttesaVuoiFareAltro = false;
        }

        LisaAccessibilityService statoMessaggio =
                LisaAccessibilityService.getInstance();

        // Conferma messaggio WhatsApp: usa IL MICROFONO PRINCIPALE di Lisa.
        if (statoMessaggio != null
                && statoMessaggio.haMessaggioInAttesa()
                && (testo.equals("invia")
                || testo.equals("invio")
                || testo.equals("in via")
                || testo.equals("un via")
                || testo.equals("manda")
                || testo.equals("mandalo")
                || testo.equals("manda messaggio")
                || testo.equals("sì")
                || testo.equals("si")
                || testo.equals("ok")
                || testo.contains("conferma")
                || testo.contains("confermo"))) {
            android.content.Intent conferma =
                    new android.content.Intent(this, LisaCommandReceiver.class);
            conferma.setAction(LisaCommandReceiver.ACTION_LISA_COMMAND);
            conferma.putExtra("azione", "conferma_invio");
            sendBroadcast(conferma);

            // Dopo INVIA Lisa resta in ascolto:
            // chiede se vuole fare altro, invece di chiudere.
            LisaSpeaker.parla(
                LisaVoiceService.this,
                "Inviato. Vuoi fare altro?",
                () -> {
                    inAttesaVuoiFareAltro = true;
                    programmaAscolto(250);
                }
        );
            aggiornaNotifica("Lisa pronta");
            return;
        }

        if (testo.equals("annulla")
                || testo.contains("non inviare")
                || testo.contains("non mandare")) {
            android.content.Intent annulla =
                    new android.content.Intent(this, LisaCommandReceiver.class);
            annulla.setAction(LisaCommandReceiver.ACTION_LISA_COMMAND);
            annulla.putExtra("azione", "annulla_invio");
            sendBroadcast(annulla);
            aggiornaNotifica("Lisa pronta");
            programmaAscolto(650);
            return;
        }

        // "no" da solo (senza "annulla") significa: non inviare cosi',
        // ma voglio riscrivere il messaggio con un testo diverso.
        if (statoMessaggio != null
                && statoMessaggio.haMessaggioInAttesa()
                && (testo.equals("no")
                || testo.equals("no riscrivi")
                || testo.contains("riscrivi")
                || testo.contains("cambia messaggio"))) {
            LisaAccessibilityService servizioRiscrivi = LisaAccessibilityService.getInstance();
            if (servizioRiscrivi != null) {
                servizioRiscrivi.chiediNuovoTesto();
                LisaSpeaker.parla(
                        this,
                        "Cosa vuoi scrivere invece?",
                        () -> programmaAscolto(250)
                );
                aggiornaNotifica("Lisa pronta");
                return;
            }
        }

        // COMANDO EDITING CONTESTUALE:
        // valido soltanto mentre Lisa sta aspettando il nuovo testo.
        LisaAccessibilityService servizioEditing =
                LisaAccessibilityService.getInstance();

        if (servizioEditing != null
                && servizioEditing.inAttesaDiNuovoTesto()) {

            String comandoEditing =
                    testo.replaceAll("[,;:!?\\.]+", " ")
                            .replaceAll("\\s+", " ")
                            .trim();

            // "Niente" significa: non voglio più riscrivere questo messaggio.
            boolean richiestaNiente =
                    comandoEditing.equals("niente")
                    || comandoEditing.equals("nulla")
                    || comandoEditing.equals("lascia stare")
                    || comandoEditing.equals("non scrivere niente")
                    || comandoEditing.equals("non scrivere nulla")
                    || comandoEditing.equals("niente cancella tutto");

            // Punteggiatura già normalizzata:
            // "niente, torna alla home" -> "niente torna alla home"
            boolean richiestaNienteHome =
                    comandoEditing.equals("niente torna alla home")
                    || comandoEditing.equals("niente vai alla home")
                    || comandoEditing.equals("nulla torna alla home")
                    || comandoEditing.equals("nulla vai alla home");

            boolean richiestaHomeDaRiscrittura =
                    comandoEditing.equals("home")
                    || comandoEditing.equals("vai alla home")
                    || comandoEditing.equals("torna alla home")
                    || comandoEditing.equals("portami alla home")
                    || comandoEditing.equals("schermata principale")
                    || comandoEditing.equals("vai alla schermata principale")
                    || comandoEditing.equals("torna alla schermata principale");

            if (richiestaNiente || richiestaNienteHome) {

                boolean cancellato =
                        servizioEditing.cancellaTestoCorrente();

                // cancellaTestoCorrente lascia volutamente attiva
                // la riscrittura; qui invece vogliamo uscirne.
                servizioEditing.annullaInvio();

                if (richiestaNienteHome) {

                }

                String risposta = cancellato
                        ? "Va bene. Vuoi fare altro?"
                        : "Ho annullato, ma non sono riuscita a cancellare il testo. Vuoi fare altro?";

                inAttesaVuoiFareAltro = true;

                LisaSpeaker.parla(
                        this,
                        risposta,
                        () -> programmaAscolto(250)
                );

                aggiornaNotifica("Lisa pronta");
                return;
            }

            // Se durante la riscrittura l'utente decide di andare alla Home,
            // abbandoniamo prima lo stato messaggio per non trascinarlo
            // nella richiesta successiva.
            if (richiestaHomeDaRiscrittura) {

                servizioEditing.annullaInvio();

                if (eseguiLocaleRapido(comandoEditing)) {
                    inAttesaVuoiFareAltro = true;

                    LisaSpeaker.parla(
                            this,
                            "Va bene. Vuoi fare altro?",
                            () -> programmaAscolto(250)
                    );

                    aggiornaNotifica("Lisa pronta");
                    return;
                }
            }

            boolean richiestaCancella =
                    comandoEditing.equals("cancella tutto")
                    || comandoEditing.equals("cancella il testo")
                    || comandoEditing.equals("svuota il campo")
                    || comandoEditing.equals("niente cancella tutto");

            if (richiestaCancella) {
                boolean cancellato =
                        servizioEditing.cancellaTestoCorrente();

                if (cancellato) {
                    LisaSpeaker.parla(
                            this,
                            "Testo cancellato. Cosa vuoi scrivere?",
                            () -> programmaAscolto(250)
                    );
                } else {
                    LisaSpeaker.parla(
                            this,
                            "Non sono riuscita a cancellare il testo.",
                            () -> programmaAscolto(250)
                    );
                }

                aggiornaNotifica("Lisa pronta");
                return;
            }
        }

        // I COMANDI ANDROID HANNO SEMPRE PRIORITÀ SUL TESTO DA SCRIVERE.
        // Anche durante la riscrittura WhatsApp, un comando come "torna alla home"
        // deve essere eseguito dall'AccessibilityService e NON inserito nel campo testo.
        String comandoAndroid = testo;
        if (eseguiLocaleRapido(comandoAndroid)) {
            aggiornaNotifica("Lisa pronta");
            programmaAscolto(250);
            return;
        }

        // Se Lisa sta aspettando il nuovo testo per riscrivere il messaggio.
        LisaAccessibilityService servizioTestoNuovo = LisaAccessibilityService.getInstance();
        if (servizioTestoNuovo != null && servizioTestoNuovo.inAttesaDiNuovoTesto()) {
            boolean riscritto = servizioTestoNuovo.riscriviMessaggio(frase.trim());
            if (riscritto) {
                LisaSpeaker.parla(
                        this,
                        "Messaggio aggiornato. Vuoi inviarlo?",
                        () -> programmaAscolto(250)
                );
            } else {
                LisaSpeaker.parla(
                        this,
                        "Non sono riuscita a riscrivere il messaggio.",
                        () -> programmaAscolto(250)
                );
            }
            aggiornaNotifica("Lisa pronta");
            return;
        }


                frase.toLowerCase(Locale.ITALIAN).trim();


        String comando = rimuoviRichiamoLisa(frase);

        if (comando.trim().isEmpty()) {

            LisaSpeaker.parla(
                    this,
                    "Ti ascolto.",
                    () -> programmaAscolto(250)
            );

            return;
        }

        // I comandi Android semplici vengono eseguiti
        // subito sul telefono, senza aspettare LisaOS.
        if (eseguiLocaleRapido(comando)) {

            aggiornaNotifica("Lisa pronta");
            programmaAscolto(250);
            return;
        
        }

        aggiornaNotifica("Lisa sta eseguendo…");
        
        LisaAccessibilityService servizioAmbiguo = LisaAccessibilityService.getInstance();
        if (servizioAmbiguo != null && servizioAmbiguo.inAttesaSceltaContatto()) {
            String nomeRisolto = servizioAmbiguo.risolviSceltaContatto(comando);
            if (nomeRisolto != null) {
                String testoSalvato = servizioAmbiguo.getTestoMessaggioAmbiguo();
                String azioneSalvata = servizioAmbiguo.getAzioneAmbigua();
                String appSalvata = servizioAmbiguo.getAppAmbigua();
                String modalitaSalvata = servizioAmbiguo.getModalitaAmbigua();
                servizioAmbiguo.pulisciAmbiguita();

                if ("chiama".equals(azioneSalvata)) {
                    boolean riuscitoChiamata =
                            servizioAmbiguo.cercaContattoEChiama(nomeRisolto, appSalvata, modalitaSalvata);

                    if (riuscitoChiamata) {
                        LisaSpeaker.parla(this, "Va bene, chiamo " + nomeRisolto + ".",
                                () -> programmaAscolto(650));
                    } else {
                        LisaSpeaker.parla(this, "Non sono riuscita a completare la chiamata.",
                                () -> programmaAscolto(650));
                    }
                    return;
                }

                // Esegue subito, senza rimandare la frase a LisaOS:
                // evita una seconda interpretazione che puo' sbagliare.
                boolean riuscito = servizioAmbiguo.cercaContattoEInvia(
                        nomeRisolto,
                        testoSalvato,
                        appSalvata,
                        null
                );

                if (riuscito) {
                    LisaSpeaker.parla(
                        this,
                        "Messaggio pronto per " + nomeRisolto + ". Vuoi inviarlo?",
                        () -> programmaAscolto(250)
                    );
                } else {
                    LisaSpeaker.parla(
                        this,
                        "Non sono riuscita a trovare il numero di " + nomeRisolto + ".",
                        () -> programmaAscolto(250)
                    );
                }

                aggiornaNotifica("Lisa pronta");
                return;
            }
        }

        inviaALisaOS(comando);
    }

    public static boolean richiestaStopWhisper(String frase) {

        LisaVoiceService servizio = instance;

        return servizio != null
                && servizio.richiestaStop(frase);
    }

    private boolean richiestaStop(String frase) {

        if (frase == null) return false;

        // Normalizza prima eventuale punteggiatura dell'ASR:
        // "Lisa, basta" -> "lisa basta"
        String testo =
                frase.toLowerCase(Locale.ITALIAN)
                        .trim()
                        .replaceAll("[,;:!?\\.]+", " ")
                        .replaceAll("\\s+", " ");

        // "Buonanotte" da sola può essere contenuto di un messaggio.
        // Per spegnere Lisa con "buonanotte" serve il richiamo esplicito.
        boolean buonanotteEsplicita =
                testo.equals("lisa buonanotte")
                || testo.equals("ehi lisa buonanotte")
                || testo.equals("ciao lisa buonanotte");

        testo =
                rimuoviRichiamoLisa(testo)
                        .toLowerCase(Locale.ITALIAN)
                        .trim()
                        .replaceAll("\\s+", " ");

        boolean salutoStopEsplicito =
                testo.equals("ok ciao");

        // Strip prefissi conversazione:
        // "ok basta" -> "basta", "va bene chiudi" -> "chiudi".
        for (String prefisso : new String[]{
                "ok ", "va bene ", "certo ", "sì ", "si "
        }) {
            if (testo.startsWith(prefisso)
                    && testo.length() > prefisso.length()) {
                testo = testo.substring(prefisso.length()).trim();
                break;
            }
        }

        // Solo il richiamo ("Lisa", "Ehi Lisa", ecc.) non è uno STOP.
        if (testo.isEmpty()) return false;

        return testo.equals("basta")
                || testo.equals("basta così")
                || testo.equals("basta cosi")
                || testo.equals("chiudi")
                || testo.equals("chiudi tutto")
                || testo.equals("chiuditi")
                || testo.equals("chiudi lisa")
                || testo.equals("esci da lisa")
                || testo.equals("esci")
                || testo.equals("stop")
                || testo.equals("ferma")
                || testo.equals("fermati")
                || testo.equals("smetti")
                || testo.equals("smetti di ascoltare")
                || testo.equals("smetto di ascoltare")
                || testo.equals("smettere di ascoltare")
                || testo.equals("smetti di ascoltarmi")
                || testo.equals("non ascoltare più")
                || testo.equals("non ascoltare piu")
                || salutoStopEsplicito
                || testo.equals("a dopo")
                || testo.equals("ok a dopo")
                || testo.equals("va bene a dopo")
                || buonanotteEsplicita
                || testo.equals("buona notte")
                || testo.equals("ci sentiamo")
                || testo.equals("alla prossima");
    }

    private String rimuoviRichiamoLisa(
            String frase) {

        if (frase == null) {
            return "";
        }

        String originale =
                frase.trim();

        if (originale.isEmpty()) {
            return "";
        }

        String normalizzata =
                java.text.Normalizer.normalize(
                        originale.toLowerCase(Locale.ITALIAN),
                        java.text.Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalizzata.isEmpty()) {
            return "";
        }

        String[] parole =
                normalizzata.split("\\s+");

        String[] richiami = {
                "ehi lisa",
                "hey lisa",
                "ciao lisa",
                "ehi elisa",
                "hey elisa",
                "ciao elisa",
                "elisa",
                "lisa",
                "liza"
        };

        for (String richiamo : richiami) {

            if (normalizzata.equals(richiamo)) {
                return "";
            }

            if (normalizzata.startsWith(richiamo + " ")) {

                String resto =
                        normalizzata
                                .substring(richiamo.length())
                                .trim();

                Log.i(
                        TAG,
                        "RICHIAMO LISA: "
                                + originale
                                + " -> "
                                + resto
                );

                return resto;
            }
        }

        /*
         * Fuzzy SOLO sul nome Lisa/Elisa.
         *
         * Struttura attesa:
         *   aggancio deformazioneLisa resto...
         *
         * Con aggancio = e / ehi / hey / ciao.
         *
         * Caso singolo:
         *   distanza <= 1, lunghezza >= 5
         *
         * Caso con "il":
         *   distanza <= 2
         */
        if (parole.length >= 3) {

            String[] agganci = {
                    "e",
                    "ehi",
                    "hey",
                    "ciao"
            };

            boolean aggancioValido = false;

            for (String aggancio : agganci) {
                if (parole[0].equals(aggancio)) {
                    aggancioValido = true;
                    break;
                }
            }

            if (aggancioValido) {

                /*
                 * Esempi:
                 * "e ilisa vai alla home"
                 * "ehi ilisa vai alla home"
                 */
                String possibileLisa =
                        parole[1];

                int distanzaLisa =
                        distanzaLevenshtein(
                                possibileLisa,
                                "lisa"
                        );

                int distanzaElisa =
                        distanzaLevenshtein(
                                possibileLisa,
                                "elisa"
                        );

                int distanza =
                        Math.min(
                                distanzaLisa,
                                distanzaElisa
                        );

                if (possibileLisa.length() >= 5
                        && distanza <= 1) {

                    String resto =
                            String.join(
                                    " ",
                                    java.util.Arrays.copyOfRange(
                                            parole,
                                            2,
                                            parole.length
                                    )
                            ).trim();

                    if (!resto.isEmpty()) {

                        Log.i(
                                TAG,
                                "RICHIAMO LISA FUZZY: "
                                        + possibileLisa
                                        + " -> lisa"
                                        + " | resto="
                                        + resto
                                        + " | distanza="
                                        + distanza
                        );

                        return resto;
                    }
                }

                /*
                 * Esempi:
                 * "e il risa torna alla home"
                 * "ehi il lisa torna alla home"
                 */
                if (parole.length >= 4
                        && parole[1].equals("il")) {

                    String possibileLisaIl =
                            parole[2];

                    int distanzaLisaIl =
                            distanzaLevenshtein(
                                    possibileLisaIl,
                                    "lisa"
                            );

                    int distanzaElisaIl =
                            distanzaLevenshtein(
                                    possibileLisaIl,
                                    "elisa"
                            );

                    int distanzaIl =
                            Math.min(
                                    distanzaLisaIl,
                                    distanzaElisaIl
                            );

                    if (possibileLisaIl.length() >= 4
                            && distanzaIl <= 2) {

                        String resto =
                                String.join(
                                        " ",
                                        java.util.Arrays.copyOfRange(
                                                parole,
                                                3,
                                                parole.length
                                        )
                                ).trim();

                        if (!resto.isEmpty()) {

                            Log.i(
                                    TAG,
                                    "RICHIAMO LISA FUZZY (il): "
                                            + possibileLisaIl
                                            + " -> lisa"
                                            + " | resto="
                                            + resto
                                            + " | distanza="
                                            + distanzaIl
                            );

                            return resto;
                        }
                    }
                }
            }
        }

        return originale;
    }

    // ============================================================
    // P4.2B - PARSER VOCALE GENERICO UI
    // Nessun nome di pulsante/toggle e' hardcoded.
    // ============================================================

    private boolean esisteInputEsterno() {

        int[] ids =
                android.view.InputDevice.getDeviceIds();

        for (int id : ids) {

            android.view.InputDevice d =
                    android.view.InputDevice.getDevice(id);

            if (d == null
                    || !d.isExternal()
                    || d.isVirtual()) {
                continue;
            }

            int src = d.getSources();

            if ((src & android.view.InputDevice.SOURCE_MOUSE)
                        == android.view.InputDevice.SOURCE_MOUSE
                    || (src & android.view.InputDevice.SOURCE_MOUSE_RELATIVE)
                        == android.view.InputDevice.SOURCE_MOUSE_RELATIVE
                    || (src & android.view.InputDevice.SOURCE_JOYSTICK)
                        == android.view.InputDevice.SOURCE_JOYSTICK
                    || (src & android.view.InputDevice.SOURCE_GAMEPAD)
                        == android.view.InputDevice.SOURCE_GAMEPAD
                    || (src & android.view.InputDevice.SOURCE_DPAD)
                        == android.view.InputDevice.SOURCE_DPAD
                    || (src & android.view.InputDevice.SOURCE_KEYBOARD)
                        == android.view.InputDevice.SOURCE_KEYBOARD) {

                Log.i(
                        TAG,
                        "SAFETY VOCE: input esterno rilevato"
                );

                return true;
            }
        }

        return false;
    }

    private String normalizzaTargetCritico(
            String target) {

        if (target == null) return "";

        return java.text.Normalizer.normalize(
                        target,
                        java.text.Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ITALIAN)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private boolean targetCriticoVoce(
            String target) {

        String t =
                normalizzaTargetCritico(target);

        return t.equals("bluetooth")
                || t.equals("wifi");
    }

    private boolean avviaAzioneUIGenericaDaVoce(
            String testo,
            LisaAccessibilityService servizio) {

        if (testo == null || servizio == null) {
            return false;
        }

        String[][] regole = {
                {"disattiva ", "spegni"},
                {"disabilita ", "spegni"},
                {"spegni ", "spegni"},
                {"chiudi ", "spegni"},

                {"attiva ", "attiva"},
                {"accendi ", "attiva"},
                {"abilita ", "attiva"},

                {"premi ", "premi"},
                {"tocca ", "premi"},
                {"clicca ", "premi"},
                {"seleziona ", "premi"},
                {"scegli ", "premi"},

                {"apri ", "apri"}
        };

        String verbo = null;
        String target = null;

        for (String[] regola : regole) {

            String prefisso = regola[0];

            if (testo.startsWith(prefisso)
                    && testo.length() > prefisso.length()) {

                verbo = regola[1];
                target =
                        testo.substring(
                                prefisso.length()
                        ).trim();

                break;
            }
        }

        if (verbo == null
                || target == null
                || target.isEmpty()) {

            return false;
        }

        if ("spegni".equals(verbo)
                && targetCriticoVoce(target)) {

            pendingCriticalTarget = target;
            pendingCriticalTimestamp =
                    System.currentTimeMillis();

            final long richiesta =
                    pendingCriticalTimestamp;

            String targetNorm =
                    normalizzaTargetCritico(target);

            String avviso;

            if ("bluetooth".equals(targetNorm)
                    && esisteInputEsterno()) {

                avviso =
                        "Attenzione, sto per spegnere "
                                + target
                                + " e potrei interrompere un dispositivo di controllo. Confermi?";

            } else if ("wifi".equals(targetNorm)) {

                avviso =
                        "Sto per spegnere "
                                + target
                                + " e potrei interrompere la connessione di rete. Confermi?";

            } else {

                avviso =
                        "Sto per spegnere "
                                + target
                                + ". Confermi?";
            }

            ultimaAzioneUiGenerica = true;

            LisaAccessibilityService.aggiungiRigaDiagnosi(
                    "⚠️",
                    "Conferma richiesta: spegni " + target
            );

            LisaAccessibilityService.aggiornaVignettaSemplice(
                    avviso
            );

            LisaSpeaker.parla(
                    this,
                    avviso,
                    null
            );

            // Timeout reale: annulla anche in assenza di risposta.
            handler.postDelayed(() -> {

                if (pendingCriticalTarget != null
                        && pendingCriticalTimestamp == richiesta) {

                    pendingCriticalTarget = null;
                    pendingCriticalTimestamp = 0L;

                    LisaAccessibilityService.aggiungiRigaDiagnosi(
                            "ℹ️",
                            "Conferma scaduta"
                    );

                    LisaAccessibilityService.aggiornaVignettaSemplice(
                            "Richiesta scaduta."
                    );

                    LisaSpeaker.parla(
                            LisaVoiceService.this,
                            "Richiesta scaduta.",
                            null
                    );
                }

            }, 15000L);

            return true;
        }

        ultimaAzioneUiGenerica = true;

        final String verboFinale = verbo;
        final String targetFinale = target;

        Log.i(
                TAG,
                "P4 VOCE UI: "
                        + verboFinale
                        + " -> "
                        + targetFinale
        );

        servizio.eseguiAzioneUIGenerica(
                verboFinale,
                targetFinale,
                (ok, statoFinale, dettaglio) -> {

                    String risposta;

                    if (!ok) {

                        if ("target_non_trovato".equals(dettaglio)) {
                            risposta =
                                    "Non trovo "
                                            + targetFinale
                                            + " nella schermata corrente.";
                        } else {
                            risposta =
                                    "Non sono riuscita a eseguire l'azione su "
                                            + targetFinale
                                            + ".";
                        }

                    } else if (
                            "gia_nello_stato_richiesto"
                                    .equals(dettaglio)) {

                        risposta =
                                "Lo stato di "
                                        + targetFinale
                                        + " è già quello richiesto.";

                    } else if ("attiva".equals(verboFinale)) {

                        risposta =
                                "Ho attivato "
                                        + targetFinale
                                        + ".";

                    } else if ("spegni".equals(verboFinale)) {

                        risposta =
                                "Ho disattivato "
                                        + targetFinale
                                        + ".";

                    } else if ("apri".equals(verboFinale)) {

                        risposta =
                                "Ho aperto "
                                        + targetFinale
                                        + ".";

                    } else {

                        risposta =
                                "Ho premuto "
                                        + targetFinale
                                        + ".";
                    }

                    LisaAccessibilityService.aggiungiRigaDiagnosi(
                            "⚙️",
                            "UI: "
                                    + verboFinale
                                    + " "
                                    + targetFinale
                    );

                    LisaAccessibilityService.aggiungiRigaDiagnosi(
                            ok ? "✅" : "⚠️",
                            "Risultato: " + dettaglio
                    );

                    LisaAccessibilityService.aggiornaVignettaSemplice(
                            risposta
                    );

                    LisaSpeaker.parla(
                            LisaVoiceService.this,
                            risposta,
                            null
                    );
                }
        );

        return true;
    }


    private boolean eseguiLocaleRapido(String frase) {

        ultimaAzioneUiGenerica = false;

        String testo =
                frase.toLowerCase(Locale.ITALIAN).trim();

        // SAFETY VOCE - secondo turno della conferma.
        if (pendingCriticalTarget != null) {

            long elapsed =
                    System.currentTimeMillis()
                            - pendingCriticalTimestamp;

            if (elapsed > 15000L) {

                pendingCriticalTarget = null;
                pendingCriticalTimestamp = 0L;
                ultimaAzioneUiGenerica = true;

                LisaSpeaker.parla(
                        this,
                        "Richiesta scaduta.",
                        null
                );

                return true;
            }

            // STOP resta prioritario e non viene inghiottito
            // dalla conferma critica.
            if (richiestaStop(testo)) {

                pendingCriticalTarget = null;
                pendingCriticalTimestamp = 0L;

                return false;
            }

            String risposta =
                    java.text.Normalizer.normalize(
                            testo,
                            java.text.Normalizer.Form.NFD
                    )
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(java.util.Locale.ITALIAN)
                    .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            String targetSalvato =
                    pendingCriticalTarget;

            pendingCriticalTarget = null;
            pendingCriticalTimestamp = 0L;

            boolean conferma =
                    risposta.equals("si")
                    || risposta.equals("confermo")
                    || risposta.equals("ok")
                    || risposta.equals("vai")
                    || risposta.equals("procedi")
                    || risposta.equals("certo");

            ultimaAzioneUiGenerica = true;

            if (!conferma) {

                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "ℹ️",
                        "Spegnimento annullato: "
                                + targetSalvato
                );

                LisaAccessibilityService.aggiornaVignettaSemplice(
                        "Annullato."
                );

                LisaSpeaker.parla(
                        this,
                        "Annullato.",
                        null
                );

                return true;
            }

            LisaAccessibilityService servizioCritico =
                    LisaAccessibilityService.getInstance();

            if (servizioCritico == null) {

                LisaSpeaker.parla(
                        this,
                        "Servizio di accessibilità non disponibile.",
                        null
                );

                return true;
            }

            servizioCritico.eseguiAzioneUIGenerica(
                    "spegni",
                    targetSalvato,
                    (ok, statoFinale, dettaglio) -> {

                        String r =
                                ok
                                        ? "Ho disattivato "
                                                + targetSalvato
                                                + "."
                                        : "Non sono riuscita a spegnere "
                                                + targetSalvato
                                                + ".";

                        LisaAccessibilityService.aggiungiRigaDiagnosi(
                                ok ? "✅" : "⚠️",
                                "Conferma critica: "
                                        + dettaglio
                        );

                        LisaAccessibilityService.aggiornaVignettaSemplice(
                                r
                        );

                        LisaSpeaker.parla(
                                LisaVoiceService.this,
                                r,
                                null
                        );
                    }
            );

            return true;
        }

        // NORMALIZZAZIONE MIRATA WHISPER:
        // corregge alcune forme ricorrenti prima dei match locali.
        testo = testo
                .replaceAll("\\bvia\\s+(?:alla|la)\\s+home\\b",
                        "vai alla home")
                .replaceAll("\\bapre\\s+il\\s+",
                        "apri ")
                .replaceAll("\\baprire\\s+",
                        "apri ");

        // ====================================================
        // COMANDI DETERMINISTICI LOCALI — NO LisaOS
        // ====================================================

        // ORA
        if (testo.equals("che ore sono")
                || testo.equals("che ora e")
                || testo.equals("che ora è")
                || testo.equals("che ora sono")
                || testo.equals("che orizzono")
                || testo.equals("orizzono")
                || testo.equals("ore sono")) {

            java.util.Calendar c =
                    java.util.Calendar.getInstance();

            int h = c.get(
                    java.util.Calendar.HOUR_OF_DAY
            );

            int m = c.get(
                    java.util.Calendar.MINUTE
            );

            String risposta;

            if (h == 1) {
                risposta = m == 0
                        ? "È l'una"
                        : "È l'una e " + m;
            } else {
                risposta = m == 0
                        ? "Sono le " + h
                        : "Sono le " + h + " e " + m;
            }

            rispostaComandoLocale = risposta;

            LisaSpeaker.parla(
                    this,
                    risposta,
                    null
            );

            return true;
        }

        // GIORNO DELLA SETTIMANA
        if (testo.equals("che giorno e")
                || testo.equals("che giorno è")
                || testo.equals("che giorno siamo")
                || testo.equals("che giorno e oggi")
                || testo.equals("che giorno è oggi")
                || testo.equals("giorno e")
                || testo.equals("giorno è")
                || testo.equals("giorno e oggi")
                || testo.equals("giorno è oggi")
                || testo.equals("e giorno e")
                || testo.equals("e giorno è")) {

            String giorno =
                    new java.text.SimpleDateFormat(
                            "EEEE",
                            java.util.Locale.ITALIAN
                    ).format(
                            new java.util.Date()
                    );

            String risposta =
                    "Oggi è " + giorno;

            rispostaComandoLocale = risposta;

            LisaSpeaker.parla(
                    this,
                    risposta,
                    null
            );

            return true;
        }

        // DATA
        if (testo.equals("che data e")
                || testo.equals("che data è")
                || testo.equals("che data abbiamo")
                || testo.equals("quanti ne abbiamo")
                || testo.equals("che giorno del mese e")
                || testo.equals("che giorno del mese è")
                || testo.equals("data e")
                || testo.equals("data è")) {

            String data =
                    new java.text.SimpleDateFormat(
                            "d MMMM yyyy",
                            java.util.Locale.ITALIAN
                    ).format(
                            new java.util.Date()
                    );

            String risposta =
                    "Oggi è il " + data;

            rispostaComandoLocale = risposta;

            LisaSpeaker.parla(
                    this,
                    risposta,
                    null
            );

            return true;
        }

        // BATTERIA
        if (testo.equals("batteria")
                || testo.equals("che batteria ho")
                || testo.equals("quanto e carica la batteria")
                || testo.equals("quanto è carica la batteria")
                || testo.equals("carica batteria")) {

            android.os.BatteryManager bm =
                    (android.os.BatteryManager)
                            getSystemService(
                                    android.content.Context.BATTERY_SERVICE
                            );

            int percentualeBatteria =
                    bm != null
                            ? bm.getIntProperty(
                                    android.os.BatteryManager
                                            .BATTERY_PROPERTY_CAPACITY
                            )
                            : -1;

            String risposta =
                    percentualeBatteria >= 0
                            ? "La batteria è al "
                                    + percentualeBatteria
                                    + " per cento"
                            : "Batteria non disponibile";

            rispostaComandoLocale = risposta;

            LisaSpeaker.parla(
                    this,
                    risposta,
                    null
            );

            return true;
        }

        // WI-FI
        // Nota: nel percorso Whisper "wi-fi" viene normalizzato in "wi fi".
        if (testo.equals("stato wifi")
                || testo.equals("stato wi fi")
                || testo.equals("wifi attivo")
                || testo.equals("wi fi attivo")
                || testo.equals("sono connesso al wifi")
                || testo.equals("sono connesso al wi fi")) {

            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager)
                            getSystemService(
                                    android.content.Context.CONNECTIVITY_SERVICE
                            );

            android.net.Network rete =
                    cm != null
                            ? cm.getActiveNetwork()
                            : null;

            android.net.NetworkCapabilities caps =
                    cm != null && rete != null
                            ? cm.getNetworkCapabilities(rete)
                            : null;

            boolean connessoWifi =
                    caps != null
                            && caps.hasTransport(
                                    android.net.NetworkCapabilities
                                            .TRANSPORT_WIFI
                            );

            String risposta =
                    connessoWifi
                            ? "Wi-Fi connesso"
                            : "Wi-Fi non connesso";

            rispostaComandoLocale = risposta;

            LisaSpeaker.parla(
                    this,
                    risposta,
                    null
            );

            return true;
        }

        // BLUETOOTH
        if (testo.equals("stato bluetooth")
                || testo.equals("bluetooth attivo")
                || testo.equals("bluetooth acceso")
                || testo.equals("bluetooth spento")
                || testo.equals("bluetooth")) {

            String risposta;

            boolean permessoBluetooth =
                    android.os.Build.VERSION.SDK_INT
                            < android.os.Build.VERSION_CODES.S
                    || checkSelfPermission(
                            android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED;

            if (!permessoBluetooth) {

                risposta =
                        "Non posso leggere lo stato Bluetooth senza autorizzazione";

            } else {

                android.bluetooth.BluetoothManager manager =
                        (android.bluetooth.BluetoothManager)
                                getSystemService(
                                        android.content.Context.BLUETOOTH_SERVICE
                                );

                android.bluetooth.BluetoothAdapter adapter =
                        manager != null
                                ? manager.getAdapter()
                                : null;

                if (adapter == null) {

                    risposta =
                            "Bluetooth non disponibile";

                } else {

                    risposta =
                            adapter.isEnabled()
                                    ? "Bluetooth attivo"
                                    : "Bluetooth spento";
                }
            }

            rispostaComandoLocale = risposta;

            LisaSpeaker.parla(
                    this,
                    risposta,
                    null
            );

            return true;
        }

        // TORCIA
        if (testo.equals("torcia")
                || testo.equals("apri torcia")
                || testo.equals("accendi torcia")
                || testo.equals("attiva torcia")
                || testo.equals("torcia accesa")
                || testo.equals("spegni torcia")
                || testo.equals("disattiva torcia")
                || testo.equals("torcia spenta")) {

            boolean accendi =
                    testo.equals("torcia")
                            || testo.equals("apri torcia")
                            || testo.equals("accendi torcia")
                            || testo.equals("attiva torcia")
                            || testo.equals("torcia accesa");

            String risposta;

            try {
                android.hardware.camera2.CameraManager cameraManager =
                        (android.hardware.camera2.CameraManager)
                                getSystemService(
                                        android.content.Context.CAMERA_SERVICE
                                );

                String cameraTorcia = null;

                if (cameraManager != null) {

                    for (String cameraId :
                            cameraManager.getCameraIdList()) {

                        android.hardware.camera2.CameraCharacteristics caratteristiche =
                                cameraManager.getCameraCharacteristics(
                                        cameraId
                                );

                        Boolean flashDisponibile =
                                caratteristiche.get(
                                        android.hardware.camera2.CameraCharacteristics
                                                .FLASH_INFO_AVAILABLE
                                );

                        Integer posizione =
                                caratteristiche.get(
                                        android.hardware.camera2.CameraCharacteristics
                                                .LENS_FACING
                                );

                        if (Boolean.TRUE.equals(flashDisponibile)
                                && posizione != null
                                && posizione ==
                                android.hardware.camera2.CameraCharacteristics
                                        .LENS_FACING_BACK) {

                            cameraTorcia = cameraId;
                            break;
                        }
                    }
                }

                if (cameraTorcia == null) {

                    risposta = "Torcia non disponibile";

                } else {

                    cameraManager.setTorchMode(
                            cameraTorcia,
                            accendi
                    );

                    risposta =
                            accendi
                                    ? "Torcia accesa"
                                    : "Torcia spenta";
                }

            } catch (SecurityException e) {

                Log.w(
                        TAG,
                        "Permesso fotocamera non disponibile per la torcia",
                        e
                );

                risposta =
                        "Non posso usare la torcia senza autorizzazione fotocamera";

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Torcia non disponibile",
                        e
                );

                risposta =
                        "Torcia non disponibile";
            }

            rispostaComandoLocale = risposta;

            LisaSpeaker.parla(
                    this,
                    risposta,
                    null
            );

            return true;
        }

        // ====================================================
        // FINE COMANDI DETERMINISTICI LOCALI
        // ====================================================

        // PERCENTUALI / MASSIMO / MINIMO -> esecuzione locale, senza cervello.
        java.util.regex.Matcher percentuale =
                java.util.regex.Pattern.compile(
                        "(?i)^(?:lisa[,;:]?\\s+)?" +
                        "(alza|aumenta|abbassa|diminuisci|imposta)\\s+" +
                        "(?:il\\s+)?" +
                        "(volume|luminosita|luminosità)\\s+" +
                        "(del|di|dal|al|a)\\s*" +
                        "(massimo|minimo|\\d{1,3})\\s*(?:%|per\\s+cento)?\\.?$"
                ).matcher(testo);

        if (percentuale.matches()) {
            String verbo = percentuale.group(1);
            String tipo = percentuale.group(2);
            String modo = percentuale.group(3);
            String valoreTesto = percentuale.group(4);

            String operazione;

            if ("massimo".equalsIgnoreCase(valoreTesto)) {
                valoreTesto = "100";
                operazione = "imposta";
            } else if ("minimo".equalsIgnoreCase(valoreTesto)) {
                valoreTesto = "0";
                operazione = "imposta";
            } else if ("al".equalsIgnoreCase(modo)
                    || "a".equalsIgnoreCase(modo)) {
                operazione = "imposta";
            } else if ("alza".equalsIgnoreCase(verbo)
                    || "aumenta".equalsIgnoreCase(verbo)) {
                operazione = "aumenta";
            } else {
                operazione = "diminuisci";
            }

            boolean riuscito =
                    SystemController.regola(
                            this,
                            tipo,
                            operazione,
                            valoreTesto
                    );

            if (riuscito) {
                return true;
            }
        }

        if (testo.equals("volume su") || testo.equals("alza volume") || testo.equals("alza il volume") || testo.equals("aumenta volume")) { android.media.AudioManager am=(android.media.AudioManager)getSystemService(android.content.Context.AUDIO_SERVICE); if(am!=null){ am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.ADJUST_RAISE,android.media.AudioManager.FLAG_SHOW_UI); return true; } }
        if (testo.equals("volume giu") || testo.equals("volume giù") || testo.equals("abbassa volume") || testo.equals("abbassa il volume") || testo.equals("diminuisci volume")) { android.media.AudioManager am=(android.media.AudioManager)getSystemService(android.content.Context.AUDIO_SERVICE); if(am!=null){ am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.ADJUST_LOWER,android.media.AudioManager.FLAG_SHOW_UI); return true; } }
        if (testo.equals("luminosita su") || testo.equals("luminosità su") || testo.equals("aumenta luminosita") || testo.equals("aumenta luminosità")) { if(android.provider.Settings.System.canWrite(this)){ int v=android.provider.Settings.System.getInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,128); android.provider.Settings.System.putInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL); android.provider.Settings.System.putInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,Math.min(255,v+25)); return true; } }
        if (testo.equals("luminosita giu") || testo.equals("luminosità giù") || testo.equals("abbassa luminosita") || testo.equals("abbassa luminosità")) { if(android.provider.Settings.System.canWrite(this)){ int v=android.provider.Settings.System.getInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,128); android.provider.Settings.System.putInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL); android.provider.Settings.System.putInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,Math.max(1,v-25)); return true; } }
        if (testo.equals("impostazioni") || testo.equals("apri impostazioni")) { Intent i=new Intent(android.provider.Settings.ACTION_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; }

        String[] verbi = {
                "apri ",
                "avvia ",
                "lancia ",
                "aprimi "
        };

        for (String verbo : verbi) {

            if (testo.startsWith(verbo)) {

                String nomeApp =
                        frase.substring(verbo.length()).trim();

                if (!nomeApp.isEmpty()) {

                    boolean aperta =
                            AppFinder.apriAppPerNome(
                                    this,
                                    nomeApp
                            );

                    if (aperta) {
                        Log.i(
                                TAG,
                                "App aperta localmente: " + nomeApp
                        );
                        return true;
                    }
                }
            }
        }

        // FAST INTENT ANDROID:
        // modi naturali di chiedere di entrare/usare un'app.
        // Il nome viene poi risolto universalmente da AppFinder.
        String[] prefissiAperturaNaturale = {
                "fammi usare ",
                "voglio usare ",
                "vorrei usare ",
                "portami su ",
                "portami a ",
                "vai su ",
                "vai a ",
                "entra in ",
                "entra su ",
                "fammi vedere ",
                "mostrami "
        };

        for (String prefisso : prefissiAperturaNaturale) {

            if (testo.startsWith(prefisso)) {

                String nomeApp =
                        frase.substring(prefisso.length()).trim();

                if (!nomeApp.isEmpty()
                        && AppFinder.apriAppPerNome(this, nomeApp)) {

                    Log.i(TAG,
                            "FAST app naturale: "
                                    + frase
                                    + " -> "
                                    + nomeApp);

                    return true;
                }
            }
        }

        LisaAccessibilityService servizio =
                LisaAccessibilityService.getInstance();

        if (servizio == null) {
            return false;
        }

        // P4.2B: prima il nuovo resolver UI universale.
        // I vecchi click naturali restano sotto come fallback.
        if (avviaAzioneUIGenericaDaVoce(
                testo,
                servizio)) {

            return true;
        }

        // FAST INTENT ACCESSIBILITY:
        // agisce direttamente sugli elementi visibili.
        String[] verbiClickNaturali = {
                "clicca ",
                "tocca ",
                "premi ",
                "seleziona ",
                "scegli ",
                "attiva ",
                "disattiva ",
                "abilita ",
                "disabilita "
        };

        for (String verboClick : verbiClickNaturali) {

            if (testo.startsWith(verboClick)) {

                String bersaglio =
                        frase.substring(verboClick.length()).trim();

                if (!bersaglio.isEmpty()
                        && servizio.cliccaTesto(bersaglio)) {

                    Log.i(TAG,
                            "FAST click naturale: "
                                    + bersaglio);

                    return true;
                }
            }
        }

        // SCROLL VOCALE VERTICALE
        if (testo.equals("scorri in basso")
                || testo.equals("scorri giù")
                || testo.equals("scorri giu")
                || testo.equals("vai giù")
                || testo.equals("vai giu")) {
            return servizio.scorriAvanti();
        }

        if (testo.equals("scorri in alto")
                || testo.equals("scorri su")
                || testo.equals("vai su")) {
            return servizio.scorriIndietro();
        }

        if (testo.equals("recenti") || testo.equals("app recenti") || testo.equals("mostra recenti")) return servizio.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS);
        if (testo.equals("notifiche") || testo.equals("apri notifiche") || testo.equals("mostra notifiche")) return servizio.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        if (testo.equals("blocca schermo") || testo.equals("blocca lo schermo") || testo.equals("spegni schermo")) return servizio.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        if (testo.equals("screenshot") || testo.equals("fai screenshot") || testo.equals("fai uno screenshot")) return servizio.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);

        if (testo.equals("home")
                || testo.equals("vai alla home")
                || testo.equals("torna alla home")
                || testo.equals("portami alla home")
                || testo.equals("schermata principale")
                || testo.equals("vai alla schermata principale")
                || testo.equals("torna alla schermata principale")) {

            return LisaHomeController.vaiAllaHomePrincipale();
        }

        if (testo.equals("indietro")
                || testo.equals("torna indietro")
                || testo.equals("vai indietro")) {

            return servizio.performGlobalAction(
                    android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_BACK
            );
        }

        return false;
    }

    private void inviaALisaOS(
            String frase) {

        new Thread(() -> {

            HttpURLConnection connessione = null;

            try {

                URL url =
                        new URL(API_LISA);

                connessione =
                        (HttpURLConnection)
                                url.openConnection();

                connessione.setRequestMethod(
                        "POST"
                );

                connessione.setConnectTimeout(
                        5000
                );

                connessione.setReadTimeout(
                        60000
                );

                connessione.setDoOutput(true);

                connessione.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                JSONObject richiesta =
                        new JSONObject();

                richiesta.put(
                        "testo",
                        "Lisa " + frase
                );

                // CONTEXT ENGINE V1:
                // invia a LisaOS lo stato corrente rilevato
                // dall'AccessibilityService.
                LisaAccessibilityService servizioContesto =
                        LisaAccessibilityService.getInstance();

                if (servizioContesto != null) {
                    JSONObject contesto = new JSONObject();

                    contesto.put(
                            "pacchetto",
                            servizioContesto.getCurrentPackageName()
                    );
                    contesto.put(
                            "classe",
                            servizioContesto.getCurrentClassName()
                    );
                    contesto.put(
                            "window_id",
                            servizioContesto.getCurrentWindowId()
                    );
                    contesto.put(
                            "focus_input",
                            servizioContesto.hasCurrentInputFocus()
                    );
                    contesto.put(
                            "editabile",
                            servizioContesto.isCurrentEditable()
                    );
                    contesto.put(
                            "timestamp",
                            servizioContesto.getCurrentContextTimestamp()
                    );

                    java.util.ArrayList<String> appVisibili =
                            servizioContesto.getVisiblePackageNames();

                    org.json.JSONArray visibiliJson =
                            new org.json.JSONArray();

                    for (String pkg : appVisibili) {
                        visibiliJson.put(pkg);
                    }

                    contesto.put("app_visibili", visibiliJson);

                    richiesta.put("contesto", contesto);

                    Log.i(
                            TAG,
                            "CTX_HTTP app="
                                    + servizioContesto.getCurrentPackageName()
                                    + " visible="
                                    + appVisibili
                                    + " editable="
                                    + servizioContesto.isCurrentEditable()
                    );
                }

                byte[] dati =
                        richiesta
                                .toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                try (OutputStream uscita =
                             connessione
                                     .getOutputStream()) {

                    uscita.write(dati);
                }

                int codice =
                        connessione
                                .getResponseCode();

                InputStream flusso =
                        codice >= 200
                                && codice < 300
                                ? connessione
                                    .getInputStream()
                                : connessione
                                    .getErrorStream();

                String risposta =
                        leggiFlusso(flusso);

                if (!sessioneAttiva) {
                    Log.i(TAG, "LisaOS: risposta scartata dopo STOP");
                    return;
                }

                if (codice < 200 || codice >= 300) {
                    Log.e(
                            TAG,
                            "LisaOS HTTP "
                                    + codice
                                    + ": "
                                    + risposta
                    );

                    LisaAccessibilityService.aggiungiRigaDiagnosi(
                            "⚠️",
                            "Non ho capito"
                    );

                    LisaAccessibilityService.aggiornaVignettaSemplice(
                            "⚠️ Non ho capito"
                    );

                    final int codiceErrore = codice;

                    handler.post(() -> {
                        if (!sessioneAttiva) {
                            return;
                        }

                        LisaSpeaker.parla(
                                LisaVoiceService.this,
                                "Errore LisaOS HTTP " + codiceErrore + ".",
                                () -> programmaAscolto(900)
                        );
                    });

                    return;
                }

                JSONObject rispostaJson =
                        new JSONObject(risposta);

                String azioneAndroid =
                        inoltraRispostaAndroid(
                                rispostaJson
                        );

                Log.i(
                        TAG,
                        "Azione Android ricevuta: "
                                + azioneAndroid
                );

                // Se non c'e' un'azione Android da eseguire
                // (es. Lisa sta facendo una domanda), la risposta
                // testuale va comunque pronunciata.
                if (azioneAndroid == null
        || azioneAndroid.isEmpty()
        || "presenza".equals(azioneAndroid)) {
                    String messaggioVocale =
                            rispostaJson.optString("risposta", "").trim();

                    if (!messaggioVocale.isEmpty()) {
                        handler.post(() -> {
                            LisaSpeaker.parla(
                                    LisaVoiceService.this,
                                    messaggioVocale,
                                    () -> programmaAscolto(250)
                            );
                        });
                    }
                }


                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "🌐",
                        "LisaOS: HTTP " + codice
                );

                String rispostaTesto =
                        rispostaJson.optString("risposta", "").trim();

                boolean rispostaUtile =
                        !rispostaTesto.isEmpty()
                        || (azioneAndroid != null
                        && !azioneAndroid.trim().isEmpty());

                if (!rispostaUtile) {

                    LisaAccessibilityService.aggiungiRigaDiagnosi(
                            "⚠️",
                            "Non ho capito"
                    );

                    handler.post(() -> {
                        if (!sessioneAttiva) {
                            return;
                        }

                        LisaAccessibilityService
                                .aggiornaVignettaSemplice(
                                        "⚠️ Non ho capito"
                                );
                    });

                    Log.i(
                            TAG,
                            "LisaOS: risposta non utile, "
                                    + "mostro Non ho capito"
                    );

                } else {

                    LisaAccessibilityService.aggiungiRigaDiagnosi(
                            "✅",
                            "Risultato: risposta ricevuta"
                    );
                }

                Log.i(
                        TAG,
                        "LisaOS HTTP "
                                + codice
                                + ": "
                                + risposta
                );

                handler.post(() -> {

                    if (!sessioneAttiva) {
                        return;
                    }

                    aggiornaNotifica(
                            "Lisa pronta"
                    );

                    /*
                     * Piccola pausa:
                     * lascia terminare eventuale
                     * risposta vocale/azione Android.
                     */
                    programmaAscolto(900);
                });

            } catch (Exception errore) {

                Log.e(
                        TAG,
                        "Errore comunicazione/elaborazione LisaOS",
                        errore
                );

                if (!sessioneAttiva) {
                    Log.i(TAG, "LisaOS: errore/timeout scartato dopo STOP");
                    return;
                }

                LisaAccessibilityService.aggiungiRigaDiagnosi(
                        "⚠️",
                        "Non ho capito"
                );

                handler.post(() -> {
                    if (!sessioneAttiva) {
                        return;
                    }

                    LisaAccessibilityService
                            .aggiornaVignettaSemplice(
                                    "⚠️ Non ho capito"
                            );
                });

                handler.post(() -> {

                    if (!sessioneAttiva) {
                        return;
                    }

                    aggiornaNotifica(
                            "Lisa attiva - LisaOS non raggiungibile"
                    );

                    programmaAscolto(900);
                });

            } finally {

                if (connessione != null) {
                    connessione.disconnect();
                }
            }

        }, "Lisa-Router").start();
    }

    private String inoltraRispostaAndroid(JSONObject risposta) {

        if (risposta == null) return "";

        String azione =
                risposta.optString("azione", "").trim();

        JSONObject comando =
                risposta.optJSONObject("comando");

        JSONObject parametri = null;

        if (comando != null) {

            if (azione.isEmpty()) {
                azione =
                        comando.optString(
                                "azione",
                                ""
                        ).trim();
            }

            parametri =
                    comando.optJSONObject("parametri");

            if (parametri == null) {
                parametri = comando;
            }
        }

        if (parametri == null) {
            parametri = risposta;
        }

        if (azione.isEmpty()
                || "ai".equals(azione)
                || "impara".equals(azione)) {

            return "";
        }

        Intent intent =
                new Intent(
                        this,
                        LisaCommandReceiver.class
                );

        intent.setAction(
                LisaCommandReceiver.ACTION_LISA_COMMAND
        );

        intent.putExtra("azione", azione);

        java.util.Iterator<String> chiavi =
                parametri.keys();

        while (chiavi.hasNext()) {

            String chiave = chiavi.next();
            Object valore = parametri.opt(chiave);

            if (valore != null
                    && valore != JSONObject.NULL
                    && !(valore instanceof JSONObject)) {

                intent.putExtra(
                        chiave,
                        String.valueOf(valore)
                );
            }
        }

        // Alias usato dal Receiver per aprire app
        if ("apri_app".equals(azione)) {

            String nome =
                    parametri.optString(
                            "nome_app",
                            ""
                    );

            if (nome.isEmpty()) {
                nome =
                        parametri.optString(
                                "nome",
                                ""
                        );
            }

            if (!nome.isEmpty()) {
                intent.putExtra(
                        "nome_app",
                        nome
                );
            }
        }

        // Alias usato dal Receiver per WhatsApp
        if ("messaggio".equals(azione)) {

            String testoMessaggio =
                    parametri.optString(
                            "testo_messaggio",
                            ""
                    );

            if (testoMessaggio.isEmpty()) {
                testoMessaggio =
                        parametri.optString(
                                "testo",
                                ""
                        );
            }

            if (testoMessaggio.isEmpty()) {
                testoMessaggio =
                        parametri.optString(
                                "messaggio",
                                ""
                        );
            }

            if (!testoMessaggio.isEmpty()) {
                intent.putExtra(
                        "testo_messaggio",
                        testoMessaggio
                );
            }
        }

        sendBroadcast(intent);

        Log.i(
                TAG,
                "Comando inoltrato ad Android: "
                        + azione
        );

        return azione;
    }

    private String leggiFlusso(
            InputStream flusso)
            throws Exception {

        if (flusso == null) {
            return "";
        }

        StringBuilder risultato =
                new StringBuilder();

        try (BufferedReader lettore =
                     new BufferedReader(
                             new InputStreamReader(
                                     flusso,
                                     StandardCharsets.UTF_8
                             )
                     )) {

            String riga;

            while ((riga =
                    lettore.readLine())
                    != null) {

                risultato.append(riga);
            }
        }

        return risultato.toString();
    }

    private void ricreaRecognizer() {

        if (recognizer != null) {

            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }

            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }

            recognizer = null;
        }

        ascoltoInCorso = false;
    }

    private void fermaRecognizer() {

        handler.removeCallbacksAndMessages(
                null
        );

        rilasciaAudioFocus();

        ricreaRecognizer();
    }

    public static void fermaLisaDaPulsante() {
        LisaVoiceService servizio = instance;
        if (servizio != null) {
            servizio.handler.post(() -> servizio.terminaSessione(false));
        }
    }

    private void terminaSessione(
            boolean silenzioso) {

        LisaSpeaker.interrompi();
        sessioneAttiva = false;
        voiceController.stopSession();

        LisaAccessibilityService.aggiornaIndicatoreAscolto(false);

        // Aggiorna subito la MainActivity:
        // da "Ferma Lisa" a "Attiva Lisa".
        MainActivity.aggiornaStatoPulsante();

        fermaRecognizer();

        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }

        stopSelf();

        Log.i(
                TAG,
                "Lisa in pausa"
        );
    }

    private void creaCanaleNotifica() {

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            NotificationChannel canale =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Lisa Voice",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            manager.createNotificationChannel(
                    canale
            );
        }
    }

    private Notification creaNotifica(
            String testo) {

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                );

        return new Notification.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle("Lisa")
                .setContentText(testo)
                .setSmallIcon(
                        android.R.drawable
                                .ic_btn_speak_now
                )
                .setOngoing(true)
                .setContentIntent(
                        pendingIntent
                )
                .build();
    }

    private void aggiornaNotifica(
            String testo) {

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        manager.notify(
                1,
                creaNotifica(testo)
        );
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }


        sessioneAttiva = false;

        LisaAccessibilityService.aggiornaIndicatoreAscolto(false);

        // Ultima sincronizzazione UI quando il Service muore.
        MainActivity.aggiornaStatoPulsante();

        fermaRecognizer();

        if (wakeWordManager != null) {
            wakeWordManager.release();
            wakeWordManager = null;
        }

        // Il Service è realmente terminato: stato vocale nuovamente pulito.
        voiceController.reset();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent) {

        return null;
    }
}
