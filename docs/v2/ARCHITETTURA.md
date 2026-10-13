# Lisa V2 — Fase 1: dizionario canonico isolato

Baseline intoccabile: `v1-baseline` -> `db6a592`. Questa patch aggiunge solo file nuovi.
Nessuna modifica a Manifest, LisaVoiceService, LisaAccessibilityService,
LisaCapabilitySystem, LisaGestureEngine, Audio, EVA, R-Net, UserLAnd o LisaOS.

## Contratto

dizionario.json v1: schema_version, scope=lisa_v2_only, intents.
Ogni intento ha id (identificatore canonico universale), aliases unici,
params opzionali (solo stringhe), capability opzionale (enum Cap),
v1_action SOLO riferimento storico alla tabella OVERLAY_CMDS (mai usato per eseguire),
source documentale. Gli id non sono istruzioni di esecuzione. LisaV2Dictionary.java importa
com.lisa.app.LisaCapabilitySystem.Cap: dipendenza di compilazione intenzionale
sull enzim della V1, che resta immutato in questa fase. Prima di separare
completamente la V2 occorrera un contratto condiviso indipendente.

Quirk V1 documentato: overlay.etichette.nascondi riporta
v1_action=nascondi_numeri per fedelta alla tabella esistente. Questa stringa
NON definisce l operazione V2. L azione futura usa id e
params.target=labels, senza eseguire v1_action.

LisaV2Dictionary.load(context) legge da assets, rifiuta schema o scope diversi,
alias duplicati, identificatori duplicati e parametri non validi. resolve(text)
restituisce una Entry immutabile senza eseguire nulla. Se non trova testo:
null (fail-closed). Il sorgente dell input e responsabilita dell adapter futuro.

Normalizzazione: minuscolo italiano, punteggiatura semplice -> spazio, spazi multipli
compressi. Non applica fuzzy matching: evita azioni sbagliate per ASR incerto.

capability e facoltativa e, se presente, deve essere un nome valido di
LisaCapabilitySystem.Cap; gli intenti senza capability (volume, stop sessione,
torcia) richiedono futuri adapter dedicati. Il resolver non promette che il
dispatcher li sappia eseguire. Prima del collegamento futuro
servono registry, verifica stato, SafetyGuard (soprattutto Wi-Fi e Bluetooth) e
callback di completamento. Attenzione: system.wifi.off e
system.bluetooth.off sono soltanto identificatori semantici, NON istruzioni
operative. Il futuro dispatcher deve bloccarli fino a conferma esplicita,
protezione degli ausili e percorso di recupero indipendente; una nota nel JSON
NON sostituisce un controllo applicativo. Mai eseguire azioni critiche dalla
sola interpretazione.

## Architettura prevista, non attivata

VOCE / PAD / MOUSE-EVA / SWITCH / AGENT -> InputAdapter ->
LisaV2Dictionary (opzionale per linguaggio) -> ActionBinding canonico ->
SafetyGuard -> CapabilitySystem -> GestureEngine / SystemController ->
Verifier -> feedback. Il pad e il mouse possono creare ActionBinding direttamente,
senza passare per il dizionario linguistico.

## Prova statica e criteri di accettazione

1. python3 scripts/check_v2_dictionary.py: controlla JSON, alias duplicati,
   casi fissi e copertura completa della tabella OVERLAY_CMDS V1, senza
   modificare la V1.
2. ./gradlew :app:assembleDebug --no-daemon: il parser Java deve compilare.
3. git diff --cached --check sui file in staging; solo file nuovi in .../v2/,
   docs/v2/ e scripts/check_v2_dictionary.py.
4. Nessun test hardware richiesto in fase 1: la V2 non e connessa al runtime.
   Prima di abilitare V2, servono test hardware e fallback a V1.

Limite esplicito: mappatura iniziale di alias statici V1, non completa ancora
varianti contestuali, parole-numero 0..99, parametri dinamici (testo, destinatario,
numero griglia), router Python LisaOS e sintassi di piu comandi. Prima di proclamare
dizionario unico completato occorre il censimento anche di questi percorsi.

Nota per revisione fuori repository: python3 scripts/check_v2_dictionary.py --json-only
valida il solo JSON; non vale come controllo della copertura V1.
