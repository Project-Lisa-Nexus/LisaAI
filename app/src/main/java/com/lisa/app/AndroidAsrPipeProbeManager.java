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
    private static final long SILENZIO_TIMEOUT_MS = 120000;

    public interface SegmentCallback {
        void onSegment(String frase);
    }

    private final Context context;
    private final Runnable alTermine;
    private final SegmentCallback segmentCallback;
    private final Runnable onSpeechStart;
    private final Runnable onSpeechEnd;
    private volatile boolean focusDucked = false;
    private volatile boolean speechActive = false;

    private SpeechRecognizer recognizer;
    private AudioRecord audioRecord;
    private ParcelFileDescriptor readFd;
    private ParcelFileDescriptor writeFd;
    private OutputStream output;

    private volatile boolean running = false;
    private volatile boolean pcmMuted = false;
    private volatile long ultimoSegmentoMs = 0L;
    private Thread audioThread;
    private final AtomicBoolean finito = new AtomicBoolean(false);

    public boolean isRunning() {
        return running;
    }

    public void setPcmMuted(boolean muted) {

        pcmMuted = muted;

        if (!muted) {
            ultimoSegmentoMs =
                    android.os.SystemClock.elapsedRealtime();
        }

        Log.i(
                TAG,
                muted
                        ? "PCM ASR silenziato per TTS"
                        : "PCM ASR microfono ripristinato"
        );
    }

    public AndroidAsrPipeProbeManager(
            Context context,
            Runnable alTermine) {

        this(context, alTermine, null, null, null);
    }

    public AndroidAsrPipeProbeManager(
            Context context,
            Runnable alTermine,
            SegmentCallback segmentCallback) {

        this(context, alTermine, segmentCallback, null, null);
    }

    public AndroidAsrPipeProbeManager(
            Context context, Runnable alTermine,
            SegmentCallback segmentCallback,
            Runnable onSpeechStart, Runnable onSpeechEnd) {
        this.context = context.getApplicationContext();
        this.alTermine = alTermine;
        this.segmentCallback = segmentCallback;
        this.onSpeechStart = onSpeechStart;
        this.onSpeechEnd = onSpeechEnd;
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
        ultimoSegmentoMs =
                android.os.SystemClock.elapsedRealtime();

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

        boolean timeoutSilenzio = false;

        try {
            while (running) {

                long adesso =
                        android.os.SystemClock.elapsedRealtime();

                if (adesso - ultimoSegmentoMs
                        >= SILENZIO_TIMEOUT_MS) {

                    timeoutSilenzio = true;
                    break;
                }

                AudioRecord audio = audioRecord;
                if (audio == null) break;

                int letti =
                        audio.read(
                                pcm,
                                0,
                                pcm.length
                        );

                if (letti <= 0) continue;

                if (pcmMuted) {

                    java.util.Arrays.fill(
                            bytes,
                            0,
                            letti * 2,
                            (byte) 0
                    );

                } else {

                    for (int i = 0; i < letti; i++) {
                        short v = pcm[i];

                        bytes[i * 2] =
                                (byte) (v & 0xff);

                        bytes[i * 2 + 1] =
                                (byte) ((v >> 8) & 0xff);
                    }
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
                    timeoutSilenzio
                            ? "Flusso PCM chiuso per timeout silenzio"
                            : "Flusso PCM chiuso"
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
                speechActive = true;
                if (!focusDucked && onSpeechStart != null) {
                    focusDucked = true;
                    onSpeechStart.run();
                }
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.i(TAG, "onEndOfSpeech");
                speechActive = false;
            }

            @Override
            public void onError(int error) {
                running = false;
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

                if (segmentCallback != null
                        && testi != null
                        && !testi.isEmpty()) {

                    String primaFrase = testi.get(0);

                    if (primaFrase != null
                            && !primaFrase.trim().isEmpty()) {

                        ultimoSegmentoMs =
                                android.os.SystemClock.elapsedRealtime();

                        segmentCallback.onSegment(
                                primaFrase.trim()
                        );
                    }
                }
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

        speechActive = false;

        if (focusDucked) {
            focusDucked = false;
            if (onSpeechEnd != null) onSpeechEnd.run();
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
