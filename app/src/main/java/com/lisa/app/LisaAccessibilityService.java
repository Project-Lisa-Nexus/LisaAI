package com.lisa.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class LisaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LisaAccessibility";
    private static LisaAccessibilityService instance;

    private android.view.WindowManager indicatoreWindowManager;
    private android.view.View indicatoreLisa;
    private android.view.WindowManager.LayoutParams indicatoreLp;
    private volatile boolean indicatoreAscolto = false;

    // ===== CONTEXT ENGINE V1 =====
    private volatile String contestoPacchetto = "";
    private volatile String contestoClasse = "";
    private volatile int contestoWindowId = -1;
    private volatile boolean contestoFocusInput = false;
    private volatile boolean contestoEditabile = false;
    private volatile long contestoTimestamp = 0L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Servizio accessibilita connesso");

        mostraStatoLisa(false);
    }

    public static void aggiornaIndicatoreAscolto(
            boolean ascolto) {

        LisaAccessibilityService servizio = instance;

        if (servizio == null) {
            return;
        }

        new android.os.Handler(
                android.os.Looper.getMainLooper()
        ).post(() -> servizio.mostraStatoLisa(ascolto));
    }


    private int dp(int valore) {
        return Math.round(
                valore * getResources().getDisplayMetrics().density
        );
    }


    private android.graphics.drawable.GradientDrawable creaBollaLisa(
            boolean ascolto) {

        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();

        d.setShape(
                android.graphics.drawable.GradientDrawable.OVAL
        );

        d.setColor(
                ascolto
                        ? android.graphics.Color.rgb(255, 145, 0)
                        : android.graphics.Color.rgb(35, 200, 80)
        );

        d.setStroke(
                dp(2),
                android.graphics.Color.WHITE
        );

        return d;
    }


    private void mostraStatoLisa(boolean ascolto) {

        indicatoreAscolto = ascolto;

        try {

            if (indicatoreWindowManager == null) {

                indicatoreWindowManager =
                        (android.view.WindowManager)
                                getSystemService(
                                        android.content.Context.WINDOW_SERVICE
                                );
            }

            if (indicatoreWindowManager == null) {
                return;
            }

            if (indicatoreLisa == null) {

                indicatoreLisa =
                        new android.view.View(this);

                indicatoreLp =
                        new android.view.WindowManager.LayoutParams(
                                dp(42),
                                dp(42),
                                android.view.WindowManager.LayoutParams
                                        .TYPE_ACCESSIBILITY_OVERLAY,
                                android.view.WindowManager.LayoutParams
                                        .FLAG_NOT_FOCUSABLE
                                        | android.view.WindowManager.LayoutParams
                                        .FLAG_NOT_TOUCH_MODAL
                                        | android.view.WindowManager.LayoutParams
                                        .FLAG_LAYOUT_IN_SCREEN,
                                android.graphics.PixelFormat.TRANSLUCENT
                        );

                indicatoreLp.gravity =
                        android.view.Gravity.TOP
                                | android.view.Gravity.START;

                android.content.SharedPreferences prefs =
                        getSharedPreferences(
                                "lisa_ui",
                                MODE_PRIVATE
                        );

                indicatoreLp.x =
                        prefs.getInt(
                                "indicatore_x",
                                dp(25)
                        );

                indicatoreLp.y =
                        prefs.getInt(
                                "indicatore_y",
                                dp(160)
                        );


                final float[] downX = new float[1];
                final float[] downY = new float[1];
                final int[] startX = new int[1];
                final int[] startY = new int[1];
                final boolean[] trascinato = new boolean[1];


                indicatoreLisa.setOnTouchListener(
                        (v, event) -> {

                            switch (event.getActionMasked()) {

                                case android.view.MotionEvent.ACTION_DOWN:

                                    downX[0] = event.getRawX();
                                    downY[0] = event.getRawY();

                                    startX[0] = indicatoreLp.x;
                                    startY[0] = indicatoreLp.y;

                                    trascinato[0] = false;

                                    return true;


                                case android.view.MotionEvent.ACTION_MOVE:

                                    float dx =
                                            event.getRawX()
                                                    - downX[0];

                                    float dy =
                                            event.getRawY()
                                                    - downY[0];

                                    if (Math.abs(dx) > dp(6)
                                            || Math.abs(dy) > dp(6)) {

                                        trascinato[0] = true;
                                    }

                                    indicatoreLp.x =
                                            startX[0]
                                                    + Math.round(dx);

                                    indicatoreLp.y =
                                            startY[0]
                                                    + Math.round(dy);

                                    android.util.DisplayMetrics dm =
                                            getResources()
                                                    .getDisplayMetrics();

                                    indicatoreLp.x =
                                            Math.max(
                                                    0,
                                                    Math.min(
                                                            indicatoreLp.x,
                                                            dm.widthPixels
                                                                    - dp(42)
                                                    )
                                            );

                                    indicatoreLp.y =
                                            Math.max(
                                                    0,
                                                    Math.min(
                                                            indicatoreLp.y,
                                                            dm.heightPixels
                                                                    - dp(42)
                                                    )
                                            );

                                    try {

                                        indicatoreWindowManager
                                                .updateViewLayout(
                                                        indicatoreLisa,
                                                        indicatoreLp
                                                );

                                    } catch (Exception ignored) {
                                    }

                                    return true;


                                case android.view.MotionEvent.ACTION_UP:

                                    prefs.edit()
                                            .putInt(
                                                    "indicatore_x",
                                                    indicatoreLp.x
                                            )
                                            .putInt(
                                                    "indicatore_y",
                                                    indicatoreLp.y
                                            )
                                            .apply();

                                    if (!trascinato[0]) {

                                        try {

                                            android.content.Intent i =
                                                    new android.content.Intent(
                                                            this,
                                                            MainActivity.class
                                                    );

                                            i.addFlags(
                                                    android.content.Intent
                                                            .FLAG_ACTIVITY_NEW_TASK
                                                            | android.content.Intent
                                                            .FLAG_ACTIVITY_SINGLE_TOP
                                            );

                                            startActivity(i);

                                            Log.i(
                                                    TAG,
                                                    "BOLLA LISA: apertura pannello Lisa"
                                            );

                                        } catch (Exception e) {

                                            Log.e(
                                                    TAG,
                                                    "Errore apertura Lisa dalla bolla",
                                                    e
                                            );
                                        }
                                    }

                                    return true;
                            }

                            return false;
                        }
                );


                indicatoreWindowManager.addView(
                        indicatoreLisa,
                        indicatoreLp
                );

                Log.i(
                        TAG,
                        "BOLLA LISA AGGIUNTA"
                );
            }


            indicatoreLisa.setBackground(
                    creaBollaLisa(ascolto)
            );

            Log.i(
                    TAG,
                    "BOLLA LISA: "
                            + (
                                ascolto
                                        ? "ARANCIONE - ASCOLTO"
                                        : "VERDE - RIPOSO"
                            )
            );


        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "ERRORE BOLLA LISA",
                    e
            );
        }
    }


    private void rimuoviIndicatoreLisa() {

        if (indicatoreLisa != null
                && indicatoreWindowManager != null) {

            try {

                indicatoreWindowManager.removeView(
                        indicatoreLisa
                );

            } catch (Exception ignored) {
            }
        }

        indicatoreLisa = null;
        indicatoreLp = null;
        indicatoreWindowManager = null;
    }


    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        if (event == null) return;

        int tipo = event.getEventType();

        if (tipo != android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            tipo != android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            return;
        }

        String eventPkg =
                event.getPackageName() == null
                        ? ""
                        : event.getPackageName().toString();

        String eventClass =
                event.getClassName() == null
                        ? ""
                        : event.getClassName().toString();

        AccessibilityNodeInfo root = getRootInActiveWindow();

        String rootPkg = "";
        String rootClass = "";

        if (root != null) {
            rootPkg =
                    root.getPackageName() == null
                            ? ""
                            : root.getPackageName().toString();

            rootClass =
                    root.getClassName() == null
                            ? ""
                            : root.getClassName().toString();
        }

        // Lisa è il pannello di controllo dell'assistente:
        // non deve sostituire l'app che l'utente stava realmente usando.
        String pacchettoLisa = getPackageName();

        if (pacchettoLisa.equals(eventPkg)) {
            Log.i(
                    TAG,
                    "CTX_V1 ignore_self keep=" + contestoPacchetto
            );
            return;
        }

        boolean eventoTransitorio =
                "com.google.android.inputmethod.latin".equals(eventPkg) ||
                "com.android.systemui".equals(eventPkg) ||
                ("com.google.android.googlequicksearchbox".equals(eventPkg)
                        && !rootPkg.isEmpty()
                        && !eventPkg.equals(rootPkg));

        // Anche tastiera/SystemUI sopra la finestra di Lisa
        // non devono cancellare l'ultimo contesto reale.
        if (eventoTransitorio && pacchettoLisa.equals(rootPkg)) {
            Log.i(
                    TAG,
                    "CTX_V1 ignore_overlay_self keep=" + contestoPacchetto
            );
            return;
        }

        String pacchettoScelto;

        if (eventoTransitorio && !rootPkg.isEmpty()) {
            pacchettoScelto = rootPkg;
        } else if (!eventPkg.isEmpty()) {
            pacchettoScelto = eventPkg;
        } else {
            pacchettoScelto = rootPkg;
        }

        boolean focusInput = false;
        boolean editabile = false;

        // La root viene usata per il focus solo se appartiene
        // realmente all'app che stiamo considerando corrente.
        if (root != null && pacchettoScelto.equals(rootPkg)) {
            AccessibilityNodeInfo focus =
                    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);

            if (focus != null) {
                focusInput = true;
                editabile = focus.isEditable();
            }
        }

        contestoPacchetto = pacchettoScelto;
        contestoClasse =
                pacchettoScelto.equals(eventPkg)
                        ? eventClass
                        : rootClass;
        contestoWindowId = event.getWindowId();
        contestoFocusInput = focusInput;
        contestoEditabile = editabile;
        contestoTimestamp = System.currentTimeMillis();

        Log.i(TAG,
                "CTX_V1"
                + " app=" + contestoPacchetto
                + " classe=" + contestoClasse
                + " window=" + contestoWindowId
                + " focus=" + contestoFocusInput
                + " editable=" + contestoEditabile);
    }

    public String getCurrentPackageName() {
        return contestoPacchetto;
    }

    public String getCurrentClassName() {
        return contestoClasse;
    }

    public int getCurrentWindowId() {
        return contestoWindowId;
    }

    public boolean hasCurrentInputFocus() {
        return contestoFocusInput;
    }

    public boolean isCurrentEditable() {
        return contestoEditabile;
    }

    public long getCurrentContextTimestamp() {
        return contestoTimestamp;
    }

    public java.util.ArrayList<String> getVisiblePackageNames() {
        java.util.LinkedHashSet<String> trovati =
                new java.util.LinkedHashSet<>();

        try {
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> finestre =
                    getWindows();

            if (finestre != null) {
                for (android.view.accessibility.AccessibilityWindowInfo finestra : finestre) {
                    if (finestra == null) continue;

                    AccessibilityNodeInfo root = finestra.getRoot();
                    if (root == null || root.getPackageName() == null) continue;

                    String pkg = root.getPackageName().toString().trim();

                    if (pkg.isEmpty()) continue;
                    if (pkg.equals(getPackageName())) continue;
                    if ("com.google.android.inputmethod.latin".equals(pkg)) continue;
                    if ("com.android.systemui".equals(pkg)) continue;

                    trovati.add(pkg);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "CTX_WINDOWS errore", e);
        }

        return new java.util.ArrayList<>(trovati);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Servizio interrotto");
    }


    private void spegniLisaCompletamente() {
        rimuoviIndicatoreLisa();
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
        Log.d(TAG, "AccessibilityService unbound: VoiceService NON viene arrestata");
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

    public boolean cliccaPulsanteChiamata(boolean video) {
        android.view.accessibility.AccessibilityNodeInfo radice = getRootInActiveWindow();
        if (radice == null) return false;

        String[] chiaviVideo = {"videochiamata", "video call", "video chiamata"};
        String[] chiaviVoce = {"chiamata vocale", "voice call", "audio call", "chiama", "call"};

        String[] chiavi = video ? chiaviVideo : chiaviVoce;

        for (String chiave : chiavi) {
            android.view.accessibility.AccessibilityNodeInfo trovato =
                    cercaPerDescrizioneEscludendo(radice, chiave, video ? null : "video");
            if (trovato != null) {
                boolean risultato = AccessibilityUtils.click(trovato);
                if (risultato) return true;
            }
        }
        return false;
    }

    private android.view.accessibility.AccessibilityNodeInfo cercaPerDescrizioneEscludendo(
            android.view.accessibility.AccessibilityNodeInfo nodo,
            String chiave,
            String daEscludere) {

        if (nodo == null) return null;

        CharSequence descrizione = nodo.getContentDescription();
        if (descrizione != null) {
            String testoDesc = descrizione.toString().toLowerCase(java.util.Locale.ITALIAN);
            boolean contieneChiave = testoDesc.contains(chiave);
            boolean contieneEsclusa = daEscludere != null && testoDesc.contains(daEscludere);

            if (contieneChiave && !contieneEsclusa) {
                return nodo;
            }
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo figlio = nodo.getChild(i);
            android.view.accessibility.AccessibilityNodeInfo risultato =
                    cercaPerDescrizioneEscludendo(figlio, chiave, daEscludere);
            if (risultato != null) return risultato;
        }

        return null;
    }

    private android.view.accessibility.AccessibilityNodeInfo cercaPerDescrizione(
            android.view.accessibility.AccessibilityNodeInfo nodo,
            String chiave) {

        if (nodo == null) return null;

        CharSequence descrizione = nodo.getContentDescription();
        if (descrizione != null &&
                descrizione.toString().toLowerCase(java.util.Locale.ITALIAN).contains(chiave)) {
            return nodo;
        }

        for (int i = 0; i < nodo.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo figlio = nodo.getChild(i);
            android.view.accessibility.AccessibilityNodeInfo risultato =
                    cercaPerDescrizione(figlio, chiave);
            if (risultato != null) return risultato;
        }

        return null;
    }

    private static String messaggioInAttesa = null;
    private String nomeOriginaleAmbiguo = null;
    private final java.util.ArrayList<String> contattiAmbigui =
            new java.util.ArrayList<>();
    private static String testoMessaggioAmbiguo = null;
    private static String azioneAmbigua = null;
    private static String appAmbigua = null;
    private static String modalitaAmbigua = null;



    public static void scriviStatic(Context context, String destinatario, String messaggio) {
        Log.d("LisaAccessibility", "Scrivi a " + destinatario + ": " + messaggio);
        messaggioInAttesa = messaggio;
    }

    private int distanzaLevenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + costo
                );
            }
        }
        return d[a.length()][b.length()];
    }

    /**
     * Quando la ricerca esatta in rubrica non trova nulla (nome sentito
     * male dal microfono, es. "jaro" invece di "Yaro"), confronta il nome
     * con tutti i contatti della rubrica usando la distanza di Levenshtein
     * e propone come ambiguita' quelli abbastanza simili.
     */
    private String cercaContattoFonetico(String query) {
        String queryNorm = query.trim().toLowerCase(java.util.Locale.ITALIAN);
        if (queryNorm.isEmpty()) return null;

        java.util.ArrayList<String> tuttiContatti = new java.util.ArrayList<>();

        try {
            android.database.Cursor cursor = getContentResolver().query(
                    android.provider.ContactsContract.Contacts.CONTENT_URI,
                    new String[]{
                            android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                    },
                    android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER + " > 0",
                    null,
                    null
            );

            if (cursor != null) {
                int indice = cursor.getColumnIndex(
                        android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                );
                while (cursor.moveToNext()) {
                    if (indice >= 0) {
                        String nome = cursor.getString(indice);
                        if (nome != null && !nome.trim().isEmpty()) {
                            tuttiContatti.add(nome.trim());
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            return null;
        }

        java.util.ArrayList<String> candidati = new java.util.ArrayList<>();

        for (String nome : tuttiContatti) {
            String primoNome = nome.split("\\s+")[0].toLowerCase(java.util.Locale.ITALIAN);
            int distanza = distanzaLevenshtein(queryNorm, primoNome);

            // Tollera 1 errore ogni 4 lettere circa (es. "jaro"/"yaro" = 1).
            int sogliaMax = Math.max(1, primoNome.length() / 4);

            if (distanza <= sogliaMax) {
                candidati.add(nome);
            }
        }

        if (candidati.isEmpty()) return null;

        if (candidati.size() == 1) {
            return candidati.get(0);
        }

        contattiAmbigui.clear();
        contattiAmbigui.addAll(candidati);
        return null;
    }

    private String risolviContattoRubrica(String richiesto) {
        contattiAmbigui.clear();

        if (richiesto == null || richiesto.trim().isEmpty())
            return richiesto;

        String query = richiesto.trim();
        nomeOriginaleAmbiguo = query;

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

        if (trovati.isEmpty()) {
            String candidatoFonetico = cercaContattoFonetico(query);
            if (candidatoFonetico != null) {
                return candidatoFonetico;
            }
            return query;
        }

        java.util.ArrayList<String> preferiti =
                new java.util.ArrayList<>();

        String q = query.toLowerCase(java.util.Locale.ITALIAN);

        for (String nome : trovati) {
            String n = nome.toLowerCase(java.util.Locale.ITALIAN);

            if (n.equals(q) || n.startsWith(q + " "))
                preferiti.add(nome);
        }

        // Raggruppa per nome "pulito" (senza emoji/simboli): evita falsa
        // ambiguita' quando lo stesso contatto e' salvato piu' volte con
        // varianti di sole emoji.
        java.util.List<String> daValutare =
                preferiti.isEmpty() ? new java.util.ArrayList<>(trovati) : preferiti;

        java.util.LinkedHashMap<String, String> perNomePulito =
                new java.util.LinkedHashMap<>();

        for (String nome : daValutare) {
            String pulito = nome.replaceAll("[^\\p{L}\\p{N}\\s]", "")
                                 .trim()
                                 .toLowerCase(java.util.Locale.ITALIAN);

            String esistente = perNomePulito.get(pulito);
            if (esistente == null || nome.length() < esistente.length()) {
                perNomePulito.put(pulito, nome);
            }
        }

        if (perNomePulito.size() == 1) {
            return perNomePulito.values().iterator().next();
        }

        contattiAmbigui.addAll(perNomePulito.values());

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

    private String trovaNumeroContattoEsatto(String nome) {

        if (nome == null || nome.trim().isEmpty())
            return null;

        android.database.Cursor cursor = null;
        String primoNumero = null;

        try {
            cursor = getContentResolver().query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE
                    },
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                            + " = ?",
                    new String[]{nome.trim()},
                    null
            );

            if (cursor == null)
                return null;

            int indiceNumero = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            );

            int indiceTipo = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.TYPE
            );

            while (cursor.moveToNext()) {

                if (indiceNumero < 0)
                    continue;

                String numero = cursor.getString(indiceNumero);

                if (numero == null || numero.trim().isEmpty())
                    continue;

                if (primoNumero == null)
                    primoNumero = numero.trim();

                if (indiceTipo >= 0) {
                    int tipo = cursor.getInt(indiceTipo);

                    if (tipo ==
                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                        return numero.trim();
                    }
                }
            }

        } catch (Exception e) {
            android.util.Log.e(
                    "LisaAccessibility",
                    "Errore lettura numero contatto",
                    e
            );

        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }

        return primoNumero;
    }


    private String normalizzaNumeroWhatsApp(String numero) {

        if (numero == null)
            return null;

        String iso = java.util.Locale.getDefault().getCountry();

        if (iso == null || iso.trim().isEmpty())
            iso = "IT";

        String e164 =
                android.telephony.PhoneNumberUtils.formatNumberToE164(
                        numero,
                        iso
                );

        String pulito;

        if (e164 != null && !e164.trim().isEmpty()) {
            pulito = e164.replaceAll("[^0-9]", "");
        } else {
            pulito = numero.replaceAll("[^0-9]", "");

            if (pulito.startsWith("00"))
                pulito = pulito.substring(2);
        }

        return pulito.isEmpty() ? null : pulito;
    }


    private boolean apriChatWhatsApp(
            String numero,
            String messaggio) {

        String numeroWhatsapp =
                normalizzaNumeroWhatsApp(numero);

        if (numeroWhatsapp == null)
            return false;

        String url =
                "https://wa.me/"
                        + numeroWhatsapp;

        if (messaggio != null
                && !messaggio.trim().isEmpty()) {

            url += "?text="
                    + android.net.Uri.encode(messaggio);
        }

        try {
            android.content.Intent intent =
                    new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                    );

            intent.setPackage("com.whatsapp");

            intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            );

            startActivity(intent);

            return true;

        } catch (Exception e) {

            android.util.Log.e(
                    "LisaAccessibility",
                    "Impossibile aprire chat WhatsApp",
                    e
            );

            return false;
        }
    }


    private volatile long ultimaChiamataMessaggio = 0;
    private volatile long ultimaChiamataTelefono = 0;
    private volatile boolean chiamataDuplicataIgnorata = false;

    public boolean isChiamataDuplicataIgnorata() {
        return chiamataDuplicataIgnorata;
    }

    private final android.os.Handler handlerAttesa =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public boolean cercaContattoEChiama(String contatto, String appChiamata, String modalitaChiamata) {

        long ora = System.currentTimeMillis();
        if (ora - ultimaChiamataTelefono < 2000) {
            android.util.Log.i("LisaAccessibility", "Chiamata duplicata ignorata (chiama)");
            chiamataDuplicataIgnorata = true;
            return false;
        }
        chiamataDuplicataIgnorata = false;
        ultimaChiamataTelefono = ora;

        String contattoRisolto = risolviContattoRubrica(contatto);

        if (contattoRisolto == null) {
            azioneAmbigua = "chiama";
            appAmbigua = appChiamata;
            modalitaAmbigua = modalitaChiamata;
            android.util.Log.i(
                    "LisaAccessibility",
                    "Contatto ambiguo (chiamata): " + contatto
            );
            return false;
        }

        String numero = trovaNumeroContattoEsatto(contattoRisolto);

        if (numero == null) {
            android.util.Log.e(
                    "LisaAccessibility",
                    "Numero non trovato per: " + contattoRisolto
            );
            return false;
        }

        if ("whatsapp".equals(appChiamata)) {
            boolean apertaChat = apriChatWhatsApp(numero, null);
            if (!apertaChat) return false;

            final boolean videoFinale = "video".equals(modalitaChiamata);

            handlerAttesa.postDelayed(() -> {
                cliccaPulsanteChiamata(videoFinale);
            }, 2000);

            return true;
        }

        chiamaStatic(this, numero);
        return true;
    }

    public boolean cercaContattoEInvia(
            String contatto,
            String messaggio,
            String app,
            String pacchetto) {

        String canale = app == null
                ? ""
                : app.trim().toLowerCase(java.util.Locale.ITALIAN);

        String pkg = pacchetto == null
                ? ""
                : pacchetto.trim();

        // WhatsApp resta sul flusso stabile già esistente.
        if (canale.isEmpty()
                || "whatsapp".equals(canale)
                || "com.whatsapp".equals(pkg)) {
            return cercaContattoEScrivi(contatto, messaggio);
        }

        String contattoRisolto = risolviContattoRubrica(contatto);

        if (contattoRisolto == null) {
            testoMessaggioAmbiguo = messaggio;
            azioneAmbigua = "messaggio";
            appAmbigua = canale;
            return false;
        }

        String numero = trovaNumeroContattoEsatto(contattoRisolto);

        if ("sms".equals(canale)) {
            if (numero == null || numero.trim().isEmpty()) return false;

            try {
                android.content.Intent intent =
                        new android.content.Intent(
                                android.content.Intent.ACTION_SENDTO
                        );

                intent.setData(
                        android.net.Uri.parse(
                                "smsto:" + android.net.Uri.encode(numero)
                        )
                );

                intent.putExtra("sms_body", messaggio);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

                startActivity(intent);

                messaggioInAttesa = messaggio;

                Log.i(
                        "LisaAccessibility",
                        "Dispatcher messaggio: SMS -> " + contattoRisolto
                );

                return true;

            } catch (Exception e) {
                Log.e("LisaAccessibility", "Errore apertura SMS", e);
                return false;
            }
        }

        if ("telegram".equals(canale)) {
            if (numero == null || numero.trim().isEmpty()) return false;

            try {
                String numeroPulito =
                        numero.replaceAll("[^0-9+]", "");

                String uri =
                        "tg://resolve?phone="
                                + android.net.Uri.encode(numeroPulito)
                                + "&text="
                                + android.net.Uri.encode(messaggio);

                android.content.Intent intent =
                        new android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(uri)
                        );

                intent.setPackage("org.telegram.messenger");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

                startActivity(intent);

                messaggioInAttesa = messaggio;

                Log.i(
                        "LisaAccessibility",
                        "Dispatcher messaggio: Telegram -> " + contattoRisolto
                );

                return true;

            } catch (Exception e) {
                Log.e("LisaAccessibility", "Errore apertura Telegram", e);
                return false;
            }
        }

        if ("email".equals(canale) || "mail".equals(canale)) {
            String email = trovaEmailContattoEsatto(contattoRisolto);

            if (email == null || email.trim().isEmpty()) return false;

            try {
                android.content.Intent intent =
                        new android.content.Intent(
                                android.content.Intent.ACTION_SENDTO
                        );

                intent.setData(
                        android.net.Uri.parse(
                                "mailto:" + android.net.Uri.encode(email)
                        )
                );

                intent.putExtra(
                        android.content.Intent.EXTRA_TEXT,
                        messaggio
                );

                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

                startActivity(intent);

                messaggioInAttesa = messaggio;

                Log.i(
                        "LisaAccessibility",
                        "Dispatcher messaggio: EMAIL -> " + contattoRisolto
                );

                return true;

            } catch (Exception e) {
                Log.e("LisaAccessibility", "Errore apertura email", e);
                return false;
            }
        }

        Log.w(
                "LisaAccessibility",
                "Canale messaggio non ancora supportato: "
                        + canale
                        + " pacchetto="
                        + pkg
        );

        return false;
    }

    private String trovaEmailContattoEsatto(String nome) {
        android.database.Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    new String[] {
                            android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS,
                            android.provider.ContactsContract.CommonDataKinds.Email.DISPLAY_NAME
                    },
                    android.provider.ContactsContract.CommonDataKinds.Email.DISPLAY_NAME
                            + " = ? COLLATE NOCASE",
                    new String[] { nome },
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int indice = cursor.getColumnIndex(
                        android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS
                );

                if (indice >= 0) {
                    String email = cursor.getString(indice);
                    if (email != null && !email.trim().isEmpty()) {
                        return email.trim();
                    }
                }
            }

        } catch (Exception e) {
            Log.e(
                    "LisaAccessibility",
                    "Errore ricerca email per " + nome,
                    e
            );

        } finally {
            if (cursor != null) cursor.close();
        }

        return null;
    }

    public boolean cercaContattoEScrivi(
            String contatto,
            String messaggio) {

        long ora = System.currentTimeMillis();
        if (ora - ultimaChiamataMessaggio < 2000) {
            android.util.Log.i("LisaAccessibility", "Chiamata duplicata ignorata");
            return false;
        }
        ultimaChiamataMessaggio = ora;

        /*
         * PRIMA risolviamo il contatto dalla rubrica.
         *
         * Se esistono più Leonardo, il metodo già presente
         * riempie contattiAmbigui e restituisce null.
         * LisaCommandReceiver farà quindi la domanda:
         * "Leonardo Astorino o Leonardo Fusi?"
         */
        String contattoRisolto =
                risolviContattoRubrica(contatto);

        if (contattoRisolto == null) {
            testoMessaggioAmbiguo = messaggio;
            azioneAmbigua = "messaggio";
            android.util.Log.i(
                    "LisaAccessibility",
                    "Contatto ambiguo: " + contatto
            );
            return false;
        }

        String numero =
                trovaNumeroContattoEsatto(
                        contattoRisolto
                );

        if (numero == null) {
            android.util.Log.e(
                    "LisaAccessibility",
                    "Numero non trovato per: "
                            + contattoRisolto
            );
            return false;
        }

        /*
         * Il messaggio viene conservato perché
         * confermaInvio() deve inviarlo soltanto
         * dopo la conferma vocale.
         */
        messaggioInAttesa = messaggio;

        boolean aperta =
                apriChatWhatsApp(
                        numero,
                        messaggio
                );

        if (aperta) {
            android.util.Log.i(
                    "LisaAccessibility",
                    "WhatsApp diretto: "
                            + contattoRisolto
            );
        }

        return aperta;
    }


    public void ascoltaConfermaInvio() {
        // La conferma usa il microfono principale di LisaVoiceService.
        // NON viene creato un secondo SpeechRecognizer.
        android.util.Log.i("LisaAccessibility", "Conferma invio: ritorno al microfono principale Lisa");
        return;
    }

    private static boolean inAttesaNuovoTesto = false;

    public void annullaInvio() {
        messaggioInAttesa = null;
        inAttesaNuovoTesto = false;
        Log.i("LisaAccessibility", "Invio annullato dalla voce");
    }

    public void chiediNuovoTesto() {
        inAttesaNuovoTesto = true;
        Log.i("LisaAccessibility", "In attesa del nuovo testo da scrivere");
    }

    public boolean inAttesaDiNuovoTesto() {
        return inAttesaNuovoTesto;
    }

    public boolean haMessaggioInAttesa() {
        return messaggioInAttesa != null
                && !messaggioInAttesa.trim().isEmpty();
    }

    /**
     * Sostituisce il testo gia' scritto nel campo messaggio (es. WhatsApp)
     * con uno nuovo, senza riaprire la chat. ACTION_SET_TEXT sovrascrive
     * sempre il contenuto esistente, quindi basta richiamare scriviTesto().
     */
    public boolean riscriviMessaggio(String nuovoTesto) {
        inAttesaNuovoTesto = false;

        boolean scritto = scriviTesto(nuovoTesto);
        if (scritto) {
            messaggioInAttesa = nuovoTesto;
        }
        return scritto;
    }

    public boolean cancellaTestoCorrente() {
        boolean cancellato = scriviTesto("");

        if (cancellato) {
            // Non deve essere possibile inviare accidentalmente
            // il vecchio testo dopo averlo cancellato.
            messaggioInAttesa = null;

            // Rimaniamo in modalità riscrittura:
            // Lisa aspetta il nuovo contenuto.
            inAttesaNuovoTesto = true;

            Log.i(
                    "LisaAccessibility",
                    "Campo testo cancellato; attesa nuovo testo attiva"
            );
        }

        return cancellato;
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

    
    public boolean inAttesaSceltaContatto() {
        return !contattiAmbigui.isEmpty();
    }

    private void insegnaContattoAppreso(String nomeTrovato) {
        String sentito = nomeOriginaleAmbiguo;
        if (sentito == null || sentito.trim().isEmpty() || nomeTrovato == null) return;

        final String sentitoFinale = sentito;
        final String esattoFinale = nomeTrovato;

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://127.0.0.1:5000/api/impara_contatto");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                org.json.JSONObject corpo = new org.json.JSONObject();
                corpo.put("sentito", sentitoFinale);
                corpo.put("esatto", esattoFinale);

                byte[] dati = corpo.toString().getBytes("UTF-8");
                conn.getOutputStream().write(dati);
                conn.getInputStream().close();
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }, "Lisa-Apprendimento").start();
    }

    public String risolviSceltaContatto(String scelta) {
        if (contattiAmbigui.isEmpty() || scelta == null) return null;
        String sceltaNorm = scelta.trim().toLowerCase(java.util.Locale.ITALIAN);

        for (String nome : contattiAmbigui) {
            String nomeNorm = nome.toLowerCase(java.util.Locale.ITALIAN);
            if (nomeNorm.contains(sceltaNorm) || sceltaNorm.contains(nomeNorm)) {
                insegnaContattoAppreso(nome);
                return nome;
            }
        }

        // Tolleranza per errori di riconoscimento vocale
        // (es. "Astorino" sentito come "storino", perde la prima lettera).
        for (String nome : contattiAmbigui) {
            String nomeNorm = nome.toLowerCase(java.util.Locale.ITALIAN);
            for (String parola : nomeNorm.split("\\s+")) {
                if (parola.length() < 4) continue;
                String senzaPrimaLettera = parola.substring(1);
                if (sceltaNorm.contains(parola) ||
                        sceltaNorm.contains(senzaPrimaLettera)) {
                    insegnaContattoAppreso(nome);
                    return nome;
                }
            }
        }

        return null;
    }

    public String getTestoMessaggioAmbiguo() {
        return testoMessaggioAmbiguo;
    }

    public String getAzioneAmbigua() {
        return azioneAmbigua;
    }

    public String getAppAmbigua() {
        return appAmbigua;
    }

    public String getModalitaAmbigua() {
        return modalitaAmbigua;
    }

    public void pulisciAmbiguita() {
        contattiAmbigui.clear();
        testoMessaggioAmbiguo = null;
        azioneAmbigua = null;
        appAmbigua = null;
        modalitaAmbigua = null;
    }

public static LisaAccessibilityService getInstance() { return instance; }

    public void apriAppPerNome(String nomeApp) { AppFinder.apriAppPerNome(this, nomeApp); }

}
