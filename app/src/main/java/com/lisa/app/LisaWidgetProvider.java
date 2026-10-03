package com.lisa.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.*;

public final class LisaWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_TOGGLE =
            "com.lisa.nexus.ACTION_WIDGET_TOGGLE";

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) aggiorna(c,m,id);
    }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c,i);
        if (!ACTION_TOGGLE.equals(i.getAction())) return;

        try {
            if (LisaVoiceService.isInAscolto())
                LisaVoiceService.fermaAscolto(c);
            else
                LisaVoiceService.avviaAscolto(c);
        } catch (RuntimeException e) {
            Toast.makeText(
                    c,
                    "Apri Lisa per avviare",
                    Toast.LENGTH_SHORT
            ).show();

            Intent a=new Intent(c,MainActivity.class);
            a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(a);
        }

        aggiornaTutti(c);
    }

    public static void aggiornaTutti(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        ComponentName n=
                new ComponentName(c,LisaWidgetProvider.class);

        for (int id:m.getAppWidgetIds(n))
            aggiorna(c,m,id);
    }

    private static void aggiorna(
            Context c,
            AppWidgetManager m,
            int id) {

        RemoteViews v=
                new RemoteViews(
                        c.getPackageName(),
                        R.layout.widget_lisa
                );

        v.setTextViewText(
                R.id.widget_toggle,
                LisaVoiceService.isInAscolto()
                        ? "⏹ Ferma Lisa"
                        : "🎤 Attiva Lisa"
        );

        Intent t=
                new Intent(c,LisaWidgetProvider.class)
                        .setAction(ACTION_TOGGLE);

        v.setOnClickPendingIntent(
                R.id.widget_toggle,
                PendingIntent.getBroadcast(
                        c,
                        4101,
                        t,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                )
        );

        Intent a=new Intent(c,MainActivity.class);
        v.setOnClickPendingIntent(
                R.id.widget_apri,
                PendingIntent.getActivity(
                        c,4102,a,
                        PendingIntent.FLAG_IMMUTABLE
                )
        );

        Intent s=new Intent(c,LisaSettingsActivity.class);
        v.setOnClickPendingIntent(
                R.id.widget_settings,
                PendingIntent.getActivity(
                        c,4103,s,
                        PendingIntent.FLAG_IMMUTABLE
                )
        );

        m.updateAppWidget(id,v);
    }
}
