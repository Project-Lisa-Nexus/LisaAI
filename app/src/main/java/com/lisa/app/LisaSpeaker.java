package com.lisa.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.UUID;

public final class LisaSpeaker {

    private static TextToSpeech voce;
    private static String testo;
    private static Runnable alTermine;
    private static boolean pronta;

    private LisaSpeaker() {}

    public static synchronized void parla(
            Context context,
            String nuovoTesto,
            Runnable fine) {

        testo = nuovoTesto;
        alTermine = fine;

        if (voce == null) {
            voce = new TextToSpeech(
                context.getApplicationContext(),
                risultato -> {
                    synchronized (LisaSpeaker.class) {
                        if (risultato != TextToSpeech.SUCCESS) {
                            termina();
                            return;
                        }

                        voce.setLanguage(Locale.ITALIAN);
                        voce.setSpeechRate(0.92f);

                        voce.setOnUtteranceProgressListener(
                            new UtteranceProgressListener() {
                                @Override
                                public void onStart(String id) {}

                                @Override
                                public void onDone(String id) {
                                    termina();
                                }

                                @Override
                                public void onError(String id) {
                                    termina();
                                }

                                @Override
                                public void onError(
                                        String id,
                                        int codice) {
                                    termina();
                                }
                            }
                        );

                        pronta = true;
                        pronuncia();
                    }
                }
            );
        } else if (pronta) {
            pronuncia();
        }
    }

    private static synchronized void pronuncia() {
        if (voce == null || testo == null) {
            termina();
            return;
        }

        String id = UUID.randomUUID().toString();

        int risultato = voce.speak(
            testo,
            TextToSpeech.QUEUE_FLUSH,
            null,
            id
        );

        testo = null;

        if (risultato == TextToSpeech.ERROR) {
            termina();
        }
    }

    private static synchronized void termina() {
        Runnable callback = alTermine;

        alTermine = null;
        testo = null;
        pronta = false;

        if (voce != null) {
            voce.stop();
            voce.shutdown();
            voce = null;
        }

        if (callback != null) {
            callback.run();
        }
    }
}
