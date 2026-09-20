package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;

public final class LisaHomeController {

    private LisaHomeController() {
    }

    public static boolean vaiAllaHomePrincipale() {

        LisaAccessibilityService service =
                LisaAccessibilityService.getInstance();

        if (service == null) {
            return false;
        }

        boolean primo = service.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME
        );

        new Handler(Looper.getMainLooper()).postDelayed(
                () -> {
                    LisaAccessibilityService s =
                            LisaAccessibilityService.getInstance();

                    if (s != null) {
                        s.performGlobalAction(
                                AccessibilityService.GLOBAL_ACTION_HOME
                        );
                    }
                },
                600
        );

        return primo;
    }
}
