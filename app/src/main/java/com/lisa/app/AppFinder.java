package com.lisa.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.List;

public class AppFinder {

    public static boolean apriAppPerNome(Context context, String nomeApp) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString();

            if (label.equalsIgnoreCase(nomeApp)) {
                Intent intent = pm.getLaunchIntentForPackage(app.packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return true;
                }
            }
        }

        return false;
    }
}
