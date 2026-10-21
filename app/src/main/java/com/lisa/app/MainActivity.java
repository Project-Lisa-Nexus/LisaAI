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
    private android.widget.RadioButton dotAccess;
    
    private static final int PERM_REQUEST = 100;
    private static volatile Button btnVoceLisaStatico;
    private static volatile TextView statoLisaStatico;
    private LocalWhisperAsrProbeManager localWhisperAsrProbe;
    private Button btnAsrPipeTest;

    public static void aggiornaStatoPulsante() {
        Button pulsante = btnVoceLisaStatico;
        TextView stato = statoLisaStatico;

        if (pulsante != null) {
            pulsante.post(() -> pulsante.setText(
                    LisaVoiceService.getStato()
                            == LisaVoiceService.LisaState.READY
                            ? "🎤 Attiva Lisa"
                            : "⏹ Ferma Lisa"
            ));
        }

        if (stato != null) {
            stato.post(() -> {
                if (LisaAccessibilityService.getInstance() == null) {
                    stato.setText("○ Accessibilità Lisa non attiva");
                } else if (LisaVoiceService.getStato()
                        == LisaVoiceService.LisaState.READY) {
                    stato.setText("● Lisa pronta");
                } else {
                    stato.setText("● Lisa in ascolto");
                }
            });
        }
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


        // ===== LISA ASSIST - DASHBOARD V1 =====
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        float density = getResources().getDisplayMetrics().density;
        int paddingLaterale = Math.round(18 * density);
        int paddingAlto = Math.round(24 * density);
        int paddingBasso = Math.round(24 * density);
        int gapDashboard = Math.round(6 * density);

        layout.setPadding(
                paddingLaterale,
                paddingAlto,
                paddingLaterale,
                paddingBasso
        );

        TextView title = new TextView(this);
        title.setText("Lisa Assist");
        title.setTextSize(26);
        title.setTextColor(android.graphics.Color.parseColor("#000000"));
        title.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        );
        layout.addView(title);


        // Rimosso Lisa pronta

        // ===== ACCESSIBILITÀ =====
        android.widget.LinearLayout rowAccess = new android.widget.LinearLayout(this);
        rowAccess.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rowAccess.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rowAccess.setPadding(0, Math.round(12 * density), 0, 0);

        android.widget.TextView tvAccess = new android.widget.TextView(this);
        tvAccess.setText("♿ Accessibilità");
        tvAccess.setTextSize(20);
        tvAccess.setTextColor(0xFF000000);
        tvAccess.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvAccess.setPadding(0, 0, Math.round(38 * density), 0);
        tvAccess.setClickable(true);

        android.view.View spacerAccess = new android.view.View(this);
        spacerAccess.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        spacerAccess.setClickable(false);
        spacerAccess.setFocusable(false);

        dotAccess = new android.widget.RadioButton(this);
        dotAccess.setClickable(false);
        dotAccess.setFocusable(false);
        dotAccess.setPadding(0,0,0,0);
        dotAccess.setMinimumWidth(0);
        dotAccess.setMinimumHeight(0);
        int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
        int[] colors = new int[] { 0xFF2196F3, 0xFF000000 };
        dotAccess.setButtonTintList(new android.content.res.ColorStateList(states, colors));
        dotAccess.setClickable(false);
        android.widget.LinearLayout.LayoutParams dotLp = new android.widget.LinearLayout.LayoutParams(
                Math.round(16 * density), Math.round(16 * density));
        dotLp.rightMargin = Math.round(16 * density);
        dotAccess.setLayoutParams(dotLp);
        aggiornaPallinoAccessibilita();

        android.widget.TextView tvFrecciaAccess = new android.widget.TextView(this);
        tvFrecciaAccess.setText("›");
        tvFrecciaAccess.setTextSize(24);
        tvFrecciaAccess.setTextColor(0xFF000000);
        tvFrecciaAccess.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvFrecciaAccess.setPadding(Math.round(16 * density), Math.round(20 * density), Math.round(32 * density), Math.round(20 * density));
        tvFrecciaAccess.setClickable(true);
        tvFrecciaAccess.setFocusable(true);

        android.view.View.OnClickListener apriAccess = new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        };
        tvAccess.setOnClickListener(apriAccess);
        tvFrecciaAccess.setOnClickListener(apriAccess);

        rowAccess.addView(tvAccess);
        rowAccess.addView(spacerAccess);
        rowAccess.addView(dotAccess);
        rowAccess.addView(tvFrecciaAccess);
        layout.addView(rowAccess);

        android.widget.TextView descAccess = new android.widget.TextView(this);
        descAccess.setText("Servizio Lisa, autorizzazioni e stato del sistema");
        descAccess.setTextSize(16);
        descAccess.setTextColor(0xFF000000);
        android.widget.LinearLayout.LayoutParams descAccessLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        
        descAccessLp.topMargin = Math.round(-16 * density); // Attaccato al titolo
        descAccessLp.bottomMargin = Math.round(14 * density); // Spazio sotto
        layout.addView(descAccess, descAccessLp);

        // ===== IMPOSTAZIONI =====
        android.widget.LinearLayout rowImpostazioni = new android.widget.LinearLayout(this);
        rowImpostazioni.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rowImpostazioni.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rowImpostazioni.setPadding(0, 0, 0, Math.round(12 * density));

        android.widget.TextView tvImpostazioni = new android.widget.TextView(this);
        tvImpostazioni.setText("⚙ Impostazioni");
        tvImpostazioni.setTextSize(20);
        tvImpostazioni.setTextColor(0xFF000000);
        tvImpostazioni.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvImpostazioni.setPadding(0, 0, Math.round(38 * density), 0);
        tvImpostazioni.setClickable(true);

        android.view.View spacerImp = new android.view.View(this);
        spacerImp.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        spacerImp.setClickable(false);
        spacerImp.setFocusable(false);

        android.widget.TextView tvFrecciaImp = new android.widget.TextView(this);
        tvFrecciaImp.setText("›");
        tvFrecciaImp.setTextSize(24);
        tvFrecciaImp.setTextColor(0xFF000000);
        tvFrecciaImp.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        tvFrecciaImp.setPadding(Math.round(16 * density), Math.round(20 * density), Math.round(32 * density), Math.round(20 * density));
        tvFrecciaImp.setClickable(true);
        tvFrecciaImp.setFocusable(true);

        android.view.View.OnClickListener apriImp = new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, LisaSettingsActivity.class));
            }
        };
        tvImpostazioni.setOnClickListener(apriImp);
        tvFrecciaImp.setOnClickListener(apriImp);

        rowImpostazioni.addView(tvImpostazioni);
        rowImpostazioni.addView(spacerImp);
        rowImpostazioni.addView(tvFrecciaImp);
        layout.addView(rowImpostazioni);

        Button btnVoceLisa = new Button(this);
        btnVoceLisaStatico = btnVoceLisa;
        btnVoceLisa.setText(
            LisaVoiceService.getStato()
                    == LisaVoiceService.LisaState.READY
                    ? "🎤 Attiva Lisa"
                    : "⏹ Ferma Lisa"
        );
        btnVoceLisa.setTextSize(20);
        btnVoceLisa.setTextColor(0xFF000000);
        btnVoceLisa.setMinHeight(Math.round(64 * density));
        btnVoceLisa.setOnClickListener(v -> {

            // Se Whisper è già attivo, il pulsante deve sempre fermarlo,
            // anche se nel frattempo è stata cambiata la preferenza ASR.
            if (localWhisperAsrProbe != null
                    && localWhisperAsrProbe.isRunning()) {

                localWhisperAsrProbe.start();
                return;
            }

            // Se è attiva una sessione Google/SpeechRecognizer, fermala.
            if (LisaVoiceService.isInAscolto()) {
                LisaVoiceService.fermaAscolto(this);
                return;
            }

            if (LisaAccessibilityService.getInstance() == null) {
                android.widget.Toast.makeText(
                        this,
                        "Attiva prima Accessibilità Lisa",
                        android.widget.Toast.LENGTH_SHORT
                ).show();

                btnVoceLisa.setText("🎤 Attiva Lisa");
                return;
            }

            String motore =
                    getSharedPreferences(
                            "lisa_ui",
                            MODE_PRIVATE
                    ).getString(
                            "motore_asr",
                            "google"
                    );

            // === WHISPER SMALL LOCALE ===
            if ("whisper".equals(motore)) {

                if (btnAsrPipeTest != null) {
                    btnAsrPipeTest.performClick();
                }

                return;
            }

            // === GOOGLE / ANDROID SPEECHRECOGNIZER ===
            LisaVoiceService.avviaAscolto(this);
        });
        layout.addView(btnVoceLisa);

        // TEST ISOLATO ASR CON AUDIO SOURCE
        btnAsrPipeTest = new Button(this);
        btnAsrPipeTest.setText("🧠 TEST VOCE LOCALE");
        btnAsrPipeTest.setVisibility(android.view.View.GONE);
        btnAsrPipeTest.setTextSize(18);
        btnAsrPipeTest.setTextColor(0xFF000000);
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
                                                        LisaVoiceService.isInAscolto()
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



        Button btnHome = new Button(this);
        btnHome.setText("Vai alla Home");
        btnHome.setOnClickListener(v -> {
            LisaHomeController.vaiAllaHomePrincipale();
        });

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

        Button btnBack = new Button(this);
        btnBack.setText("Indietro");
        btnBack.setOnClickListener(v -> {
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        });

        Button btnRecent = new Button(this);
        btnRecent.setText("Recenti");
        btnRecent.setOnClickListener(v -> {
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS);
        });

        Button btnNotif = new Button(this);
        btnNotif.setText("Notifiche");
        btnNotif.setOnClickListener(v -> {
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        });

        Button btnSettings = new Button(this);
        btnSettings.setText("Impostazioni");
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        });

        Button btnLock=new Button(this);
        btnLock.setText("Blocca schermo");
        btnLock.setOnClickListener(v->{
            LisaAccessibilityService s=LisaAccessibilityService.getInstance();
            if(s!=null)
                s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        });

        Button btnScreenshot=new Button(this);
        btnScreenshot.setText("Screenshot");
        btnScreenshot.setOnClickListener(v->{
            LisaAccessibilityService.eseguiScreenshotConRipristino();
        });

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





        // ===== COMANDI RAPIDI DASHBOARD =====

        TextView titoloRapidi = new TextView(this);
        titoloRapidi.setText("Comandi rapidi");
        titoloRapidi.setTextSize(20);
        titoloRapidi.setTextColor(android.graphics.Color.parseColor("#000000"));
        titoloRapidi.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        );

        LinearLayout.LayoutParams titoloRapidiLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        titoloRapidiLp.topMargin = 0;
        titoloRapidiLp.bottomMargin = Math.round(8 * density);

        layout.addView(titoloRapidi, titoloRapidiLp);

        btnHome.setText("🏠 Home");
        btnBack.setText("↩ Indietro");

        btnRecent.setText("▣ Recenti");
        btnNotif.setText("🔔 Notifiche");

        btnVolumeUp.setText("🔊 Volume +");
        btnVolumeDown.setText("🔉 Volume -");

        btnBrightnessUp.setText("☀ Luminosità +");
        btnBrightnessDown.setText("☀ Luminosità -");

        btnScreenshot.setText("📸 Screenshot");
        btnLock.setText("🔒 Blocca");

        layout.addView(
                creaRigaDashboard(
                        btnHome,
                        btnBack,
                        gapDashboard
                )
        );

        layout.addView(
                creaRigaDashboard(
                        btnRecent,
                        btnNotif,
                        gapDashboard
                )
        );

        layout.addView(
                creaRigaDashboard(
                        btnVolumeUp,
                        btnVolumeDown,
                        gapDashboard
                )
        );

        layout.addView(
                creaRigaDashboard(
                        btnBrightnessUp,
                        btnBrightnessDown,
                        gapDashboard
                )
        );

        layout.addView(
                creaRigaDashboard(
                        btnScreenshot,
                        btnLock,
                        gapDashboard
                )
        );



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

    private void aggiornaStatoLisa(TextView tv) {
        if (LisaAccessibilityService.getInstance() == null) {
            tv.setText("○ Accessibilità Lisa non attiva");
            return;
        }
        LisaVoiceService.LisaState st = LisaVoiceService.getStato();
        if (st == null) {
            tv.setText("● Lisa non avviata");
            return;
        }
        switch (st) {
            case READY:
                tv.setText("● Lisa pronta");
                break;
            case LISTENING:
                tv.setText("● Lisa in ascolto");
                break;
            case SPEAKING:
                tv.setText("● Lisa sta parlando");
                break;
            default:
                tv.setText("● Stato sconosciuto");
                break;
        }
    }

    private void aggiornaPallinoAccessibilita() {
        if (dotAccess == null) return;
        android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager) getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
        boolean attivo = false;
        if (am != null) {
            java.util.List<android.accessibilityservice.AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            String targetService = getPackageName() + "/" + LisaAccessibilityService.class.getName();
            for (android.accessibilityservice.AccessibilityServiceInfo info : services) {
                if (targetService.equals(info.getId())) {
                    attivo = true;
                    break;
                }
            }
        }
        dotAccess.setChecked(attivo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        aggiornaPallinoAccessibilita();
    }

    private LinearLayout creaRigaDashboard(
            Button sinistra,
            Button destra,
            int gap) {

        LinearLayout riga = new LinearLayout(this);
        riga.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams lpSinistra =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        lpSinistra.rightMargin = gap / 2;
        lpSinistra.bottomMargin = gap;

        LinearLayout.LayoutParams lpDestra =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        lpDestra.leftMargin = gap / 2;
        lpDestra.bottomMargin = gap;

        riga.addView(
                sinistra,
                lpSinistra
        );

        riga.addView(
                destra,
                lpDestra
        );

        return riga;
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
