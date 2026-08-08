package com.lisa.app;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashSet;
import java.util.Set;

public final class SafetyGuard {

    private SafetyGuard() {}

    public static boolean schermataProtetta(
            Context context,
            AccessibilityNodeInfo root) {

        if (root == null || root.getPackageName() == null) {
            return false;
        }

        String pacchettoAttivo =
            root.getPackageName().toString();

        // Lisa può controllare la propria interfaccia.
        if (pacchettoAttivo.equals(context.getPackageName())) {
            return false;
        }

        return pacchettiAccessibilitaAttivi(context)
            .contains(pacchettoAttivo);
    }

    private static Set<String> pacchettiAccessibilitaAttivi(
            Context context) {

        Set<String> risultato = new HashSet<>();

        String servizi = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (servizi == null || servizi.trim().isEmpty()) {
            return risultato;
        }

        for (String servizio : servizi.split(":")) {
            ComponentName componente =
                ComponentName.unflattenFromString(
                    servizio.trim()
                );

            if (componente != null) {
                risultato.add(componente.getPackageName());
            }
        }

        return risultato;
    }
}
