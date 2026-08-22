package com.lisa.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class LisaVoiceCommandActivity extends Activity {

    private static final int RICHIESTA_VOCE = 501;
    private static final String API_LISA =
        "http://127.0.0.1:5000/api/invoca";

    private TextView stato;
    private Button pulsanteParla;

    private SpeechRecognizer speechRecognizer;
    private final Handler voceHandler =
        new Handler(Looper.getMainLooper());
    private final StringBuilder voceTesto =
        new StringBuilder();
    private boolean voceAttiva = false;
    private long voceAvviata = 0L;

    private final Runnable chiudiVoce = () -> finalizzaVoce();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(40, 80, 40, 40);

        TextView titolo = new TextView(this);
        titolo.setText("Comando vocale Lisa");
        titolo.setTextSize(26);
        titolo.setGravity(Gravity.CENTER);
        layout.addView(titolo);

        stato = new TextView(this);
        stato.setText(
            "Lisa è pronta.\n" +
            "Tocca il pulsante soltanto quando vuoi parlare."
        );
        stato.setTextSize(19);
        stato.setGravity(Gravity.CENTER);
        stato.setPadding(10, 40, 10, 40);
        layout.addView(stato);

        pulsanteParla = new Button(this);
        pulsanteParla.setText("🎤 Parla con Lisa");
        pulsanteParla.setTextSize(22);
        pulsanteParla.setMinHeight(150);
        pulsanteParla.setOnClickListener(v -> avviaRiconoscimento());
        layout.addView(pulsanteParla);

        setContentView(layout);

        if (getIntent().getBooleanExtra("ascolta_subito", false)) {
            stato.setText("Ti ascolto...");
            voceHandler.postDelayed(
                    this::avviaRiconoscimento,
                    350L
            );
        }
    }


    private Intent creaIntentVoce() {
        Intent intent = new Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "it-IT"
        );
        intent.putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            true
        );
        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            3
        );


        // Una sola sessione: termina dopo 2 secondi reali di silenzio.
        intent.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            2000L
        );

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.putExtra(
                "android.speech.extra.SEGMENTED_SESSION",
                "android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS"
            );
        }

        return intent;
    }

    private void avviaRiconoscimento() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stato.setText("Riconoscimento vocale non disponibile.");
            return;
        }

        voceHandler.removeCallbacksAndMessages(null);
        voceTesto.setLength(0);
        voceAttiva = true;
        voceAvviata = System.currentTimeMillis();

        pulsanteParla.setEnabled(false);
        stato.setText("Sto ascoltando...");

        avviaSegmentoVoce();
    }

    private void avviaSegmentoVoce() {
        if (!voceAttiva) return;

        distruggiRecognizer();

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(
            new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    stato.setText("Sto ascoltando...");
                }

                @Override
                public void onBeginningOfSpeech() {
                    voceHandler.removeCallbacks(chiudiVoce);
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    stato.setText("Ti ascolto ancora...");
                }

                @Override
                public void onError(int error) {
                    if (!voceAttiva) return;

                    distruggiRecognizer();

                    if (
                        voceTesto.length() == 0 &&
                        System.currentTimeMillis() - voceAvviata > 8000L
                    ) {
                        voceAttiva = false;
                        stato.setText("Non ho sentito nessuna frase.");
                        pulsanteParla.setEnabled(true);
                        return;
                    }

                    voceHandler.postDelayed(
                        () -> avviaSegmentoVoce(),
                        300L
                    );
                }

                @Override
                public void onResults(Bundle results) {
                    if (!voceAttiva) return;

                    ArrayList<String> parole =
                        results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        );

                    if (parole != null && !parole.isEmpty()) {
                        String pezzo = parole.get(0).trim();

                        if (!pezzo.isEmpty()) {
                            if (voceTesto.length() > 0) {
                                voceTesto.append(" ");
                            }
                            voceTesto.append(pezzo);
                        }
                    }

                    // Fallback per recognizer senza sessione segmentata.
                    finalizzaVoce();
                }

                @Override
                public void onSegmentResults(Bundle results) {
                    if (!voceAttiva) return;

                    ArrayList<String> parole =
                        results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        );

                    if (parole != null && !parole.isEmpty()) {
                        String pezzo = parole.get(0).trim();

                        if (!pezzo.isEmpty()) {
                            if (voceTesto.length() > 0) {
                                voceTesto.append(" ");
                            }
                            voceTesto.append(pezzo);
                        }
                    }

                    stato.setText("Ti ascolto...");
                }

                @Override
                public void onEndOfSegmentedSession() {
                    finalizzaVoce();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    if (voceAttiva) {
                        voceHandler.removeCallbacks(chiudiVoce);
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            }
        );

        speechRecognizer.startListening(creaIntentVoce());
    }

    private void distruggiRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}

            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}

            speechRecognizer = null;
        }
    }

    private void finalizzaVoce() {
        if (!voceAttiva) return;

        voceAttiva = false;
        voceHandler.removeCallbacksAndMessages(null);
        distruggiRecognizer();

        String frase = voceTesto.toString().trim();

        if (frase.isEmpty()) {
            stato.setText("Non ho riconosciuto la frase.");
            pulsanteParla.setEnabled(true);
            return;
        }

        ArrayList<String> risultati = new ArrayList<>();
        risultati.add(frase);

        Intent dati = new Intent();
        dati.putStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS,
            risultati
        );

        onActivityResult(
            RICHIESTA_VOCE,
            RESULT_OK,
            dati
        );
    }


    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != RICHIESTA_VOCE) return;

        if (resultCode != RESULT_OK || data == null) {
            stato.setText(
                "Ascolto terminato senza eseguire comandi."
            );
            return;
        }

        ArrayList<String> risultati =
            data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            );

        if (risultati == null || risultati.isEmpty()) {
            stato.setText("Non ho riconosciuto la frase.");
            return;
        }

        String frase = risultati.get(0).trim();

        if (frase.isEmpty()) {
            stato.setText("Non ho riconosciuto la frase.");
            return;
        }

        String rispostaLocale =
            rispostaInformazioneLocale(frase);

        if (rispostaLocale != null) {
            stato.setText(rispostaLocale);
            pulsanteParla.setEnabled(true);
            return;
        }

        stato.setText(
            "Hai detto:\n" + frase +
            "\n\nSto elaborando..."
        );

        pulsanteParla.setEnabled(false);

        new Thread(() -> inviaAlRouter(frase)).start();
    }

    private String rispostaInformazioneLocale(
            String frase) {

        String testo =
            frase.toLowerCase(Locale.ITALIAN).trim();

        if (testo.contains("che ore sono") ||
            testo.contains("che ora è") ||
            testo.contains("che ora e") ||
            testo.contains("dimmi l'ora") ||
            testo.equals("ora")) {

            String ora = new SimpleDateFormat(
                "HH:mm",
                Locale.ITALIAN
            ).format(new Date());

            return "Sono le " + ora + ".";
        }

        if (testo.contains("che giorno è") ||
            testo.contains("che giorno e") ||
            testo.contains("che data è") ||
            testo.contains("che data e") ||
            testo.contains("data di oggi")) {

            String data = new SimpleDateFormat(
                "EEEE d MMMM yyyy",
                Locale.ITALIAN
            ).format(new Date());

            return "Oggi è " + data + ".";
        }

        return null;
    }

    private void inviaAlRouter(String frase) {
        HttpURLConnection connessione = null;

        try {
            URL url = new URL(API_LISA);
            connessione =
                (HttpURLConnection) url.openConnection();

            connessione.setRequestMethod("POST");
            connessione.setConnectTimeout(5000);
            connessione.setReadTimeout(30000);
            connessione.setDoOutput(true);
            connessione.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            );

            JSONObject richiesta = new JSONObject();
            richiesta.put("testo", "Lisa " + frase);

            byte[] dati = richiesta.toString().getBytes(
                StandardCharsets.UTF_8
            );

            try (OutputStream uscita =
                     connessione.getOutputStream()) {
                uscita.write(dati);
            }

            int codice = connessione.getResponseCode();

            InputStream flusso =
                codice >= 200 && codice < 300
                    ? connessione.getInputStream()
                    : connessione.getErrorStream();

            String rispostaTesto = leggiFlusso(flusso);

            if (rispostaTesto.trim().isEmpty()) {
                throw new Exception(
                    "Risposta vuota dal router"
                );
            }

            JSONObject risposta =
                new JSONObject(rispostaTesto);

            runOnUiThread(() -> {
                boolean eseguito =
                    eseguiRispostaRouter(risposta);

                String messaggio =
                    risposta.optString("risposta", "");

                if (messaggio.isEmpty()) {
                    messaggio = eseguito
                        ? "Comando eseguito."
                        : "Comando ricevuto ma non ancora eseguibile.";
                }

                stato.setText(messaggio);
                pulsanteParla.setEnabled(true);
            });

        } catch (Exception errore) {
            runOnUiThread(() -> {
                boolean fallback =
                    eseguiComandoLocale(frase);

                if (fallback) {
                    stato.setText(
                        "Comando eseguito localmente."
                    );
                } else {
                    stato.setText(
                        "LisaOS non è raggiungibile.\n" +
                        errore.getMessage()
                    );
                }

                pulsanteParla.setEnabled(true);
            });

        } finally {
            if (connessione != null) {
                connessione.disconnect();
            }
        }
    }

    private boolean eseguiRispostaRouter(
            JSONObject risposta) {

        String azione =
            risposta.optString("azione", "");

        JSONObject comando =
            risposta.optJSONObject("comando");

        JSONObject parametri = null;

        if (comando != null) {
            if (azione.isEmpty()) {
                azione = comando.optString(
                    "azione",
                    ""
                );
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

        if ("apri_app".equals(azione)) {
            String nome =
                parametri.optString("nome", "");

            String pacchetto =
                parametri.optString(
                    "pacchetto",
                    ""
                );

            boolean aperta = false;

            if (!nome.trim().isEmpty()) {
                aperta = AppFinder.apriAppPerNome(
                    this,
                    nome
                );
            }

            if (!aperta &&
                !pacchetto.trim().isEmpty()) {

                LisaAccessibilityService.apriAppStatic(
                    this,
                    pacchetto
                );

                aperta = true;
            }

            return aperta;
        }

        LisaAccessibilityService servizio =
            LisaAccessibilityService.getInstance();

        if (servizio == null) {
            return false;
        }

        switch (azione) {
            case "home":
                return servizio.performGlobalAction(
                    android.accessibilityservice
                        .AccessibilityService
                        .GLOBAL_ACTION_HOME
                );

            case "indietro":
            case "back":
                return servizio.performGlobalAction(
                    android.accessibilityservice
                        .AccessibilityService
                        .GLOBAL_ACTION_BACK
                );

            case "recenti":
            case "recents":
                return servizio.performGlobalAction(
                    android.accessibilityservice
                        .AccessibilityService
                        .GLOBAL_ACTION_RECENTS
                );

            case "notifiche":
            case "notifications":
                return servizio.performGlobalAction(
                    android.accessibilityservice
                        .AccessibilityService
                        .GLOBAL_ACTION_NOTIFICATIONS
                );

            case "screenshot":
                return servizio.performGlobalAction(
                    android.accessibilityservice
                        .AccessibilityService
                        .GLOBAL_ACTION_TAKE_SCREENSHOT
                );

            case "blocca":
                return servizio.performGlobalAction(
                    android.accessibilityservice
                        .AccessibilityService
                        .GLOBAL_ACTION_LOCK_SCREEN
                );

            case "scorri_giu":
                return servizio.scorriAvanti();

            case "scorri_su":
                return servizio.scorriIndietro();

            case "clicca":
                return servizio.cliccaTesto(
                    parametri.optString("testo", "")
                );

            case "scrivi_testo":
                return servizio.scriviTesto(
                    parametri.optString("testo", "")
                );

            default:
                return false;
        }
    }

    private boolean eseguiComandoLocale(String frase) {
        String testo =
            frase.toLowerCase(Locale.ITALIAN)
                 .trim();

        String[] verbi = {
            "apri ",
            "avvia ",
            "lancia ",
            "aprimi "
        };

        for (String verbo : verbi) {
            if (testo.startsWith(verbo)) {
                String nomeApp =
                    frase.substring(verbo.length())
                         .trim();

                return AppFinder.apriAppPerNome(
                    this,
                    nomeApp
                );
            }
        }

        LisaAccessibilityService servizio =
            LisaAccessibilityService.getInstance();

        if (servizio == null) return false;

        if (testo.equals("home") ||
            testo.equals("vai alla home")) {

            return servizio.performGlobalAction(
                android.accessibilityservice
                    .AccessibilityService
                    .GLOBAL_ACTION_HOME
            );
        }

        if (testo.equals("indietro") ||
            testo.equals("torna indietro")) {

            return servizio.performGlobalAction(
                android.accessibilityservice
                    .AccessibilityService
                    .GLOBAL_ACTION_BACK
            );
        }

        return false;
    }

    private String leggiFlusso(InputStream flusso)
            throws Exception {

        if (flusso == null) return "";

        StringBuilder contenuto =
            new StringBuilder();

        try (BufferedReader lettore =
                 new BufferedReader(
                     new InputStreamReader(
                         flusso,
                         StandardCharsets.UTF_8
                     )
                 )) {

            String riga;

            while ((riga = lettore.readLine())
                    != null) {
                contenuto.append(riga);
            }
        }

        return contenuto.toString();
    }
}
