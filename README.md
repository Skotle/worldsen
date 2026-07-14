# EarthShape

NeoForge 1.21.1 Earth-shaped overworld generation that retains Minecraft's normal seed-driven terrain and biome systems.

EarthShape does not replace `NoiseBasedChunkGenerator` or `BiomeSource`. It wraps the Overworld `NoiseRouter` with two density functions:

- `earthshape:continentalness` blends the original continentalness climate signal with the Earth map. Temperature, vegetation, erosion, depth and ridges remain seed-driven, so biome placement still varies naturally with the world seed.
- `earthshape:terrain_mask` adds a smooth, Y-limited land/ocean density bias to the original final density. Vanilla or mod-provided caves, aquifers, surface rules, features, structures and ore veins are still evaluated by the original generator.

Nether and End routers are never modified.

## Included map

`src/main/resources/earthshape/earth_continentalness.png` is a 2:1 grayscale Earth silhouette. At first launch it is copied to `config/earthshape/earth_continentalness.png`; edit that copy, not the JAR. White represents land and black represents ocean. Map coordinates outside the image are ocean.

The service builds a signed-distance field, samples it bilinearly, applies a low-frequency domain warp, and converts it through `smootherstep`. The map's warp is deliberately independent of the world seed so the requested continental silhouette remains fixed; biome and terrain detail still use Minecraft's normal seeded noise. No calculation uses chunk-local coordinates or per-chunk random state, preventing chunk-border seams.

## Use

Install the built JAR on both client and server before creating a new world. EarthShape applies to the normal Overworld, including the default, large-biomes and amplified Overworld router variants. Existing chunks are not regenerated.

At first launch, edit `config/earthshape/earth_continentalness.png` to change the silhouette, then create a new world. `/earthshape sample` reports the continuous signed distance, land factor and continentalness signal at the command position.

`controlStrength` governs both climate continentalness blending and terrain masking. Start with the default `0.80`; lower it if a terrain overhaul needs more influence, or raise it for a stricter silhouette.

## Build

Use a JDK 21 installation (Minecraft 1.21.1 requirement), then run:

```powershell
.\gradlew.bat build
```

The artifact is written to `build/libs/`.
