package com.lisa.app;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AndroidAsrPipeProbeManager {

    private static final String TAG = "LisaAsrPipe";
    private static final int SAMPLE_RATE = 16000;
    private static final long DURATA_MS = 12000;

    private final Context context;
    private final Runnable alTermine;

    private SpeechRecognizer recognizer;
    private AudioRecord audioRecord;
    private ParcelFileDescriptor readFd;
    private ParcelFileDescriptor writeFd;
    private OutputStream output;

    private volatile boolean running = false;
    private Thread audioThread;
    private final AtomicBoolean finito = new AtomicBoolean(false);

    public AndroidAsrPipeProbeManager(
            Context context,
            Runnable alTermine) {

        this.context = context.getApplicationContext();
        this.alTermine = alTermine;
    }

    public void start() {

        if (Build.VERSION.SDK_INT < 33) {
            Log.e(TAG, "API < 33: EXTRA_AUDIO_SOURCE non disponibile");
            termina();
            return;
        }

        try {
            ParcelFileDescriptor[] pipe =
                    ParcelFileDescriptor.createPipe();

            readFd = pipe[0];
            writeFd = pipe[1];

            if (SpeechRecognizer
                    .isOnDeviceRecognitionAvailable(context)) {

                recognizer =
                        SpeechRecognizer
                                .createOnDeviceSpeechRecognizer(context);

                Log.i(TAG, "Recognizer ON-DEVICE");
            } else {
                recognizer =
                        SpeechRecognizer
                                .createSpeechRecognizer(context);

                Log.i(TAG, "Recognizer STANDARD");
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
                    "it-IT"
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE,
                    readFd
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,
                    1
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                    SAMPLE_RATE
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE
            );

            Log.i(TAG, "startListening con AUDIO_SOURCE + SEGMENTED");

            recognizer.startListening(intent);

            avviaAudioRecord();

        } catch (Throwable e) {
            Log.e(TAG, "Errore avvio probe", e);
            termina();
        }
    }

    private void avviaAudioRecord() throws Exception {

        int minimo =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

        if (minimo <= 0) {
            throw new IllegalStateException(
                    "Buffer AudioRecord non valido: " + minimo
            );
        }

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
                    "AudioRecord non inizializzato"
            );
        }

        output =
                new ParcelFileDescriptor
                        .AutoCloseOutputStream(writeFd);

        writeFd = null;

        audioRecord.startRecording();

        if (audioRecord.getRecordingState()
                != AudioRecord.RECORDSTATE_RECORDING) {

            throw new IllegalStateException(
                    "AudioRecord non entra in RECORDING"
            );
        }

        running = true;

        Log.i(TAG, "AudioRecord attivo: parla ora");

        audioThread =
                new Thread(
                        this::cicloAudio,
                        "Lisa-AndroidAsrPipe"
                );

        audioThread.start();
    }

    private void cicloAudio() {

        short[] pcm = new short[1600];
        byte[] bytes = new byte[pcm.length * 2];

        long fine =
                android.os.SystemClock.elapsedRealtime()
                        + DURATA_MS;

        try {
            while (running
                    && android.os.SystemClock.elapsedRealtime() < fine) {

                AudioRecord audio = audioRecord;
                if (audio == null) break;

                int letti =
                        audio.read(
                                pcm,
                                0,
                                pcm.length
                        );

                if (letti <= 0) continue;

                for (int i = 0; i < letti; i++) {
                    short v = pcm[i];

                    bytes[i * 2] =
                            (byte) (v & 0xff);

                    bytes[i * 2 + 1] =
                            (byte) ((v >> 8) & 0xff);
                }

                OutputStream out = output;

                if (out == null) break;

                out.write(
                        bytes,
                        0,
                        letti * 2
                );
            }

        } catch (Throwable e) {
            Log.e(TAG, "Errore flusso PCM", e);

        } finally {

            running = false;
            fermaAudio();

            Log.i(
                    TAG,
                    "Flusso PCM chiuso dopo 12 secondi"
            );
        }
    }

    private RecognitionListener creaListener() {

        return new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.i(TAG, "onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.i(TAG, "onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.i(TAG, "onEndOfSpeech");
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "onError=" + error);
                termina();
            }

            @Override
            public void onResults(Bundle results) {

                ArrayList<String> testi =
                        results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                Log.i(
                        TAG,
                        "onResults=" + testi
                );

                termina();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {

                ArrayList<String> testi =
                        partialResults.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                Log.i(
                        TAG,
                        "PARTIAL=" + testi
                );
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onSegmentResults(Bundle segmentResults) {

                ArrayList<String> testi =
                        segmentResults.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                Log.i(
                        TAG,
                        "SEGMENT=" + testi
                );
            }

            @Override
            public void onEndOfSegmentedSession() {
                Log.i(TAG, "END_SEGMENTED");
                termina();
            }
        };
    }

    private synchronized void fermaAudio() {

        running = false;

        if (audioRecord != null) {

            try {
                audioRecord.stop();
            } catch (Exception ignored) {}

            try {
                audioRecord.release();
            } catch (Exception ignored) {}

            audioRecord = null;
        }

        if (output != null) {

            try {
                output.close();
            } catch (Exception ignored) {}

            output = null;
        }
    }

    public void release() {
        termina();
    }

    private void termina() {

        if (!finito.compareAndSet(false, true)) {
            return;
        }

        fermaAudio();

        if (readFd != null) {
            try {
                readFd.close();
            } catch (Exception ignored) {}
            readFd = null;
        }

        if (writeFd != null) {
            try {
                writeFd.close();
            } catch (Exception ignored) {}
            writeFd = null;
        }

        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {}

            try {
                recognizer.destroy();
            } catch (Exception ignored) {}

            recognizer = null;
        }

        Log.i(TAG, "PROBE TERMINATO");

        if (alTermine != null) {
            try {
                alTermine.run();
            } catch (Exception ignored) {}
        }
    }
}
