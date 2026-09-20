package com.lisa.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.KeywordSpotter;
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig;
import com.k2fsa.sherpa.onnx.KeywordSpotterResult;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

public class WakeWordManager {

    private static final String TAG = "LisaWake";
    private static final int SAMPLE_RATE = 16000;
    private static final int FEATURE_DIM = 80;

    public interface Listener {
        void onWakeWord(String keyword);
        void onWakeError(Throwable error);
    }

    private final Context context;
    private final Listener listener;

    private volatile boolean running = false;
    private volatile String stato = "CREATO";
    private volatile String ultimoErrore = "";

    private volatile long campioniTotali = 0;
    private volatile long decodeTotali = 0;
    private volatile double rmsMassimo = 0.0;
    private volatile int piccoMassimo = 0;
    private volatile String ultimiTokens = "[]";

    public synchronized String getStatus() {

        String diagnostica =
                String.format(
                        java.util.Locale.US,
                        "%s | samples=%d decodes=%d rms_max=%.5f peak=%.5f tokens=%s",
                        stato,
                        campioniTotali,
                        decodeTotali,
                        rmsMassimo,
                        piccoMassimo / 32768.0,
                        ultimiTokens
                );

        if (ultimoErrore == null || ultimoErrore.isEmpty()) {
            return diagnostica;
        }

        return diagnostica + " | " + ultimoErrore;
    }

    private AudioRecord audioRecord;
    private Thread audioThread;

    private KeywordSpotter keywordSpotter;
    private OnlineStream stream;

    public WakeWordManager(
            Context context,
            Listener listener) {

        this.context =
                context.getApplicationContext();

        this.listener = listener;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void start() {

        stato = "AVVIO";
        ultimoErrore = "";

        if (running) {
            return;
        }

        if (context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            stato = "ERRORE_PERMESSO";
            ultimoErrore = "RECORD_AUDIO non concesso";

            errore(
                    new SecurityException(
                            "RECORD_AUDIO non concesso"
                    )
            );

            return;
        }

        try {

            campioniTotali = 0;
            decodeTotali = 0;
            rmsMassimo = 0.0;
            piccoMassimo = 0;
            ultimiTokens = "[]";

            inizializzaSherpa();
            stato = "SHERPA_OK";

            int minimo =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            if (minimo <= 0) {
                throw new IllegalStateException(
                        "Buffer AudioRecord non valido: "
                                + minimo
                );
            }

            audioRecord =
                    new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(minimo * 2, 3200)
                    );

            if (audioRecord.getState()
                    != AudioRecord.STATE_INITIALIZED) {

                throw new IllegalStateException(
                        "AudioRecord wake non inizializzato"
                );
            }

            stato = "AUDIO_OK";
            running = true;

            audioRecord.startRecording();

            audioThread =
                    new Thread(
                            this::cicloAudio,
                            "Lisa-WakeWord"
                    );

            audioThread.start();

            stato = "IN_ASCOLTO";

            Log.i(TAG, "Wake word in ascolto");

        } catch (Throwable error) {

            stato = "ERRORE";
            ultimoErrore =
                    error.getClass().getSimpleName()
                            + ": "
                            + String.valueOf(error.getMessage());

            running = false;
            rilasciaAudio();

            errore(error);
        }
    }

    private void inizializzaSherpa() {

        if (keywordSpotter != null
                && stream != null) {
            return;
        }

        FeatureConfig feature =
                new FeatureConfig();

        feature.setSampleRate(SAMPLE_RATE);
        feature.setFeatureDim(FEATURE_DIM);

        OnlineTransducerModelConfig transducer =
                new OnlineTransducerModelConfig();

        transducer.setEncoder(
                "lisa_kws/encoder.onnx"
        );

        transducer.setDecoder(
                "lisa_kws/decoder.onnx"
        );

        transducer.setJoiner(
                "lisa_kws/joiner.onnx"
        );

        OnlineModelConfig modello =
                new OnlineModelConfig();

        modello.setTransducer(transducer);

        modello.setTokens(
                "lisa_kws/tokens.txt"
        );

        modello.setNumThreads(1);
        modello.setDebug(false);
        modello.setProvider("cpu");
        modello.setModelType("zipformer2");

        KeywordSpotterConfig config =
                new KeywordSpotterConfig();

        config.setFeatConfig(feature);
        config.setModelConfig(modello);
        config.setMaxActivePaths(4);

        config.setKeywordsFile(
                "lisa_kws/keywords.txt"
        );

        config.setKeywordsScore(1.5f);
        config.setKeywordsThreshold(0.25f);
        config.setNumTrailingBlanks(2);

        keywordSpotter =
                new KeywordSpotter(
                        context.getAssets(),
                        config
                );

        stream =
                keywordSpotter.createStream("");

        Log.i(TAG, "Sherpa inizializzato");
    }

    private void cicloAudio() {

        short[] pcm =
                new short[800];

        String keywordRilevata = null;

        try {

            while (running) {

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

                campioniTotali += letti;

                double sommaQuadrati = 0.0;

                for (int i = 0; i < letti; i++) {

                    int valore = pcm[i];
                    int assoluto = Math.abs(valore);

                    if (assoluto > piccoMassimo) {
                        piccoMassimo = assoluto;
                    }

                    double normalizzato =
                            valore / 32768.0;

                    sommaQuadrati +=
                            normalizzato * normalizzato;
                }

                double rms =
                        Math.sqrt(
                                sommaQuadrati / letti
                        );

                if (rms > rmsMassimo) {
                    rmsMassimo = rms;
                }

                float[] samples =
                        new float[letti];

                for (int i = 0; i < letti; i++) {

                    samples[i] =
                            pcm[i] / 32768.0f;
                }

                stream.acceptWaveform(
                        samples,
                        SAMPLE_RATE
                );

                while (running
                        && keywordSpotter.isReady(stream)) {

                    keywordSpotter.decode(stream);
                    decodeTotali++;

                    KeywordSpotterResult risultato =
                            keywordSpotter.getResult(stream);

                    if (risultato == null) {
                        continue;
                    }

                    if (risultato.getTokens() != null
                            && risultato.getTokens().length > 0) {

                        ultimiTokens =
                                java.util.Arrays.toString(
                                        risultato.getTokens()
                                );
                    }

                    String keyword =
                            risultato.getKeyword();

                    if (keyword != null
                            && !keyword.trim().isEmpty()) {

                        keywordSpotter.reset(stream);

                        keywordRilevata =
                                keyword.trim();

                        running = false;
                        break;
                    }
                }
            }

        } catch (Throwable error) {

            if (running) {
                errore(error);
            }

        } finally {

            running = false;
            rilasciaAudio();
        }

        if (keywordRilevata != null) {

            Log.i(
                    TAG,
                    "WAKE RILEVATA: "
                            + keywordRilevata
            );

            if (listener != null) {

                listener.onWakeWord(
                        keywordRilevata
                );
            }
        }
    }

    public synchronized void stopListening() {

        running = false;
        rilasciaAudio();

        Log.i(TAG, "Wake word in pausa");
    }

    private synchronized void rilasciaAudio() {

        AudioRecord audio =
                audioRecord;

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

        audioThread = null;
    }

    public synchronized void release() {

        stopListening();

        if (stream != null) {

            try {
                stream.release();
            } catch (Exception ignored) {
            }

            stream = null;
        }

        if (keywordSpotter != null) {

            try {
                keywordSpotter.release();
            } catch (Exception ignored) {
            }

            keywordSpotter = null;
        }

        Log.i(TAG, "Wake engine rilasciato");
    }

    private void errore(Throwable error) {

        Log.e(
                TAG,
                "Errore wake word",
                error
        );

        if (listener != null) {
            listener.onWakeError(error);
        }
    }
}
