# EarthShape compatibility policy

EarthShape owns Overworld continentalness, density relief, source-river placement, and final biome selection.

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
