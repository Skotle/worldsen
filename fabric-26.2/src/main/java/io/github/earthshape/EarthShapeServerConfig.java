package io.github.earthshape;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric configuration equivalent of the NeoForge server config.
 *
 * <p>All options use the same defaults and bounds as the NeoForge port. The
 * first load writes {@code config/earthshape.properties}; edits apply the next
 * time the game or dedicated server starts.</p>
 */
public final class EarthShapeServerConfig {
   public static final class Value<T> {
      private final T defaultValue;
      private volatile T value;

      private Value(T defaultValue) {
         this.defaultValue = defaultValue;
         this.value = defaultValue;
      }

      public T get() {
         return value;
      }

      private void set(T value) {
         this.value = value;
      }

      private void reset() {
         this.value = defaultValue;
      }
   }

   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("earthshape.properties");

   public static final Value<Integer> BLOCKS_PER_PIXEL = i(20);
   public static final Value<Boolean> RANDOM_MAP_CENTER_ENABLED = b(false);
   public static final Value<Integer> RANDOM_MAP_CENTER_MIN_X = i(0);
   public static final Value<Integer> RANDOM_MAP_CENTER_MAX_X = i(5999);
   public static final Value<Integer> RANDOM_MAP_CENTER_MIN_Z = i(0);
   public static final Value<Integer> RANDOM_MAP_CENTER_MAX_Z = i(3399);
   public static final Value<Boolean> CONTINENTS_ENABLED = b(true);
   public static final Value<Boolean> CONTINENT_HEIGHT_LIMIT_ENABLED = b(true);
   public static final Value<Boolean> TERRAIN_BIOMES_ENABLED = b(true);
   public static final Value<Integer> TERRAIN_BIOME_MINIMUM_REGION_PIXELS = i(4);
   public static final Value<Integer> TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS = i(12);
   public static final Value<Integer> BIOME_MINIMUM_REGION_PIXELS = i(16);
   public static final Value<Integer> BIOME_MINIMUM_REGION_CHUNKS = i(32);
   public static final Value<Integer> BIOME_ISOLATED_MINIMUM_REGION_CHUNKS = i(48);
   public static final Value<Integer> RARE_BIOME_MINIMUM_REGION_CHUNKS = i(64);
   public static final Value<Boolean> OCEAN_TEMPERATURE_ENABLED = b(true);
   public static final Value<Boolean> TUNDRA_TEMPERATURE_ENABLED = b(true);
   public static final Value<Double> TUNDRA_TEMPERATURE_THRESHOLD = d(-0.25);
   public static final Value<Integer> SNOW_ALTITUDE_BLOCKS = i(160);
   public static final Value<Double> SNOW_TEMPERATURE_THRESHOLD = d(-0.625);
   public static final Value<Boolean> RIVER_BIOMES_ENABLED = b(true);
   public static final Value<Boolean> BIOME_BOUNDARY_WARP_ENABLED = b(true);
   public static final Value<Integer> BIOME_BOUNDARY_WARP_BLOCKS = i(24);
   public static final Value<Integer> COAST_HEIGHT_FADE_BLOCKS = i(320);
   public static final Value<Boolean> COAST_SHELF_VARIATION_ENABLED = b(true);
   public static final Value<Double> COAST_SHELF_VARIATION_MIN_SCALE = d(0.55);
   public static final Value<Double> COAST_SHELF_VARIATION_MAX_SCALE = d(2.0);
   public static final Value<Integer> COAST_SHALLOW_SHELF_WIDTH_BLOCKS = i(6);
   public static final Value<Integer> COAST_SHELF_TRANSITION_BLOCKS = i(16);
   public static final Value<Integer> COAST_SHELF_DEEP_FLOOR_Y = i(51);
   public static final Value<Integer> ISLAND_MAXIMUM_SURFACE_Y = i(127);
   public static final Value<Integer> REGIONAL_MAXIMUM_SURFACE_Y = i(159);
   public static final Value<Integer> CONTINENT_MAXIMUM_SURFACE_Y = i(183);
   public static final Value<Integer> MOUNTAIN_REGION_MINIMUM_SURFACE_Y = i(96);
   public static final Value<Integer> MOUNTAIN_REGION_FULL_HEIGHT_SPAN_BLOCKS = i(1200);
   public static final Value<Integer> RIVER_MAXIMUM_DEPTH_BLOCKS = i(6);
   public static final Value<Integer> RIVER_WIDTH_000064 = i(27);
   public static final Value<Integer> RIVER_WIDTH_000096 = i(22);
   public static final Value<Integer> RIVER_WIDTH_0000C8 = i(17);
   public static final Value<Integer> RIVER_WIDTH_0000FF = i(13);
   public static final Value<Integer> RIVER_WIDTH_0064FF = i(10);
   public static final Value<Integer> RIVER_WIDTH_00C8FF = i(7);
   public static final Value<Integer> RIVER_WIDTH_00E1FF = i(5);
   public static final Value<Double> RIVER_WIDTH_SCALE = d(0.5);
   public static final Value<Integer> RIVER_MINIMUM_WIDTH_BLOCKS = i(12);
   public static final Value<Integer> RIVER_GAP_BRIDGE_PIXELS = i(4);
   public static final Value<Double> RIVER_CHANNEL_CONTINENTALNESS = d(-0.17);
   public static final Value<Integer> RIVER_BANK_FADE_BLOCKS = i(48);
   public static final Value<Integer> RIVER_MINIMUM_INLAND_BLOCKS = i(24);
   public static final Value<Integer> RIVER_CHANNEL_EDGE_FADE_BLOCKS = i(32);
   public static final Value<Boolean> DESERT_WATER_REDUCTION_ENABLED = b(true);
   public static final Value<Double> DESERT_RIVER_WIDTH_SCALE = d(0.2);
   public static final Value<Integer> DESERT_MINIMUM_RIVER_WIDTH_BLOCKS = i(20);
   public static final Value<Integer> DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS = i(18);
   public static final Value<Double> SURFACE_STRUCTURE_RATE = d(1.0);

   private EarthShapeServerConfig() {
   }

   /** Reloads all values from the Fabric configuration file. */
   public static synchronized void load() {
      Properties properties = new Properties();
      if (Files.isRegularFile(FILE)) {
         try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
         } catch (IOException exception) {
            EarthShape.LOGGER.warn("[EarthShape] Could not read {}; using defaults.", FILE, exception);
         }
      }

      BLOCKS_PER_PIXEL.set(integer(properties, "map_scale.blocksPerPixel", BLOCKS_PER_PIXEL, 1, 4096));
      RANDOM_MAP_CENTER_ENABLED.set(bool(properties, "map_scale.randomCenterEnabled", RANDOM_MAP_CENTER_ENABLED));
      RANDOM_MAP_CENTER_MIN_X.set(integer(properties, "map_scale.randomCenterMinLayerX", RANDOM_MAP_CENTER_MIN_X, 0, 5999));
      RANDOM_MAP_CENTER_MAX_X.set(integer(properties, "map_scale.randomCenterMaxLayerX", RANDOM_MAP_CENTER_MAX_X, 0, 5999));
      RANDOM_MAP_CENTER_MIN_Z.set(integer(properties, "map_scale.randomCenterMinLayerZ", RANDOM_MAP_CENTER_MIN_Z, 0, 3399));
      RANDOM_MAP_CENTER_MAX_Z.set(integer(properties, "map_scale.randomCenterMaxLayerZ", RANDOM_MAP_CENTER_MAX_Z, 0, 3399));
      CONTINENTS_ENABLED.set(bool(properties, "layers.continentsEnabled", CONTINENTS_ENABLED));
      CONTINENT_HEIGHT_LIMIT_ENABLED.set(bool(properties, "layers.continentHeightLimitEnabled", CONTINENT_HEIGHT_LIMIT_ENABLED));
      TERRAIN_BIOMES_ENABLED.set(bool(properties, "layers.terrainBiomesEnabled", TERRAIN_BIOMES_ENABLED));
      TERRAIN_BIOME_MINIMUM_REGION_PIXELS.set(integer(properties, "layers.terrainBiomeMinimumRegionPixels", TERRAIN_BIOME_MINIMUM_REGION_PIXELS, 1, 4096));
      TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS.set(integer(properties, "layers.terrainBiomeIsolatedMinimumRegionPixels", TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS, 1, 4096));
      BIOME_MINIMUM_REGION_PIXELS.set(integer(properties, "layers.biomeMinimumRegionPixels", BIOME_MINIMUM_REGION_PIXELS, 1, 4096));
      BIOME_MINIMUM_REGION_CHUNKS.set(integer(properties, "layers.biomeMinimumRegionChunks", BIOME_MINIMUM_REGION_CHUNKS, 1, 4096));
      BIOME_ISOLATED_MINIMUM_REGION_CHUNKS.set(integer(properties, "layers.biomeIsolatedMinimumRegionChunks", BIOME_ISOLATED_MINIMUM_REGION_CHUNKS, 1, 4096));
      RARE_BIOME_MINIMUM_REGION_CHUNKS.set(integer(properties, "layers.rareBiomeMinimumRegionChunks", RARE_BIOME_MINIMUM_REGION_CHUNKS, 0, 4096));
      OCEAN_TEMPERATURE_ENABLED.set(bool(properties, "layers.oceanTemperatureEnabled", OCEAN_TEMPERATURE_ENABLED));
      TUNDRA_TEMPERATURE_ENABLED.set(bool(properties, "layers.tundraTemperatureEnabled", TUNDRA_TEMPERATURE_ENABLED));
      TUNDRA_TEMPERATURE_THRESHOLD.set(decimal(properties, "layers.tundraTemperatureThreshold", TUNDRA_TEMPERATURE_THRESHOLD, -1.0, 1.0));
      SNOW_ALTITUDE_BLOCKS.set(integer(properties, "layers.snowAltitudeBlocks", SNOW_ALTITUDE_BLOCKS, 64, 320));
      SNOW_TEMPERATURE_THRESHOLD.set(decimal(properties, "layers.snowTemperatureThreshold", SNOW_TEMPERATURE_THRESHOLD, -1.0, 0.0));
      RIVER_BIOMES_ENABLED.set(bool(properties, "layers.riverBiomesEnabled", RIVER_BIOMES_ENABLED));
      BIOME_BOUNDARY_WARP_ENABLED.set(bool(properties, "layers.biomeBoundaryWarpEnabled", BIOME_BOUNDARY_WARP_ENABLED));
      BIOME_BOUNDARY_WARP_BLOCKS.set(integer(properties, "layers.biomeBoundaryWarpBlocks", BIOME_BOUNDARY_WARP_BLOCKS, 0, 64));
      COAST_HEIGHT_FADE_BLOCKS.set(integer(properties, "terrain_shaping.coastHeightFadeBlocks", COAST_HEIGHT_FADE_BLOCKS, 20, 1024));
      COAST_SHELF_VARIATION_ENABLED.set(bool(properties, "terrain_shaping.coastShelfVariationEnabled", COAST_SHELF_VARIATION_ENABLED));
      COAST_SHELF_VARIATION_MIN_SCALE.set(decimal(properties, "terrain_shaping.coastShelfVariationMinScale", COAST_SHELF_VARIATION_MIN_SCALE, 0.25, 1.0));
      COAST_SHELF_VARIATION_MAX_SCALE.set(decimal(properties, "terrain_shaping.coastShelfVariationMaxScale", COAST_SHELF_VARIATION_MAX_SCALE, 1.0, 2.0));
      COAST_SHALLOW_SHELF_WIDTH_BLOCKS.set(integer(properties, "terrain_shaping.coastShallowShelfWidthBlocks", COAST_SHALLOW_SHELF_WIDTH_BLOCKS, 4, 8));
      COAST_SHELF_TRANSITION_BLOCKS.set(integer(properties, "terrain_shaping.coastShelfTransitionBlocks", COAST_SHELF_TRANSITION_BLOCKS, 4, 128));
      COAST_SHELF_DEEP_FLOOR_Y.set(integer(properties, "terrain_shaping.coastShelfDeepFloorY", COAST_SHELF_DEEP_FLOOR_Y, -64, 61));
      ISLAND_MAXIMUM_SURFACE_Y.set(integer(properties, "terrain_shaping.islandMaximumSurfaceY", ISLAND_MAXIMUM_SURFACE_Y, 64, 319));
      REGIONAL_MAXIMUM_SURFACE_Y.set(integer(properties, "terrain_shaping.regionalMaximumSurfaceY", REGIONAL_MAXIMUM_SURFACE_Y, 64, 319));
      CONTINENT_MAXIMUM_SURFACE_Y.set(integer(properties, "terrain_shaping.continentMaximumSurfaceY", CONTINENT_MAXIMUM_SURFACE_Y, 64, 319));
      MOUNTAIN_REGION_MINIMUM_SURFACE_Y.set(integer(properties, "terrain_shaping.mountainRegionMinimumSurfaceY", MOUNTAIN_REGION_MINIMUM_SURFACE_Y, 64, 319));
      MOUNTAIN_REGION_FULL_HEIGHT_SPAN_BLOCKS.set(integer(properties, "terrain_shaping.mountainRegionFullHeightSpanBlocks", MOUNTAIN_REGION_FULL_HEIGHT_SPAN_BLOCKS, 160, 8192));
      RIVER_MAXIMUM_DEPTH_BLOCKS.set(integer(properties, "terrain_shaping.riverMaximumDepthBlocks", RIVER_MAXIMUM_DEPTH_BLOCKS, 6, 6));
      RIVER_WIDTH_000064.set(integer(properties, "river_widths.color_000064", RIVER_WIDTH_000064, 1, 256));
      RIVER_WIDTH_000096.set(integer(properties, "river_widths.color_000096", RIVER_WIDTH_000096, 1, 256));
      RIVER_WIDTH_0000C8.set(integer(properties, "river_widths.color_0000C8", RIVER_WIDTH_0000C8, 1, 256));
      RIVER_WIDTH_0000FF.set(integer(properties, "river_widths.color_0000FF", RIVER_WIDTH_0000FF, 1, 256));
      RIVER_WIDTH_0064FF.set(integer(properties, "river_widths.color_0064FF", RIVER_WIDTH_0064FF, 1, 256));
      RIVER_WIDTH_00C8FF.set(integer(properties, "river_widths.color_00C8FF", RIVER_WIDTH_00C8FF, 1, 256));
      RIVER_WIDTH_00E1FF.set(integer(properties, "river_widths.color_00E1FF", RIVER_WIDTH_00E1FF, 1, 256));
      RIVER_WIDTH_SCALE.set(decimal(properties, "river_widths.widthScale", RIVER_WIDTH_SCALE, 0.05, 4.0));
      RIVER_MINIMUM_WIDTH_BLOCKS.set(integer(properties, "river_widths.minimumWidthBlocks", RIVER_MINIMUM_WIDTH_BLOCKS, 1, 64));
      RIVER_GAP_BRIDGE_PIXELS.set(integer(properties, "river_widths.gapBridgePixels", RIVER_GAP_BRIDGE_PIXELS, 0, 4));
      RIVER_CHANNEL_CONTINENTALNESS.set(decimal(properties, "river_widths.channelContinentalness", RIVER_CHANNEL_CONTINENTALNESS, -0.8, -0.05));
      RIVER_BANK_FADE_BLOCKS.set(integer(properties, "river_widths.bankFadeBlocks", RIVER_BANK_FADE_BLOCKS, 2, 128));
      RIVER_MINIMUM_INLAND_BLOCKS.set(integer(properties, "river_widths.minimumInlandBlocks", RIVER_MINIMUM_INLAND_BLOCKS, 4, 128));
      RIVER_CHANNEL_EDGE_FADE_BLOCKS.set(integer(properties, "river_widths.channelEdgeFadeBlocks", RIVER_CHANNEL_EDGE_FADE_BLOCKS, 0, 128));
      DESERT_WATER_REDUCTION_ENABLED.set(bool(properties, "surface_water.enabled", DESERT_WATER_REDUCTION_ENABLED));
      DESERT_RIVER_WIDTH_SCALE.set(decimal(properties, "surface_water.riverWidthScale", DESERT_RIVER_WIDTH_SCALE, 0.0, 1.0));
      DESERT_MINIMUM_RIVER_WIDTH_BLOCKS.set(integer(properties, "surface_water.minimumRiverWidthBlocks", DESERT_MINIMUM_RIVER_WIDTH_BLOCKS, 1, 128));
      DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS.set(integer(properties, "surface_water.maximumRiverWidthBlocks", DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS, 4, 64));
      SURFACE_STRUCTURE_RATE.set(decimal(properties, "structures.surfaceStructureRate", SURFACE_STRUCTURE_RATE, 0.01, 1.0));

      save(properties);
   }

   private static boolean bool(Properties properties, String key, Value<Boolean> value) {
      String raw = properties.getProperty(key);
      if (raw == null) return value.get();
      if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) return Boolean.parseBoolean(raw);
      warnInvalid(key, raw, value.get());
      return value.get();
   }

   private static int integer(Properties properties, String key, Value<Integer> value, int min, int max) {
      String raw = properties.getProperty(key);
      if (raw == null) return value.get();
      try {
         int parsed = Integer.parseInt(raw);
         if (parsed >= min && parsed <= max) return parsed;
      } catch (NumberFormatException ignored) {
      }
      warnInvalid(key, raw, value.get());
      return value.get();
   }

   private static double decimal(Properties properties, String key, Value<Double> value, double min, double max) {
      String raw = properties.getProperty(key);
      if (raw == null) return value.get();
      try {
         double parsed = Double.parseDouble(raw);
         if (Double.isFinite(parsed) && parsed >= min && parsed <= max) return parsed;
      } catch (NumberFormatException ignored) {
      }
      warnInvalid(key, raw, value.get());
      return value.get();
   }

   private static void warnInvalid(String key, String raw, Object fallback) {
      EarthShape.LOGGER.warn("[EarthShape] Invalid config value {}={}; using {}.", key, raw, fallback);
   }

   private static void save(Properties properties) {
      put(properties, "map_scale.blocksPerPixel", BLOCKS_PER_PIXEL);
      put(properties, "map_scale.randomCenterEnabled", RANDOM_MAP_CENTER_ENABLED);
      put(properties, "map_scale.randomCenterMinLayerX", RANDOM_MAP_CENTER_MIN_X);
      put(properties, "map_scale.randomCenterMaxLayerX", RANDOM_MAP_CENTER_MAX_X);
      put(properties, "map_scale.randomCenterMinLayerZ", RANDOM_MAP_CENTER_MIN_Z);
      put(properties, "map_scale.randomCenterMaxLayerZ", RANDOM_MAP_CENTER_MAX_Z);
      put(properties, "layers.continentsEnabled", CONTINENTS_ENABLED);
      put(properties, "layers.continentHeightLimitEnabled", CONTINENT_HEIGHT_LIMIT_ENABLED);
      put(properties, "layers.terrainBiomesEnabled", TERRAIN_BIOMES_ENABLED);
      put(properties, "layers.terrainBiomeMinimumRegionPixels", TERRAIN_BIOME_MINIMUM_REGION_PIXELS);
      put(properties, "layers.terrainBiomeIsolatedMinimumRegionPixels", TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS);
      put(properties, "layers.biomeMinimumRegionPixels", BIOME_MINIMUM_REGION_PIXELS);
      put(properties, "layers.biomeMinimumRegionChunks", BIOME_MINIMUM_REGION_CHUNKS);
      put(properties, "layers.biomeIsolatedMinimumRegionChunks", BIOME_ISOLATED_MINIMUM_REGION_CHUNKS);
      put(properties, "layers.rareBiomeMinimumRegionChunks", RARE_BIOME_MINIMUM_REGION_CHUNKS);
      put(properties, "layers.oceanTemperatureEnabled", OCEAN_TEMPERATURE_ENABLED);
      put(properties, "layers.tundraTemperatureEnabled", TUNDRA_TEMPERATURE_ENABLED);
      put(properties, "layers.tundraTemperatureThreshold", TUNDRA_TEMPERATURE_THRESHOLD);
      put(properties, "layers.snowAltitudeBlocks", SNOW_ALTITUDE_BLOCKS);
      put(properties, "layers.snowTemperatureThreshold", SNOW_TEMPERATURE_THRESHOLD);
      put(properties, "layers.riverBiomesEnabled", RIVER_BIOMES_ENABLED);
      put(properties, "layers.biomeBoundaryWarpEnabled", BIOME_BOUNDARY_WARP_ENABLED);
      put(properties, "layers.biomeBoundaryWarpBlocks", BIOME_BOUNDARY_WARP_BLOCKS);
      put(properties, "terrain_shaping.coastHeightFadeBlocks", COAST_HEIGHT_FADE_BLOCKS);
      put(properties, "terrain_shaping.coastShelfVariationEnabled", COAST_SHELF_VARIATION_ENABLED);
      put(properties, "terrain_shaping.coastShelfVariationMinScale", COAST_SHELF_VARIATION_MIN_SCALE);
      put(properties, "terrain_shaping.coastShelfVariationMaxScale", COAST_SHELF_VARIATION_MAX_SCALE);
      put(properties, "terrain_shaping.coastShallowShelfWidthBlocks", COAST_SHALLOW_SHELF_WIDTH_BLOCKS);
      put(properties, "terrain_shaping.coastShelfTransitionBlocks", COAST_SHELF_TRANSITION_BLOCKS);
      put(properties, "terrain_shaping.coastShelfDeepFloorY", COAST_SHELF_DEEP_FLOOR_Y);
      put(properties, "terrain_shaping.islandMaximumSurfaceY", ISLAND_MAXIMUM_SURFACE_Y);
      put(properties, "terrain_shaping.regionalMaximumSurfaceY", REGIONAL_MAXIMUM_SURFACE_Y);
      put(properties, "terrain_shaping.continentMaximumSurfaceY", CONTINENT_MAXIMUM_SURFACE_Y);
      put(properties, "terrain_shaping.mountainRegionMinimumSurfaceY", MOUNTAIN_REGION_MINIMUM_SURFACE_Y);
      put(properties, "terrain_shaping.mountainRegionFullHeightSpanBlocks", MOUNTAIN_REGION_FULL_HEIGHT_SPAN_BLOCKS);
      put(properties, "terrain_shaping.riverMaximumDepthBlocks", RIVER_MAXIMUM_DEPTH_BLOCKS);
      put(properties, "river_widths.color_000064", RIVER_WIDTH_000064);
      put(properties, "river_widths.color_000096", RIVER_WIDTH_000096);
      put(properties, "river_widths.color_0000C8", RIVER_WIDTH_0000C8);
      put(properties, "river_widths.color_0000FF", RIVER_WIDTH_0000FF);
      put(properties, "river_widths.color_0064FF", RIVER_WIDTH_0064FF);
      put(properties, "river_widths.color_00C8FF", RIVER_WIDTH_00C8FF);
      put(properties, "river_widths.color_00E1FF", RIVER_WIDTH_00E1FF);
      put(properties, "river_widths.widthScale", RIVER_WIDTH_SCALE);
      put(properties, "river_widths.minimumWidthBlocks", RIVER_MINIMUM_WIDTH_BLOCKS);
      put(properties, "river_widths.gapBridgePixels", RIVER_GAP_BRIDGE_PIXELS);
      put(properties, "river_widths.channelContinentalness", RIVER_CHANNEL_CONTINENTALNESS);
      put(properties, "river_widths.bankFadeBlocks", RIVER_BANK_FADE_BLOCKS);
      put(properties, "river_widths.minimumInlandBlocks", RIVER_MINIMUM_INLAND_BLOCKS);
      put(properties, "river_widths.channelEdgeFadeBlocks", RIVER_CHANNEL_EDGE_FADE_BLOCKS);
      put(properties, "surface_water.enabled", DESERT_WATER_REDUCTION_ENABLED);
      put(properties, "surface_water.riverWidthScale", DESERT_RIVER_WIDTH_SCALE);
      put(properties, "surface_water.minimumRiverWidthBlocks", DESERT_MINIMUM_RIVER_WIDTH_BLOCKS);
      put(properties, "surface_water.maximumRiverWidthBlocks", DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS);
      put(properties, "structures.surfaceStructureRate", SURFACE_STRUCTURE_RATE);

      try {
         Files.createDirectories(FILE.getParent());
         try (OutputStream output = Files.newOutputStream(FILE)) {
            properties.store(output, "EarthShape Fabric server configuration. Restart after editing.");
         }
      } catch (IOException exception) {
         EarthShape.LOGGER.warn("[EarthShape] Could not write {}.", FILE, exception);
      }
   }

   private static void put(Properties properties, String key, Value<?> value) {
      properties.setProperty(key, String.valueOf(value.get()));
   }

   private static Value<Boolean> b(boolean value) { return new Value<>(value); }
   private static Value<Integer> i(int value) { return new Value<>(value); }
   private static Value<Double> d(double value) { return new Value<>(value); }
}
