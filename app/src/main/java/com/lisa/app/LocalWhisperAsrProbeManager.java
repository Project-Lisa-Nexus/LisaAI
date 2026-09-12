package com.lisa.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

import java.io.File;
import java.util.Arrays;

public final class LocalWhisperAsrProbeManager {

    private static final String TAG = "LisaWhisperAsr";
    private static final int SR = 16000;
    private static final int DURATA = 8;

    private final Context context;
    private final Runnable statoListener;
    private volatile boolean running = false;
    private volatile boolean stopRichiesto = false;
    private volatile AudioRecord audioRecord;

    // Whisper Small rimane caricato in RAM tra una frase e la successiva.
    private OfflineRecognizer recognizerPersistente;

    public LocalWhisperAsrProbeManager(
            Context context,
            Runnable statoListener
    ) {
        this.context = context.getApplicationContext();
        this.statoListener = statoListener;
    }

    public boolean isRunning() {
        return running;
    }

    private void notificaStato() {
        if (statoListener == null) return;

        new Handler(Looper.getMainLooper()).post(statoListener);
    }

    private void toast(String testo) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, testo, Toast.LENGTH_LONG).show()
        );
    }

    public synchronized void start() {

        if (running) {
            stopRichiesto = true;

            AudioRecord ar = audioRecord;
            if (ar != null) {
                try { ar.stop(); } catch (Exception ignored) {}
            }

            toast("⏹ Ascolto locale fermato");
            return;
        }

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("Permesso microfono mancante");
            return;
        }

        if (LisaAccessibilityService.getInstance() == null) {
            toast("Attiva prima Accessibilità Lisa");
            return;
        }

        android.content.Intent servizioLisa =
                new android.content.Intent(
                        context,
                        LisaVoiceService.class
                );

        servizioLisa.setAction(
                LisaVoiceService.ACTION_LOCAL_WHISPER_SESSION
        );

        if (android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.O) {

            context.startForegroundService(servizioLisa);

        } else {

            context.startService(servizioLisa);
        }

        stopRichiesto = false;
        running = true;
        notificaStato();
        toast("🧠 Avvio ascolto locale...");

        new Thread(this::esegui, "Lisa-Whisper-IT").start();
    }

    private void esegui() {

        OfflineRecognizer recognizer = recognizerPersistente;
        OfflineStream stream = null;
        com.k2fsa.sherpa.onnx.Vad vad = null;

        try {

            File dir = new File(context.getFilesDir(), "lisa_whisper_small");

            File encoder = new File(dir, "encoder.onnx");
            File decoder = new File(dir, "decoder.onnx");
            File tokens = new File(dir, "tokens.txt");

            if (!encoder.isFile()
                    || !decoder.isFile()
                    || !tokens.isFile()) {
                throw new IllegalStateException(
                        "Modello Whisper non installato"
                );
            }

            FeatureConfig feat = new FeatureConfig();
            feat.setSampleRate(SR);
            feat.setFeatureDim(80);

            OfflineWhisperModelConfig whisper =
                    new OfflineWhisperModelConfig();

            whisper.setEncoder(encoder.getAbsolutePath());
            whisper.setDecoder(decoder.getAbsolutePath());
            whisper.setLanguage("it");
            whisper.setTask("transcribe");

            OfflineModelConfig modelConfig =
                    new OfflineModelConfig();

            modelConfig.setWhisper(whisper);
            modelConfig.setTokens(tokens.getAbsolutePath());
            modelConfig.setModelType("whisper");
            modelConfig.setNumThreads(4);
            modelConfig.setProvider("cpu");
            modelConfig.setDebug(false);

            OfflineRecognizerConfig config =
                    new OfflineRecognizerConfig();

            config.setFeatConfig(feat);
            config.setModelConfig(modelConfig);
            config.setDecodingMethod("greedy_search");

            if (recognizerPersistente == null) {

                Log.i(TAG, "Caricamento Whisper Small IT in RAM...");

                recognizerPersistente =
                        new OfflineRecognizer(null, config);

                Log.i(TAG, "WHISPER SMALL CARICATO IN RAM");

            } else {

                Log.i(TAG, "WHISPER SMALL GIA IN RAM");
            }

            recognizer = recognizerPersistente;

            int minimo =
                    AudioRecord.getMinBufferSize(
                            SR,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            audioRecord =
                    new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            SR,
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

            com.k2fsa.sherpa.onnx.SileroVadModelConfig silero =
                    new com.k2fsa.sherpa.onnx.SileroVadModelConfig();

            silero.setModel("silero_vad.onnx");
            silero.setThreshold(0.5f);
            silero.setMinSilenceDuration(0.8f);
            silero.setMinSpeechDuration(0.15f);
            silero.setWindowSize(512);
            silero.setMaxSpeechDuration(20.0f);

            com.k2fsa.sherpa.onnx.VadModelConfig vadConfig =
                    new com.k2fsa.sherpa.onnx.VadModelConfig();

            vadConfig.setSileroVadModelConfig(silero);
            vadConfig.setSampleRate(SR);
            vadConfig.setNumThreads(1);
            vadConfig.setProvider("cpu");
            vadConfig.setDebug(false);

            vad =
                    new com.k2fsa.sherpa.onnx.Vad(
                            context.getAssets(),
                            vadConfig
                    );

            short[] framePcm = new short[512];
            boolean parlatoVisto = false;

            audioRecord.startRecording();

            Log.i(
                    TAG,
                    "SESSIONE CONTINUA AVVIATA - MIC APERTO UNA VOLTA"
            );

            Log.i(
                    TAG,
                    "MIC/VAD source="
                            + audioRecord.getAudioSource()
                            + " sampleRate="
                            + audioRecord.getSampleRate()
                            + " window=512"
            );

            toast("🎤 PARLA ORA");

            while (!stopRichiesto) {

                int n =
                        audioRecord.read(
                                framePcm,
                                0,
                                framePcm.length,
                                AudioRecord.READ_BLOCKING
                        );

                if (n <= 0) {
                    if (stopRichiesto) break;
                    continue;
                }

                float[] frame = new float[n];

                for (int i = 0; i < n; i++) {
                    frame[i] = framePcm[i] / 32768.0f;
                }

                vad.acceptWaveform(frame);

                if (vad.isSpeechDetected() && !parlatoVisto) {
                    parlatoVisto = true;
                    Log.i(TAG, "VAD: INIZIO PARLATO");
                }

                while (!vad.empty() && !stopRichiesto) {

                    com.k2fsa.sherpa.onnx.SpeechSegment segmento =
                            vad.front();

                    float[] audio =
                            segmento.getSamples();

                    vad.pop();
                    parlatoVisto = false;

                    if (audio == null || audio.length == 0) {
                        continue;
                    }

                    int totale = audio.length;

                    double sommaQuadrati = 0.0;
                    float picco = 0.0f;

                    for (float f : audio) {
                        float v = Math.max(-1.0f, Math.min(1.0f, f));
                        sommaQuadrati += v * v;
                        picco = Math.max(picco, Math.abs(v));
                    }

                    double rms =
                            Math.sqrt(
                                    sommaQuadrati / totale
                            );

                    Log.i(
                            TAG,
                            String.format(
                                    java.util.Locale.US,
                                    "VAD: FINE FRASE campioni=%d durata=%.2fs rms=%.5f peak=%.5f",
                                    totale,
                                    totale / (double) SR,
                                    rms,
                                    picco
                            )
                    );

                    stream = recognizer.createStream();

                    stream.acceptWaveform(audio, SR);

                    long t0 =
                            android.os.SystemClock.elapsedRealtime();

                    recognizer.decode(stream);

                    long ms =
                            android.os.SystemClock.elapsedRealtime() - t0;

                    OfflineRecognizerResult result =
                            recognizer.getResult(stream);

                    String testo =
                            result != null && result.getText() != null
                                    ? result.getText().trim()
                                    : "";

                    String lingua =
                            result != null
                                    ? result.getLang()
                                    : "";

                    Log.i(TAG, "================================");
                    Log.i(TAG, "TESTO WHISPER IT: " + testo);
                    Log.i(TAG, "LINGUA RISULTATO: " + lingua);
                    Log.i(TAG, "TEMPO WHISPER: " + ms + " ms");
                    Log.i(TAG, "================================");

                    try { stream.release(); } catch (Exception ignored) {}
                    stream = null;

                    toast("Lisa ha capito: " + testo);

                    String normale =
                            testo.toLowerCase(java.util.Locale.ITALIAN)
                                    .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                                    .replaceAll("\\s+", " ")
                                    .trim();

                    if (normale.equals("basta")
                            || normale.equals("stop")
                            || normale.contains("smetti di ascoltare")
                            || normale.contains("smettere di ascoltare")) {

                        Log.i(TAG, "STOP VOCALE RICEVUTO: " + testo);
                        stopRichiesto = true;
                        toast("⏹ Lisa ha smesso di ascoltare");
                        break;
                    }

                    LisaVoiceService.eseguiComandoWhisperLocale(
                            testo
                    );

                    Log.i(TAG, "PRONTO PER FRASE SUCCESSIVA");
                }
            }

        } catch (Throwable e) {

            if (!stopRichiesto) {
                Log.e(TAG, "ERRORE WHISPER", e);
                toast("Errore Whisper: " + e.getMessage());
            }

        } finally {

            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Exception ignored) {}
                try { audioRecord.release(); } catch (Exception ignored) {}
                audioRecord = null;
            }

            if (stream != null) {
                try { stream.release(); } catch (Exception ignored) {}
            }

            if (vad != null) {
                try { vad.release(); } catch (Exception ignored) {}
            }

            running = false;
            stopRichiesto = false;
            notificaStato();

            LisaVoiceService.fermaSessioneWhisperLocale();

            Log.i(
                    TAG,
                    "SESSIONE CONTINUA TERMINATA - MIC RILASCIATO"
            );
        }
    }

}
