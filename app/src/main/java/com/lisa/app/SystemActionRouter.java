package com.lisa.app;

import android.provider.Settings;

import java.text.Normalizer;
import java.util.Locale;

public final class SystemActionRouter {

    public static final java.util.Map<String, String[]>
            TOGGLE_LABELS;

    static {
        java.util.Map<String, String[]> labels =
                new java.util.HashMap<>();

        labels.put("location", new String[] {
                "Usa posizione",
                "Posizione",
                "Utilizza posizione",
                "Localizzazione"
        });

        labels.put("bluetooth", new String[] {
                "Bluetooth", "Utilizza Bluetooth"
        });

        labels.put("wifi", new String[] {
                "Wi-Fi", "Utilizza Wi-Fi"
        });

        labels.put("airplane_mode", new String[] {
                "Modalità aereo", "Aereo"
        });

        labels.put("nfc", new String[] {
                "NFC", "Consenti NFC"
        });

        labels.put("auto_rotate", new String[] {
                "Rotazione automatica",
                "Rotazione schermo automatica"
        });

        labels.put("battery_saver", new String[] {
                "Risparmio batteria",
                "Risparmio energetico"
        });

        labels.put("do_not_disturb", new String[] {
                "Non disturbare"
        });

        labels.put("mobile_data", new String[] {
                "Dati mobili", "Utilizza dati mobili"
        });

        TOGGLE_LABELS =
                java.util.Collections.unmodifiableMap(labels);
    }

    public static String[] toggleLabelsFor(
            String capability) {

        String[] labels = TOGGLE_LABELS.get(capability);

        return labels == null
                ? new String[0]
                : labels.clone();
    }


    public enum Action {
        OPEN,
        ENABLE,
        DISABLE
    }

    public static final class ActionPlan {
        public final Action action;
        public final String capability;
        public final Boolean desiredEnabled;
        public final String settingsAction;
        public final String fallbackSettingsAction;
        public final String originalTarget;

        ActionPlan(
                Action action,
                String capability,
                Boolean desiredEnabled,
                String settingsAction,
                String fallbackSettingsAction,
                String originalTarget) {

            this.action = action;
            this.capability = capability;
            this.desiredEnabled = desiredEnabled;
            this.settingsAction = settingsAction;
            this.fallbackSettingsAction = fallbackSettingsAction;
            this.originalTarget = originalTarget;
        }
    }

    private SystemActionRouter() {}

    private static String normalizza(String testo) {
        if (testo == null) return "";

        return Normalizer.normalize(
                        testo,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ITALIAN)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replaceAll("[^\\p{L}\\p{N}'\\s-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String rimuoviArticolo(String target) {
        String t = normalizza(target);

        String[] articoli = {
                "il ",
                "lo ",
                "la ",
                "i ",
                "gli ",
                "le ",
                "l'"
        };

        for (String articolo : articoli) {
            if (t.startsWith(articolo)
                    && t.length() > articolo.length()) {

                return t.substring(
                        articolo.length()
                ).trim();
            }
        }

        return t;
    }

    private static String canonicalizzaTarget(String target) {
        String t = rimuoviArticolo(target);

        if (t.equals("bt")) {
            return "bluetooth";
        }

        if (t.equals("rete wifi")
                || t.equals("rete wi-fi")
                || t.equals("rete wi fi")) {
            return "wifi";
        }

        if (t.equals("hot spot")) {
            return "hotspot";
        }

        return SystemCapabilityRegistry
                .canonicalizeCapability(t);
    }

    public static ActionPlan parse(String testo) {
        String t = normalizza(testo);

        if (t.isEmpty()) {
            return null;
        }

        Action action = null;
        Boolean desiredEnabled = null;
        String target = null;

        String[][] regole = {
                {"disattiva ", "DISABLE"},
                {"disabilita ", "DISABLE"},
                {"spegni ", "DISABLE"},
                {"togli ", "DISABLE"},
                {"chiudi ", "DISABLE"},

                {"attiva ", "ENABLE"},
                {"accendi ", "ENABLE"},
                {"abilita ", "ENABLE"},
                {"attivare ", "ENABLE"},
                {"metti su ", "ENABLE"},

                {"apri ", "ENABLE"}
        };

        for (String[] regola : regole) {
            String prefisso = regola[0];

            if (t.startsWith(prefisso)
                    && t.length() > prefisso.length()) {

                String tipo = regola[1];

                if ("ENABLE".equals(tipo)) {
                    action = Action.ENABLE;
                    desiredEnabled = true;

                } else if ("DISABLE".equals(tipo)) {
                    action = Action.DISABLE;
                    desiredEnabled = false;

                } else {
                    action = Action.OPEN;
                    desiredEnabled = null;
                }

                target = t.substring(
                        prefisso.length()
                ).trim();

                break;
            }
        }

        if (action == null
                || target == null
                || target.isEmpty()) {

            return null;
        }

        String capability =
                canonicalizzaTarget(target);

        if (capability == null) {
            return null;
        }

        String settingsAction =
                settingsActionFor(capability);

        String fallbackSettingsAction =
                fallbackSettingsActionFor(capability);

        return new ActionPlan(
                action,
                capability,
                desiredEnabled,
                settingsAction,
                fallbackSettingsAction,
                target
        );
    }

    public static boolean richiedeCambio(
            ActionPlan plan) {

        return plan != null
                && (plan.action == Action.ENABLE
                || plan.action == Action.DISABLE);
    }

    public static boolean giaNelloStatoRichiesto(
            ActionPlan plan,
            SystemCapabilityRegistry.Result stato) {

        if (plan == null
                || stato == null
                || plan.desiredEnabled == null
                || stato.enabled == null) {

            return false;
        }

        return plan.desiredEnabled.equals(
                stato.enabled
        );
    }

    private static String settingsActionFor(
            String capability) {

        if (capability == null) {
            return null;
        }

        switch (capability) {

            case "bluetooth":
                return Settings.ACTION_BLUETOOTH_SETTINGS;

            case "wifi":
                return Settings.ACTION_WIFI_SETTINGS;

            case "location":
                return Settings.ACTION_LOCATION_SOURCE_SETTINGS;

            case "airplane_mode":
                return Settings.ACTION_AIRPLANE_MODE_SETTINGS;

            case "nfc":
                return Settings.ACTION_NFC_SETTINGS;

            case "mobile_data":
                return Settings.ACTION_DATA_USAGE_SETTINGS;

            case "battery_saver":
                return Settings.ACTION_BATTERY_SAVER_SETTINGS;

            case "auto_rotate":
                return Settings.ACTION_AUTO_ROTATE_SETTINGS;

            case "do_not_disturb":
                return Settings.ACTION_SOUND_SETTINGS;

            case "hotspot":
                return "android.settings.TETHER_SETTINGS";

            default:
                return null;
        }
    }

    private static String fallbackSettingsActionFor(
            String capability) {

        if (capability == null) {
            return Settings.ACTION_SETTINGS;
        }

        switch (capability) {

            case "bluetooth":
            case "wifi":
            case "airplane_mode":
            case "nfc":
            case "mobile_data":
            case "hotspot":
                return Settings.ACTION_WIRELESS_SETTINGS;

            case "location":
                return Settings.ACTION_SETTINGS;

            case "battery_saver":
                return Settings.ACTION_SETTINGS;

            case "auto_rotate":
                return Settings.ACTION_DISPLAY_SETTINGS;

            case "do_not_disturb":
                return Settings.ACTION_SOUND_SETTINGS;

            default:
                return Settings.ACTION_SETTINGS;
        }
    }
}
