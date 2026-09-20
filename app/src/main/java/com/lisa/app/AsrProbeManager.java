package com.lisa.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

import java.util.Arrays;
import java.util.Locale;

public class AsrProbeManager {

    private static final String TAG = "LisaAsrProbe";
    private static final int SAMPLE_RATE = 16000;
    private static final int FEATURE_DIM = 80;
    private static final long DURATA_MS = 10000;

    private final Context context;

    private volatile boolean running = false;
    private volatile String stato = "CREATO";
    private volatile String risultato = "";

    private AudioRecord audioRecord;
    private Thread thread;
    private OnlineRecognizer recognizer;
    private OnlineStream stream;

    public AsrProbeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized String getStatus() {
        if (risultato == null || risultato.isEmpty()) {
            return stato;
        }
        return stato + " | " + risultato;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void start() {

        if (running) {
            return;
        }

        stato = "AVVIO";
        risultato = "";

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            stato = "ERRORE_PERMESSO";
            return;
        }

        try {
            inizializzaRecognizer();

            int minimo = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            if (minimo <= 0) {
                throw new IllegalStateException(
                        "Buffer AudioRecord non valido: " + minimo
                );
            }

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimo * 2, 3200)
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException(
                        "AudioRecord ASR probe non inizializzato"
                );
            }

            audioRecord.startRecording();

            if (audioRecord.getRecordingState()
                    != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException(
                        "AudioRecord non è entrato in RECORDING"
                );
            }

            running = true;
            stato = "IN_ASCOLTO_ASR";

            thread = new Thread(
                    this::ciclo,
                    "Lisa-AsrProbe"
            );

            thread.start();

        } catch (Throwable e) {
            running = false;
            stato = "ERRORE";
            risultato =
                    e.getClass().getSimpleName()
                            + ": "
                            + String.valueOf(e.getMessage());

            Log.e(TAG, "Errore avvio ASR probe", e);
            rilasciaAudio();
            rilasciaSherpa();
        }
    }

    private void inizializzaRecognizer() {

        FeatureConfig feature = new FeatureConfig();
        feature.setSampleRate(SAMPLE_RATE);
        feature.setFeatureDim(FEATURE_DIM);

        OnlineTransducerModelConfig transducer =
                new OnlineTransducerModelConfig();

        transducer.setEncoder("lisa_kws/encoder.onnx");
        transducer.setDecoder("lisa_kws/decoder.onnx");
        transducer.setJoiner("lisa_kws/joiner.onnx");

        OnlineModelConfig modello =
                new OnlineModelConfig();

        modello.setTransducer(transducer);
        modello.setTokens("lisa_kws/tokens.txt");
        modello.setNumThreads(1);
        modello.setDebug(false);
        modello.setProvider("cpu");
        modello.setModelType("zipformer2");

        OnlineRecognizerConfig config =
                new OnlineRecognizerConfig();

        config.setFeatConfig(feature);
        config.setModelConfig(modello);
        config.setEnableEndpoint(false);
        config.setDecodingMethod("greedy_search");
        config.setMaxActivePaths(4);

        recognizer =
                new OnlineRecognizer(
                        context.getAssets(),
                        config
                );

        stream = recognizer.createStream("");

        Log.i(TAG, "OnlineRecognizer diagnostico inizializzato");
    }

    private void ciclo() {

        short[] pcm = new short[1600];

        double rmsMassimo = 0.0;
        int piccoMassimo = 0;
        long campioniTotali = 0;

        try {
            long fine =
                    SystemClock.elapsedRealtime()
                            + DURATA_MS;

            while (running
                    && SystemClock.elapsedRealtime() < fine) {

                AudioRecord audio = audioRecord;

                if (audio == null) {
                    break;
                }

                int letti =
                        audio.read(
                                pcm,
                                0,
                                pcm.length
                        );

                if (letti <= 0) {
                    continue;
                }

                double sommaQuadrati = 0.0;
                float[] samples = new float[letti];

                for (int i = 0; i < letti; i++) {

                    int valore = pcm[i];
                    int assoluto = Math.abs(valore);

                    if (assoluto > piccoMassimo) {
                        piccoMassimo = assoluto;
                    }

                    float normalizzato =
                            valore / 32768.0f;

                    samples[i] = normalizzato;

                    sommaQuadrati +=
                            normalizzato
                                    * normalizzato;
                }

                double rms =
                        Math.sqrt(
                                sommaQuadrati / letti
                        );

                if (rms > rmsMassimo) {
                    rmsMassimo = rms;
                }

                campioniTotali += letti;

                stream.acceptWaveform(
                        samples,
                        SAMPLE_RATE
                );

                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream);
                }
            }

            // Piccolo padding finale per consentire
            // al transducer di chiudere l'ultima emissione.
            float[] silenzio =
                    new float[(int) (0.8 * SAMPLE_RATE)];

            stream.acceptWaveform(
                    silenzio,
                    SAMPLE_RATE
            );

            while (recognizer.isReady(stream)) {
                recognizer.decode(stream);
            }

            OnlineRecognizerResult r =
                    recognizer.getResult(stream);

            String testo =
                    r != null && r.getText() != null
                            ? r.getText()
                            : "";

            String tokens =
                    r != null && r.getTokens() != null
                            ? Arrays.toString(r.getTokens())
                            : "[]";

            risultato =
                    String.format(
                            Locale.US,
                            "rms_max=%.5f peak=%.5f samples=%d text=\"%s\" tokens=%s",
                            rmsMassimo,
                            piccoMassimo / 32768.0,
                            campioniTotali,
                            testo,
                            tokens
                    );

            stato = "FINITO";

            Log.i(
                    TAG,
                    "RISULTATO: " + risultato
            );

        } catch (Throwable e) {

            stato = "ERRORE";
            risultato =
                    e.getClass().getSimpleName()
                            + ": "
                            + String.valueOf(e.getMessage());

            Log.e(TAG, "Errore durante ASR probe", e);

        } finally {

            running = false;
            rilasciaAudio();
            rilasciaSherpa();
        }
    }

    public synchronized void stop() {
        running = false;
        rilasciaAudio();
    }

    public synchronized void release() {
        running = false;
        rilasciaAudio();
        rilasciaSherpa();
    }

    private synchronized void rilasciaAudio() {

        AudioRecord audio = audioRecord;
        audioRecord = null;

        if (audio != null) {

            try {
                audio.stop();
            } catch (Exception ignored) {
            }

            try {
                audio.release();
            } catch (Exception ignored) {
            }
        }
    }

    private synchronized void rilasciaSherpa() {

        if (stream != null) {
            try {
                stream.release();
            } catch (Throwable ignored) {
            }
            stream = null;
        }

        if (recognizer != null) {
            try {
                recognizer.release();
            } catch (Throwable ignored) {
            }
            recognizer = null;
        }
    }
}
