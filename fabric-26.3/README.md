# EarthShape — Fabric 26.3

Fabric port of the current NeoForge 1.21.1 / EarthShape 1.1.4-A source.

## Installation

- Minecraft Java **26.3**
- Java **25**
- Fabric Loader **0.19.5 or newer**
- Fabric API **0.160.1+26.3** (or a newer API built for Minecraft 26.3)

Install `build/libs/earthshape-fabric-26.3-1.1.4-A-fabric-26.3.jar` in the
client/server `mods` folder. The `-sources.jar` is for development, not installation.
Use a new world to see generated terrain; existing chunks are not regenerated.

Configuration is written to `config/earthshape.properties`. Its 55 options use
the current NeoForge defaults and ranges, including **10 blocks per map pixel**.
Restart after editing. This Fabric configuration is installation-wide; it does
not automatically import NeoForge's per-world TOML configuration.
Each setting includes English and Korean comments, its default, and accepted
range. The file uses UTF-8. Starting the updated mod adds these comments to an
existing configuration while keeping its valid values and unknown entries.

## Ported behavior

- Source-map continents, rivers, river banks, coastal shelves, desert water
  suppression, and ocean temperature/iceberg selection.
- Mountain and hill relief, climate/vegetation layers, biome region cleanup,
  curved boundaries, rare biome area checks, and map-center selection by seed.
- Additional-biome classification, deterministic surface structure rate,
  server hang diagnostics, optional Chunky `/chunky shape earth`, and optional
  C2ME OpenCL fallback.

Minecraft 26.3 replaces the old density-function visitor with compiled scalar
and volume samplers. `MappedDensityFunction` bridges that API while preserving
the NeoForge calculation bodies. The port also updates material rules, feature
placement, final biome resolution, aquifer hooks, and density JSON codecs.

All eight original map resources are packaged directly from `../map`.
The NeoForge and existing Fabric 26.2 builds remain separate.

## Build and verification

With `JAVA_HOME` pointing to JDK 25:

```powershell
.\gradlew.bat build
python verify-parity.py
```

The build runs `verifyDensityAdapter`, checking 60 nonuniform volume coordinates
against independent point samples, including negative coordinates and unequal
axis strides. The parity script compares the shared algorithms, all configuration
defaults/ranges, the three density calculation bodies, and packaged map bytes.

For the development server, accept Minecraft's EULA in `run/eula.txt`, configure
`run/server.properties` for a local test world named `earthshape-smoke`, and run:

```powershell
python smoke-test.py
```

This starts the server, forces test chunks in all three dimensions, flushes the
save, and shuts down normally. `python smoke-test.py --verify-log` rechecks the
saved result. The test uses only this project's `run` directory.

## Verified on 2026-09-29

- Build successful with Gradle 9.6.0 / Loom 1.17.21 / Java 25.
- All 60 density adapter samples and all parity checks passed.
- Fabric 0.19.5 + Fabric API 0.160.1+26.3 dedicated server started successfully.
- Seed `263114`: new-world spawn generation, forced Overworld/Nether/End chunks,
  region files, flush-save, and normal shutdown confirmed.
- The first map initialization took about 69 seconds on the test machine.

External mods (Chunky, C2ME, TerraBlender, BOP, TFC, Terralith) were not installed
in this smoke test. Their compatibility hooks are ported but require compatible
Fabric 26.3 releases and separate integration testing. A NeoForge-only mod cannot
be loaded by Fabric. Client rendering has not been tested.

Matching map algorithms and settings does not guarantee block-for-block identical
worlds across Minecraft 1.21.1 and 26.3: vanilla generation and the density
pipeline changed, including 26.3's float-based sampling.
