package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned world-generation settings. They are stored per world in serverconfig. */
public final class EarthShapeServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BLOCKS_PER_PIXEL;
    public static final ModConfigSpec.IntValue COAST_SMOOTHING_RADIUS_BLOCKS;
    public static final ModConfigSpec.BooleanValue USE_RIVERS_CONTINENT_MASK;
    public static final ModConfigSpec.BooleanValue USE_HEIGHTMAP_LAYER;
    public static final ModConfigSpec.BooleanValue USE_NORMAL_LAYER;
    public static final ModConfigSpec.BooleanValue USE_TEMPERATURE_LAYER;
    public static final ModConfigSpec.BooleanValue USE_TREES_LAYER;
    public static final ModConfigSpec.BooleanValue USE_RIVERS_HUMIDITY_LAYER;
    public static final ModConfigSpec.BooleanValue USE_TERRAIN_DESERT_LAYER;
    public static final ModConfigSpec.BooleanValue ENABLE_DETAILED_TERRAIN;
    public static final ModConfigSpec.BooleanValue ENABLE_TERRAIN_MOD_COMPATIBILITY;
    public static final ModConfigSpec.DoubleValue OCEAN_CONTINENTALNESS;
    public static final ModConfigSpec.DoubleValue LAND_CONTINENTALNESS_RANGE;
    public static final ModConfigSpec.DoubleValue HEIGHTMAP_GUIDE_STRENGTH;
    public static final ModConfigSpec.DoubleValue NORMAL_GUIDE_STRENGTH;
    public static final ModConfigSpec.DoubleValue MOUNTAIN_ELEVATION_THRESHOLD;
    public static final ModConfigSpec.DoubleValue MOUNTAIN_RIDGE_GUIDE_STRENGTH;
    public static final ModConfigSpec.DoubleValue MOUNTAIN_EROSION_GUIDE_STRENGTH;
    public static final ModConfigSpec.DoubleValue TEMPERATURE_GUIDE_STRENGTH;
    public static final ModConfigSpec.DoubleValue VEGETATION_GUIDE_STRENGTH;
    public static final ModConfigSpec.DoubleValue HUMIDITY_BASE;
    public static final ModConfigSpec.DoubleValue TREE_HUMIDITY_WEIGHT;
    public static final ModConfigSpec.DoubleValue TROPICAL_TREE_TEMPERATURE_FLOOR;
    public static final ModConfigSpec.DoubleValue TROPICAL_TREE_HUMIDITY_FLOOR;
    public static final ModConfigSpec.DoubleValue RIVER_HUMIDITY_WEIGHT;
    public static final ModConfigSpec.DoubleValue DESERT_DRYNESS_WEIGHT;
    public static final ModConfigSpec.IntValue DESERT_CLUSTER_RADIUS_BLOCKS;
    public static final ModConfigSpec.DoubleValue DESERT_CLUSTER_THRESHOLD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rivers_continents");
        BLOCKS_PER_PIXEL = builder
                .comment("Minecraft blocks represented by one rivers.bmp pixel. 20 preserves the bundled map's 283840 x 141920 block size.")
                .defineInRange("blocksPerPixel", 20, 1, 4096);
        COAST_SMOOTHING_RADIUS_BLOCKS = builder.comment("Radius used only to soften the native continentalness transition at a raster coastline. 0 keeps hard source pixels.")
                .defineInRange("coastSmoothingRadiusBlocks", 60, 0, 1024);
        builder.push("enabled_layers");
        USE_RIVERS_CONTINENT_MASK = builder.comment("Use rivers.bmp as the land/ocean coastline mask. Disabling this supplies neutral continentalness instead of the map coastline.")
                .define("riversContinentMask", true);
        USE_HEIGHTMAP_LAYER = builder.comment("Use heightmap.bmp to guide native terrain elevation.")
                .define("heightmap", true);
        USE_NORMAL_LAYER = builder.comment("Use world_normal.bmp to add mapped local relief to native terrain.")
                .define("worldNormal", true);
        USE_TEMPERATURE_LAYER = builder.comment("Use earth_temperature.png to guide native temperature noise.")
                .define("temperature", true);
        USE_TREES_LAYER = builder.comment("Use trees.bmp as a humidity contribution.")
                .define("trees", true);
        USE_RIVERS_HUMIDITY_LAYER = builder.comment("Use non-ocean markings in rivers.bmp as a humidity contribution. This is independent from riversContinentMask.")
                .define("riversHumidity", true);
        USE_TERRAIN_DESERT_LAYER = builder.comment("Use terrain.bmp as the primary temperature and humidity classification layer. It never changes coastlines or terrain shape.")
                .define("terrainClimate", true);
        builder.pop();
        builder.push("detailed_terrain");
        ENABLE_DETAILED_TERRAIN = builder.comment("Enable regional detailed-terrain biome rules. When enabled, Badlands/Mesa biomes are restricted to the listed real-world regions.")
                .define("enabled", true);
        ENABLE_TERRAIN_MOD_COMPATIBILITY = builder.comment("Guide compatible overworld terrain mods through their noise-router inputs while preserving each mod's final density, caves, structures and surface rules. Amplified terrain mods still require a dedicated patch.")
                .define("terrainModCompatibility", true);
        builder.pop();
        builder.push("terrain_guidance");
        OCEAN_CONTINENTALNESS = builder.comment("Continentalness supplied for mapped open ocean.")
                .defineInRange("oceanContinentalness", -0.65D, -1.20D, 0.20D);
        LAND_CONTINENTALNESS_RANGE = builder.comment("How far mapped land moves continentalness above the ocean value.")
                .defineInRange("landContinentalnessRange", 0.85D, 0.0D, 1.50D);
        HEIGHTMAP_GUIDE_STRENGTH = builder.comment("Heightmap influence on native continental terrain splines; it does not replace final density.")
                .defineInRange("heightmapGuideStrength", 0.42D, 0.0D, 1.0D);
        NORMAL_GUIDE_STRENGTH = builder.comment("Normal-map relief influence on native continental terrain splines.")
                .defineInRange("normalGuideStrength", 0.13D, 0.0D, 0.50D);
        MOUNTAIN_ELEVATION_THRESHOLD = builder.comment("Normalized heightmap value where mapped highland starts to steer vanilla peaks and erosion toward mountains.")
                .defineInRange("mountainElevationThreshold", 0.58D, 0.0D, 1.0D);
        MOUNTAIN_RIDGE_GUIDE_STRENGTH = builder.comment("How strongly high heightmap areas guide vanilla ridge noise toward peak terrain. Does not replace final density.")
                .defineInRange("mountainRidgeGuideStrength", 0.85D, 0.0D, 1.0D);
        MOUNTAIN_EROSION_GUIDE_STRENGTH = builder.comment("How strongly high heightmap areas guide vanilla erosion toward rugged mountain terrain.")
                .defineInRange("mountainErosionGuideStrength", 0.70D, 0.0D, 1.0D);
        builder.pop();
        builder.push("climate_guidance");
        TEMPERATURE_GUIDE_STRENGTH = builder.comment("Blend weight of earth_temperature.png into vanilla temperature noise. 1.0 makes the temperature map mandatory and leaves no vanilla temperature-noise contribution.")
                .defineInRange("temperatureGuideStrength", 1.0D, 0.0D, 1.0D);
        VEGETATION_GUIDE_STRENGTH = builder.comment("Blend weight of trees/rivers/terrain humidity into vanilla vegetation noise.")
                .defineInRange("vegetationGuideStrength", 0.92D, 0.0D, 1.0D);
        HUMIDITY_BASE = builder.comment("Base normalized humidity before raster layers.")
                .defineInRange("humidityBase", 0.08D, 0.0D, 1.0D);
        TREE_HUMIDITY_WEIGHT = builder.comment("Humidity contributed by trees.bmp.")
                .defineInRange("treeHumidityWeight", 0.86D, 0.0D, 1.0D);
        TROPICAL_TREE_TEMPERATURE_FLOOR = builder.comment("Minimum normalized temperature inside purple tropical-tree pixels in trees.bmp. Green tree pixels keep the temperature map's temperate/cold value.")
                .defineInRange("tropicalTreeTemperatureFloor", 0.78D, 0.0D, 1.0D);
        TROPICAL_TREE_HUMIDITY_FLOOR = builder.comment("Minimum normalized humidity inside purple tropical-tree pixels in trees.bmp.")
                .defineInRange("tropicalTreeHumidityFloor", 0.72D, 0.0D, 1.0D);
        RIVER_HUMIDITY_WEIGHT = builder.comment("Humidity contributed by rivers.bmp markings.")
                .defineInRange("riverHumidityWeight", 0.25D, 0.0D, 1.0D);
        DESERT_DRYNESS_WEIGHT = builder.comment("Humidity removed by the exact terrain.bmp desert palette.")
                .defineInRange("desertDrynessWeight", 0.60D, 0.0D, 1.0D);
        DESERT_CLUSTER_RADIUS_BLOCKS = builder.comment("Climate-only radius used to merge small terrain.bmp desert speckles into coherent desert regions. Coastlines and terrain height are unaffected.")
                .defineInRange("desertClusterRadiusBlocks", 480, 0, 4096);
        DESERT_CLUSTER_THRESHOLD = builder.comment("Fraction of nearby terrain.bmp samples that must be desert before the whole climate cell becomes desert.")
                .defineInRange("desertClusterThreshold", 0.45D, 0.0D, 1.0D);
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private EarthShapeServerConfig() {}
}
