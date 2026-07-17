package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned world-generation settings. They are stored per world in serverconfig. */
public final class EarthShapeServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BLOCKS_PER_PIXEL;
    public static final ModConfigSpec.BooleanValue CONTINENTS_ENABLED;
    public static final ModConfigSpec.BooleanValue HEIGHTMAP_ENABLED;
    public static final ModConfigSpec.BooleanValue TERRAIN_BIOMES_ENABLED;
    public static final ModConfigSpec.BooleanValue OCEAN_TEMPERATURE_ENABLED;
    public static final ModConfigSpec.BooleanValue TUNDRA_TEMPERATURE_ENABLED;
    public static final ModConfigSpec.DoubleValue TUNDRA_TEMPERATURE_THRESHOLD;
    public static final ModConfigSpec.BooleanValue RIVER_BIOMES_ENABLED;
    public static final ModConfigSpec.IntValue COAST_HEIGHT_FADE_BLOCKS;
    public static final ModConfigSpec.IntValue RIVER_HEIGHT_FADE_BLOCKS;
    public static final ModConfigSpec.DoubleValue HEIGHTMAP_MEDIAN;
    public static final ModConfigSpec.IntValue RIVER_MAXIMUM_DEPTH_BLOCKS;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_000064;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_000096;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_0000C8;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_0000FF;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_0064FF;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_00C8FF;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_00E1FF;
    public static final ModConfigSpec.DoubleValue RIVER_WIDTH_SCALE;
    public static final ModConfigSpec.IntValue RIVER_MINIMUM_WIDTH_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("map_scale");
        BLOCKS_PER_PIXEL = builder
                .comment("Minecraft blocks represented by one rivers.bmp pixel. 20 preserves the bundled map's 283840 x 141920 block size.")
                .defineInRange("blocksPerPixel", 20, 1, 4096);
        builder.pop();
        builder.push("layers");
        CONTINENTS_ENABLED = builder.comment("Use rivers.bmp as the continental land/ocean mask.").define("continentsEnabled", true);
        HEIGHTMAP_ENABLED = builder.comment("Use heightmap.bmp for terrain height and mountain relief.").define("heightmapEnabled", true);
        TERRAIN_BIOMES_ENABLED = builder.comment("Use terrain.bmp to select land biomes.").define("terrainBiomesEnabled", true);
        OCEAN_TEMPERATURE_ENABLED = builder.comment("Use earth_temperature.png only to select ocean biome temperatures.").define("oceanTemperatureEnabled", true);
        TUNDRA_TEMPERATURE_ENABLED = builder.comment("Use earth_temperature.png on sufficiently cold land to select tundra, taiga and snowy mountain biomes.").define("tundraTemperatureEnabled", true);
        TUNDRA_TEMPERATURE_THRESHOLD = builder
                .comment("Temperature cutoff for snowy land. Higher values expand tundra/snow coverage; default -0.25 replaces the former sparse -0.45 cutoff.")
                .defineInRange("tundraTemperatureThreshold", -0.25D, -1.0D, 1.0D);
        RIVER_BIOMES_ENABLED = builder.comment("Use blue rivers.bmp lines as real river biomes and suppress all other river biomes.").define("riverBiomesEnabled", true);
        builder.pop();
        builder.push("terrain_shaping");
        COAST_HEIGHT_FADE_BLOCKS = builder.comment("Distance from an ocean coast over which heightmap relief rises from a negative shoreline grade.").defineInRange("coastHeightFadeBlocks", 180, 20, 1024);
        RIVER_HEIGHT_FADE_BLOCKS = builder.comment("Distance from a source river over which heightmap relief rises from a negative riverbank grade.").defineInRange("riverHeightFadeBlocks", 100, 20, 1024);
        HEIGHTMAP_MEDIAN = builder.comment("Normalized heightmap midpoint. Values below receive negative relief; values above receive progressively stronger positive relief.").defineInRange("heightmapMedian", 0.50D, 0.05D, 0.95D);
        RIVER_MAXIMUM_DEPTH_BLOCKS = builder.comment("Maximum source-river bed drop in blocks before vanilla aquifer and surface generation.").defineInRange("riverMaximumDepthBlocks", 8, 2, 32);
        builder.pop();
        builder.push("river_widths");
        RIVER_WIDTH_000064 = builder.comment("Width in blocks for rivers.bmp colour #000064.").defineInRange("color_000064", 27, 1, 256);
        RIVER_WIDTH_000096 = builder.comment("Width in blocks for rivers.bmp colour #000096.").defineInRange("color_000096", 22, 1, 256);
        RIVER_WIDTH_0000C8 = builder.comment("Width in blocks for rivers.bmp colour #0000C8.").defineInRange("color_0000C8", 17, 1, 256);
        RIVER_WIDTH_0000FF = builder.comment("Width in blocks for rivers.bmp colour #0000FF.").defineInRange("color_0000FF", 13, 1, 256);
        RIVER_WIDTH_0064FF = builder.comment("Width in blocks for rivers.bmp colour #0064FF.").defineInRange("color_0064FF", 10, 1, 256);
        RIVER_WIDTH_00C8FF = builder.comment("Width in blocks for rivers.bmp colour #00C8FF.").defineInRange("color_00C8FF", 7, 1, 256);
        RIVER_WIDTH_00E1FF = builder.comment("Width in blocks for rivers.bmp colour #00E1FF.").defineInRange("color_00E1FF", 5, 1, 256);
        RIVER_WIDTH_SCALE = builder.comment("Global multiplier for all source river widths. 0.5 compensates for source lines that already occupy several pixels.").defineInRange("widthScale", 0.50D, 0.05D, 4.0D);
        RIVER_MINIMUM_WIDTH_BLOCKS = builder.comment("Minimum generated width for a source river. 8 blocks keeps small rivers continuous across 4-block biome samples.").defineInRange("minimumWidthBlocks", 8, 1, 64);
        builder.pop();
        SPEC = builder.build();
    }

    private EarthShapeServerConfig() {}
}
