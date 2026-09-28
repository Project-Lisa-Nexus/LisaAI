package com.lisa.app;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import java.util.Locale;

public final class SystemCapabilityRegistry {

    public enum Status {
        SUPPORTED_ON,
        SUPPORTED_OFF,
        UNKNOWN,
        UNSUPPORTED,
        PERMISSION_REQUIRED
    }

    public static final class Result {
        public final String capability;
        public final Status status;
        public final Boolean enabled;
        public final Boolean connected;
        public final String source;
        public final String detail;

        Result(
                String capability,
                Status status,
                Boolean enabled,
                Boolean connected,
                String source,
                String detail) {

            this.capability = capability;
            this.status = status;
            this.enabled = enabled;
            this.connected = connected;
            this.source = source;
            this.detail = detail;
        }
    }

    private SystemCapabilityRegistry() {}

    public static String canonicalizeCapability(String testo) {
        if (testo == null) return null;

        String t = testo
                .toLowerCase(Locale.ITALIAN)
                .replace("wi-fi", "wifi")
                .replace("wi fi", "wifi")
                .trim();

        if (t.equals("wifi")) return "wifi";
        if (t.equals("bluetooth")) return "bluetooth";

        if (t.equals("posizione")
                || t.equals("localizzazione")
                || t.equals("gps"))
            return "location";

        if (t.equals("modalita aereo")
                || t.equals("modalità aereo")
                || t.equals("modalita aeroplano")
                || t.equals("modalità aeroplano")
                || t.equals("modalita in volo")
                || t.equals("modalità in volo")
                || t.equals("aereo")
                || t.equals("aeroplano"))
            return "airplane_mode";

        if (t.equals("nfc")) return "nfc";

        if (t.equals("rotazione automatica")
                || t.equals("rotazione"))
            return "auto_rotate";

        if (t.equals("risparmio energetico")
                || t.equals("risparmio batteria"))
            return "battery_saver";

        if (t.equals("non disturbare")
                || t.equals("modalita non disturbare")
                || t.equals("modalità non disturbare"))
            return "do_not_disturb";

        if (t.equals("dati mobili")
                || t.equals("rete mobile")
                || t.equals("connessione dati"))
            return "mobile_data";

        if (t.equals("hotspot")
                || t.equals("hotspot personale")
                || t.equals("tethering"))
            return "hotspot";

        return null;
    }

    public static String resolveVoiceStateQuery(String testo) {
        if (testo == null) return null;

        String candidato = testo
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .toLowerCase(Locale.ITALIAN)
                .trim()
                .replaceAll("[\\p{Punct}\\s]+$", "")
                .trim();

        String diretto =
                canonicalizeCapability(candidato);

        if (diretto != null) return diretto;

        String[] prefissi = {
                "stato della ",
                "stato dello ",
                "stato delle ",
                "stato degli ",
                "stato del ",
                "stato dei ",
                "stato di ",
                "come stanno ",
                "come sta ",
                "come è ",
                "com'è ",
                "stato ",
                "della ",
                "dello ",
                "delle ",
                "degli ",
                "del ",
                "dei ",
                "dell'",
                "di ",
                "il ",
                "lo ",
                "la ",
                "i ",
                "gli ",
                "le ",
                "l'"
        };

        boolean rimosso = true;

        while (rimosso) {
            rimosso = false;

            for (String prefisso : prefissi) {
                if (candidato.startsWith(prefisso)
                        && candidato.length() > prefisso.length()) {

                    candidato = candidato
                            .substring(prefisso.length())
                            .trim();

                    rimosso = true;
                    break;
                }
            }
        }

        String[] suffissi = {
                " sono disattivati",
                " sono disattivate",
                " sono attivi",
                " sono attive",
                " sono accesi",
                " sono accese",
                " sono spenti",
                " sono spente",
                " e disattivato",
                " è disattivato",
                " e disattivata",
                " è disattivata",
                " e attivo",
                " è attivo",
                " e attiva",
                " è attiva",
                " e acceso",
                " è acceso",
                " e accesa",
                " è accesa",
                " e spento",
                " è spento",
                " e spenta",
                " è spenta",
                " disattivati",
                " disattivate",
                " disattivato",
                " disattivata",
                " disattivo",
                " disattiva",
                " attivi",
                " attive",
                " attivo",
                " attiva",
                " accesi",
                " accese",
                " acceso",
                " accesa",
                " spenti",
                " spente",
                " spento",
                " spenta"
        };

        rimosso = true;

        while (rimosso) {
            rimosso = false;

            for (String suffisso : suffissi) {
                if (candidato.endsWith(suffisso)
                        && candidato.length() > suffisso.length()) {

                    candidato = candidato
                            .substring(
                                    0,
                                    candidato.length() - suffisso.length()
                            )
                            .trim();

                    rimosso = true;
                    break;
                }
            }
        }

        return canonicalizeCapability(candidato);
    }

    public static Result get(
            Context context,
            String capability) {

        if (context == null || capability == null) {
            return unknown(
                    capability,
                    "input",
                    "capability non valida"
            );
        }

        switch (capability) {
            case "wifi": return readWifi(context);
            case "bluetooth": return readBluetooth(context);
            case "location": return readLocation(context);
            case "airplane_mode": return readAirplane(context);
            case "nfc": return readNfc(context);
            case "auto_rotate": return readAutoRotate(context);
            case "battery_saver": return readBatterySaver(context);
            case "do_not_disturb": return readDoNotDisturb(context);
            case "mobile_data": return readMobileData(context);

            case "hotspot":
                return unknown(
                        "hotspot",
                        "android_public_api",
                        "stato hotspot non disponibile in modo universale"
                );

            default:
                return unsupported(capability, "registry");
        }
    }

    private static Result readWifi(Context context) {

        if (context.checkSelfPermission(
                android.Manifest.permission.ACCESS_WIFI_STATE
        ) != PackageManager.PERMISSION_GRANTED) {

            return permission(
                    "wifi",
                    "WifiManager"
            );
        }

        WifiManager wm =
                (WifiManager)
                        context.getApplicationContext()
                                .getSystemService(
                                        Context.WIFI_SERVICE
                                );

        if (wm == null) {
            return unsupported(
                    "wifi",
                    "WifiManager"
            );
        }

        boolean enabled =
                wm.isWifiEnabled();

        Boolean connected = false;

        ConnectivityManager cm =
                (ConnectivityManager)
                        context.getSystemService(
                                Context.CONNECTIVITY_SERVICE
                        );

        if (cm != null) {

            Network rete =
                    cm.getActiveNetwork();

            NetworkCapabilities caps =
                    rete != null
                            ? cm.getNetworkCapabilities(rete)
                            : null;

            connected =
                    caps != null
                            && caps.hasTransport(
                                    NetworkCapabilities.TRANSPORT_WIFI
                            );
        }

        return state(
                "wifi",
                enabled,
                connected,
                "WifiManager+NetworkCapabilities",
                null
        );
    }

    private static Result readBluetooth(
            Context context) {

        BluetoothManager bm =
                (BluetoothManager)
                        context.getSystemService(
                                Context.BLUETOOTH_SERVICE
                        );

        BluetoothAdapter adapter =
                bm != null
                        ? bm.getAdapter()
                        : null;

        if (adapter == null) {
            return unsupported(
                    "bluetooth",
                    "BluetoothAdapter"
            );
        }

        try {

            return state(
                    "bluetooth",
                    adapter.isEnabled(),
                    null,
                    "BluetoothAdapter",
                    null
            );

        } catch (SecurityException e) {

            return permission(
                    "bluetooth",
                    "BluetoothAdapter"
            );
        }
    }

    private static Result readLocation(
            Context context) {

        LocationManager lm =
                (LocationManager)
                        context.getSystemService(
                                Context.LOCATION_SERVICE
                        );

        if (lm == null) {
            return unsupported(
                    "location",
                    "LocationManager"
            );
        }

        try {

            boolean enabled;

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.P) {

                enabled = lm.isLocationEnabled();

            } else {

                enabled =
                        lm.isProviderEnabled(
                                LocationManager.GPS_PROVIDER
                        )
                        || lm.isProviderEnabled(
                                LocationManager.NETWORK_PROVIDER
                        );
            }

            return state(
                    "location",
                    enabled,
                    null,
                    "LocationManager",
                    null
            );

        } catch (SecurityException e) {

            return permission(
                    "location",
                    "LocationManager"
            );
        }
    }

    private static Result readAirplane(
            Context context) {

        boolean enabled =
                Settings.Global.getInt(
                        context.getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON,
                        0
                ) == 1;

        return state(
                "airplane_mode",
                enabled,
                null,
                "Settings.Global",
                null
        );
    }

    private static Result readNfc(
            Context context) {

        NfcAdapter adapter =
                NfcAdapter.getDefaultAdapter(context);

        if (adapter == null) {
            return unsupported(
                    "nfc",
                    "NfcAdapter"
            );
        }

        try {
            return state(
                    "nfc",
                    adapter.isEnabled(),
                    null,
                    "NfcAdapter",
                    null
            );

        } catch (SecurityException e) {
            return permission(
                    "nfc",
                    "NfcAdapter"
            );
        }
    }

    private static Result readAutoRotate(
            Context context) {

        try {
            boolean enabled =
                    Settings.System.getInt(
                            context.getContentResolver(),
                            Settings.System.ACCELEROMETER_ROTATION,
                            0
                    ) == 1;

            return state(
                    "auto_rotate",
                    enabled,
                    null,
                    "Settings.System",
                    null
            );

        } catch (Exception e) {
            return unknown(
                    "auto_rotate",
                    "Settings.System",
                    e.getClass().getSimpleName()
            );
        }
    }

    private static Result readBatterySaver(
            Context context) {

        PowerManager pm =
                (PowerManager)
                        context.getSystemService(
                                Context.POWER_SERVICE
                        );

        if (pm == null) {
            return unsupported(
                    "battery_saver",
                    "PowerManager"
            );
        }

        return state(
                "battery_saver",
                pm.isPowerSaveMode(),
                null,
                "PowerManager",
                null
        );
    }

    private static Result readDoNotDisturb(
            Context context) {

        NotificationManager nm =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (nm == null) {
            return unsupported(
                    "do_not_disturb",
                    "NotificationManager"
            );
        }

        try {
            int filtro =
                    nm.getCurrentInterruptionFilter();

            if (filtro
                    == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {

                return unknown(
                        "do_not_disturb",
                        "NotificationManager",
                        "interruption filter unknown"
                );
            }

            boolean enabled =
                    filtro
                            != NotificationManager.INTERRUPTION_FILTER_ALL;

            return state(
                    "do_not_disturb",
                    enabled,
                    null,
                    "NotificationManager",
                    "filter=" + filtro
            );

        } catch (SecurityException e) {
            return permission(
                    "do_not_disturb",
                    "NotificationManager"
            );
        }
    }

    private static Result readMobileData(
            Context context) {

        if (!context.getPackageManager()
                .hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY
                )) {

            return unsupported(
                    "mobile_data",
                    "TelephonyManager"
            );
        }

        TelephonyManager tm =
                (TelephonyManager)
                        context.getSystemService(
                                Context.TELEPHONY_SERVICE
                        );

        if (tm == null) {
            return unsupported(
                    "mobile_data",
                    "TelephonyManager"
            );
        }

        try {

            boolean enabled =
                    Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.O
                            && tm.isDataEnabled();

            Boolean connected = false;

            ConnectivityManager cm =
                    (ConnectivityManager)
                            context.getSystemService(
                                    Context.CONNECTIVITY_SERVICE
                            );

            if (cm != null) {

                Network rete =
                        cm.getActiveNetwork();

                NetworkCapabilities caps =
                        rete != null
                                ? cm.getNetworkCapabilities(rete)
                                : null;

                connected =
                        caps != null
                                && caps.hasTransport(
                                        NetworkCapabilities.TRANSPORT_CELLULAR
                                );
            }

            return state(
                    "mobile_data",
                    enabled,
                    connected,
                    "TelephonyManager+NetworkCapabilities",
                    null
            );

        } catch (SecurityException e) {

            return permission(
                    "mobile_data",
                    "TelephonyManager"
            );

        } catch (UnsupportedOperationException e) {

            return unsupported(
                    "mobile_data",
                    "TelephonyManager"
            );
        }
    }

    private static Result state(
            String capability,
            boolean enabled,
            Boolean connected,
            String source,
            String detail) {

        return new Result(
                capability,
                enabled
                        ? Status.SUPPORTED_ON
                        : Status.SUPPORTED_OFF,
                enabled,
                connected,
                source,
                detail
        );
    }

    private static Result unknown(
            String capability,
            String source,
            String detail) {

        return new Result(
                capability,
                Status.UNKNOWN,
                null,
                null,
                source,
                detail
        );
    }

    private static Result unsupported(
            String capability,
            String source) {

        return new Result(
                capability,
                Status.UNSUPPORTED,
                null,
                null,
                source,
                null
        );
    }

    private static Result permission(
            String capability,
            String source) {

        return new Result(
                capability,
                Status.PERMISSION_REQUIRED,
                null,
                null,
                source,
                null
        );
    }

    public static String toSpeech(Result r) {

        if (r == null || r.capability == null) {
            return "Non riesco a leggere questo stato.";
        }

        String soggetto;
        String complemento;
        String copula;
        String statoOn;
        String statoOff;
        String indisponibile;

        switch (r.capability) {

            case "wifi":
                soggetto = "Il Wi-Fi";
                complemento = "del Wi-Fi";
                copula = " è ";
                statoOn = "attivo";
                statoOff = "spento";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "bluetooth":
                soggetto = "Il Bluetooth";
                complemento = "del Bluetooth";
                copula = " è ";
                statoOn = "attivo";
                statoOff = "spento";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "location":
                soggetto = "La posizione";
                complemento = "della posizione";
                copula = " è ";
                statoOn = "attiva";
                statoOff = "disattivata";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "airplane_mode":
                soggetto = "La modalità aereo";
                complemento = "della modalità aereo";
                copula = " è ";
                statoOn = "attiva";
                statoOff = "disattivata";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "nfc":
                soggetto = "NFC";
                complemento = "di NFC";
                copula = " è ";
                statoOn = "attivo";
                statoOff = "disattivato";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "auto_rotate":
                soggetto = "La rotazione automatica";
                complemento = "della rotazione automatica";
                copula = " è ";
                statoOn = "attiva";
                statoOff = "disattivata";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "battery_saver":
                soggetto = "Il risparmio energetico";
                complemento = "del risparmio energetico";
                copula = " è ";
                statoOn = "attivo";
                statoOff = "disattivato";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "do_not_disturb":
                soggetto = "La modalità Non disturbare";
                complemento = "della modalità Non disturbare";
                copula = " è ";
                statoOn = "attiva";
                statoOff = "disattivata";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            case "mobile_data":
                soggetto = "I dati mobili";
                complemento = "dei dati mobili";
                copula = " sono ";
                statoOn = "attivi";
                statoOff = "disattivati";
                indisponibile = " non sono disponibili su questo dispositivo.";
                break;

            case "hotspot":
                soggetto = "L'hotspot";
                complemento = "dell'hotspot";
                copula = " è ";
                statoOn = "attivo";
                statoOff = "disattivato";
                indisponibile = " non è disponibile su questo dispositivo.";
                break;

            default:
                return "Non riesco a leggere questo stato.";
        }

        if (r.status == Status.UNSUPPORTED) {
            return soggetto + indisponibile;
        }

        if (r.status == Status.PERMISSION_REQUIRED) {
            return "Non posso leggere lo stato "
                    + complemento
                    + " senza autorizzazione.";
        }

        if (r.status == Status.UNKNOWN || r.enabled == null) {
            return "Non posso determinare in modo affidabile lo stato "
                    + complemento
                    + ".";
        }

        if ("wifi".equals(r.capability)) {
            if (!r.enabled) return "Il Wi-Fi è spento.";
            if (Boolean.TRUE.equals(r.connected))
                return "Il Wi-Fi è attivo e connesso.";
            return "Il Wi-Fi è attivo, ma non è connesso a una rete.";
        }

        if ("mobile_data".equals(r.capability)) {
            if (!r.enabled) return "I dati mobili sono disattivati.";
            if (Boolean.TRUE.equals(r.connected))
                return "I dati mobili sono attivi e in uso.";
            return "I dati mobili sono attivi, ma al momento non sono la connessione utilizzata.";
        }

        return soggetto
                + copula
                + (r.enabled ? statoOn : statoOff)
                + ".";
    }
}
