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

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private final VoiceController voiceController = VoiceController.getInstance();
    private boolean ascoltoInCorso = false;
    private int errorSilenzioConsecutivi = 0;
    private boolean inAttesaVuoiFareAltro = false;
    private Runnable ascoltoProgrammato;

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

            servizio.handler
                    .removeCallbacksAndMessages(null);

            if (servizio.recognizer != null) {

                try {
                    servizio.recognizer.cancel();
                } catch (Exception ignored) {
                }
            }

            servizio.ascoltoInCorso = false;

            servizio.rilasciaAudioFocus();

            Log.d(TAG,
                    "ASR sospeso: Lisa sta parlando");
        });
    }

    public static void riprendiDopoTts() {

        LisaVoiceService servizio = instance;

        if (servizio == null) return;

        servizio.handler.postDelayed(() -> {

            servizio.sospesoPerTts = false;

            if (sessioneAttiva) {
                servizio.programmaAscolto(250);
            }

        }, 300);
    }

    private android.media.AudioManager audioManager;
    private android.media.AudioFocusRequest focusRequest;

    private void chiediAudioFocus() {
        if (audioManager == null) {
            audioManager = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void rilasciaAudioFocus() {
        if (audioManager == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
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
            sessioneAttiva = false;
            fermaRecognizer();
            voiceController.reset();

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

            boolean eseguito =
                    servizio.eseguiLocaleRapido(comando);

            Log.i(
                    TAG,
                    "WHISPER -> COMANDO LOCALE: "
                            + comando
                            + " | eseguito="
                            + eseguito
            );
        });
    }

    public static void fermaSessioneWhisperLocale() {

        LisaVoiceService servizio = instance;

        if (servizio == null) {
            return;
        }

        new android.os.Handler(
                android.os.Looper.getMainLooper()
        ).post(() -> {

            sessioneAttiva = false;
            servizio.voiceController.reset();

            try {
                servizio.stopForeground(true);
            } catch (Exception ignored) {
            }

            servizio.stopSelf();

            Log.i(
                    TAG,
                    "SERVICE WHISPER LOCALE TERMINATO"
            );
        });
    }


    private void avviaAndroidAsrPipeProbe() {

        sessioneAttiva = false;
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

        if (!sessioneAttiva
                || !accessibilitaLisaAttiva()
                || sospesoPerTts
                || LisaSpeaker.isParlando()
                || voiceController.is(VoiceController.State.STOPPING)) {

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
                    || voiceController.is(VoiceController.State.STOPPING)) {

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
        if (!sessioneAttiva
                || !accessibilitaLisaAttiva()
                || sospesoPerTts
                || LisaSpeaker.isParlando()
                || voiceController.is(VoiceController.State.STOPPING)) {
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

            Log.e(
                    TAG,
                    "Errore avvio riconoscimento",
                    errore
            );

            ricreaRecognizer();

            programmaAscolto(800);
        }
    }

    private RecognitionListener creaListener() {
        return new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                aggiornaNotifica("Lisa sta ascoltando…");
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                aggiornaNotifica("Lisa sta elaborando…");
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
        String testo = frase.toLowerCase(Locale.ITALIAN).trim();

        // STOP ASSOLUTO: priorità massima.
        if (richiestaStop(testo)) {
            String risposta = testo.contains("buonanotte")
                    ? "Buonanotte."
                    : "Va bene.";

            sessioneAttiva = false;
            inAttesaVuoiFareAltro = false;
            voiceController.stopSession();

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

                terminaSessione(false);

                LisaSpeaker.parla(
                        getApplicationContext(),
                        "Va bene.",
                        () -> {}
                );

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

            // Fine procedura WhatsApp:
            // dopo INVIA Lisa torna inattiva.
            terminaSessione(false);
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
                || testo.equals("no, riscrivi")
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
                    eseguiLocaleRapido("torna alla home");
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

    private boolean richiestaStop(String frase) {

        if (frase == null) return false;

        // Normalizza prima eventuale punteggiatura dell'ASR:
        // "Lisa, basta" -> "lisa basta"
        String testo =
                frase.toLowerCase(Locale.ITALIAN)
                        .trim()
                        .replaceAll("[,;:!?\\.]+", " ")
                        .replaceAll("\\s+", " ");

        // Il richiamo a Lisa NON deve impedire lo STOP:
        // "Lisa basta" / "Ehi Lisa smetti di ascoltare"
        // diventano rispettivamente "basta" / "smetti di ascoltare".
        testo =
                rimuoviRichiamoLisa(testo)
                        .toLowerCase(Locale.ITALIAN)
                        .trim()
                        .replaceAll("\\s+", " ");

        // Solo il richiamo ("Lisa", "Ehi Lisa", ecc.) non è uno STOP.
        if (testo.isEmpty()) return false;

        return testo.equals("basta")
                || testo.equals("stop")
                || testo.equals("fermati")
                || testo.equals("smetti")
                || testo.equals("smetti di ascoltare")
                || testo.equals("smetto di ascoltare")
                || testo.equals("smettere di ascoltare")
                || testo.equals("smetti di ascoltarmi")
                || testo.equals("non ascoltare più")
                || testo.equals("non ascoltare piu")
                || testo.equals("ciao")
                || testo.equals("a dopo")
                || testo.equals("ok a dopo")
                || testo.equals("va bene a dopo")
                || testo.equals("buonanotte")
                || testo.equals("buona notte")
                || testo.equals("ci sentiamo")
                || testo.equals("alla prossima");
    }

    private String rimuoviRichiamoLisa(
            String frase) {

        String originale =
                frase.trim();

        String basso =
                originale.toLowerCase(
                        Locale.ITALIAN
                );

        String[] richiami = {
                "ehi lisa",
                "hey lisa",
                "ciao lisa",
                "ehi elisa",
                "hey elisa",
                "ciao elisa",
                "elisa",
                "lisa"
        };

        for (String richiamo : richiami) {

            if (basso.equals(richiamo)) {
                return "";
            }

            if (basso.startsWith(
                    richiamo + " ")) {

                return originale
                        .substring(
                                richiamo.length()
                        )
                        .trim();
            }
        }

        return originale;
    }

    private boolean eseguiLocaleRapido(String frase) {

        String testo =
                frase.toLowerCase(Locale.ITALIAN).trim();

        // VOICE_ALL_V1
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

            return servizio.performGlobalAction(
                    android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_HOME
            );
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
                if (azioneAndroid == null || azioneAndroid.isEmpty()) {
                    String messaggioVocale =
                            rispostaJson.optString("risposta", "").trim();

                    if (!messaggioVocale.isEmpty()) {
                        handler.post(() -> {
                            if (!sessioneAttiva) return;
                            LisaSpeaker.parla(
                                    LisaVoiceService.this,
                                    messaggioVocale,
                                    () -> programmaAscolto(250)
                            );
                        });
                    }
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
                    if ("apri_url".equals(azioneAndroid)) {
                        programmaAscolto(900);
                    } else {
                        programmaAscolto(900);
                    }
                });

            } catch (Exception errore) {

                Log.e(
                        TAG,
                        "LisaOS non raggiungibile",
                        errore
                );

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

        sessioneAttiva = false;
        voiceController.stopSession();

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
