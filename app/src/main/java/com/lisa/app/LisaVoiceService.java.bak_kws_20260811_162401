package com.lisa.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class LisaVoiceService extends Service {
    private static final String TAG = "LisaVoice";
    private static final String CHANNEL_ID = "LisaVoiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LisaVoiceService creato");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "LisaVoiceService avviato - in ascolto");
        
        // Crea notifica foreground
        Notification notification = createNotification();
        startForeground(1, notification);
        
        // Qui andra Vosk per la wake word
        // Per ora logghiamo solo che siamo attivi
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification createNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Lisa Voice",
                NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Lisa")
            .setContentText("In ascolto... Di 'Ehi Lisa' per attivarmi")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .build();
    }
}
