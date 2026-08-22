package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class LisaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LisaAccessibility";
    private static LisaAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Servizio accessibilita connesso");
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Servizio interrotto");
    }


    private void spegniLisaCompletamente() {
        instance = null;

        LisaSpeaker.spegni();

        try {
            Intent voiceIntent =
                    new Intent(this, LisaVoiceService.class);

            stopService(voiceIntent);
        } catch (Exception ignored) {
        }

        Log.d(TAG, "Lisa completamente spenta");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        spegniLisaCompletamente();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        spegniLisaCompletamente();
        super.onDestroy();
    }

    public void apriApp(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Aperta app: " + packageName);
        } else {
            Log.e(TAG, "App non trovata: " + packageName);
        }
    }

    public boolean cliccaTesto(String testo) {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        Log.d("LisaAccessibility", "CLICK richiesta: " + testo);

        if (root == null) {
            Log.e("LisaAccessibility", "CLICK: root nullo");
            return false;
        }

        Log.d("LisaAccessibility", "CLICK pacchetto: " +
                (root.getPackageName() == null ? "" : root.getPackageName().toString()));

        AccessibilityNodeInfo nodo =
                AccessibilityUtils.trovaPerTesto(root, testo);

        if (nodo == null) {
            Log.e("LisaAccessibility", "CLICK nodo NON trovato: " + testo);
            return false;
        }

        Log.d("LisaAccessibility", "CLICK nodo trovato: testo=" +
                nodo.getText() +
                " descrizione=" +
                nodo.getContentDescription() +
                " classe=" +
                nodo.getClassName() +
                " cliccabile=" +
                nodo.isClickable());

        boolean risultato = AccessibilityUtils.click(nodo);

        Log.d("LisaAccessibility", "CLICK risultato ACTION_CLICK: " + risultato);

        return risultato;
    }

    public static void apriAppStatic(Context context, String packageName) {
        if (instance != null) {
            instance.apriApp(packageName);
        } else {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }

    public static void chiamaStatic(Context context, String numero) {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(android.net.Uri.parse("tel:" + numero));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static String messaggioInAttesa = null;
    private final java.util.ArrayList<String> contattiAmbigui =
            new java.util.ArrayList<>();


    public static void scriviStatic(Context context, String destinatario, String messaggio) {
        Log.d("LisaAccessibility", "Scrivi a " + destinatario + ": " + messaggio);
        messaggioInAttesa = messaggio;
    }

    private String risolviContattoRubrica(String richiesto) {
        contattiAmbigui.clear();

        if (richiesto == null || richiesto.trim().isEmpty())
            return richiesto;

        String query = richiesto.trim();

        java.util.LinkedHashSet<String> trovati =
                new java.util.LinkedHashSet<>();

        try {
            android.database.Cursor cursor =
                    getContentResolver().query(
                            android.provider.ContactsContract.Contacts.CONTENT_URI,
                            new String[]{
                                    android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                            },
                            android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER +
                                    " > 0 AND " +
                                    android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY +
                                    " LIKE ?",
                            new String[]{"%" + query + "%"},
                            android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY +
                                    " COLLATE NOCASE ASC"
                    );

            if (cursor != null) {
                int indice = cursor.getColumnIndex(
                        android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                );

                while (cursor.moveToNext()) {
                    if (indice >= 0) {
                        String nome = cursor.getString(indice);
                        if (nome != null && !nome.trim().isEmpty())
                            trovati.add(nome.trim());
                    }
                }

                cursor.close();
            }
        } catch (Exception e) {
            return query;
        }

        if (trovati.isEmpty())
            return query;

        java.util.ArrayList<String> preferiti =
                new java.util.ArrayList<>();

        String q = query.toLowerCase(java.util.Locale.ITALIAN);

        for (String nome : trovati) {
            String n = nome.toLowerCase(java.util.Locale.ITALIAN);

            if (n.equals(q) || n.startsWith(q + " "))
                preferiti.add(nome);
        }

        if (preferiti.size() == 1)
            return preferiti.get(0);

        if (preferiti.size() > 1)
            contattiAmbigui.addAll(preferiti);
        else if (trovati.size() == 1)
            return trovati.iterator().next();
        else
            contattiAmbigui.addAll(trovati);

        return null;
    }

    public String domandaContattoAmbiguo() {
        if (contattiAmbigui.isEmpty())
            return null;

        StringBuilder testo =
                new StringBuilder("Ho trovato ");

        int massimo = Math.min(3, contattiAmbigui.size());

        for (int i = 0; i < massimo; i++) {
            if (i > 0) testo.append(", ");
            testo.append(contattiAmbigui.get(i));
        }

        testo.append(". Quale vuoi?");
        return testo.toString();
    }

    public boolean cercaContattoEScrivi(String contatto, String messaggio) {
        String contattoRisolto = risolviContattoRubrica(contatto);

        if (contattoRisolto == null)
            return false;

        contatto = contattoRisolto;

        android.content.Intent whatsapp =
                getPackageManager().getLaunchIntentForPackage("com.whatsapp");

        if (whatsapp == null)
            return false;

        whatsapp.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(whatsapp);

        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null)
            return false;


        // ============================================================
        // 1 - SE SIAMO GIA' NELLA CHAT GIUSTA, SCRIVE SUBITO
        // ============================================================

        AccessibilityNodeInfo campoComposer = null;
        String nomeChatAperta = "";

        try {
            java.util.List<AccessibilityNodeInfo> campi =
                    root.findAccessibilityNodeInfosByViewId(
                            "com.whatsapp:id/entry"
                    );

            if (campi != null && !campi.isEmpty())
                campoComposer = campi.get(0);

            java.util.List<AccessibilityNodeInfo> nomi =
                    root.findAccessibilityNodeInfosByViewId(
                            "com.whatsapp:id/conversation_contact_name"
                    );

            if (nomi != null && !nomi.isEmpty()
                    && nomi.get(0).getText() != null) {

                nomeChatAperta =
                        nomi.get(0).getText().toString().trim();
            }

        } catch (Exception ignored) {
        }


        // Fallback: riconosce il campo messaggio tramite hint
        if (campoComposer == null) {

            AccessibilityNodeInfo possibile =
                    AccessibilityUtils.trovaCampoTesto(root);

            if (possibile != null) {

                CharSequence hint = possibile.getHintText();
                String id = possibile.getViewIdResourceName();

                boolean sembraMessaggio =
                        (id != null && id.contains("/entry"))
                        ||
                        (hint != null &&
                         hint.toString().toLowerCase().contains("messagg"));

                if (sembraMessaggio)
                    campoComposer = possibile;
            }
        }


        if (campoComposer != null
                && nomeChatAperta.equalsIgnoreCase(contatto)) {

            if (AccessibilityUtils.scrivi(campoComposer, messaggio)) {
                messaggioInAttesa = messaggio;
                return true;
            }
        }


        // ============================================================
        // 2 - SE SIAMO IN UN'ALTRA CHAT, TORNA ALLA LISTA CHAT
        // ============================================================

        if (campoComposer != null
                && !nomeChatAperta.equalsIgnoreCase(contatto)) {

            performGlobalAction(
                    android.accessibilityservice.AccessibilityService
                            .GLOBAL_ACTION_BACK
            );

            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            root = getRootInActiveWindow();

            if (root == null)
                return false;
        }


        // ============================================================
        // 3 - SE LA CHAT E' GIA' VISIBILE NELLA LISTA, CLICCALA
        //     SENZA USARE LA RICERCA
        // ============================================================

        AccessibilityNodeInfo chatVisibile =
                AccessibilityUtils.trovaPerTestoVisibile(
                        root,
                        contatto
                );

        if (chatVisibile != null
                && AccessibilityUtils.click(chatVisibile)) {

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            AccessibilityNodeInfo rootChat =
                    getRootInActiveWindow();

            if (rootChat != null) {

                AccessibilityNodeInfo campoMessaggio = null;

                try {
                    java.util.List<AccessibilityNodeInfo> campi =
                            rootChat.findAccessibilityNodeInfosByViewId(
                                    "com.whatsapp:id/entry"
                            );

                    if (campi != null && !campi.isEmpty())
                        campoMessaggio = campi.get(0);

                } catch (Exception ignored) {
                }

                if (campoMessaggio == null)
                    campoMessaggio =
                            AccessibilityUtils.trovaCampoTesto(rootChat);

                if (campoMessaggio != null
                        && AccessibilityUtils.scrivi(
                                campoMessaggio,
                                messaggio
                        )) {

                    messaggioInAttesa = messaggio;
                    return true;
                }
            }
        }


        // ============================================================
        // 4 - SOLO SE NON TROVA LA CHAT VISIBILE USA LA LENTE CERCA
        // ============================================================

        root = getRootInActiveWindow();
        if (root == null)
            return false;

        AccessibilityNodeInfo pulsanteCerca =
                AccessibilityUtils.trovaPerTesto(root, "Cerca");

        if (pulsanteCerca == null)
            return false;

        if (!AccessibilityUtils.click(pulsanteCerca))
            return false;

        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        root = getRootInActiveWindow();
        if (root == null)
            return false;

        AccessibilityNodeInfo campoRicerca =
                AccessibilityUtils.trovaCampoTesto(root);

        if (campoRicerca == null)
            return false;

        if (!AccessibilityUtils.scrivi(campoRicerca, contatto))
            return false;

        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        AccessibilityNodeInfo rootRisultati =
                getRootInActiveWindow();

        if (rootRisultati == null)
            return false;

        AccessibilityNodeInfo risultato =
                AccessibilityUtils.trovaPerTestoVisibile(
                        rootRisultati,
                        contatto
                );

        if (risultato == null)
            return false;

        if (!AccessibilityUtils.click(risultato))
            return false;

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        AccessibilityNodeInfo rootChat =
                getRootInActiveWindow();

        if (rootChat == null)
            return false;

        AccessibilityNodeInfo campoMessaggio = null;

        try {
            java.util.List<AccessibilityNodeInfo> campi =
                    rootChat.findAccessibilityNodeInfosByViewId(
                            "com.whatsapp:id/entry"
                    );

            if (campi != null && !campi.isEmpty())
                campoMessaggio = campi.get(0);

        } catch (Exception ignored) {
        }

        if (campoMessaggio == null)
            campoMessaggio =
                    AccessibilityUtils.trovaCampoTesto(rootChat);

        if (campoMessaggio == null)
            return false;

        if (!AccessibilityUtils.scrivi(
                campoMessaggio,
                messaggio
        ))
            return false;

        messaggioInAttesa = messaggio;

        // NON INVIA.
        // Aspetta "invia", "conferma" oppure comando equivalente.
        return true;
    }


    public void ascoltaConfermaInvio() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                final android.speech.SpeechRecognizer r =
                        android.speech.SpeechRecognizer.createSpeechRecognizer(this);

                android.content.Intent i = new android.content.Intent(
                        android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                );

                i.putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                );
                i.putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE,
                        "it-IT"
                );
                i.putExtra(
                        android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,
                        3
                );

                r.setRecognitionListener(new android.speech.RecognitionListener() {

                    @Override public void onReadyForSpeech(android.os.Bundle b) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float v) {}
                    @Override public void onBufferReceived(byte[] b) {}
                    @Override public void onEndOfSpeech() {}

                    @Override
                    public void onError(int error) {
                        try { r.destroy(); } catch (Exception ignored) {}

                        LisaSpeaker.parla(
                                LisaAccessibilityService.this,
                                "Non ti ho sentito. Dimmi invia oppure annulla.",
                                () -> ascoltaConfermaInvio()
                        );
                    }

                    @Override
                    public void onResults(android.os.Bundle results) {
                        java.util.ArrayList<String> lista =
                                results.getStringArrayList(
                                        android.speech.SpeechRecognizer.RESULTS_RECOGNITION
                                );

                        String frase =
                                lista != null && !lista.isEmpty()
                                        ? lista.get(0).trim().toLowerCase(java.util.Locale.ITALIAN)
                                        : "";

                        try { r.destroy(); } catch (Exception ignored) {}

                        boolean annulla =
                                frase.equals("no")
                                || frase.contains("annulla")
                                || frase.contains("non inviare")
                                || frase.contains("non mandare");

                        if (annulla) {
                            LisaSpeaker.parla(
                                    LisaAccessibilityService.this,
                                    "Va bene. Non invio.",
                                    () -> {}
                            );
                            return;
                        }

                        boolean invia =
                                frase.equals("si")
                                || frase.equals("sì")
                                || frase.equals("ok")
                                || frase.contains("invia")
                                || frase.contains("invio")
                                || frase.contains("conferma")
                                || frase.contains("confermo");

                        if (invia) {
                            boolean ok = confermaInvio();

                            LisaSpeaker.parla(
                                    LisaAccessibilityService.this,
                                    ok ? "Messaggio inviato." : "Non sono riuscita a inviarlo.",
                                    () -> {}
                            );
                            return;
                        }

                        LisaSpeaker.parla(
                                LisaAccessibilityService.this,
                                "Non ho capito. Dimmi invia oppure annulla.",
                                () -> ascoltaConfermaInvio()
                        );
                    }

                    @Override public void onPartialResults(android.os.Bundle b) {}
                    @Override public void onEvent(int t, android.os.Bundle b) {}
                });

                r.startListening(i);

            } catch (Exception e) {
                android.util.Log.e("LisaAccessibility", "Errore microfono", e);
            }
        });
    }

    public boolean confermaInvio() {
        if (messaggioInAttesa == null || messaggioInAttesa.trim().isEmpty())
            return false;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo pulsanteInvia =
                AccessibilityUtils.trovaPerTesto(root, "Invia");

        if (pulsanteInvia == null) return false;

        boolean inviato = AccessibilityUtils.click(pulsanteInvia);

        if (inviato) {
            messaggioInAttesa = null;
        }

        return inviato;
    }

    public boolean scriviTesto(String testo) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo campo =
            AccessibilityUtils.trovaCampoTesto(root);

        return AccessibilityUtils.scrivi(campo, testo);
    }

    public boolean scorriAvanti() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root != null && AccessibilityUtils.scorri(root, true);
    }

    public boolean scorriIndietro() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root != null && AccessibilityUtils.scorri(root, false);
    }

    public static LisaAccessibilityService getInstance() { return instance; }

    public void apriAppPerNome(String nomeApp) { AppFinder.apriAppPerNome(this, nomeApp); }

}
