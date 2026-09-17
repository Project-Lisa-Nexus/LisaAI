package com.lisa.app;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;

public class SystemController {

    public static boolean regola(
            Context context,
            String tipo,
            String operazione,
            String valoreTesto) {

        if (context == null || tipo == null || operazione == null)
            return false;

        tipo = tipo.trim().toLowerCase();
        operazione = operazione.trim().toLowerCase();

        Integer valore = null;

        if (valoreTesto != null && !valoreTesto.trim().isEmpty()) {
            try {
                valore = Integer.parseInt(
                        valoreTesto.replace("%", "").trim()
                );
                valore = Math.max(0, Math.min(100, valore));
            } catch (Exception ignored) {
            }
        }

        if ("volume".equals(tipo)) {
            return regolaVolume(context, operazione, valore);
        }

        if ("luminosita".equals(tipo)
                || "luminosità".equals(tipo)
                || "brightness".equals(tipo)) {

            return regolaLuminosita(context, operazione, valore);
        }

        return false;
    }


    // ========================================================
    // VOLUME
    // ========================================================

    private static boolean regolaVolume(
            Context context,
            String operazione,
            Integer percentuale) {
        try {
            AudioManager audio =
                    (AudioManager) context.getSystemService(
                            Context.AUDIO_SERVICE);

            if (audio == null || audio.isVolumeFixed())
                return false;

            final int stream = AudioManager.STREAM_MUSIC;
            final int max = audio.getStreamMaxVolume(stream);

            if (max <= 0)
                return false;

            if (percentuale == null) {
                if ("aumenta".equals(operazione)) {
                    audio.adjustStreamVolume(
                            stream,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI);
                    return true;
                }

                if ("diminuisci".equals(operazione)) {
                    audio.adjustStreamVolume(
                            stream,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI);
                    return true;
                }

                return false;
            }

            int p = Math.max(0, Math.min(100, percentuale));
            int corrente = audio.getStreamVolume(stream);
            int nuovo;

            if ("imposta".equals(operazione)) {
                nuovo = Math.round(max * p / 100f);

            } else if ("aumenta".equals(operazione)) {
                int delta = Math.max(1, Math.round(corrente * p / 100f));
                nuovo = Math.min(max, corrente + delta);

            } else if ("diminuisci".equals(operazione)) {
                int delta = Math.max(1, Math.round(corrente * p / 100f));
                nuovo = Math.max(0, corrente - delta);

            } else {
                return false;
            }

            int direzione = (nuovo > corrente)
                    ? AudioManager.ADJUST_RAISE
                    : AudioManager.ADJUST_LOWER;

            int letto = corrente;
            int iterazioniMax = 40;
            int contatore = 0;

            while (contatore < iterazioniMax) {
                boolean targetRaggiunto =
                        (direzione == AudioManager.ADJUST_RAISE && letto >= nuovo)
                        || (direzione == AudioManager.ADJUST_LOWER && letto <= nuovo);

                if (targetRaggiunto) break;

                audio.adjustStreamVolume(
                        stream,
                        direzione,
                        AudioManager.FLAG_SHOW_UI);

                contatore++;
                letto = audio.getStreamVolume(stream);
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private static boolean regolaLuminosita(
            Context context,
            String operazione,
            Integer percentuale) {
        try {
            if (!Settings.System.canWrite(context))
                return false;

            int corrente = Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);

            int nuovo;

            if ("imposta".equals(operazione)) {
                if (percentuale == null)
                    return false;

                int p = Math.max(0, Math.min(100, percentuale));
                nuovo = Math.round(255f * p / 100f);

            } else if ("aumenta".equals(operazione)) {
                if (percentuale == null) {
                    nuovo = corrente + 25;
                } else {
                    int p = Math.max(0, Math.min(100, percentuale));
                    int delta = Math.max(1, Math.round(corrente * p / 100f));
                    nuovo = corrente + delta;
                }

            } else if ("diminuisci".equals(operazione)) {
                if (percentuale == null) {
                    nuovo = corrente - 25;
                } else {
                    int p = Math.max(0, Math.min(100, percentuale));
                    int delta = Math.max(1, Math.round(corrente * p / 100f));
                    nuovo = corrente - delta;
                }

            } else {
                return false;
            }

            nuovo = Math.max(1, Math.min(255, nuovo));

            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

            return Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    nuovo);

        } catch (Exception e) {
            return false;
        }
    }

    // ========================================================
    // TORCIA
    // ========================================================

    private static boolean torciaAccesa = false;

    public static boolean toggleTorcia(Context context) {
        try {
            android.hardware.camera2.CameraManager cameraManager =
                (android.hardware.camera2.CameraManager)
                    context.getSystemService(Context.CAMERA_SERVICE);

            String cameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics c =
                    cameraManager.getCameraCharacteristics(id);
                Boolean flash = c.get(
                    android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && facing != null &&
                    facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) return false;

            torciaAccesa = !torciaAccesa;
            cameraManager.setTorchMode(cameraId, torciaAccesa);
            return true;

        } catch (Exception e) {
            return false;
        }
    }
}