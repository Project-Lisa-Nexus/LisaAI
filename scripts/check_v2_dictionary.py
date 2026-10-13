#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / 'app/src/main/assets/v2/dizionario.json'
LEGACY = ROOT / 'app/src/main/java/com/lisa/app/LisaVoiceService.java'

def ensure(ok, *detail):
    if not ok: raise ValueError(str(detail))

def norm(x):
    return ' '.join(re.sub(r'[,;:!?.]+', ' ', x.lower()).split())

def validate(data, source=None):
    ensure(data['schema_version'] == 1, 'schema_version')
    ensure(data['scope'] == 'lisa_v2_only', 'scope')
    ensure(isinstance(data['intents'], list) and data['intents'], 'intents_vuoti')
    aliases, ids = {}, set()
    for obj in data['intents']:
        ident = obj['id']
        ensure(isinstance(ident, str) and ident and ident not in ids, 'id_duplicato', ident)
        ids.add(ident)
        ensure(isinstance(obj['aliases'], list) and obj['aliases'], 'alias_vuoti', ident)
        ensure(isinstance(obj.get('params', {}), dict), 'params_invalidi', ident)
        ensure(all(isinstance(v, str) for v in obj.get('params', {}).values()), 'params_non_stringa', ident)
        for alias in obj['aliases']:
            ensure(isinstance(alias, str), 'alias_non_stringa', ident)
            key = norm(alias)
            ensure(key and key not in aliases, 'alias_duplicato', key)
            aliases[key] = obj
    if source is not None:
        legacy = re.findall(r'OVERLAY_CMDS\.put\("([^"]+)",\s*"([^"]+)"\)', source)
        ensure(legacy, 'OVERLAY_CMDS_non_trovato')
        for alias, old in legacy:
            key = norm(alias)
            ensure(key in aliases, 'alias_v1_mancante', alias)
            ensure(aliases[key].get('v1_action') == old, 'mappatura_v1_errata', alias, old)
        print(f'OVERLAY_CMDS V1 coperti: {len(legacy)} / {len(legacy)}')
    for phrase, expected in [('vai alla home', 'nav.home'),
                             ('nascondi etichette', 'overlay.etichette.nascondi'),
                             ('griglia', 'overlay.griglia.mostra'),
                             ('scorri a sinistra', 'gesture.scroll.left'),
                             ('abbassa volume', 'system.volume.down'),
                             ('spegni wifi', 'system.wifi.off')]:
        ensure(norm(phrase) in aliases and aliases[norm(phrase)]['id'] == expected, 'routing_errato', phrase)
    if source is not None:
        cap_source = (ROOT / 'app/src/main/java/com/lisa/app/LisaCapabilitySystem.java').read_text(encoding='utf-8')
        cap_source = re.sub(r'/\*.*?\*/|//[^\n]*', '', cap_source, flags=re.S)
        m = re.search(r'\bpublic\s+enum\s+Cap\s*\{([^}]*)\}', cap_source, re.S)
        ensure(m is not None, 'enum_Cap_non_trovato')
        parts = [p.strip() for p in m.group(1).split(',')]
        ensure(all(re.fullmatch(r'[A-Z][A-Z_0-9]*', p) for p in parts), 'enum_Cap_non_semplice')
        caps = set(parts)
        for obj in data['intents']:
            if 'capability' in obj:
                ensure(obj['capability'] in caps, 'capability_non_valida', obj['capability'])
    print(f'OK V2 JSON: {len(ids)} intenti, {len(aliases)} alias unici')

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--json-only', action='store_true')
    a = ap.parse_args()
    if not a.json_only and not LEGACY.is_file(): ap.error('V1 non trovato')
    validate(json.loads(FILE.read_text(encoding='utf-8')),
             None if a.json_only else LEGACY.read_text(encoding='utf-8'))
