#!/usr/bin/env bash
set -u

LISAOS="$HOME/LisaOS"
PACKAGE="com.lisa.nexus"
ACTIVITY="com.lisa.app.MainActivity"

echo "====================================="
echo "        AVVIO RAPIDO LISA"
echo "====================================="

TARGET="${1:-}"

# --------------------------------------------------
# 1. ADB
# --------------------------------------------------

if [ -n "$TARGET" ]; then
    echo
    echo "=== ADB ==="

    if adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -Fxq "$TARGET"; then
        echo "✅ ADB già connesso: $TARGET"
    else
        echo "Connessione ADB a $TARGET..."
        RISPOSTA=$(adb connect "$TARGET" 2>&1 || true)
        echo "$RISPOSTA"

        if ! adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -Fxq "$TARGET"; then
            echo "❌ ADB non connesso a $TARGET"
            exit 1
        fi

        echo "✅ ADB connesso: $TARGET"
    fi
else
    mapfile -t DEVICES < <(
        adb devices |
        awk 'NR>1 && $2=="device" {print $1}'
    )

    if [ "${#DEVICES[@]}" -eq 0 ]; then
        echo
        echo "❌ Nessun dispositivo ADB connesso."
        echo
        echo "Uso:"
        echo "  bash scripts/lisa_start.sh IP:PORTA"
        echo
        echo "Esempio:"
        echo "  bash scripts/lisa_start.sh 192.168.18.171:38987"
        exit 1
    fi

    if [ "${#DEVICES[@]}" -gt 1 ]; then
        echo
        echo "❌ Più dispositivi ADB collegati."
        echo "Specifica IP:PORTA come argomento."
        printf '  %s\n' "${DEVICES[@]}"
        exit 1
    fi

    TARGET="${DEVICES[0]}"
    echo
    echo "✅ ADB già connesso: $TARGET"
fi

# --------------------------------------------------
# 2. LISA OS
# --------------------------------------------------

echo
echo "=== LISA OS ==="

if bash "$LISAOS/scripts/dev_status.sh" >/dev/null 2>&1; then
    echo "✅ LisaOS già operativo"
else
    echo "LisaOS non operativo: avvio..."

    if ! bash "$LISAOS/scripts/dev_start.sh"; then
        echo "❌ Avvio LisaOS fallito"
        exit 1
    fi

    if bash "$LISAOS/scripts/dev_status.sh" >/dev/null 2>&1; then
        echo "✅ LisaOS operativo"
    else
        echo "❌ LisaOS non supera il controllo finale"
        exit 1
    fi
fi

# --------------------------------------------------
# 3. LISA APP
# --------------------------------------------------

echo
echo "=== LISA APP ==="

if adb -s "$TARGET" shell am start \
        -n "$PACKAGE/$ACTIVITY" >/dev/null 2>&1; then
    echo "✅ LisaApp aperta"
else
    echo "❌ Impossibile aprire LisaApp"
    exit 1
fi

# --------------------------------------------------
# RIEPILOGO
# --------------------------------------------------

echo
echo "====================================="
echo "          ✅ LISA PRONTA"
echo "====================================="
echo "ADB    : $TARGET"
echo "LisaOS : operativo"
echo "LisaApp: aperta"
