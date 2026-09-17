package com.lisa.app.assistant;

import android.service.voice.VoiceInteractionService;
import android.content.Intent;
import android.util.Log;
import com.lisa.app.LisaVoiceService;

public class LisaAssistantService extends VoiceInteractionService {

    private static final String TAG = "LisaAssistant";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Lisa Assistant Service creato");
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.i(TAG, "Lisa Assistant pronta");

        Log.i(TAG, "Lisa Assistant pronta: avvio microfono manuale");
    }

    @Override
    public void onShutdown() {
        Log.i(TAG, "Lisa Assistant arrestata");
        super.onShutdown();
    }
}
