package com.lisa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class LisaSegmentedProbeReceiver
        extends BroadcastReceiver {

    private static final String TAG =
            "LisaSegmentedProbeReceiver";

    public static final String ACTION_START =
            "com.lisa.nexus.SEGMENTED_PROBE_START";

    public static final String ACTION_STOP =
            "com.lisa.nexus.SEGMENTED_PROBE_STOP";

    public static final String ACTION_STATUS =
            "com.lisa.nexus.SEGMENTED_PROBE_STATUS";

    private static LisaSegmentedProbeManager manager;

    @Override
    public void onReceive(
            Context context,
            Intent intent) {

        if (intent == null) return;

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {

            if (manager == null) {
                manager =
                        new LisaSegmentedProbeManager(
                                context.getApplicationContext()
                        );
            }

            Log.i(TAG, "ADB START");

            manager.start();

            setResultCode(1);
            setResultData("probe_start_richiesto");
            return;
        }

        if (ACTION_STOP.equals(action)) {

            if (manager != null) {
                manager.stop("adb_stop");
            }

            Log.i(TAG, "ADB STOP");

            setResultCode(1);
            setResultData("probe_stop_richiesto");
            return;
        }

        if (ACTION_STATUS.equals(action)) {

            String stato =
                    manager != null
                            ? manager.getStatus()
                            : "manager=null";

            Log.i(TAG, "STATUS " + stato);

            setResultCode(1);
            setResultData(stato);
        }
    }
}
