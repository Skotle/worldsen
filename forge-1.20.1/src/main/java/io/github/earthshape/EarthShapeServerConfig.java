package io.github.earthshape;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuration used by the coastline raster adapter. */
public final class EarthShapeServerConfig {
   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue BLOCKS_PER_PIXEL;
   public static final ForgeConfigSpec.BooleanValue RANDOM_MAP_CENTER_ENABLED;
   public static final ForgeConfigSpec.IntValue RANDOM_MAP_CENTER_MIN_X;
   public static final ForgeConfigSpec.IntValue RANDOM_MAP_CENTER_MAX_X;
   public static final ForgeConfigSpec.IntValue RANDOM_MAP_CENTER_MIN_Z;
   public static final ForgeConfigSpec.IntValue RANDOM_MAP_CENTER_MAX_Z;
   public static final ForgeConfigSpec.DoubleValue SNOW_TEMPERATURE_THRESHOLD;
   public static final ForgeConfigSpec.IntValue COAST_HEIGHT_FADE_BLOCKS;
   public static final ForgeConfigSpec.IntValue COAST_SHALLOW_SHELF_WIDTH_BLOCKS;
   public static final ForgeConfigSpec.IntValue COAST_SHELF_TRANSITION_BLOCKS;
   public static final ForgeConfigSpec.IntValue COAST_SHELF_DEEP_FLOOR_Y;
   public static final ForgeConfigSpec.IntValue RIVER_MAXIMUM_DEPTH_BLOCKS;
   public static final ForgeConfigSpec.IntValue RIVER_WIDTH_000064;
   public static final ForgeConfigSpec.IntValue RIVER_WIDTH_000096;
   public static final ForgeConfigSpec.IntValue RIVER_WIDTH_0000C8;
   public static final ForgeConfigSpec.IntValue RIVER_WIDTH_0000FF;
   public static final ForgeConfigSpec.IntValue RIVER_WIDTH_0064FF;
   public static final ForgeConfigSpec.IntValue RIVER_WIDTH_00C8FF;
   public static final ForgeConfigSpec.IntValue RIVER_WIDTH_00E1FF;
   public static final ForgeConfigSpec.DoubleValue RIVER_WIDTH_SCALE;
   public static final ForgeConfigSpec.IntValue RIVER_GAP_BRIDGE_PIXELS;
   public static final ForgeConfigSpec.IntValue RIVER_BANK_FADE_BLOCKS;
   public static final ForgeConfigSpec.IntValue RIVER_CHANNEL_EDGE_FADE_BLOCKS;

   static {
      ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
      builder.push("map_scale");
      BLOCKS_PER_PIXEL = builder.defineInRange("blocksPerPixel", 20, 1, 4096);
      RANDOM_MAP_CENTER_ENABLED = builder.define("randomCenterEnabled", false);
      RANDOM_MAP_CENTER_MIN_X = builder.defineInRange("randomCenterMinLayerX", 0, 0, 5999);
      RANDOM_MAP_CENTER_MAX_X = builder.defineInRange("randomCenterMaxLayerX", 5999, 0, 5999);
      RANDOM_MAP_CENTER_MIN_Z = builder.defineInRange("randomCenterMinLayerZ", 0, 0, 3399);
      RANDOM_MAP_CENTER_MAX_Z = builder.defineInRange("randomCenterMaxLayerZ", 3399, 0, 3399);
      SNOW_TEMPERATURE_THRESHOLD = builder.defineInRange("snowTemperatureThreshold", -0.625, -1.0, 0.0);
      builder.pop();
      builder.push("coastline");
      COAST_HEIGHT_FADE_BLOCKS = builder.defineInRange("coastHeightFadeBlocks", 320, 20, 1024);
      COAST_SHALLOW_SHELF_WIDTH_BLOCKS = builder.defineInRange("coastShallowShelfWidthBlocks", 6, 4, 8);
      COAST_SHELF_TRANSITION_BLOCKS = builder.defineInRange("coastShelfTransitionBlocks", 16, 4, 128);
      COAST_SHELF_DEEP_FLOOR_Y = builder.defineInRange("coastShelfDeepFloorY", 51, -64, 61);
      builder.pop();
      builder.push("river_mask");
      RIVER_MAXIMUM_DEPTH_BLOCKS = builder.defineInRange("riverMaximumDepthBlocks", 6, 1, 32);
      RIVER_WIDTH_000064 = builder.defineInRange("color_000064", 27, 1, 256);
      RIVER_WIDTH_000096 = builder.defineInRange("color_000096", 22, 1, 256);
      RIVER_WIDTH_0000C8 = builder.defineInRange("color_0000C8", 17, 1, 256);
      RIVER_WIDTH_0000FF = builder.defineInRange("color_0000FF", 13, 1, 256);
      RIVER_WIDTH_0064FF = builder.defineInRange("color_0064FF", 10, 1, 256);
      RIVER_WIDTH_00C8FF = builder.defineInRange("color_00C8FF", 7, 1, 256);
      RIVER_WIDTH_00E1FF = builder.defineInRange("color_00E1FF", 5, 1, 256);
      RIVER_WIDTH_SCALE = builder.defineInRange("widthScale", 0.5, 0.05, 4.0);
      RIVER_GAP_BRIDGE_PIXELS = builder.defineInRange("gapBridgePixels", 4, 0, 4);
      RIVER_BANK_FADE_BLOCKS = builder.defineInRange("bankFadeBlocks", 48, 2, 128);
      RIVER_CHANNEL_EDGE_FADE_BLOCKS = builder.defineInRange("channelEdgeFadeBlocks", 32, 0, 128);
      builder.pop();
      SPEC = builder.build();
   }

   private EarthShapeServerConfig() {
   }
}
