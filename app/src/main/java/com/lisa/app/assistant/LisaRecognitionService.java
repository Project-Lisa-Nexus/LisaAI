package com.lisa.app.assistant;

import android.content.Intent;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

public class LisaRecognitionService extends RecognitionService {

    @Override
    protected void onStartListening(
            Intent recognizerIntent,
            Callback callback) {

        // Motore vocale reale collegato nel prossimo passaggio.
        try {
            callback.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (android.os.RemoteException e) {
            // Il client vocale si è disconnesso.
        }
    }

    @Override
    protected void onStopListening(Callback callback) {
    }

    @Override
    protected void onCancel(Callback callback) {
    }
}
