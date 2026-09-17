package com.lisa.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;
import java.util.UUID;

public final class LisaSpeaker {

    private static final String TAG = "LisaSpeaker";

    private static TextToSpeech voce;
    private static boolean pronta = false;
    private static boolean parlando = false;
    private static String testo;
    private static Runnable alTermine;
    private static Context appContextSalvato;
    private static final java.util.ArrayDeque<Object[]> coda =
            new java.util.ArrayDeque<>();

    private LisaSpeaker() {}

    public static synchronized boolean isParlando() {
        return parlando;
    }

    public static synchronized void parla(
            Context context,
            String nuovoTesto,
            Runnable callback) {

        if (nuovoTesto == null ||
                nuovoTesto.trim().isEmpty()) {

            if (callback != null) callback.run();
            return;
        }

        if (context != null) {
            appContextSalvato = context.getApplicationContext();
        }

        if (parlando) {
            coda.add(new Object[]{nuovoTesto.trim(), callback});
            Log.d(TAG, "Accodato mentre Lisa parla: " + nuovoTesto);
            return;
        }

        testo = nuovoTesto.trim();
        alTermine = callback;

        if (voce == null) {
            Context appContext = appContextSalvato;

            voce = new TextToSpeech(
                    appContext,
                    stato -> {

                        synchronized (LisaSpeaker.class) {

                            pronta =
                                    stato ==
                                            TextToSpeech.SUCCESS;

                            if (!pronta) {
                                Log.e(TAG,
                                        "TTS non disponibile");
                                completa();
                                return;
                            }

                            voce.setLanguage(
                                    Locale.ITALIAN
                            );

                            voce.setAudioAttributes(
                                    new android.media.AudioAttributes.Builder()
                                            .setUsage(
                                                    android.media.AudioAttributes.USAGE_ASSISTANT
                                            )
                                            .setContentType(
                                                    android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                                            )
                                            .build()
                            );

                            voce.setOnUtteranceProgressListener(
                                    new UtteranceProgressListener() {

                                        @Override
                                        public void onStart(String id) {
                                        }

                                        @Override
                                        public void onDone(String id) {
                                            completa();
                                        }

                                        @Override
                                        public void onError(String id) {
                                            completa();
                                        }
                                    });

                            pronuncia();
                        }
                    }
            );

        } else if (pronta) {
            pronuncia();
        }
    }

    private static synchronized void pronuncia() {

        if (!pronta ||
                voce == null ||
                testo == null) {
            return;
        }

        LisaVoiceService.sospendiPerTts();

        parlando = true;
        VoiceController.getInstance().speakingStarted();

        String id =
                "LISA_" +
                        UUID.randomUUID().toString();

        android.os.Bundle parametriVoce = new android.os.Bundle();
        parametriVoce.putFloat(
                TextToSpeech.Engine.KEY_PARAM_VOLUME,
                1.0f
        );

        voce.speak(
                testo,
                TextToSpeech.QUEUE_FLUSH,
                parametriVoce,
                id
        );
    }

    private static void completa() {

        Runnable callback;
        Object[] prossimo;

        synchronized (LisaSpeaker.class) {

            parlando = false;
            VoiceController.getInstance().speakingFinished();

            callback = alTermine;
            testo = null;
            alTermine = null;

            prossimo = coda.poll();
        }

        if (callback != null) {
            try {
                callback.run();
            } catch (Exception ignored) {
            }
        }

        if (prossimo != null) {
            String prossimoTesto = (String) prossimo[0];
            Runnable prossimoCallback = (Runnable) prossimo[1];
            parla(appContextSalvato, prossimoTesto, prossimoCallback);
            return;
        }

        LisaVoiceService.riprendiDopoTts();
    }

    public static synchronized void interrompi() {

        testo = null;
        alTermine = null;
        parlando = false;
        coda.clear();
        VoiceController.getInstance().reset();

        if (voce != null) {
            try {
                voce.stop();
            } catch (Exception ignored) {
            }
        }
    }

    public static synchronized void spegni() {

        testo = null;
        alTermine = null;
        parlando = false;
        pronta = false;
        coda.clear();
        VoiceController.getInstance().reset();

        if (voce != null) {

            try {
                voce.stop();
            } catch (Exception ignored) {
            }

            try {
                voce.shutdown();
            } catch (Exception ignored) {
            }

            voce = null;
        }
    }
}
