package com.lisa.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
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

    public static final String ACTION_START =
            "com.lisa.app.voice.START";

    public static final String ACTION_STOP =
            "com.lisa.app.voice.STOP";

    private static final String TAG = "LisaVoiceSession";
    private static final String CHANNEL_ID = "LisaVoiceChannel";
    private static final String API_LISA =
            "http://127.0.0.1:5000/api/invoca";

    private static volatile boolean sessioneAttiva = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private boolean ascoltoInCorso = false;

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

        if (ACTION_STOP.equals(azione)) {
            terminaSessione(false);
            return START_NOT_STICKY;
        }

        startForeground(
                1,
                creaNotifica("Lisa attiva")
        );

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "RECORD_AUDIO non concesso");
            terminaSessione(false);
            return START_NOT_STICKY;
        }

        sessioneAttiva = true;

        aggiornaNotifica(
                "Lisa pronta ad ascoltare"
        );

        programmaAscolto(250);

        return START_NOT_STICKY;
    }

    private void programmaAscolto(long ritardoMs) {
        if (!sessioneAttiva
                || sospesoPerTts
                || LisaSpeaker.isParlando()) {
            return;
        }


        if (!sessioneAttiva) return;

        handler.removeCallbacksAndMessages(null);

        handler.postDelayed(
                () -> {
                    if (sessioneAttiva
                            && !ascoltoInCorso) {

                        avviaAscolto();
                    }
                },
                ritardoMs
        );
    }

    private void avviaAscolto() {
        if (!sessioneAttiva
                || sospesoPerTts
                || LisaSpeaker.isParlando()) {
            return;
        }


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

                if (Build.VERSION.SDK_INT >= 31
                        && SpeechRecognizer
                        .isOnDeviceRecognitionAvailable(this)) {

                    recognizer =
                            SpeechRecognizer
                                    .createOnDeviceSpeechRecognizer(
                                            this
                                    );

                    Log.i(TAG,
                            "ASR on-device attivo");

                } else {

                    recognizer =
                            SpeechRecognizer
                                    .createSpeechRecognizer(
                                            this
                                    );

                    Log.i(TAG,
                            "ASR Android standard attivo");
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

            voce.putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    true
            );

            ascoltoInCorso = true;

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
            public void onReadyForSpeech(
                    Bundle params) {

                aggiornaNotifica(
                        "Lisa sta ascoltando…"
                );
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(
                    byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {

                aggiornaNotifica(
                        "Lisa sta elaborando…"
                );
            }

            @Override
            public void onError(int error) {
                if (sospesoPerTts
                        || LisaSpeaker.isParlando()) {
                    ascoltoInCorso = false;
                    return;
                }


                ascoltoInCorso = false;

                if (!sessioneAttiva) {
                    return;
                }

                Log.d(
                        TAG,
                        "SpeechRecognizer error=" + error
                );

                Log.w(
                        TAG,
                        "Ascolto terminato con errore: " + error
                );

                ricreaRecognizer();
            }

            @Override
            public void onResults(Bundle results) {

                ascoltoInCorso = false;

                if (!sessioneAttiva) {
                    return;
                }

                ArrayList<String> frasi =
                        results.getStringArrayList(
                                SpeechRecognizer
                                        .RESULTS_RECOGNITION
                        );

                if (frasi == null
                        || frasi.isEmpty()) {

                    programmaAscolto(300);
                    return;
                }

                String frase =
                        frasi.get(0).trim();

                if (frase.isEmpty()) {

                    programmaAscolto(300);
                    return;
                }

                Log.i(
                        TAG,
                        "Hai detto: " + frase
                );

                gestisciFrase(frase);
            }

            @Override
            public void onPartialResults(
                    Bundle partialResults) {
            }

            @Override
            public void onEvent(
                    int eventType,
                    Bundle params) {
            }
        };
    }

    private void gestisciFrase(String frase) {

        String testo =
                frase.toLowerCase(Locale.ITALIAN).trim();

        if (richiestaStop(testo)) {

            String risposta =
                    testo.contains("buonanotte")
                            ? "Buonanotte."
                            : "Va bene.";

            // PRIMA spegniamo davvero Lisa.
            sessioneAttiva = false;
            fermaRecognizer();

            try {
                stopForeground(true);
            } catch (Exception ignored) {
            }

            stopSelf();

            // La risposta vocale NON decide più
            // se il microfono deve spegnersi.
            LisaSpeaker.parla(
                    getApplicationContext(),
                    risposta,
                    () -> {}
            );

            Log.i(TAG, "Lisa fermata dalla voce");
            return;
        }

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
            programmaAscolto(650);
            return;
        }

        aggiornaNotifica("Lisa sta eseguendo…");
        inviaALisaOS(comando);
    }

    private boolean richiestaStop(String frase) {

        if (frase == null) return false;

        String testo =
                frase.toLowerCase(Locale.ITALIAN)
                        .trim()
                        .replaceAll("\\s+", " ");

        // Queste sono chiamate a Lisa, NON comandi di stop.
        if (testo.equals("ciao lisa")
                || testo.equals("ehi lisa")
                || testo.equals("hey lisa")) {
            return false;
        }

        return testo.equals("basta")
                || testo.equals("stop")
                || testo.equals("fermati")
                || testo.equals("smetti")
                || testo.equals("smetti di ascoltare")
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

        LisaAccessibilityService servizio =
                LisaAccessibilityService.getInstance();

        if (servizio == null) {
            return false;
        }

        if (testo.equals("home")
                || testo.equals("vai alla home")) {

            return servizio.performGlobalAction(
                    android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_HOME
            );
        }

        if (testo.equals("indietro")
                || testo.equals("torna indietro")) {

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
                        30000
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

        ricreaRecognizer();
    }

    private void terminaSessione(
            boolean silenzioso) {

        sessioneAttiva = false;

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

        fermaRecognizer();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent) {

        return null;
    }
}
