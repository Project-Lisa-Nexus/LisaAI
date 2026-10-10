# LISA AI NEXUS — CHECKLIST

Ultimo aggiornamento: 07/10/2026
Legenda: [OK] testato · [WIP] in corso · [NO] congelato · [TODO] coda

## VOICE ACCESS CORE
[OK] NUMERI overlay + click + comandi voce
[OK] ETICHETTE stesso Target Overlay, rinumerazione compatta
[OK] GRIGLIA base 9x10 adattiva
[OK] GRIGLIA raffinamento 3x3
[OK] GRIGLIA tap cella
[OK] GRIGLIA riapri dopo tap
[OK] GRIGLIA reset livello 0 al cambio pagina

## INTERFACCIA
[OK] Badge NUMERI/GRIGLIA uniformati (15sp, 0x80202020)
[OK] Vignetta principale + "Sentito:" uniformate (15sp, 0xCC202020, r=20dp)

## LOCK SCREEN
[TODO] Lisa attiva su lock screen
[TODO] Ripristino ultima modalita (numeri/griglia/etichette)
[NO] Microfono condiviso con Google (audio focus)

## COMANDI VOCALI
[TODO] Dizionario centrale (verbi + target + azioni)
[TODO] Prefisso "Lisa" opzionale
[TODO] Memoria alias personali utente

## IMPOSTAZIONI VISIVE
[TODO] Dimensione caratteri / badge / vignette
[TODO] Trasparenza / contrasto
[TODO] Densita / stile linee griglia

## CODA
[TODO] Gesti (long press, drag, swipe, pinch)
[TODO] Suggerimenti vocali quando Lisa non capisce
[TODO] Widget Google: numeri/etichette sulle icone
[TODO] Freccia Android Sinistra/Destra
[TODO] UserLAnd: regressione scroll
[OK] STATO_LISA script diagnostica read-only

## REGOLE
- Una funzione alla volta
- Build + test hardware prima del commit
- 1 ora senza soluzione -> congelare
- Non toccare zone verdi senza bug reale

## FASE A — PULIZIA (chiusa 10/10/2026)
- SshTest rimosso (381752a)
- LisaVoiceCommandActivity rimossa (381752a)
- LocalSherpaAsrProbeManager rimosso (3697961)
- Archiviati: open-jarvis, droidrun, Open-AutoGLM, backup, log WhatsApp
- Test utente OK su WhatsApp, griglia, numeri, etichette, scroll, volume, torcia, indietro, home, basta, lock screen

## FEEDBACK #12 (bug aperto)
TTS Lisa piu basso della musica. Deve stare a volume normale.

## FASE A - PULIZIA (chiusa 10/10/2026, test parziali)
- SshTest rimosso (381752a)
- LisaVoiceCommandActivity rimossa (381752a, commit non atomico)
- LocalSherpaAsrProbeManager rimosso (3697961)
- Archiviati: open-jarvis, droidrun, Open-AutoGLM, backup, log WhatsApp
- Test manuali utente: WhatsApp, griglia, numeri, etichette, scroll, volume, torcia, indietro, home, basta, lock screen. NON ancora ripetuti in sequenza (criterio: 15 comandi consecutivi)

## FEEDBACK 12 (bug aperto)
TTS Lisa piu basso della musica.
