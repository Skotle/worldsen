package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned settings for the independent map layers. */
public final class EarthShapeServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BLOCKS_PER_PIXEL;
    public static final ModConfigSpec.BooleanValue CONTINENTS_ENABLED;
    public static final ModConfigSpec.BooleanValue HEIGHTMAP_ENABLED;
    public static final ModConfigSpec.BooleanValue TERRAIN_ENABLED;
    public static final ModConfigSpec.BooleanValue TEMPERATURE_ENABLED;
    public static final ModConfigSpec.BooleanValue RIVERS_ENABLED;
    public static final ModConfigSpec.IntValue COAST_RELIEF_FADE_BLOCKS;
    public static final ModConfigSpec.DoubleValue RIVER_CHANNEL_CONTINENTALNESS;
    public static final ModConfigSpec.IntValue RIVER_MINIMUM_WIDTH_BLOCKS;
    public static final ModConfigSpec.DoubleValue RIVER_WIDTH_SCALE;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_000064;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_000096;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_0000C8;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_0000FF;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_0064FF;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_00C8FF;
    public static final ModConfigSpec.IntValue RIVER_WIDTH_00E1FF;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("map_scale");
        BLOCKS_PER_PIXEL = builder.comment("Minecraft blocks represented by one 6000x3400 world map pixel.")
                .defineInRange("blocksPerPixel", 4, 1, 4096);
        builder.pop();

        builder.push("layers");
        CONTINENTS_ENABLED = builder.comment("Use worldmap_river.png white/gray pixels as the land and ocean ownership mask.")
                .define("continentsEnabled", true);
        HEIGHTMAP_ENABLED = builder.comment("Use heightmap.bmp only as terrain relief.").define("heightmapEnabled", true);
        TERRAIN_ENABLED = builder.comment("Use terrain.bmp only for surface terrain classes.").define("terrainEnabled", true);
        TEMPERATURE_ENABLED = builder.comment("Use earth_temperature.png for all land and ocean climate bands.").define("temperatureEnabled", true);
        RIVERS_ENABLED = builder.comment("Generate river biomes only from blue lines in worldmap_river.png.").define("riversEnabled", true);
        builder.pop();

        builder.push("terrain_shape");
        COAST_RELIEF_FADE_BLOCKS = builder.comment("Distance over which imported heightmap relief fades in from the exact coastline.")
                .defineInRange("coastReliefFadeBlocks", 96, 16, 512);
        RIVER_CHANNEL_CONTINENTALNESS = builder.comment("Shallow continentalness used only inside a source-river width. Negative coast values create ravines.")
                .defineInRange("riverChannelContinentalness", -0.03D, -0.12D, 0.12D);
        builder.pop();

        builder.push("river_widths");
        RIVER_MINIMUM_WIDTH_BLOCKS = builder.comment("Smallest source river width. This does not expand a river beyond its blue layer line.")
                .defineInRange("minimumWidthBlocks", 4, 1, 64);
        RIVER_WIDTH_SCALE = builder.comment("Multiplier applied to every configured source-river width.")
                .defineInRange("widthScale", 1.0D, 0.1D, 3.0D);
        RIVER_WIDTH_000064 = width(builder, "color_000064", 20);
        RIVER_WIDTH_000096 = width(builder, "color_000096", 16);
        RIVER_WIDTH_0000C8 = width(builder, "color_0000C8", 12);
        RIVER_WIDTH_0000FF = width(builder, "color_0000FF", 9);
        RIVER_WIDTH_0064FF = width(builder, "color_0064FF", 7);
        RIVER_WIDTH_00C8FF = width(builder, "color_00C8FF", 5);
        RIVER_WIDTH_00E1FF = width(builder, "color_00E1FF", 4);
        builder.pop();
        SPEC = builder.build();
    }

    private static ModConfigSpec.IntValue width(ModConfigSpec.Builder builder, String key, int defaultValue) {
        return builder.comment("Width in blocks for source colour #" + key.substring("color_".length()).toUpperCase() + ".")
                .defineInRange(key, defaultValue, 1, 128);
    }

    private EarthShapeServerConfig() {}
}
