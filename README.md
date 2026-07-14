# EarthShape

NeoForge 1.21.1 Earth-shaped overworld generation that retains Minecraft's normal seed-driven terrain and biome systems.

EarthShape does not replace `NoiseBasedChunkGenerator` or `BiomeSource`. It wraps the Overworld `NoiseRouter` with two density functions:

- `earthshape:continentalness` blends the original continentalness climate signal with the Earth map. Temperature, vegetation, erosion, depth and ridges remain seed-driven, so biome placement still varies naturally with the world seed.
- `earthshape:terrain_mask` converts the map SDF into a target surface: mapped ocean is forced below sea level and mapped land is forced above it. Bounded original density remains as local hills, valleys and cave detail, but cannot turn mapped ocean into large islands or split a mapped continent.

Nether and End routers are never modified.

## Included map

`src/main/resources/earthshape/earth_continentalness.png` is a 2:1 grayscale Earth silhouette. At first launch it is copied to `config/earthshape/earth_continentalness.png`; edit that copy, not the JAR. White represents land and black represents ocean. Map coordinates outside the image are ocean.

The service builds a signed-distance field, samples it bilinearly, applies a low-frequency domain warp, and converts it through `smootherstep`. The map's warp is deliberately independent of the world seed so the requested continental silhouette remains fixed; biome and terrain detail still use Minecraft's normal seeded noise. No calculation uses chunk-local coordinates or per-chunk random state, preventing chunk-border seams.

## Use

Install the built JAR on both client and server before creating a new world. EarthShape applies to the normal Overworld, including the default, large-biomes and amplified Overworld router variants. Existing chunks are not regenerated.

At first launch, edit `config/earthshape/earth_continentalness.png` to change the silhouette, then create a new world. `/earthshape sample` reports the continuous signed distance, land factor and continentalness signal at the command position.

`controlStrength` governs biome continentalness blending. Coastline enforcement is controlled by `oceanFloorY`, `landBaseY`, `shapeVerticalScale`, and `terrainDetailStrength`; the defaults put the map shoreline at sea level and are designed to preserve the silhouette.

`strictOceanMask` is enabled by default. It prevents positive terrain-density spikes from creating islands or continents above sea level in pixels that the map marks as ocean.

With `strictOceanMask=true` (the default), EarthShape disables coastline domain warp and caps original final-density detail at `0.05`, even if an older config has larger values. This makes the source PNG authoritative and prevents noisy macro terrain from reappearing.

## Optional real-world height and climate layers

Set `realWorldLayersEnabled=true` in `config/earthshape-common.toml` only after supplying all three grayscale PNG files below in `config/earthshape/`. Each must be exactly the same size as `earth_continentalness.png` (the bundled map is `1774x887`).

```text
earth_height.png       black = heightmapMinY, white = heightmapMaxY
earth_temperature.png  black = cold, white = hot
earth_humidity.png     black = dry, white = wet
```

Height-map influence begins inside land so the SDF coastline stays at sea level. Temperature and humidity blend with—not replace—the original `BiomeSource` climate using `realClimateStrength`; this keeps normal biome-addition mods in their own configured biome pools. If a layer is missing or the wrong dimensions, EarthShape logs one warning and falls back to seed-only height and climate.

## Build

Use a JDK 21 installation (Minecraft 1.21.1 requirement), then run:

```powershell
.\gradlew.bat build
```

The artifact is written to `build/libs/`.
