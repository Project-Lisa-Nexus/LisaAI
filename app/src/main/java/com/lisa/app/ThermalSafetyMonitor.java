package com.lisa.app;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.Locale;

final class ThermalSafetyMonitor {

    private static final String TAG = "LisaThermal";

    private static final float BATTERY_WARNING_C = 45.0f;
    private static final float BATTERY_CRITICAL_C = 48.0f;
    private static final float BATTERY_RECOVERY_C = 43.0f;

    private static final long ALERT_COOLDOWN_MS =
            5L * 60L * 1000L;

    private final Context context;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private PowerManager powerManager;

    private PowerManager.OnThermalStatusChangedListener
            thermalListener;

    private boolean started = false;
    private boolean hotState = false;

    private int thermalStatus =
            PowerManager.THERMAL_STATUS_NONE;

    private float batteryTempC = Float.NaN;

    private int lastAlertLevel = 0;
    private long lastAlertTime = 0L;

    ThermalSafetyMonitor(Context context) {
        this.context =
                context.getApplicationContext();
    }

    private final BroadcastReceiver batteryReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(
                        Context context,
                        Intent intent) {

                    updateBattery(intent);
                }
            };

    private final Runnable repeatAlertRunnable =
            new Runnable() {
                @Override
                public void run() {

                    if (!started || !hotState) {
                        return;
                    }

                    evaluate(true);
                }
            };

    void start() {

        boolean monitorAttivo =
                context.getSharedPreferences(
                        "lisa_ui",
                        Context.MODE_PRIVATE
                ).getBoolean(
                        "monitor_temperatura_attivo",
                        true
                );

        if (!monitorAttivo) {

            Log.i(
                    TAG,
                    "Monitor disattivato da impostazioni"
            );

            return;
        }

        if (started) {
            return;
        }

        started = true;

        IntentFilter filter =
                new IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED
                );

        Intent sticky;

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU) {

            sticky = context.registerReceiver(
                    batteryReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            sticky = context.registerReceiver(
                    batteryReceiver,
                    filter
            );
        }

        if (sticky != null) {
            updateBattery(sticky);
        }

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.Q) {

            powerManager =
                    (PowerManager)
                            context.getSystemService(
                                    Context.POWER_SERVICE
                            );

            if (powerManager != null) {

                thermalStatus =
                        powerManager
                                .getCurrentThermalStatus();

                thermalListener =
                        status -> {

                            thermalStatus = status;

                            Log.i(
                                    TAG,
                                    "Thermal status="
                                            + status
                            );

                            evaluate(false);
                        };

                powerManager
                        .addThermalStatusListener(
                                context.getMainExecutor(),
                                thermalListener
                        );
            }
        }

        Log.i(
                TAG,
                "Thermal Safety Monitor avviato"
        );

        evaluate(false);
    }

    void stop() {

        if (!started) {
            return;
        }

        started = false;

        handler.removeCallbacks(
                repeatAlertRunnable
        );

        try {
            context.unregisterReceiver(
                    batteryReceiver
            );
        } catch (Exception ignored) {
        }

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.Q
                && powerManager != null
                && thermalListener != null) {

            try {
                powerManager
                        .removeThermalStatusListener(
                                thermalListener
                        );
            } catch (Exception ignored) {
            }
        }

        thermalListener = null;
        powerManager = null;

        Log.i(
                TAG,
                "Thermal Safety Monitor fermato"
        );
    }

    private void updateBattery(
            Intent intent) {

        if (intent == null) {
            return;
        }

        int value =
                intent.getIntExtra(
                        BatteryManager.EXTRA_TEMPERATURE,
                        Integer.MIN_VALUE
                );

        if (value == Integer.MIN_VALUE) {
            return;
        }

        batteryTempC =
                value / 10.0f;

        Log.i(
                TAG,
                String.format(
                        Locale.ITALIAN,
                        "Batteria %.1f C",
                        batteryTempC
                )
        );

        evaluate(false);
    }

    private int currentAlertLevel() {

        boolean critical =
                thermalStatus
                        >= PowerManager
                        .THERMAL_STATUS_EMERGENCY
                || (!Float.isNaN(batteryTempC)
                    && batteryTempC
                    >= BATTERY_CRITICAL_C);

        if (critical) {
            return 2;
        }

        boolean warning =
                thermalStatus
                        >= PowerManager
                        .THERMAL_STATUS_CRITICAL
                || (!Float.isNaN(batteryTempC)
                    && batteryTempC
                    >= BATTERY_WARNING_C);

        return warning ? 1 : 0;
    }

    private boolean recovered() {

        boolean batteryOk =
                Float.isNaN(batteryTempC)
                || batteryTempC
                <= BATTERY_RECOVERY_C;

        boolean thermalOk =
                Build.VERSION.SDK_INT
                        < Build.VERSION_CODES.Q
                || thermalStatus
                <= PowerManager
                        .THERMAL_STATUS_MODERATE;

        return batteryOk && thermalOk;
    }

    private void evaluate(
            boolean forcedRepeat) {

        if (!started) {
            return;
        }

        int level =
                currentAlertLevel();

        long now =
                SystemClock.elapsedRealtime();

        if (level > 0) {

            boolean firstAlert =
                    !hotState;

            boolean escalation =
                    level > lastAlertLevel;

            boolean cooldownExpired =
                    now - lastAlertTime
                            >= ALERT_COOLDOWN_MS;

            hotState = true;

            if (firstAlert
                    || escalation
                    || forcedRepeat
                    || cooldownExpired) {

                alertUser(level);

                lastAlertLevel = level;
                lastAlertTime = now;
            }

            handler.removeCallbacks(
                    repeatAlertRunnable
            );

            handler.postDelayed(
                    repeatAlertRunnable,
                    ALERT_COOLDOWN_MS
            );

            return;
        }

        if (hotState && recovered()) {

            hotState = false;
            lastAlertLevel = 0;
            lastAlertTime = 0L;

            handler.removeCallbacks(
                    repeatAlertRunnable
            );

            recoveryMessage();
        }
    }

    public String getStatoDettagliato() {

        if (!started) {
            return "🌡️ Monitor temperatura disattivato da impostazioni";
        }

        int statoAttuale = thermalStatus;

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.Q
                && powerManager != null) {

            try {
                statoAttuale =
                        powerManager
                                .getCurrentThermalStatus();

                thermalStatus =
                        statoAttuale;

            } catch (Exception ignored) {
            }
        }

        ActivityManager activityManager =
                (ActivityManager)
                        context.getSystemService(
                                Context.ACTIVITY_SERVICE
                        );

        String ramText =
                "non disponibile";

        if (activityManager != null) {

            ActivityManager.MemoryInfo memoryInfo =
                    new ActivityManager.MemoryInfo();

            activityManager.getMemoryInfo(
                    memoryInfo
            );

            double totaleGb =
                    memoryInfo.totalMem
                            / 1073741824.0;

            double usataGb =
                    (memoryInfo.totalMem
                            - memoryInfo.availMem)
                            / 1073741824.0;

            ramText =
                    String.format(
                            Locale.ITALIAN,
                            "%.1f / %.1f GB",
                            usataGb,
                            totaleGb
                    );
        }

        String batteriaText;

        if (Float.isNaN(
                batteryTempC)) {

            batteriaText =
                    "non disponibile";

        } else {

            batteriaText =
                    String.format(
                            Locale.ITALIAN,
                            "%.1f °C",
                            batteryTempC
                    );
        }

        String statoNome =
                nomeStatoTermico(
                        statoAttuale
                );

        String statoItaliano =
                descrizioneStatoTermico(
                        statoAttuale
                );

        return "🔋 Batteria: "
                + batteriaText
                + "\n🌡️ Sistema: "
                + statoNome
                + "\n💾 RAM: "
                + ramText
                + "\n"
                + iconaStato(statoAttuale)
                + " Stato: "
                + statoItaliano;
    }

    public String getStatoVocale() {

        if (!started) {
            return "Monitor temperatura disattivato da impostazioni.";
        }

        int statoAttuale = thermalStatus;

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.Q
                && powerManager != null) {

            try {
                statoAttuale =
                        powerManager
                                .getCurrentThermalStatus();

                thermalStatus =
                        statoAttuale;

            } catch (Exception ignored) {
            }
        }

        String batteriaVoce;

        if (Float.isNaN(batteryTempC)) {

            batteriaVoce =
                    "non disponibile";

        } else {

            batteriaVoce =
                    String.format(
                            Locale.ITALIAN,
                            "%.1f gradi",
                            batteryTempC
                    );
        }

        ActivityManager activityManager =
                (ActivityManager)
                        context.getSystemService(
                                Context.ACTIVITY_SERVICE
                        );

        String ramVoce =
                "non disponibile";

        if (activityManager != null) {

            ActivityManager.MemoryInfo memoryInfo =
                    new ActivityManager.MemoryInfo();

            activityManager.getMemoryInfo(
                    memoryInfo
            );

            double totaleGb =
                    memoryInfo.totalMem
                            / 1073741824.0;

            double usataGb =
                    (memoryInfo.totalMem
                            - memoryInfo.availMem)
                            / 1073741824.0;

            ramVoce =
                    String.format(
                            Locale.ITALIAN,
                            "%.1f su %.1f gigabyte",
                            usataGb,
                            totaleGb
                    );
        }

        String statoItaliano =
                descrizioneStatoTermico(
                        statoAttuale
                );

        return "Temperatura batteria: "
                + batteriaVoce
                + ". Stato termico del sistema: "
                + statoItaliano
                + ". Memoria RAM utilizzata: "
                + ramVoce
                + ". Stato generale del telefono: "
                + statoItaliano
                + ".";
    }


    private String nomeStatoTermico(
            int stato) {

        switch (stato) {

            case PowerManager.THERMAL_STATUS_LIGHT:
                return "LIGHT";

            case PowerManager.THERMAL_STATUS_MODERATE:
                return "MODERATE";

            case PowerManager.THERMAL_STATUS_SEVERE:
                return "SEVERE";

            case PowerManager.THERMAL_STATUS_CRITICAL:
                return "CRITICAL";

            case PowerManager.THERMAL_STATUS_EMERGENCY:
                return "EMERGENCY";

            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "SHUTDOWN";

            case PowerManager.THERMAL_STATUS_NONE:
            default:
                return "NONE";
        }
    }

    private String descrizioneStatoTermico(
            int stato) {

        if (stato
                >= PowerManager
                .THERMAL_STATUS_EMERGENCY) {

            return "critico";
        }

        if (stato
                >= PowerManager
                .THERMAL_STATUS_CRITICAL) {

            return "molto caldo";
        }

        if (stato
                >= PowerManager
                .THERMAL_STATUS_SEVERE) {

            return "caldo";
        }

        if (stato
                >= PowerManager
                .THERMAL_STATUS_MODERATE) {

            return "tiepido";
        }

        return "normale";
    }

    private String iconaStato(
            int stato) {

        if (stato
                >= PowerManager
                .THERMAL_STATUS_EMERGENCY) {

            return "🔴";
        }

        if (stato
                >= PowerManager
                .THERMAL_STATUS_CRITICAL) {

            return "🟠";
        }

        return "✅";
    }

    private void alertUser(
            int level) {

        boolean critical =
                level >= 2;

        String visualText =
                critical
                        ? "🔴 TEMPERATURA CRITICA"
                        : "🟠 TELEFONO MOLTO CALDO";

        if (!Float.isNaN(batteryTempC)) {

            visualText +=
                    String.format(
                            Locale.ITALIAN,
                            " • batteria %.1f °C",
                            batteryTempC
                    );
        }

        LisaAccessibilityService
                .aggiornaVignettaSemplice(
                        visualText
                );

        String spokenText =
                critical
                        ? "Pericolo. Temperatura critica. "
                          + "Interrompi il lavoro "
                          + "e lascia raffreddare il telefono."
                        : "Attenzione. "
                          + "Il telefono è molto caldo. "
                          + "Ti consiglio di interrompere "
                          + "il lavoro e lasciarlo raffreddare.";

        /*
         * IMPORTANTE:
         * finché il Voice Engine Pipe è attivo
         * non facciamo parlare automaticamente Lisa,
         * perché il Pipe potrebbe sentire il proprio TTS.
         *
         * A Voice Engine spento l'avviso vocale funziona.
         */
        boolean avvisoVocaleAttivo =
                context.getSharedPreferences(
                        "lisa_ui",
                        Context.MODE_PRIVATE
                ).getBoolean(
                        "avviso_vocale_attivo",
                        true
                );

        if (avvisoVocaleAttivo) {

            LisaSpeaker.parla(
                    context,
                    spokenText,
                    () -> {
                    }
            );

        } else {

            Log.i(
                    TAG,
                    "Avviso vocale termico disattivato"
            );
        }

        boolean notificaAndroidAttiva =
                context.getSharedPreferences(
                        "lisa_ui",
                        Context.MODE_PRIVATE
                ).getBoolean(
                        "notifica_android_attiva",
                        true
                );

        if (notificaAndroidAttiva) {

            Log.d(
                    TAG,
                    "Notifica Android termica abilitata "
                    + "(implementazione futura)"
            );
        }

        Log.w(
                TAG,
                "ALLARME TERMICO"
                        + " level="
                        + level
                        + " battery="
                        + batteryTempC
                        + " thermalStatus="
                        + thermalStatus
        );
    }

    private void recoveryMessage() {

        LisaAccessibilityService
                .aggiornaVignettaSemplice(
                        "✅ Temperatura rientrata nella norma"
                );

        boolean avvisoVocaleAttivo =
                context.getSharedPreferences(
                        "lisa_ui",
                        Context.MODE_PRIVATE
                ).getBoolean(
                        "avviso_vocale_attivo",
                        true
                );

        if (avvisoVocaleAttivo) {

            LisaSpeaker.parla(
                    context,
                    "Temperatura rientrata nella norma.",
                    () -> {
                    }
            );
        }

        Log.i(
                TAG,
                "Temperatura rientrata"
        );
    }
}
