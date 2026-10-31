"""Merge module fragments into messages.yml / plugin.yml and validate message keys."""
import glob, os, re, sys
import yaml

ROOT = '/tmp/hoplite/workspace/Core'
RES = f'{ROOT}/src/main/resources'
FRAG = f'{ROOT}/fragments'
order = ['teleport', 'warp', 'chat', 'inventory', 'player', 'server']

def flat(d, p=''):
    out = set()
    for k, v in d.items():
        key = p + str(k)
        out |= flat(v, key + '.') if isinstance(v, dict) else {key}
    return out

messages = open(f'{RES}/messages.yml', encoding='utf-8').read().rstrip('\n') + '\n'
perms = open(f'{RES}/plugin.yml', encoding='utf-8').read().rstrip('\n') + '\n'
base_sections = set(yaml.safe_load(messages).keys())
for mod in order:
    m = f'{FRAG}/{mod}.messages.yml'
    p = f'{FRAG}/{mod}.permissions.yml'
    if os.path.exists(m):
        text = open(m, encoding='utf-8').read().strip('\n')
        sections = set((yaml.safe_load(text) or {}).keys())
        clash = sections & base_sections
        if clash:
            sys.exit(f'{mod}: duplicate message sections {clash}')
        base_sections |= sections
        messages += f'\n# ---------------------------------------------------------------- {mod}\n' + text + '\n'
    if os.path.exists(p):
        text = open(p, encoding='utf-8').read().strip('\n')
        perms += text + '\n'

data = yaml.safe_load(messages)
plugin = yaml.safe_load(perms)
defined = flat(data)
used = {}
declared = set(plugin['permissions'].keys())
undeclared = {}
for f in glob.glob(f'{ROOT}/src/main/java/**/*.java', recursive=True):
    if f.endswith('config/Settings.java'):
        continue
    src = open(f, encoding='utf-8').read()
    for key in re.findall(r'"([a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+)"', src):
        if key in declared or key in defined:
            used.setdefault(key, f)
        elif key.startswith('core.') and key.split('.')[1] not in data.get('core', {}):
            undeclared.setdefault(key, os.path.basename(f))
        elif key.split('.')[0] in data:
            used.setdefault(key, f)
missing = sorted(k for k in used if k not in defined and k not in declared)
print('permission-like literals not declared:', sorted(undeclared.items()))
print('sections:', len(data), 'keys:', len(defined), 'permissions:', len(plugin['permissions']))
print('missing keys:', missing)
if '--write' in sys.argv and not missing:
    open(f'{RES}/messages.yml', 'w', encoding='utf-8').write(messages)
    open(f'{RES}/plugin.yml', 'w', encoding='utf-8').write(perms)
    print('written')
