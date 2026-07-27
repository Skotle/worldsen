/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.neoforged.neoforge.common.ModConfigSpec
 *  net.neoforged.neoforge.common.ModConfigSpec$BooleanValue
 *  net.neoforged.neoforge.common.ModConfigSpec$Builder
 *  net.neoforged.neoforge.common.ModConfigSpec$DoubleValue
 *  net.neoforged.neoforge.common.ModConfigSpec$IntValue
 */
package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EarthShapeServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BLOCKS_PER_PIXEL;
    public static final ModConfigSpec.BooleanValue CONTINENTS_ENABLED;
    public static final ModConfigSpec.BooleanValue TERRAIN_NOISE_ENABLED;
    public static final ModConfigSpec.BooleanValue TERRAIN_BIOMES_ENABLED;
    public static final ModConfigSpec.IntValue TERRAIN_BIOME_MINIMUM_REGION_PIXELS;
    public static final ModConfigSpec.IntValue TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS;
    public static final ModConfigSpec.BooleanValue OCEAN_TEMPERATURE_ENABLED;
    public static final ModConfigSpec.BooleanValue TUNDRA_TEMPERATURE_ENABLED;
    public static final ModConfigSpec.DoubleValue TUNDRA_TEMPERATURE_THRESHOLD;
    public static final ModConfigSpec.IntValue SNOW_ALTITUDE_BLOCKS;
    public static final ModConfigSpec.DoubleValue SNOW_TEMPERATURE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TEMPERATURE_VERTICAL_SCALE;
    public static final ModConfigSpec.BooleanValue RIVER_BIOMES_ENABLED;
    public static final ModConfigSpec.BooleanValue BIOME_BOUNDARY_WARP_ENABLED;
    public static final ModConfigSpec.IntValue BIOME_BOUNDARY_WARP_BLOCKS;
    public static final ModConfigSpec.IntValue COAST_HEIGHT_FADE_BLOCKS;
    public static final ModConfigSpec.IntValue RIVER_HEIGHT_FADE_BLOCKS;
    public static final ModConfigSpec.IntValue MOUNTAIN_NOISE_MAXIMUM_HEIGHT_BLOCKS;
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
    public static final ModConfigSpec.IntValue RIVER_GAP_BRIDGE_PIXELS;
    public static final ModConfigSpec.DoubleValue RIVER_CHANNEL_CONTINENTALNESS;
    public static final ModConfigSpec.IntValue RIVER_BANK_FADE_BLOCKS;
    public static final ModConfigSpec.IntValue RIVER_MINIMUM_INLAND_BLOCKS;
    public static final ModConfigSpec.IntValue RIVER_CHANNEL_EDGE_FADE_BLOCKS;
    public static final ModConfigSpec.BooleanValue DESERT_WATER_REDUCTION_ENABLED;
    public static final ModConfigSpec.DoubleValue DESERT_RIVER_WIDTH_SCALE;
    public static final ModConfigSpec.IntValue DESERT_MINIMUM_RIVER_WIDTH_BLOCKS;
    public static final ModConfigSpec.IntValue DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS;

    private EarthShapeServerConfig() {
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("map_scale");
        BLOCKS_PER_PIXEL = builder.comment("rivers.bmp \ud53d\uc140 \ud558\ub098\uc5d0 \ud574\ub2f9\ud558\ub294 \ub9c8\uc778\ud06c\ub798\ud504\ud2b8 \ube14\ub85d \uc218. 20\uc73c\ub85c \uc124\uc815\ud558\uba74 \uae30\ubcf8 \uc81c\uacf5 \ub9f5\uc758 283840 x 141920 \ube14\ub85d \ud06c\uae30\uac00 \uc720\uc9c0\ub429\ub2c8\ub2e4.").defineInRange("blocksPerPixel", 20, 1, 4096);
        builder.pop();
        builder.push("layers");
        CONTINENTS_ENABLED = builder.comment("rivers.bmp\ub97c \ub300\ub959(\uc721\uc9c0/\ubc14\ub2e4) \ub9c8\uc2a4\ud06c\ub85c \uc0ac\uc6a9\ud569\ub2c8\ub2e4.").define("continentsEnabled", true);
        TERRAIN_NOISE_ENABLED = builder.comment("\ubc14\ub2d0\ub77c \ubc00\ub3c4 \ub178\uc774\uc988\uc5d0 \uc0b0\ub9e5\u00b7\uad6c\ub989\uc6a9 \uc5f0\uc18d \ub178\uc774\uc988\ub97c \ub354\ud569\ub2c8\ub2e4.").define("terrainNoiseEnabled", true);
        TERRAIN_BIOMES_ENABLED = builder.comment("\uae30\ud6c4\uc5d0 \ub9de\ub294 \ubc14\uc774\uc634\uc744 \uc120\ud0dd\ud558\uae30 \uc804, terrain.bmp\ub97c \uc138\ubd80 \uc9c0\uc5ed \uc9c0\ud615 \ubd84\ub958\uc5d0 \uc0ac\uc6a9\ud569\ub2c8\ub2e4.").define("terrainBiomesEnabled", true);
        TERRAIN_BIOME_MINIMUM_REGION_PIXELS = builder.comment("Minimum connected terrain.bmp region area in source pixels. Smaller fragments merge into surrounding terrain.").defineInRange("terrainBiomeMinimumRegionPixels", 4, 1, 4096);
        TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS = builder.comment("Minimum source-pixel area for a terrain island surrounded by one terrain family.").defineInRange("terrainBiomeIsolatedMinimumRegionPixels", 12, 1, 4096);
        OCEAN_TEMPERATURE_ENABLED = builder.comment("\ud574\uc591 \ubc14\uc774\uc634\uc758 \uc628\ub3c4\ub97c \uc120\ud0dd\ud560 \ub54c\ub9cc earth_temperature.png\ub97c \uc0ac\uc6a9\ud569\ub2c8\ub2e4.").define("oceanTemperatureEnabled", true);
        TUNDRA_TEMPERATURE_ENABLED = builder.comment("\ucda9\ubd84\ud788 \ucd94\uc6b4 \uc721\uc9c0\uc5d0\uc11c \ud230\ub4dc\ub77c, \ud0c0\uc774\uac00, \uc124\uc0b0 \ubc14\uc774\uc634\uc744 \uc120\ud0dd\ud560 \ub54c earth_temperature.png\ub97c \uc0ac\uc6a9\ud569\ub2c8\ub2e4.").define("tundraTemperatureEnabled", true);
        TUNDRA_TEMPERATURE_THRESHOLD = builder.comment("\ub208 \ub36e\uc778 \ub545\uc73c\ub85c \ubd84\ub958\ub418\ub294 \uc628\ub3c4 \uae30\uc900\uac12. \uac12\uc774 \ub192\uc744\uc218\ub85d \ud230\ub4dc\ub77c/\ub208 \ubc94\uc704\uac00 \ub113\uc5b4\uc9d1\ub2c8\ub2e4. \uae30\ubcf8\uac12 -0.25\ub294 \uae30\uc874\uc758 -0.45(\uc801\uc740 \ubc94\uc704)\ub97c \ub300\uccb4\ud569\ub2c8\ub2e4.").defineInRange("tundraTemperatureThreshold", -0.25, -1.0, 1.0);
        SNOW_ALTITUDE_BLOCKS = builder.comment("\uc628\ub3c4 \ub9f5\uacfc \ubb34\uad00\ud558\uac8c \uace0\uc9c0\ub300 \ubc14\uc774\uc634\uc5d0 \ub208\uc774 \ub0b4\ub9b4 \uc218 \uc788\ub294 \uc9c0\ud45c\uba74 Y \ub808\ubca8.").defineInRange("snowAltitudeBlocks", 160, 64, 320);
        SNOW_TEMPERATURE_THRESHOLD = builder.comment("\uace0\uc9c0\ub300 \uc124\uc120 \uc544\ub798\uc5d0\uc11c \ub208\uc774 \ub0b4\ub9ac\ub294 \uc628\ub3c4 \ub9f5 \uae30\uc900\uac12. -0.625\ub294 \ubcf4\ub77c\uc0c9\uacfc \ud30c\ub780\uc0c9\uc758 \uc601\ud558 \uad6c\uac04\uc5d0 \ud574\ub2f9\ud569\ub2c8\ub2e4.").defineInRange("snowTemperatureThreshold", -0.625, -1.0, 0.0);
        TEMPERATURE_VERTICAL_SCALE = builder.comment("\uc801\ub3c4 \ubd80\uadfc\uc5d0\uc11c earth_temperature.png\ub97c \uc218\uc9c1\uc73c\ub85c \ud655\uc7a5\ud558\ub294 \ube44\uc728. 1.12\ub294 \ud655\uc7a5\ub41c 6000x3400 \uc6d4\ub4dc \ub9f5\uc744 \uc0ac\uc6a9\ud560 \ub54c \ub0a8\uc544\ud504\ub9ac\uce74\uac00 \uc758\ub3c4\ub41c \uc628\ub09c\ub300\uc5d0 \uc720\uc9c0\ub418\ub3c4\ub85d \ud569\ub2c8\ub2e4.").defineInRange("temperatureVerticalScale", 1.12, 0.75, 1.5);
        RIVER_BIOMES_ENABLED = builder.comment("rivers.bmp\uc758 \ud30c\ub780 \uc120\uc744 \uc2e4\uc81c \uac15 \ubc14\uc774\uc634\uc73c\ub85c \uc0ac\uc6a9\ud558\uace0, \uadf8 \uc678 \ubaa8\ub4e0 \uac15 \ubc14\uc774\uc634\uc740 \uc5b5\uc81c\ud569\ub2c8\ub2e4.").define("riverBiomesEnabled", true);
        BIOME_BOUNDARY_WARP_ENABLED = builder.comment("\uae34 \ube44\ud2b8\ub9f5 \ubc14\uc774\uc634 \uacbd\uacc4\uac00 \uc9c1\uc120\uc73c\ub85c \uc0dd\uc131\ub418\uc9c0 \uc54a\ub3c4\ub85d \uc9c0\ud615, \ub098\ubb34, \uc628\ub3c4 \ub808\uc774\uc5b4 \uc0d8\ud50c\ub9c1\uc744 \ubd80\ub4dc\ub7fd\uac8c \ud718\uc5b4\uc90d\ub2c8\ub2e4. \uac15\uacfc \ud574\uc548\uc120\uc740 \uc815\ud655\ud558\uac8c \uc720\uc9c0\ub429\ub2c8\ub2e4.").define("biomeBoundaryWarpEnabled", true);
        BIOME_BOUNDARY_WARP_BLOCKS = builder.comment("\uc628\ub3c4 \ubcc0\ud615 \ubc14\uc774\uc634 \uacbd\uacc4\uc758 \ucd5c\ub300 \uc88c\uc6b0 \uc774\ub3d9 \uac70\ub9ac. terrain.bmp\uc640 trees.bmp \uc601\uc5ed\uc744 \ub118\uc9c0 \uc54a\ub3c4\ub85d \uc6d0\ubcf8 \ub9f5 \ud53d\uc140 1\uac1c \ubbf8\ub9cc\uc73c\ub85c \uc81c\ud55c\ub429\ub2c8\ub2e4.").defineInRange("biomeBoundaryWarpBlocks", 12, 0, 64);
        builder.pop();
        builder.push("terrain_shaping");
        COAST_HEIGHT_FADE_BLOCKS = builder.comment("\ud574\uc548\uc120 \ub300\ub959\ubd95 \uacbd\uc0ac\uac00 \uc644\ub9cc\ud558\uac8c \uc774\uc5b4\uc9c0\ub294 \ud574\uc548\uc73c\ub85c\ubd80\ud130\uc758 \uac70\ub9ac.").defineInRange("coastHeightFadeBlocks", 320, 20, 1024);
        RIVER_HEIGHT_FADE_BLOCKS = builder.comment("\uac15\ubcc0 \uacbd\uc0ac\uac00 \uc644\ub9cc\ud558\uac8c \uc774\uc5b4\uc9c0\ub294 \uc6d0\ubcf8 \uac15\ub451\uc73c\ub85c\ubd80\ud130\uc758 \uac70\ub9ac.").defineInRange("riverHeightFadeBlocks", 160, 20, 1024);
        MOUNTAIN_NOISE_MAXIMUM_HEIGHT_BLOCKS = builder.comment("terrain.bmp \uc0b0\uc545 \ub4f1\uae09 \uc548\uc5d0\uc11c \uc5f0\uc18d \ub178\uc774\uc988\uac00 \ub354\ud560 \uc218 \uc788\ub294 \ucd5c\ub300 \uace0\ub3c4(\ube14\ub85d).").defineInRange("mountainNoiseMaximumHeightBlocks", 120, 32, 192);
        RIVER_MAXIMUM_DEPTH_BLOCKS = builder.comment("\ubc14\ub2d0\ub77c \ub300\uc218\uce35 \ubc0f \uc9c0\ud45c \uc0dd\uc131 \uc774\uc804, \uc6d0\ubcf8 \uac15 \ubc14\ub2e5\uc774 \ub0b4\ub824\uac00\ub294 \ucd5c\ub300 \ube14\ub85d \uc218.").defineInRange("riverMaximumDepthBlocks", 7, 1, 7);
        builder.pop();
        builder.push("river_widths");
        RIVER_WIDTH_000064 = builder.comment("rivers.bmp \uc0c9\uc0c1 #000064\uc758 \uac15 \ub108\ube44(\ube14\ub85d \ub2e8\uc704).").defineInRange("color_000064", 27, 1, 256);
        RIVER_WIDTH_000096 = builder.comment("rivers.bmp \uc0c9\uc0c1 #000096\uc758 \uac15 \ub108\ube44(\ube14\ub85d \ub2e8\uc704).").defineInRange("color_000096", 22, 1, 256);
        RIVER_WIDTH_0000C8 = builder.comment("rivers.bmp \uc0c9\uc0c1 #0000C8\uc758 \uac15 \ub108\ube44(\ube14\ub85d \ub2e8\uc704).").defineInRange("color_0000C8", 17, 1, 256);
        RIVER_WIDTH_0000FF = builder.comment("rivers.bmp \uc0c9\uc0c1 #0000FF\uc758 \uac15 \ub108\ube44(\ube14\ub85d \ub2e8\uc704).").defineInRange("color_0000FF", 13, 1, 256);
        RIVER_WIDTH_0064FF = builder.comment("rivers.bmp \uc0c9\uc0c1 #0064FF\uc758 \uac15 \ub108\ube44(\ube14\ub85d \ub2e8\uc704).").defineInRange("color_0064FF", 10, 1, 256);
        RIVER_WIDTH_00C8FF = builder.comment("rivers.bmp \uc0c9\uc0c1 #00C8FF\uc758 \uac15 \ub108\ube44(\ube14\ub85d \ub2e8\uc704).").defineInRange("color_00C8FF", 7, 1, 256);
        RIVER_WIDTH_00E1FF = builder.comment("rivers.bmp \uc0c9\uc0c1 #00E1FF\uc758 \uac15 \ub108\ube44(\ube14\ub85d \ub2e8\uc704).").defineInRange("color_00E1FF", 5, 1, 256);
        RIVER_WIDTH_SCALE = builder.comment("\ubaa8\ub4e0 \uc6d0\ubcf8 \uac15 \ub108\ube44\uc5d0 \uc801\uc6a9\ub418\ub294 \uc804\uc5ed \ubc30\uc728. 0.5\ub294 \uc774\ubbf8 \uc5ec\ub7ec \ud53d\uc140\uc744 \ucc28\uc9c0\ud558\ub294 \uc6d0\ubcf8 \uc120\uc744 \ubcf4\uc815\ud569\ub2c8\ub2e4.").defineInRange("widthScale", 0.5, 0.05, 4.0);
        RIVER_MINIMUM_WIDTH_BLOCKS = builder.comment("\uc6d0\ubcf8 \uac15\uc758 \ucd5c\uc18c \uc0dd\uc131 \ub108\ube44. 12\ube14\ub85d\uc73c\ub85c \uc124\uc815\ud558\uba74 4\ube14\ub85d \ub2e8\uc704 \ubc14\uc774\uc634 \uc0d8\ud50c\uc5d0\uc11c\ub3c4 \uc791\uc740 \uac15\uc774 \ub04a\uae30\uc9c0 \uc54a\uace0 \uc774\uc5b4\uc9d1\ub2c8\ub2e4.").defineInRange("minimumWidthBlocks", 12, 1, 64);
        RIVER_GAP_BRIDGE_PIXELS = builder.comment("\ube44\uc2b7\ud55c \ubc29\ud5a5\uc758 \uac15 \uc120 \uc0ac\uc774\uc5d0\uc11c \uc5f0\uacb0\ud560 \uc218 \uc788\ub294 \ucd5c\ub300 \ub204\ub77d \uc6d0\ubcf8 \ud53d\uc140 \uc218. \uc778\uc811\ud55c \ub3c5\ub9bd\ub41c \uac15\ub07c\ub9ac \uc5f0\uacb0\ub418\uc9c0 \uc54a\ub3c4\ub85d 2 \uc774\ud558\ub85c \uc720\uc9c0\ud558\uc138\uc694.").defineInRange("gapBridgePixels", 3, 0, 4);
        RIVER_CHANNEL_CONTINENTALNESS = builder.comment("\uc6d0\ubcf8 \ub808\uc774\uc5b4 \uac15 \uc911\uc2ec\uc758 \ub300\ub959\uc131(continentalness) \uac12. \uac12\uc774 \ub0ae\uc744\uc218\ub85d \uc77c\ubc18 \uc9c0\ud615 \uc0dd\uc131 \uc774\uc804\uc5d0 \uc548\uc815\uc801\uc778 \uc595\uc740 \uc218\ub85c\uac00 \ud615\uc131\ub429\ub2c8\ub2e4.").defineInRange("channelContinentalness", -0.17, -0.8, -0.05);
        RIVER_BANK_FADE_BLOCKS = builder.comment("\uac01 \uac15\ub451\uc744 \uc8fc\ubcc0 \uc9c0\ud615\uc73c\ub85c \uc790\uc5f0\uc2a4\ub7fd\uac8c \uacbd\uc0ac\uc9c0\uac8c \ub9cc\ub4dc\ub294 \uc2e4\uc81c \ube14\ub85d \uac70\ub9ac. \ub9f5 \ud53d\uc140\uc5d0 \ube44\ub840\ud574\uc11c\ub294 \uc548 \ub418\uba70, \uadf8\ub807\uc9c0 \uc54a\uc73c\uba74 blocksPerPixel \uac12\uc774 \ud074 \ub54c \uac15\uc774 \uacfc\ub3c4\ud558\uac8c \ucee4\uc9d1\ub2c8\ub2e4.").defineInRange("bankFadeBlocks", 48, 2, 128);
        RIVER_MINIMUM_INLAND_BLOCKS = builder.comment("\uc6d0\ubcf8 \uac15\uc758 \ub124 \ubc29\ud5a5 \ubaa8\ub450\uc5d0 \ud544\uc694\ud55c \uc721\uc9c0 \uc5ec\ubc31. \ud574\uc548\uc120 \uc120\uc774 \uc791\uc740 \uc12c\uc744 \uac15 \uc804\uc6a9 \ubc14\uc774\uc634\uc73c\ub85c \ub9cc\ub4dc\ub294 \uac83\uc744 \ubc29\uc9c0\ud569\ub2c8\ub2e4.").defineInRange("minimumInlandBlocks", 24, 4, 128);
        RIVER_CHANNEL_EDGE_FADE_BLOCKS = builder.comment("\uc6d0\ubcf8 \uac15\ubc14\ub2e5\uc5d0\uc11c \uac15\ub451\uc73c\ub85c \uc774\uc5b4\uc9c0\ub294 \uc2e4\uc81c \ud398\uc774\ub4dc \uac70\ub9ac. \uc810\uc9c4\uc801\uc778 \uac12\uc744 \uc0ac\uc6a9\ud558\uba74 \uc218\uc9c1\uc73c\ub85c \uae4e\uc778 \ubb3c\uac00 \uc808\ubcbd\uc744 \ubc29\uc9c0\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4.").defineInRange("channelEdgeFadeBlocks", 32, 0, 128);
        builder.pop();
        builder.push("surface_water");
        DESERT_WATER_REDUCTION_ENABLED = builder.comment("\uac15 \ub808\uc774\uc5b4\uc5d0 \uc18d\ud558\uc9c0 \uc54a\ub294 \ud55c, \ub9f5\uc5d0 \uc9c0\uc815\ub41c \uc721\uc9c0\uc5d0\uc11c \uc0dd\uc131\ub418\ub294 \uc9c0\ud45c\uc218 \uc9c0\ud615\uc744 \uc81c\uac70\ud569\ub2c8\ub2e4. \ub9f5\uc5d0 \uc9c0\uc815\ub41c \ubc14\ub2e4\uc640 \uc9c0\ud558\uc218\ub294 \uadf8\ub300\ub85c \uc720\uc9c0\ub429\ub2c8\ub2e4.").define("enabled", true);
        DESERT_RIVER_WIDTH_SCALE = builder.comment("\uc0ac\ub9c9\uc744 \uac00\ub85c\uc9c0\ub974\ub294 \uc6d0\ubcf8 \uac15\uc758 \ub108\ube44 \ubc30\uc728. 0.30\uc744 \ucd08\uacfc\ud558\ub294 \uac12\uc740 \uc0ac\ub9c9 \uac15 \ub124\ud2b8\uc6cc\ud06c\uac00 \ud638\uc218\uac00 \ub418\ub294 \uac83\uc744 \ubc29\uc9c0\ud558\uae30 \uc704\ud574 \uc548\uc804\ud558\uac8c \uc81c\ud55c\ub429\ub2c8\ub2e4.").defineInRange("riverWidthScale", 0.2, 0.0, 1.0);
        DESERT_MINIMUM_RIVER_WIDTH_BLOCKS = builder.comment("\uc0ac\ub9c9\uc5d0\uc11c \ud5c8\uc6a9\ub418\ub294, \uc81c\ud55c \uc801\uc6a9 \uc804 \ucd5c\uc18c \uc6d0\ubcf8 \uac15 \ub108\ube44. \uc774\ubcf4\ub2e4 \uc791\uc740 \uc120\uc740 \uc77c\ubc18 \uc0ac\ub9c9 \uc9c0\ud615\uc774 \ub429\ub2c8\ub2e4.").defineInRange("minimumRiverWidthBlocks", 20, 1, 128);
        DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS = builder.comment("\uc0b4\uc544\ub0a8\uc740 \uc0ac\ub9c9 \uac15\uc758 \ucd5c\uc885 \ucd5c\ub300 \uc218\uba74 \ub108\ube44. \ub113\uc740 \uc6d0\ubcf8 \uc120\uacfc \ub8e8\ud504\uac00 \uc0ac\ub9c9 \ud638\uc218\uac00 \ub418\ub294 \uac83\uc744 \ubc29\uc9c0\ud569\ub2c8\ub2e4.").defineInRange("maximumRiverWidthBlocks", 18, 4, 64);
        builder.pop();
        SPEC = builder.build();
    }
}

