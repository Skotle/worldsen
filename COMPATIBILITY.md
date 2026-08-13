# EarthShape compatibility policy

EarthShape owns Overworld continentalness, density relief, source-river placement, and final biome selection.

## TerraFirmaCraft 4.2.7 experimental preset override

When TFC 4.2.7 is present, EarthShape overrides the `tfc:overworld` world
preset so its Overworld uses `minecraft:noise` terrain and its normal
multi-noise source solely as a transport layer. This deliberately prevents
TFC's `TFCChunkGenerator` and independent `RegionBiomeSource` continent noise
from selecting terrain or biomes.

When TFC is installed, EarthShape's final resolver returns only `tfc:*`
biomes. The EarthShape map decides ocean, river, coast, terrain class, tree
cover, and temperature; TFC's public biome tags choose the matching TFC biome.
No vanilla biome is returned, including below the surface. If a narrow TFC tag
family is absent, EarthShape falls back only to another TFC biome with the same
hydrology, never to a vanilla biome. EarthShape's density-function terrain
layers remain active through the vanilla noise-generator base class.

The `terrain.bmp` desert class is authoritative even where the temperature
layer is cold: it selects TFC dry/badlands candidates and applies a TFC yellow
sand surface rule. This prevents a cold map desert from silently falling back
to TFC plains and vanilla grass.

This is a generator replacement, not a complete TFC world-generation bridge.
TFC terrain, regional biomes, rock strata, climate/groundwater `ChunkData`,
rivers, and surface processing are therefore absent. Use it only for newly
created experimental worlds; it must not be enabled for an existing TFC world.

TFC placed features which directly consume `ChunkData` or cast the active
generator to `ChunkGeneratorExtension` cannot run with the replacement vanilla
generator: boulders, dynamic patches, erosion, fissures, hot springs, loose
rocks, noisy-multiple features, sea stacks, soil forests, springs, tide pools,
cave columns/spikes/ice caves, and forests. EarthShape skips exactly this set
when its replacement generator is active, preventing feature-generation crashes
while leaving them untouched in a normal TFC world. Remaining TFC features can
run, but receive fallback climate data instead of TFC-generated strata and
groundwater.

For reference, the TFC-only map selection uses this tag mapping:

| TFC tag family | EarthShape layer |
| --- | --- |
| `c:is_ocean`, `c:is_deep_ocean`, `c:is_shallow_ocean`, `tfc:is_isolated_island` | ocean |
| `c:is_beach`, `c:is_stony_shores`, `tfc:is_coastal_cliffs` | coast |
| `c:is_river`, `tfc:is_river` | river |
| `c:is_cave_lake` | cave |
| `c:is_badlands`, `c:is_dry`, `c:is_sandy`, `tfc:is_burren` | desert |
| `c:is_swamp`, `c:is_wet/overworld`, lake tags, `tfc:is_salt_marsh` | wetland |
| `c:is_hill`, `c:is_plateau`, `c:is_windswept`, karst/cenote/doline/rift tags | hills |
| `c:is_mountain`, continental/oceanic/non-volcanic/volcanic mountain tags | mountains |
| `c:is_plains` and unclassified neutral TFC landforms | plains, forest, or jungle according to tree cover and temperature |
| `c:is_cold/overworld`, `c:is_hot/overworld`, `c:is_snowy`, `c:is_icy`, glaciated/ice-sheet tags | temperature and snow eligibility filters |

Feature-capability tags such as `tfc:has_atolls`, `tfc:has_cinder_cones`,
`tfc:has_stratovolcanoes`, `tfc:has_tuff_cones`, `tfc:has_tuyas`, and
`tfc:has_predictable_winds` remain attached to the selected biome; they do not
describe a separate EarthShape placement layer.

External selectors are allowed to initialize and finish their calculation so dependent mods keep their normal startup path. At every Overworld `MultiNoiseBiomeSource#getNoiseBiome` return, EarthShape discards that completed holder and performs the final selection itself. Candidates injected into the climate table are excluded; additional mod biomes can be placed only through EarthShape's layer/tag registry.

## Compatible without an EarthShape code patch

- Terralith
- Oh The Biomes We've Gone (BYG)
- TerraBlender
- Lithostitched
- Geophilic – Vanilla Biome Overhauls
- Better Biome Reblend (BBRB)
- Pufferfish's Biome Dither
- Oh The Trees You'll Grow
- Wetland Whimsy
- Stony Cliffs Are Cool
- Hearths
- Climate Rivers

TerraBlender, Biolith, Lithostitched, Climate Rivers and backport selectors may calculate candidates, but their returned biome does not survive the final hook. BYG and other registered additional biomes remain available only when their common biome tags match the active EarthShape terrain/temperature condition. Source rivers and land/ocean masks therefore remain authoritative.

Pufferfish's biome dither can still vary surface-material sampling inside a biome boundary; it cannot replace the biome holder returned by the final EarthShape selector.

## Terralith 2.6.2

- EarthShape loads after Terralith/Lithostitched so `overworld/erosion` cannot replace EarthShape's mapped erosion axis.
- EarthShape owns continentalness, erosion, weirdness and the final chunk biome holder.
- Terralith's final-density graph, surface rules, vanilla-biome enhancements, carvers, configured features and structures remain loaded.
- Terralith `skylands` and `terrain_slabs` are disabled in memory because they add solid density outside the mapped continentalness axis. The user's `terralith.json` is not modified.
- Terralith surface biomes enter only through their `c:is_*` terrain tag after the active EarthShape layer admits that family.
- Terralith cave biomes use a separate vanilla-plus-Terralith-cave parameter tree below Y=48; Terralith surface biomes cannot leak into cave mouths.
- Warm Terralith rivers may replace a non-frozen mapped river in broad hot-region patches. Frozen connected rivers remain exactly `minecraft:frozen_river`.


## 픽셀당 4블록 기준의 엄격한 육지 마스크(`#FFFFFF`) 집계 결과입니다.

- 원본 마스크: 6000 × 3400 픽셀
- 청크 격자: 1500 × 850
- 전체 청크: 1,275,000개
- 육지 픽셀이 하나 이상 포함된 청크: **406,383개**
- 비율: **31.873%**
- 완전 해양 청크: 868,617개

즉, `/chunky shape earth`의 기본 육지 대상은 약 **40.6만 청크**입니다. 해협·독립 내해를 포함한 실제 `earth` shape 대상은 이보다 조금 늘어납니다.
