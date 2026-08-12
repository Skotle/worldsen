package io.github.earthshape;

/**
 * Fabric replacement for the NeoForge config facade. Defaults intentionally
 * match the NeoForge 1.21.1 port; TOML persistence is added after the 26.2
 * world-generation hooks have been migrated.
 */
public final class EarthShapeServerConfig {
    public record Value<T>(T value) { public T get() { return value; } }
    private static Value<Boolean> b(boolean value) { return new Value<>(value); }
    private static Value<Integer> i(int value) { return new Value<>(value); }
    private static Value<Double> d(double value) { return new Value<>(value); }

    public static final Value<Integer> BLOCKS_PER_PIXEL = i(20);
    public static final Value<Boolean> RANDOM_MAP_CENTER_ENABLED = b(false);
    public static final Value<Integer> RANDOM_MAP_CENTER_MIN_X = i(0), RANDOM_MAP_CENTER_MAX_X = i(5999), RANDOM_MAP_CENTER_MIN_Z = i(0), RANDOM_MAP_CENTER_MAX_Z = i(3399);
    public static final Value<Boolean> CONTINENTS_ENABLED = b(true), CONTINENT_HEIGHT_LIMIT_ENABLED = b(true), TERRAIN_BIOMES_ENABLED = b(true);
    public static final Value<Integer> TERRAIN_BIOME_MINIMUM_REGION_PIXELS = i(4), TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS = i(12), BIOME_MINIMUM_REGION_PIXELS = i(16);
    public static final Value<Boolean> OCEAN_TEMPERATURE_ENABLED = b(true), TUNDRA_TEMPERATURE_ENABLED = b(true), RIVER_BIOMES_ENABLED = b(true), BIOME_BOUNDARY_WARP_ENABLED = b(true);
    public static final Value<Double> TUNDRA_TEMPERATURE_THRESHOLD = d(-0.25), SNOW_TEMPERATURE_THRESHOLD = d(-0.625), TEMPERATURE_VERTICAL_SCALE = d(1.12);
    public static final Value<Integer> SNOW_ALTITUDE_BLOCKS = i(160), BIOME_BOUNDARY_WARP_BLOCKS = i(24), COAST_HEIGHT_FADE_BLOCKS = i(320), COAST_SHALLOW_SHELF_WIDTH_BLOCKS = i(6), COAST_SHELF_TRANSITION_BLOCKS = i(16), COAST_SHELF_DEEP_FLOOR_Y = i(51);
    public static final Value<Integer> ISLAND_MAXIMUM_SURFACE_Y = i(127), REGIONAL_MAXIMUM_SURFACE_Y = i(159), CONTINENT_MAXIMUM_SURFACE_Y = i(183), MOUNTAIN_REGION_MINIMUM_SURFACE_Y = i(96), MOUNTAIN_REGION_FULL_HEIGHT_SPAN_BLOCKS = i(1200), RIVER_MAXIMUM_DEPTH_BLOCKS = i(6);
    public static final Value<Integer> RIVER_WIDTH_000064 = i(27), RIVER_WIDTH_000096 = i(22), RIVER_WIDTH_0000C8 = i(17), RIVER_WIDTH_0000FF = i(13), RIVER_WIDTH_0064FF = i(10), RIVER_WIDTH_00C8FF = i(7), RIVER_WIDTH_00E1FF = i(5);
    public static final Value<Double> RIVER_WIDTH_SCALE = d(0.5), RIVER_CHANNEL_CONTINENTALNESS = d(-0.17), DESERT_RIVER_WIDTH_SCALE = d(0.2), SURFACE_STRUCTURE_RATE = d(1.0);
    public static final Value<Integer> RIVER_MINIMUM_WIDTH_BLOCKS = i(12), RIVER_GAP_BRIDGE_PIXELS = i(4), RIVER_BANK_FADE_BLOCKS = i(48), RIVER_MINIMUM_INLAND_BLOCKS = i(24), RIVER_CHANNEL_EDGE_FADE_BLOCKS = i(32), DESERT_MINIMUM_RIVER_WIDTH_BLOCKS = i(20), DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS = i(18);
    public static final Value<Boolean> DESERT_WATER_REDUCTION_ENABLED = b(true);
    private EarthShapeServerConfig() {}
}
