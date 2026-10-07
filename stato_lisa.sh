#!/bin/bash
# LISA — Stato diagnostico read-only
# Non modifica codice, impostazioni o dispositivo.

cd "$(dirname "$0")"

echo "=================================================="
echo " STATO LISA — $(date '+%Y-%m-%d %H:%M:%S')"
echo "=================================================="
echo

echo "== CHECKLIST =="
[ -f LISA_CHECKLIST.md ] && cat LISA_CHECKLIST.md || echo "(assente)"
echo

echo "== GIT =="
echo "HEAD: $(git log --oneline -1)"
echo "Branch: $(git branch --show-current)"
echo "Remote: $(git remote get-url origin 2>/dev/null)"
echo "--- status ---"
git status --short
echo "--- ultimi 5 commit ---"
git log --oneline -5
echo

ADB_OK=0
if timeout 5 adb get-state 2>/dev/null | grep -q "^device$"; then
    ADB_OK=1
fi

echo "== ADB =="
if [ "$ADB_OK" -eq 1 ]; then
    echo "🟢 dispositivo connesso"
else
    echo "🔴 dispositivo non connesso"
fi
echo

echo "== APK INSTALLATO =="
if [ "$ADB_OK" -eq 1 ]; then
    timeout 5 adb shell pm list packages 2>/dev/null |
        grep "com.lisa.nexus" || echo "(app assente)"
    timeout 5 adb shell dumpsys package com.lisa.nexus 2>/dev/null |
        grep -E "versionName|versionCode|lastUpdateTime" |
        head -3
else
    echo "(saltato: ADB non connesso)"
fi
echo

echo "== ACCESSIBILITY ATTIVA =="
if [ "$ADB_OK" -eq 1 ]; then
    timeout 5 adb shell settings get secure \
        enabled_accessibility_services 2>/dev/null
else
    echo "(saltato: ADB non connesso)"
fi
echo

echo "== PROCESSI LISA / LLAMA / FLASK / PYTHON =="
if [ "$ADB_OK" -eq 1 ]; then
    timeout 5 adb shell \
        "ps -A | grep -iE 'lisa|llama|flask|python'" \
        2>/dev/null | head -15
else
    echo "(saltato: ADB non connesso)"
fi
echo

echo "== PORTE IN ASCOLTO ANDROID =="
if [ "$ADB_OK" -eq 1 ]; then
    timeout 5 adb shell \
        "netstat -tlnp 2>/dev/null | grep -E ':5000|:8080'" \
        2>/dev/null || true
else
    echo "(saltato: ADB non connesso)"
fi
echo

echo "== MODELLI GGUF PRESENTI =="
find ~/lisa_models -name "*.gguf" -exec ls -lh {} \; 2>/dev/null ||
    echo "(nessuno)"
echo

echo "== FILE JAVA PRINCIPALI =="
ls -la app/src/main/java/com/lisa/app/*.java 2>/dev/null |
    awk '{print $5, $9}'
echo

echo "== FINE STATO =="
