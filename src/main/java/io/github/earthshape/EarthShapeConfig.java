package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tunables intentionally limited to continental placement; biome and decoration settings stay vanilla/pack-owned. */
public final class EarthShapeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue BLOCKS_PER_PIXEL;
    public static final ModConfigSpec.IntValue COAST_WIDTH_BLOCKS;
    public static final ModConfigSpec.IntValue TERRAIN_COAST_WIDTH_BLOCKS;
    public static final ModConfigSpec.IntValue OCEAN_INSET_BLOCKS;
    public static final ModConfigSpec.DoubleValue CONTROL_STRENGTH;
    public static final ModConfigSpec.IntValue OCEAN_FLOOR_Y;
    public static final ModConfigSpec.IntValue LAND_BASE_Y;
    public static final ModConfigSpec.DoubleValue SHAPE_VERTICAL_SCALE;
    public static final ModConfigSpec.DoubleValue TERRAIN_DETAIL_STRENGTH;
    public static final ModConfigSpec.BooleanValue APPLY_TO_COMPATIBLE_OVERWORLD_SETTINGS;
    public static final ModConfigSpec.BooleanValue TERRALITH_COMPATIBILITY;
    public static final ModConfigSpec.BooleanValue STRICT_OCEAN_MASK;
    public static final ModConfigSpec.BooleanValue REAL_WORLD_LAYERS_ENABLED;
    public static final ModConfigSpec.IntValue HEIGHTMAP_MIN_Y;
    public static final ModConfigSpec.IntValue HEIGHTMAP_MAX_Y;
    public static final ModConfigSpec.DoubleValue REAL_CLIMATE_STRENGTH;
    public static final ModConfigSpec.IntValue WARP_STRENGTH_BLOCKS;
    public static final ModConfigSpec.IntValue WARP_SCALE_BLOCKS;
    public static final ModConfigSpec.IntValue MINIMUM_LAND_COMPONENT_PIXELS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("earthshape");
        ENABLED = b.comment("Enable EarthShape's Overworld continentalness and terrain-density wrappers.")
                .define("enabled", true);
        BLOCKS_PER_PIXEL = b.comment("Horizontal scale of the bundled HOI4 terrain.bmp map.")
                .defineInRange("blocksPerPixel", 20, 1, 4096);
        COAST_WIDTH_BLOCKS = b.comment("Width of the smooth coast transition, in blocks.")
                .defineInRange("coastWidthBlocks", 96, 16, 4096);
        TERRAIN_COAST_WIDTH_BLOCKS = b.comment("Physical terrain transition at the shoreline. Keep this narrow so map straits remain open; coastWidthBlocks still controls biome blending.")
                .defineInRange("terrainCoastWidthBlocks", 8, 1, 128);
        OCEAN_INSET_BLOCKS = b.comment("Expand mapped ocean by this many blocks before shaping terrain. A small inset keeps narrow straits open.")
                .defineInRange("oceanInsetBlocks", 4, 0, 64);
        CONTROL_STRENGTH = b.comment("Blend weight for EarthShape continentalness and terrain masking.")
                .defineInRange("controlStrength", 0.80D, 0.0D, 1.0D);
        OCEAN_FLOOR_Y = b.comment("Target seafloor height. Together with landBaseY it places the map shoreline at sea level.")
                .defineInRange("oceanFloorY", 36, -64, 62);
        LAND_BASE_Y = b.comment("Default height for flat mapped land; keep near sea level for broad plains.")
                .defineInRange("landBaseY", 65, 64, 200);
        SHAPE_VERTICAL_SCALE = b.comment("Smaller values enforce the map coastline more strongly.")
                .defineInRange("shapeVerticalScale", 14.0D, 4.0D, 64.0D);
        TERRAIN_DETAIL_STRENGTH = b.comment("How much of the original final density remains as local terrain and cave detail.")
                .defineInRange("terrainDetailStrength", 0.08D, 0.0D, 0.50D);
        APPLY_TO_COMPATIBLE_OVERWORLD_SETTINGS = b.comment("Also apply to non-vanilla NoiseBasedChunkGenerator settings that look like a standard Overworld. Disable for a pack's bespoke dimension settings.")
                .define("applyToCompatibleOverworldSettings", true);
        TERRALITH_COMPATIBILITY = b.comment("When Terralith is installed, recognize its overworld data-pack generator and preserve its multi-noise biome source while applying EarthShape's map, height and climate layers.")
                .define("terralithCompatibility", true);
        STRICT_OCEAN_MASK = b.comment("Forbid terrain above sea level where the Earth map is ocean. Keep enabled to preserve coastlines.")
                .define("strictOceanMask", true);
        REAL_WORLD_LAYERS_ENABLED = b.comment("Enable the bundled HOI4 heightmap. Optional earth_temperature.png and earth_humidity.png in config/earthshape must both match the terrain-map dimensions.")
                .define("realWorldLayersEnabled", true);
        HEIGHTMAP_MIN_Y = b.comment("Y represented by black in earth_height.png.")
                .defineInRange("heightmapMinY", 64, -64, 319);
        HEIGHTMAP_MAX_Y = b.comment("Y represented by white in the HOI4 heightmap.")
                .defineInRange("heightmapMaxY", 260, -64, 319);
        REAL_CLIMATE_STRENGTH = b.comment("Blend amount for temperature and humidity map layers; original biome-source climate remains the remainder.")
                .defineInRange("realClimateStrength", 0.75D, 0.0D, 1.0D);
        WARP_STRENGTH_BLOCKS = b.comment("Maximum deterministic coastline domain warp. Set to 0 for the closest PNG coastline match.")
                .defineInRange("warpStrengthBlocks", 16, 0, 512);
        WARP_SCALE_BLOCKS = b.comment("Low-frequency domain warp wavelength.")
                .defineInRange("warpScaleBlocks", 1536, 64, 16384);
        MINIMUM_LAND_COMPONENT_PIXELS = b.comment("Discard disconnected land fragments smaller than this many source-map pixels. This removes raster speckles, not normal coasts.")
                .defineInRange("minimumLandComponentPixels", 128, 0, 100000);
        b.pop();
        SPEC = b.build();
    }

    private EarthShapeConfig() {}
}
