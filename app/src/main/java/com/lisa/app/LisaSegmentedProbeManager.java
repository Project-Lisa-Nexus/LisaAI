package com.lisa.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

public final class LisaSegmentedProbeManager {

    private static final String TAG = "LisaSegmentedProbe";
    private static final long DURATA_PROBE_MS = 30000L;

    private final Context context;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;

    private boolean attivo = false;
    private int startListeningCount = 0;
    private int segmenti = 0;
    private int risultatiFinali = 0;
    private int errori = 0;

    private final Runnable timeout =
            () -> stop("timeout_30s");

    public LisaSegmentedProbeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        handler.post(this::startInterno);
    }

    private void startInterno() {

        if (attivo) {
            Log.w(TAG, "Probe già attivo");
            return;
        }

        if (Build.VERSION.SDK_INT < 33) {
            Log.e(TAG, "Segmented Session richiede API 33+");
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer non disponibile");
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 31
                    && SpeechRecognizer
                    .isOnDeviceRecognitionAvailable(context)) {

                recognizer =
                        SpeechRecognizer
                                .createOnDeviceSpeechRecognizer(context);

                Log.i(TAG, "Recognizer ON-DEVICE");

            } else {

                Log.e(TAG,
                        "Recognizer ON-DEVICE non disponibile: "
                                + "probe annullato");
                return;
            }

            recognizer.setRecognitionListener(
                    creaListener()
            );

            Intent intent =
                    new Intent(
                            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                    );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.ITALIAN.toLanguageTag()
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent
                            .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS
            );

            intent.putExtra(
                    RecognizerIntent
                            .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    (int) DURATA_PROBE_MS
            );

            attivo = true;
            startListeningCount++;

            Log.i(
                    TAG,
                    "PROBE START"
                            + " startListening="
                            + startListeningCount
            );

            recognizer.startListening(intent);

            handler.postDelayed(
                    timeout,
                    DURATA_PROBE_MS
            );

        } catch (Throwable e) {

            Log.e(TAG, "Errore avvio probe", e);
            stopInterno("eccezione_avvio");
        }
    }

    private RecognitionListener creaListener() {

        return new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.i(TAG, "READY");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.i(TAG, "BEGIN");
            }

            @Override
            public void onEndOfSpeech() {
                Log.i(TAG, "END_OF_SPEECH");
            }

            @Override
            public void onSegmentResults(
                    Bundle results) {

                segmenti++;

                String frase =
                        primaFrase(results);

                Log.i(
                        TAG,
                        "SEGMENTO #" + segmenti
                                + " = [" + frase + "]"
                );

                if (richiestaStop(frase)) {
                    stopInterno("stop_vocale");
                }
            }

            @Override
            public void onEndOfSegmentedSession() {

                Log.i(
                        TAG,
                        "END_SEGMENTED_SESSION"
                );

                stopInterno(
                        "fine_segmented_session"
                );
            }

            @Override
            public void onResults(Bundle results) {

                risultatiFinali++;

                String frase =
                        primaFrase(results);

                Log.i(
                        TAG,
                        "ON_RESULTS #" + risultatiFinali
                                + " = [" + frase + "]"
                );

                if (richiestaStop(frase)) {
                    stopInterno("stop_vocale_results");
                }
            }

            @Override
            public void onError(int error) {

                errori++;

                Log.e(
                        TAG,
                        "ERROR #" + errori
                                + " codice=" + error
                );

                stopInterno(
                        "errore_" + error
                );
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEvent(
                    int eventType,
                    Bundle params) {
            }
        };
    }

    private String primaFrase(Bundle bundle) {

        if (bundle == null) return "";

        ArrayList<String> risultati =
                bundle.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                );

        if (risultati == null
                || risultati.isEmpty()
                || risultati.get(0) == null) {

            return "";
        }

        return risultati.get(0).trim();
    }

    private boolean richiestaStop(String frase) {

        if (frase == null) return false;

        String t =
                frase.toLowerCase(Locale.ITALIAN)
                        .trim();

        return t.equals("basta")
                || t.equals("stop")
                || t.equals("ferma")
                || t.equals("fermati")
                || t.equals("smetti");
    }

    public void stop(String motivo) {
        handler.post(
                () -> stopInterno(motivo)
        );
    }

    private void stopInterno(String motivo) {

        if (!attivo && recognizer == null) {
            return;
        }

        handler.removeCallbacks(timeout);

        attivo = false;

        if (recognizer != null) {

            try {
                recognizer.cancel();
            } catch (Throwable ignored) {
            }

            try {
                recognizer.destroy();
            } catch (Throwable ignored) {
            }

            recognizer = null;
        }

        Log.i(
                TAG,
                "PROBE STOP motivo=" + motivo
                        + " startListening="
                        + startListeningCount
                        + " segmenti="
                        + segmenti
                        + " results="
                        + risultatiFinali
                        + " errori="
                        + errori
        );
    }

    public String getStatus() {

        return "attivo=" + attivo
                + "|start=" + startListeningCount
                + "|segmenti=" + segmenti
                + "|results=" + risultatiFinali
                + "|errori=" + errori;
    }
}
