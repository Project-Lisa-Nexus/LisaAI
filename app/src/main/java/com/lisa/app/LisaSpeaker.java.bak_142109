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

        testo = nuovoTesto.trim();
        alTermine = callback;

        if (voce == null) {

            Context appContext =
                    context.getApplicationContext();

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

        // PRIMA di parlare Lisa smette di ascoltare.
        LisaVoiceService.sospendiPerTts();

        parlando = true;

        String id =
                "LISA_" +
                UUID.randomUUID().toString();

        voce.speak(
                testo,
                TextToSpeech.QUEUE_FLUSH,
                null,
                id
        );
    }

    private static void completa() {

        Runnable callback;

        synchronized (LisaSpeaker.class) {

            parlando = false;

            callback = alTermine;

            testo = null;
            alTermine = null;
        }

        if (callback != null) {
            try {
                callback.run();
            } catch (Exception ignored) {
            }
        }

        // Solo DOPO che Lisa ha finito di parlare
        // può tornare ad ascoltare.
        LisaVoiceService.riprendiDopoTts();
    }

    public static synchronized void interrompi() {

        testo = null;
        alTermine = null;
        parlando = false;

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
