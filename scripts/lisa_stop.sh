#!/usr/bin/env bash
set -u

LISAOS="$HOME/LisaOS"
PACKAGE="com.lisa.nexus"
TARGET="${1:-}"

OS_ESITO="⚠️ non verificato"
APP_ESITO="⚠️ non gestita"

echo "====================================="
echo "        ARRESTO RAPIDO LISA"
echo "====================================="

# --------------------------------------------------
# 1. LISA OS
# --------------------------------------------------

echo
echo "=== LISA OS ==="

LISAOS_ERA_ATTIVO=0

if bash "$LISAOS/scripts/dev_status.sh" >/dev/null 2>&1; then
    LISAOS_ERA_ATTIVO=1
fi

if bash "$LISAOS/scripts/dev_stop.sh"; then
    if [ "$LISAOS_ERA_ATTIVO" -eq 1 ]; then
        echo "✅ LisaOS arrestato"
        OS_ESITO="✅ arrestato"
    else
        echo "✅ LisaOS già spento / cleanup completato"
        OS_ESITO="✅ già spento"
    fi
else
    echo "⚠️ dev_stop.sh ha segnalato un problema"
    OS_ESITO="⚠️ controllare"
fi

# --------------------------------------------------
# 2. ADB + LISA APP
# --------------------------------------------------

echo
echo "=== LISA APP ==="

if [ -n "$TARGET" ]; then

    if adb devices |
            awk 'NR>1 && $2=="device" {print $1}' |
            grep -Fxq "$TARGET"; then

        echo "✅ ADB connesso: $TARGET"

    else
        echo "⚠️ ADB non connesso a: $TARGET"
        TARGET=""
    fi

else

    mapfile -t DEVICES < <(
        adb devices |
        awk 'NR>1 && $2=="device" {print $1}'
    )

    if [ "${#DEVICES[@]}" -eq 1 ]; then
        TARGET="${DEVICES[0]}"
        echo "✅ ADB rilevato: $TARGET"

    elif [ "${#DEVICES[@]}" -eq 0 ]; then
        echo "⚠️ Nessun dispositivo ADB connesso"

    else
        echo "⚠️ Più dispositivi ADB connessi"
        echo "   LisaApp non viene chiusa per sicurezza"
        printf '   %s\n' "${DEVICES[@]}"
    fi
fi

if [ -n "$TARGET" ]; then
    if adb -s "$TARGET" shell am force-stop "$PACKAGE" \
            >/dev/null 2>&1; then
        echo "✅ LisaApp fermata"
        APP_ESITO="✅ fermata"
    else
        echo "⚠️ Impossibile fermare LisaApp via ADB"
        APP_ESITO="⚠️ non fermata"
    fi
else
    echo "⚠️ LisaApp non gestita: ADB non disponibile"
    APP_ESITO="⚠️ ADB assente"
fi

# --------------------------------------------------
# 3. RIEPILOGO
# --------------------------------------------------

echo
echo "====================================="
echo "       ARRESTO LISA COMPLETATO"
echo "====================================="
echo "LisaOS : $OS_ESITO"
echo "LisaApp: $APP_ESITO"

if [ -n "$TARGET" ]; then
    echo "ADB    : ✅ $TARGET"
else
    echo "ADB    : ⚠️ non connesso"
fi

echo
echo "PROMEMORIA MANUALE:"
echo "👉 Spegni Wireless Debug"
echo "👉 Disattiva Accessibility Lisa"
