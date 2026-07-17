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
