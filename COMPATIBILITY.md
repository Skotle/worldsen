# EarthShape compatibility policy

EarthShape owns Overworld continentalness, density relief, source-river placement, and final biome selection.  Mods that own the same router or deliberately dither biome boundaries cannot safely be merged without version-specific APIs and joint world-generation tests.

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

EarthShape retains the original non-vanilla biome selected by these mods rather than replacing it with a vanilla `terrain.bmp` biome. This also takes precedence over a source-river line: custom-biome features, surface rules and terrain intent are not erased merely because the map has a river at that position. EarthShape still applies its continent mask and heightmap relief. Test each pack in a new world because feature density is pack-dependent.

## Incompatible: EarthShape world generation is automatically disabled

- Climate Rivers

Climate Rivers owns the same source-river generation stage. EarthShape detects it at startup and leaves vanilla/other-mod generation active instead of combining two river generators.


## 픽셀당 4블록 기준의 엄격한 육지 마스크(`#FFFFFF`) 집계 결과입니다.

- 원본 마스크: 6000 × 3400 픽셀
- 청크 격자: 1500 × 850
- 전체 청크: 1,275,000개
- 육지 픽셀이 하나 이상 포함된 청크: **406,383개**
- 비율: **31.873%**
- 완전 해양 청크: 868,617개

즉, `/chunky shape earth`의 기본 육지 대상은 약 **40.6만 청크**입니다. 해협·독립 내해를 포함한 실제 `earth` shape 대상은 이보다 조금 늘어납니다.