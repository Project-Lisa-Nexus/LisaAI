package com.lisa.app;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

public class MainActivity extends Activity {
    
    private static final int PERM_REQUEST = 100;
    private static volatile Button btnVoceLisaStatico;
    private LocalWhisperAsrProbeManager localWhisperAsrProbe;

    public static void aggiornaStatoPulsante() {
        Button pulsante = btnVoceLisaStatico;
        if (pulsante == null) return;
        pulsante.post(() -> {
            pulsante.setText(
                LisaVoiceService.isSessioneAttiva()
                        ? "⏹ Ferma Lisa"
                        : "🎤 Attiva Lisa"
            );
        });
    }

    private void gestisciIntentTest(Intent intent) {
        if (intent == null) return;

        if ("com.lisa.nexus.ACTION_ANDROID_ASR_PIPE_PROBE".equals(intent.getAction())) {

            Intent probe = new Intent(this, LisaVoiceService.class);
            probe.setAction("com.lisa.nexus.ACTION_ANDROID_ASR_PIPE_PROBE");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(probe);
            } else {
                startService(probe);
            }

            android.util.Log.i(
                    "LisaMain",
                    "Avviato probe ASR Pipe tramite Activity visibile"
            );
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        gestisciIntentTest(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gestisciIntentTest(getIntent());

        // Richiedi permessi
        richiediPermessi();


        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 100, 40, 40);

        TextView title = new TextView(this);
        title.setText("Lisa - Assistente vocale");
        title.setTextSize(20);
        layout.addView(title);

        Button btnVoceLisa = new Button(this);
        btnVoceLisaStatico = btnVoceLisa;
        btnVoceLisa.setText(
            LisaVoiceService.isSessioneAttiva()
                    ? "⏹ Ferma Lisa"
                    : "🎤 Attiva Lisa"
        );
        btnVoceLisa.setTextSize(20);
        btnVoceLisa.setMinHeight(140);
        btnVoceLisa.setOnClickListener(v -> {

            if (localWhisperAsrProbe != null
                    && localWhisperAsrProbe.isRunning()) {

                localWhisperAsrProbe.start();
                return;
            }

            Intent intent = new Intent(this, LisaVoiceService.class);

            if (LisaVoiceService.isSessioneAttiva()) {
                LisaVoiceService.fermaLisaDaPulsante();
                btnVoceLisa.setText("🎤 Attiva Lisa");
            } else {
                if (LisaAccessibilityService.getInstance() == null) {
                    android.widget.Toast.makeText(
                            this,
                            "Attiva prima Accessibilità Lisa",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();

                    btnVoceLisa.setText("🎤 Attiva Lisa");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                btnVoceLisa.setText("⏹ Ferma Lisa");
            }
        });
        layout.addView(btnVoceLisa);

        // TEST ISOLATO ASR CON AUDIO SOURCE
        Button btnAsrPipeTest = new Button(this);
        btnAsrPipeTest.setText("🧠 TEST VOCE LOCALE");
        btnAsrPipeTest.setTextSize(18);
        btnAsrPipeTest.setMinHeight(120);

        btnAsrPipeTest.setOnClickListener(v -> {

            if (localWhisperAsrProbe == null) {
                localWhisperAsrProbe =
                        new LocalWhisperAsrProbeManager(
                                this,
                                () -> {
                                    boolean localeAttivo =
                                            localWhisperAsrProbe != null
                                                    && localWhisperAsrProbe.isRunning();

                                    btnAsrPipeTest.setText(
                                            localeAttivo
                                                    ? "⏹️ FERMA VOCE LOCALE"
                                                    : "🧠 TEST VOCE LOCALE"
                                    );

                                    btnVoceLisa.setText(
                                            localeAttivo
                                                    ? "⏹️ Ferma Lisa"
                                                    : (
                                                        LisaVoiceService.isSessioneAttiva()
                                                                ? "⏹ Ferma Lisa"
                                                                : "🎤 Attiva Lisa"
                                                    )
                                    );
                                }
                        );
            }

            localWhisperAsrProbe.start();
        });

        layout.addView(btnAsrPipeTest);


        Button btnAccess = new Button(this);
        btnAccess.setText("Apri Impostazioni Accessibilita");
        btnAccess.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btnAccess);


        Button btnHome = new Button(this);
        btnHome.setText("Vai alla Home");
        btnHome.setOnClickListener(v -> {
            LisaAccessibilityService service = LisaAccessibilityService.getInstance();
            if (service != null) {
                service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                );
            }
        });
        layout.addView(btnHome);

        Button btnVolumeUp = new Button(this);
        btnVolumeUp.setText("Volume +");
        btnVolumeUp.setOnClickListener(v -> {
            android.media.AudioManager audioManager =
                (android.media.AudioManager) getSystemService(AUDIO_SERVICE);

            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_RAISE,
                android.media.AudioManager.FLAG_SHOW_UI
            );
        });
        layout.addView(btnVolumeUp);

        Button btnVolumeDown = new Button(this);
        btnVolumeDown.setText("Volume -");
        btnVolumeDown.setOnClickListener(v -> {
            android.media.AudioManager audioManager =
                (android.media.AudioManager) getSystemService(AUDIO_SERVICE);

            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_LOWER,
                android.media.AudioManager.FLAG_SHOW_UI
            );
        });
        layout.addView(btnVolumeDown);

        Button btnBrightnessUp = new Button(this);
        btnBrightnessUp.setText("Luminosita +");
        btnBrightnessUp.setOnClickListener(v -> {
            if (!android.provider.Settings.System.canWrite(this)) {
                Intent intent = new Intent(
                    android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
                return;
            }

            try {
                int current = android.provider.Settings.System.getInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                );

                int nuovo = Math.min(255, current + 25);

                android.provider.Settings.System.putInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                );

                android.provider.Settings.System.putInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    nuovo
                );

                android.view.WindowManager.LayoutParams params =
                    getWindow().getAttributes();
                params.screenBrightness = nuovo / 255f;
                getWindow().setAttributes(params);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        layout.addView(btnBrightnessUp);

        Button btnBrightnessDown = new Button(this);
        btnBrightnessDown.setText("Luminosita -");
        btnBrightnessDown.setOnClickListener(v -> {
            if (!android.provider.Settings.System.canWrite(this)) {
                Intent intent = new Intent(
                    android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
                return;
            }

            try {
                int current = android.provider.Settings.System.getInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                );

                int nuovo = Math.max(1, current - 25);

                android.provider.Settings.System.putInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                );

                android.provider.Settings.System.putInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    nuovo
                );

                android.view.WindowManager.LayoutParams params =
                    getWindow().getAttributes();
                params.screenBrightness = nuovo / 255f;
                getWindow().setAttributes(params);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        layout.addView(btnBrightnessDown);

        Button btnBack = new Button(this);
        btnBack.setText("Indietro");
        btnBack.setOnClickListener(v -> {
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        });
        layout.addView(btnBack);

        Button btnRecent = new Button(this);
        btnRecent.setText("Recenti");
        btnRecent.setOnClickListener(v -> {
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS);
        });
        layout.addView(btnRecent);

        Button btnNotif = new Button(this);
        btnNotif.setText("Notifiche");
        btnNotif.setOnClickListener(v -> {
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        });
        layout.addView(btnNotif);

        Button btnSettings = new Button(this);
        btnSettings.setText("Impostazioni");
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        });
        layout.addView(btnSettings);

        Button btnLock=new Button(this);
        btnLock.setText("Blocca schermo");
        btnLock.setOnClickListener(v->{
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        });
        layout.addView(btnLock);

        Button btnScreenshot=new Button(this);
        btnScreenshot.setText("Screenshot");
        btnScreenshot.setOnClickListener(v->{
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
        });
        layout.addView(btnScreenshot);

        Button btnTorch=new Button(this);
        btnTorch.setText("Torcia");
        final boolean[] torciaAccesa = {false};

        btnTorch.setOnClickListener(v -> {
            try {
                android.hardware.camera2.CameraManager cameraManager =
                    (android.hardware.camera2.CameraManager) getSystemService(CAMERA_SERVICE);

                String cameraId = null;

                for (String id : cameraManager.getCameraIdList()) {
                    android.hardware.camera2.CameraCharacteristics c =
                        cameraManager.getCameraCharacteristics(id);

                    Boolean flash =
                        c.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);

                    Integer facing =
                        c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);

                    if (Boolean.TRUE.equals(flash) &&
                        facing != null &&
                        facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id;
                        break;
                    }
                }

                if (cameraId == null) {
                    android.widget.Toast.makeText(
                        this,
                        "Torcia non disponibile",
                        android.widget.Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                torciaAccesa[0] = !torciaAccesa[0];
                cameraManager.setTorchMode(cameraId, torciaAccesa[0]);

                btnTorch.setText(torciaAccesa[0] ? "Spegni torcia" : "Torcia");

            } catch (Exception e) {
                android.widget.Toast.makeText(
                    this,
                    "Errore torcia: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG
                ).show();
            }
        });
        layout.addView(btnTorch);

        Button btnVibra=new Button(this);
        btnVibra.setText("Vibrazione");
        btnVibra.setOnClickListener(v->{
            android.os.Vibrator vib=(android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(vib!=null){
                if(android.os.Build.VERSION.SDK_INT>=26)
                    vib.vibrate(android.os.VibrationEffect.createOneShot(250,android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                else
                    vib.vibrate(250);
            }
        });
        layout.addView(btnVibra);





        android.widget.ScrollView scrollView =
            new android.widget.ScrollView(this);

        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setSmoothScrollingEnabled(true);
        scrollView.setFocusable(true);
        scrollView.setContentDescription("Controlli Lisa scorrevoli");

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void richiediPermessi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permessi = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_CONTACTS
            };
            
            for (String permesso : permessi) {
                if (checkSelfPermission(permesso) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(permessi, PERM_REQUEST);
                    break;
                }
            }
        }
    }
}
