"""Check shared algorithms, config defaults/bounds, and packaged maps against NeoForge."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

port = Path(__file__).resolve().parent
project = port.parent
root = project / 'src/main/java/io/github/earthshape'
dest = port / 'src/main/java/io/github/earthshape'
read = lambda path: path.read_text(encoding='utf-8')
for rel in ['map/ClimateLayers.java', 'map/RiversMask.java']:
    assert read(root / rel) == read(dest / rel), rel
print('PASS: complete climate and river algorithms equal NeoForge source')

source = read(root / 'EarthShapeServerConfig.java')
config = read(dest / 'EarthShapeServerConfig.java')
count = 0
pattern = r'([A-Z][A-Z_0-9]*)\s*=\s*builder\.comment\(.*?\)\s*\.define(InRange)?\("([^"]+)",\s*([^;]+)\);'
for match in re.finditer(pattern, source, re.S):
    name, key = match[1], match[3]
    values = [value.strip() for value in match[4].split(',')]
    assert re.search(r'\b' + name + r' = [bid]\(' + re.escape(values[0]) + r'\);', config), name
    if match[2]:
        assert re.search(re.escape(name) + r'\.set\([^;]+, ' + re.escape(', '.join(values[1:])) + r'\)\);', config), name
    count += 1
assert count == len(re.findall(r'public static final (?:IntValue|DoubleValue|BooleanValue)', source))
print(f'PASS: {count} NeoForge configuration defaults and ranges')

def compute_body(text):
    start = text.index('{', text.index('double compute('))
    depth, end = 1, start + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return re.sub(r'\s+', '', text[start:end].replace('this.argument.compute(context)', 'original'))

for name in ['CoastalContinentalnessDensity.java', 'TerrainErosionDensity.java', 'RiverWeirdnessDensity.java']:
    assert compute_body(read(root / 'worldgen' / name)) == compute_body(read(dest / 'worldgen' / name)), name
print('PASS: all three density calculations retain NeoForge compute bodies')

jar = next(path for path in (port / 'build/libs').glob('*.jar') if '-sources' not in path.name)
with zipfile.ZipFile(jar) as archive:
    metadata = json.loads(archive.read('fabric.mod.json'))
    assert metadata['depends']['minecraft'] == '~26.3'
    maps = ['terrain.bmp', 'rivers.bmp', 'trees.bmp', 'world_normal.bmp', 'earth_temperature.png',
            'worldmap_full.png', 'world_rivers_full.png', 'worldmap_river.png']
    for name in maps:
        assert archive.read('earthshape/hoi4/' + name) == (project / 'map' / name).read_bytes(), name
    assert 'META-INF/neoforge.mods.toml' not in archive.namelist()
print('PASS: Fabric 26.3 metadata and all 8 packaged source maps')
print('SHA256', hashlib.sha256(jar.read_bytes()).hexdigest())
