package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class EarthShapeServerConfig {
   public static final ModConfigSpec SPEC;
   public static final IntValue BLOCKS_PER_PIXEL;
   public static final BooleanValue CONTINENTS_ENABLED;
   public static final BooleanValue HEIGHTMAP_ENABLED;
   public static final BooleanValue TERRAIN_BIOMES_ENABLED;
   public static final BooleanValue OCEAN_TEMPERATURE_ENABLED;
   public static final BooleanValue TUNDRA_TEMPERATURE_ENABLED;
   public static final DoubleValue TUNDRA_TEMPERATURE_THRESHOLD;
   public static final DoubleValue TEMPERATURE_VERTICAL_SCALE;
   public static final BooleanValue RIVER_BIOMES_ENABLED;
   public static final IntValue COAST_HEIGHT_FADE_BLOCKS;
   public static final IntValue RIVER_HEIGHT_FADE_BLOCKS;
   public static final DoubleValue HEIGHTMAP_MEDIAN;
   public static final IntValue RIVER_MAXIMUM_DEPTH_BLOCKS;
   public static final IntValue RIVER_WIDTH_000064;
   public static final IntValue RIVER_WIDTH_000096;
   public static final IntValue RIVER_WIDTH_0000C8;
   public static final IntValue RIVER_WIDTH_0000FF;
   public static final IntValue RIVER_WIDTH_0064FF;
   public static final IntValue RIVER_WIDTH_00C8FF;
   public static final IntValue RIVER_WIDTH_00E1FF;
   public static final DoubleValue RIVER_WIDTH_SCALE;
   public static final IntValue RIVER_MINIMUM_WIDTH_BLOCKS;
   public static final IntValue RIVER_GAP_BRIDGE_PIXELS;
   public static final DoubleValue RIVER_CHANNEL_CONTINENTALNESS;
   public static final IntValue RIVER_BANK_FADE_BLOCKS;
   public static final IntValue RIVER_MINIMUM_INLAND_BLOCKS;
   public static final IntValue RIVER_CHANNEL_EDGE_FADE_BLOCKS;
   public static final BooleanValue DESERT_WATER_REDUCTION_ENABLED;
   public static final DoubleValue DESERT_RIVER_WIDTH_SCALE;
   public static final IntValue DESERT_MINIMUM_RIVER_WIDTH_BLOCKS;
   public static final IntValue DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS;

   private EarthShapeServerConfig() {
   }

   static {
      Builder builder = new Builder();
      builder.push("map_scale");
      BLOCKS_PER_PIXEL = builder.comment("Minecraft blocks represented by one rivers.bmp pixel. 20 preserves the bundled map's 283840 x 141920 block size.")
         .defineInRange("blocksPerPixel", 20, 1, 4096);
      builder.pop();
      builder.push("layers");
      CONTINENTS_ENABLED = builder.comment("Use rivers.bmp as the continental land/ocean mask.").define("continentsEnabled", true);
      HEIGHTMAP_ENABLED = builder.comment("Use heightmap.bmp for terrain height and mountain relief.").define("heightmapEnabled", true);
      TERRAIN_BIOMES_ENABLED = builder.comment("Use terrain.bmp for detailed local land classes before selecting a climate-compatible biome.")
         .define("terrainBiomesEnabled", true);
      OCEAN_TEMPERATURE_ENABLED = builder.comment("Use earth_temperature.png only to select ocean biome temperatures.").define("oceanTemperatureEnabled", true);
      TUNDRA_TEMPERATURE_ENABLED = builder.comment("Use earth_temperature.png on sufficiently cold land to select tundra, taiga and snowy mountain biomes.")
         .define("tundraTemperatureEnabled", true);
      TUNDRA_TEMPERATURE_THRESHOLD = builder.comment(
            "Temperature cutoff for snowy land. Higher values expand tundra/snow coverage; default -0.25 replaces the former sparse -0.45 cutoff."
         )
         .defineInRange("tundraTemperatureThreshold", -0.25, -1.0, 1.0);
      TEMPERATURE_VERTICAL_SCALE = builder.comment(
            "Vertical expansion of earth_temperature.png around the equator. 1.12 keeps southern Africa in its intended warm band when using the expanded 6000x3400 world map."
         )
         .defineInRange("temperatureVerticalScale", 1.12, 0.75, 1.5);
      RIVER_BIOMES_ENABLED = builder.comment("Use blue rivers.bmp lines as real river biomes and suppress all other river biomes.")
         .define("riverBiomesEnabled", true);
      builder.pop();
      builder.push("terrain_shaping");
      COAST_HEIGHT_FADE_BLOCKS = builder.comment("Distance from an ocean coast over which heightmap relief rises from a negative shoreline grade.")
         .defineInRange("coastHeightFadeBlocks", 320, 20, 1024);
      RIVER_HEIGHT_FADE_BLOCKS = builder.comment("Distance from a source river bank over which heightmap relief rises from the water level.")
         .defineInRange("riverHeightFadeBlocks", 160, 20, 1024);
      HEIGHTMAP_MEDIAN = builder.comment(
            "Normalized heightmap midpoint. Values below receive negative relief; values above receive progressively stronger positive relief."
         )
         .defineInRange("heightmapMedian", 0.5, 0.05, 0.95);
      RIVER_MAXIMUM_DEPTH_BLOCKS = builder.comment("Maximum source-river bed drop in blocks before vanilla aquifer and surface generation.")
         .defineInRange("riverMaximumDepthBlocks", 6, 2, 32);
      builder.pop();
      builder.push("river_widths");
      RIVER_WIDTH_000064 = builder.comment("Width in blocks for rivers.bmp colour #000064.").defineInRange("color_000064", 27, 1, 256);
      RIVER_WIDTH_000096 = builder.comment("Width in blocks for rivers.bmp colour #000096.").defineInRange("color_000096", 22, 1, 256);
      RIVER_WIDTH_0000C8 = builder.comment("Width in blocks for rivers.bmp colour #0000C8.").defineInRange("color_0000C8", 17, 1, 256);
      RIVER_WIDTH_0000FF = builder.comment("Width in blocks for rivers.bmp colour #0000FF.").defineInRange("color_0000FF", 13, 1, 256);
      RIVER_WIDTH_0064FF = builder.comment("Width in blocks for rivers.bmp colour #0064FF.").defineInRange("color_0064FF", 10, 1, 256);
      RIVER_WIDTH_00C8FF = builder.comment("Width in blocks for rivers.bmp colour #00C8FF.").defineInRange("color_00C8FF", 7, 1, 256);
      RIVER_WIDTH_00E1FF = builder.comment("Width in blocks for rivers.bmp colour #00E1FF.").defineInRange("color_00E1FF", 5, 1, 256);
      RIVER_WIDTH_SCALE = builder.comment("Global multiplier for all source river widths. 0.5 compensates for source lines that already occupy several pixels.")
         .defineInRange("widthScale", 0.5, 0.05, 4.0);
      RIVER_MINIMUM_WIDTH_BLOCKS = builder.comment(
            "Minimum generated width for a source river. 8 blocks keeps small rivers continuous across 4-block biome samples."
         )
         .defineInRange("minimumWidthBlocks", 8, 1, 64);
      RIVER_GAP_BRIDGE_PIXELS = builder.comment(
            "Maximum missing source pixels joined only between similarly directed river strokes.  This repairs broken thin lines without joining nearby rivers."
         )
         .defineInRange("gapBridgePixels", 8, 0, 16);
      RIVER_CHANNEL_CONTINENTALNESS = builder.comment(
            "Continentalness at the center of a source-layer river. Lower values make a reliable shallow water channel before normal terrain generation."
         )
         .defineInRange("channelContinentalness", -0.42, -0.8, -0.05);
      RIVER_BANK_FADE_BLOCKS = builder.comment(
            "Physical block distance used to grade each river bank into surrounding terrain. This must not scale with map pixels, or rivers become oversized at large blocksPerPixel values."
         )
         .defineInRange("bankFadeBlocks", 48, 2, 128);
      RIVER_MINIMUM_INLAND_BLOCKS = builder.comment(
            "Required land margin on all four sides of a source river. Prevents shoreline strokes from turning small islands into river-only biomes."
         )
         .defineInRange("minimumInlandBlocks", 24, 4, 128);
      RIVER_CHANNEL_EDGE_FADE_BLOCKS = builder.comment(
            "Physical fade from a source river bed into its bank. A gradual value prevents vertical water-side cliffs."
         )
         .defineInRange("channelEdgeFadeBlocks", 32, 0, 128);
      builder.pop();
      builder.push("desert_water");
      DESERT_WATER_REDUCTION_ENABLED = builder.comment("Suppress generated surface lake features and minor source rivers in terrain.bmp desert areas.")
         .define("enabled", true);
      DESERT_RIVER_WIDTH_SCALE = builder.comment(
            "Width multiplier for source rivers crossing desert. Values above 0.30 are safely capped to prevent desert river networks becoming lakes."
         )
         .defineInRange("riverWidthScale", 0.2, 0.0, 1.0);
      DESERT_MINIMUM_RIVER_WIDTH_BLOCKS = builder.comment("Minimum pre-cap source river width allowed in desert. Smaller lines become ordinary desert terrain.")
         .defineInRange("minimumRiverWidthBlocks", 20, 1, 128);
      DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS = builder.comment(
            "Maximum final water width for a surviving desert river. Prevents broad source strokes and loops from becoming desert lakes."
         )
         .defineInRange("maximumRiverWidthBlocks", 18, 4, 64);
      builder.pop();
      SPEC = builder.build();
   }
}
