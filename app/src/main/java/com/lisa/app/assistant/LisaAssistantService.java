package com.lisa.app.assistant;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

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
    }

    @Override
    public void onShutdown() {
        Log.i(TAG, "Lisa Assistant arrestata");
        super.onShutdown();
    }
}
