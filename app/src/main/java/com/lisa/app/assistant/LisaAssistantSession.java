package com.lisa.app.assistant;

import android.content.Context;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

public class LisaAssistantSession
        extends VoiceInteractionSession {

    private static final String TAG = "LisaAssistantSession";

    public LisaAssistantSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Log.i(TAG, "Sessione Lisa aperta");
    }

    @Override
    public void onHide() {
        Log.i(TAG, "Sessione Lisa chiusa");
        super.onHide();
    }
}
