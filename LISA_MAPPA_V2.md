
# LISA MAPPA FASE B (10/10/2026)
Bozza verificata con prove file:riga.

## VERIFICATO

### dispatchGesture Android (5 chiamate)
- LisaAccessibilityService.java:1519, 1575, 1837 -> bypass del motore
- LisaGestureEngine.java:313, 332 -> dentro il motore
- Azione futura: migrare i 3 bypass

### Android receiver (4 nel manifest)
- LisaWidgetProvider (94): widget
- LisaBootReceiver (106): boot
- LisaCommandReceiver (113): filtro com.lisa.app.LISA_COMMAND
- BridgeReceiver (122): filtro com.lisa.nexus.COMANDO

### Action LisaCommandReceiver
- Java: ACTION_LISA_COMMAND = com.lisa.app.COMMAND (riga 13)
- Manifest filtro: com.lisa.app.LISA_COMMAND
- LisaVoiceService invia action esplicita (righe 1805, 1828, 3123, 4680)
- Broadcast espliciti bypassano il filtro del manifest

### Bridge LisaOS -> Android
- bridge/esegui_android.py:19 -> COMPONENTE com.lisa.nexus/...LisaCommandReceiver
- bridge/esegui_android.py:46 -> broadcast(extras) via adb shell am broadcast -n
- AZIONE_BROADCAST valore: da verificare

### Memoria LisaOS
- src/memory_store.py:8 -> SQLite memoria/lisa_memory_v1.db
- memory/manager.py:10 -> JSON memory/data.json
- src/command_processor.py:12 -> importa memory.manager
- Entrambi collegati al codice

### Agent Loop
- bridge/agent_loop.py -> 0 riferimenti Python
- Dormiente nei percorsi esaminati

### API LisaOS (14 endpoint)
- /api/invoca -> usato da LisaVoiceService (riga 57)
- /api/bridge -> da verificare

## NON VERIFICATO
- Doppia esecuzione: LisaOS broadcast + LisaVoiceService inoltraRispostaAndroid (4478, 4632) -> verificare se stessa azione
- Percorso completo broadcast
- AZIONE_BROADCAST valore
- Altre memorie JSON: alias_personali, ricordi_personali, storico
- WakeWordManager attivazione hardware
- Qwen modello GGUF caricato
- SQLite FTS5 indice
- SafetyGuard richiedeConfermaObbligatoria ritorna false in LisaCapabilitySystem

## FUNZIONA (test manuale utente 10/10)
Android:
- NUMERI, ETICHETTE, GRIGLIA, vignette
- Persistenza SCREEN_OFF/ON overlay
- Audio ASR, TTS MAY_DUCK
- Scroll vocale con clamp
- Lock screen FISICO
- Comandi: apri WhatsApp, volume, torcia, indietro, home, basta
LisaOS:
- Flask 5000 + llama-server 8080 (verificare con dev_status.sh)
- Non testato hardware: router, memoria, agent

## POSSIBILI SOVRAPPOSIZIONI
1. dispatchGesture: 5 punti, 3 bypass del motore
2. Memoria: SQLite + JSON entrambi collegati
3. Router LisaOS: bridge/router.py + src/command_router.py
4. File .bak multipli (conteggio da fare)

## V2 PROPOSTA (non implementare adesso)
- Android esecutore fisico
- LisaOS pianificatore + LLM
- Unico punto centralizzato gestione gesture
- Chiarire memoria: quale autorevole
- Collegare o archiviare agent_loop.py
- Completare LisaCapabilitySystem
- SafetyGuard: implementare conferme

## ATTIVITA FUTURE (FASE C in poi)
C1. Chiarire AZIONE_BROADCAST e percorso
C2. Migrare 3 dispatchGesture in GestureEngine
C3. Decidere agent_loop
C4. Completare LisaCapabilitySystem
C5. Pad, hotspot, context menu, dettatura, Verifier
