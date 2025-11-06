import json

from pathlib import Path

with open('build/0-aslgen/rvopcodes.json') as f:
    rvopcodes = json.load(f)

inst = [x['name'] for x in rvopcodes['instructions'] if x['bit_width'] == '32']
cinst = [x['name'] for x in rvopcodes['instructions'] if x['bit_width'] == '16']

unimpl_inst = [x.replace('.', '_') for x in inst]
for x in Path('template/extensions').glob('**/*.asl'):
    if x.stem in unimpl_inst:
        unimpl_inst.remove(x.stem)
for x in Path('template/extensions').glob('**/*.toml'):
    if x.stem in unimpl_inst:
        unimpl_inst.remove(x.stem)

print(unimpl_inst)
# print(cinst)
