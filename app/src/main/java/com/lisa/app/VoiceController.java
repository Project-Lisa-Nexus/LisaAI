package com.lisa.app;

import android.util.Log;

/**
 * VoiceController
 *
 * Macchina a stati del Voice Engine di Lisa.
 *
 * NON possiede un SpeechRecognizer.
 * NON crea un secondo recognizer.
 * Coordina esclusivamente lo stato della sessione vocale.
 */
public final class VoiceController {

    private static final String TAG = "LisaVoiceController";

    public enum State {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        WAITING_FOLLOWUP,
        STOPPING
    }

    private State state = State.IDLE;

    private static final VoiceController INSTANCE =
            new VoiceController();

    public static VoiceController getInstance() {
        return INSTANCE;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized boolean is(State expected) {
        return state == expected;
    }

    public synchronized void setState(State newState) {
        if (newState == null) {
            return;
        }

        State oldState = state;

        if (oldState == newState) {
            return;
        }

        state = newState;

        Log.d(
                TAG,
                "STATO: " + oldState + " -> " + newState
        );
    }

    public synchronized boolean isActive() {
        return state != State.IDLE
                && state != State.STOPPING;
    }

    public synchronized boolean canStartListening() {
        return state == State.IDLE
                || state == State.PROCESSING
                || state == State.WAITING_FOLLOWUP;
    }

    public synchronized boolean canProcess() {
        return state == State.LISTENING;
    }

    public synchronized boolean canSpeak() {
        return state == State.PROCESSING
                || state == State.WAITING_FOLLOWUP;
    }

    public synchronized boolean canStop() {
        return state != State.STOPPING;
    }

    public synchronized void startSession() {
        setState(State.IDLE);
    }

    public synchronized void listeningStarted() {
        setState(State.LISTENING);
    }

    public synchronized void processingStarted() {
        setState(State.PROCESSING);
    }

    public synchronized void speakingStarted() {
        setState(State.SPEAKING);
    }

    public synchronized void waitingFollowup() {
        setState(State.WAITING_FOLLOWUP);
    }

    public synchronized void speakingFinished() {
        if (state == State.SPEAKING) {
            setState(State.IDLE);
        }
    }

    public synchronized void listeningFinished() {
        if (state == State.LISTENING) {
            setState(State.PROCESSING);
        }
    }

    public synchronized void stopSession() {
        setState(State.STOPPING);
    }

    public synchronized void reset() {
        setState(State.IDLE);
    }
}
