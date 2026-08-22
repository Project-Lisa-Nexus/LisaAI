package com.lisa.app.assistant;

import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public class LisaAssistantSessionService
        extends VoiceInteractionSessionService {

    @Override
    public VoiceInteractionSession onNewSession(
            android.os.Bundle args) {

        return new LisaAssistantSession(this);
    }
}
