package com.lisa.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;
import java.util.Arrays;

public final class LocalSherpaAsrProbeManager {

    private static final String TAG = "LisaLocalAsr";
    private static final int SAMPLE_RATE = 16000;
    private static final int DURATA_SECONDI = 8;

    private final Context context;
    private volatile boolean running = false;
    private AudioRecord audioRecord;

    public LocalSherpaAsrProbeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    private void toast(String testo) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(
                        context,
                        testo,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    public synchronized void start() {

        if (running) {
            toast("Test già in corso");
            return;
        }

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            toast("Permesso microfono mancante");
            return;
        }

        running = true;
        toast("Carico il cervello vocale...");

        new Thread(this::esegui, "Lisa-Local-ASR").start();
    }

    private void esegui() {

        OfflineRecognizer recognizer = null;
        OfflineStream stream = null;

        try {

            File dir =
                    new File(
                            context.getFilesDir(),
                            "lisa_asr"
                    );

            File model =
                    new File(dir, "model.onnx");

            File tokens =
                    new File(dir, "tokens.txt");

            if (!model.isFile() || !tokens.isFile()) {
                throw new IllegalStateException(
                        "Modello vocale non installato"
                );
            }

            FeatureConfig feature =
                    new FeatureConfig();

            feature.setSampleRate(SAMPLE_RATE);
            feature.setFeatureDim(80);

            OfflineNemoEncDecCtcModelConfig nemo =
                    new OfflineNemoEncDecCtcModelConfig();

            nemo.setModel(
                    model.getAbsolutePath()
            );

            OfflineModelConfig modelConfig =
                    new OfflineModelConfig();

            modelConfig.setNemo(nemo);
            modelConfig.setTokens(
                    tokens.getAbsolutePath()
            );

            modelConfig.setNumThreads(4);
            modelConfig.setProvider("cpu");
            modelConfig.setDebug(false);

            OfflineRecognizerConfig config =
                    new OfflineRecognizerConfig();

            config.setFeatConfig(feature);
            config.setModelConfig(modelConfig);
            config.setDecodingMethod("greedy_search");

            Log.i(TAG, "Caricamento modello...");

            recognizer =
                    new OfflineRecognizer(
                            null,
                            config
                    );

            Log.i(TAG, "SHERPA PRONTO - PARLA ORA");
            toast("🎤 PARLA ORA per circa 5-8 secondi");

            int minimo =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            audioRecord =
                    new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(minimo * 2, 6400)
                    );

            if (audioRecord.getState()
                    != AudioRecord.STATE_INITIALIZED) {

                throw new IllegalStateException(
                        "Microfono non inizializzato"
                );
            }

            float[] registrazione =
                    new float[
                            SAMPLE_RATE * DURATA_SECONDI
                    ];

            short[] buffer =
                    new short[1600];

            int totale = 0;

            audioRecord.startRecording();

            long fine =
                    android.os.SystemClock.elapsedRealtime()
                            + DURATA_SECONDI * 1000L;

            while (android.os.SystemClock.elapsedRealtime() < fine
                    && totale < registrazione.length) {

                int n =
                        audioRecord.read(
                                buffer,
                                0,
                                Math.min(
                                        buffer.length,
                                        registrazione.length - totale
                                )
                        );

                if (n <= 0) continue;

                for (int i = 0; i < n; i++) {
                    registrazione[totale++] =
                            buffer[i] / 32768.0f;
                }
            }

            try {
                audioRecord.stop();
            } catch (Exception ignored) {}

            audioRecord.release();
            audioRecord = null;

            toast("🧠 Sto trascrivendo...");

            float[] audio =
                    Arrays.copyOf(
                            registrazione,
                            totale
                    );

            stream =
                    recognizer.createStream();

            stream.acceptWaveform(
                    audio,
                    SAMPLE_RATE
            );

            long inizio =
                    android.os.SystemClock.elapsedRealtime();

            recognizer.decode(stream);

            long durata =
                    android.os.SystemClock.elapsedRealtime()
                            - inizio;

            OfflineRecognizerResult risultato =
                    recognizer.getResult(stream);

            String testo =
                    risultato != null
                            ? risultato.getText()
                            : "";

            Log.i(TAG, "================================");
            Log.i(TAG, "TESTO LOCALE: " + testo);
            Log.i(TAG, "TEMPO ASR: " + durata + " ms");
            Log.i(TAG, "================================");

            toast(
                    "Lisa ha capito: "
                            + testo
            );

        } catch (Throwable e) {

            Log.e(TAG, "ERRORE ASR LOCALE", e);
            toast("Errore ASR locale: " + e.getMessage());

        } finally {

            running = false;

            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Exception ignored) {}
                try { audioRecord.release(); } catch (Exception ignored) {}
                audioRecord = null;
            }

            if (stream != null) {
                try { stream.release(); } catch (Exception ignored) {}
            }

            if (recognizer != null) {
                try { recognizer.release(); } catch (Exception ignored) {}
            }
        }
    }
}
