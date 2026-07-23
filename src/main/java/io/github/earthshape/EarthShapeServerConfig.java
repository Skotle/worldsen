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
   public static final IntValue SNOW_ALTITUDE_BLOCKS;
   public static final DoubleValue SNOW_TEMPERATURE_THRESHOLD;
   public static final DoubleValue TEMPERATURE_VERTICAL_SCALE;
   public static final BooleanValue RIVER_BIOMES_ENABLED;
   public static final BooleanValue BIOME_BOUNDARY_WARP_ENABLED;
   public static final IntValue BIOME_BOUNDARY_WARP_BLOCKS;
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
      BLOCKS_PER_PIXEL = builder.comment("rivers.bmp 픽셀 하나에 해당하는 마인크래프트 블록 수. 20으로 설정하면 기본 제공 맵의 283840 x 141920 블록 크기가 유지됩니다.")
              .defineInRange("blocksPerPixel", 20, 1, 4096);
      builder.pop();
      builder.push("layers");
      CONTINENTS_ENABLED = builder.comment("rivers.bmp를 대륙(육지/바다) 마스크로 사용합니다.").define("continentsEnabled", true);
      HEIGHTMAP_ENABLED = builder.comment("heightmap.bmp를 지형 높이와 산맥 기복에 사용합니다.").define("heightmapEnabled", true);
      TERRAIN_BIOMES_ENABLED = builder.comment("기후에 맞는 바이옴을 선택하기 전, terrain.bmp를 세부 지역 지형 분류에 사용합니다.")
              .define("terrainBiomesEnabled", true);
      OCEAN_TEMPERATURE_ENABLED = builder.comment("해양 바이옴의 온도를 선택할 때만 earth_temperature.png를 사용합니다.").define("oceanTemperatureEnabled", true);
      TUNDRA_TEMPERATURE_ENABLED = builder.comment("충분히 추운 육지에서 툰드라, 타이가, 설산 바이옴을 선택할 때 earth_temperature.png를 사용합니다.")
              .define("tundraTemperatureEnabled", true);
      TUNDRA_TEMPERATURE_THRESHOLD = builder.comment(
                      "눈 덮인 땅으로 분류되는 온도 기준값. 값이 높을수록 툰드라/눈 범위가 넓어집니다. 기본값 -0.25는 기존의 -0.45(적은 범위)를 대체합니다."
              )
              .defineInRange("tundraTemperatureThreshold", -0.25, -1.0, 1.0);
      SNOW_ALTITUDE_BLOCKS = builder.comment("온도 맵과 무관하게 고지대 바이옴에 눈이 내릴 수 있는 지표면 Y 레벨.")
              .defineInRange("snowAltitudeBlocks", 160, 64, 320);
      SNOW_TEMPERATURE_THRESHOLD = builder.comment(
                      "고지대 설선 아래에서 눈이 내리는 온도 맵 기준값. -0.625는 보라색과 파란색의 영하 구간에 해당합니다."
              )
              .defineInRange("snowTemperatureThreshold", -0.625, -1.0, 0.0);
      TEMPERATURE_VERTICAL_SCALE = builder.comment(
                      "적도 부근에서 earth_temperature.png를 수직으로 확장하는 비율. 1.12는 확장된 6000x3400 월드 맵을 사용할 때 남아프리카가 의도된 온난대에 유지되도록 합니다."
              )
              .defineInRange("temperatureVerticalScale", 1.12, 0.75, 1.5);
      RIVER_BIOMES_ENABLED = builder.comment("rivers.bmp의 파란 선을 실제 강 바이옴으로 사용하고, 그 외 모든 강 바이옴은 억제합니다.")
              .define("riverBiomesEnabled", true);
      BIOME_BOUNDARY_WARP_ENABLED = builder.comment(
                      "긴 비트맵 바이옴 경계가 직선으로 생성되지 않도록 지형, 나무, 온도 레이어 샘플링을 부드럽게 휘어줍니다. 강과 해안선은 정확하게 유지됩니다."
              )
              .define("biomeBoundaryWarpEnabled", true);
      BIOME_BOUNDARY_WARP_BLOCKS = builder.comment(
                      "온도 변형 바이옴 경계의 최대 좌우 이동 거리. terrain.bmp와 trees.bmp 영역을 넘지 않도록 원본 맵 픽셀 1개 미만으로 제한됩니다."
              )
              .defineInRange("biomeBoundaryWarpBlocks", 12, 0, 64);
      builder.pop();
      builder.push("terrain_shaping");
      COAST_HEIGHT_FADE_BLOCKS = builder.comment("해안선의 음(-)의 경사에서 heightmap 기복이 상승하기 시작하는 해안으로부터의 거리.")
              .defineInRange("coastHeightFadeBlocks", 320, 20, 1024);
      RIVER_HEIGHT_FADE_BLOCKS = builder.comment("수면 높이에서 heightmap 기복이 상승하기 시작하는 원본 강둑으로부터의 거리.")
              .defineInRange("riverHeightFadeBlocks", 160, 20, 1024);
      HEIGHTMAP_MEDIAN = builder.comment(
                      "정규화된 heightmap 중간값. 이 값보다 낮으면 음(-)의 기복을, 높으면 점점 더 강한 양(+)의 기복을 받습니다."
              )
              .defineInRange("heightmapMedian", 0.5, 0.05, 0.95);
      RIVER_MAXIMUM_DEPTH_BLOCKS = builder.comment("바닐라 대수층 및 지표 생성 이전, 원본 강 바닥이 내려가는 최대 블록 수.")
              .defineInRange("riverMaximumDepthBlocks", 6, 2, 32);
      builder.pop();
      builder.push("river_widths");
      RIVER_WIDTH_000064 = builder.comment("rivers.bmp 색상 #000064의 강 너비(블록 단위).").defineInRange("color_000064", 27, 1, 256);
      RIVER_WIDTH_000096 = builder.comment("rivers.bmp 색상 #000096의 강 너비(블록 단위).").defineInRange("color_000096", 22, 1, 256);
      RIVER_WIDTH_0000C8 = builder.comment("rivers.bmp 색상 #0000C8의 강 너비(블록 단위).").defineInRange("color_0000C8", 17, 1, 256);
      RIVER_WIDTH_0000FF = builder.comment("rivers.bmp 색상 #0000FF의 강 너비(블록 단위).").defineInRange("color_0000FF", 13, 1, 256);
      RIVER_WIDTH_0064FF = builder.comment("rivers.bmp 색상 #0064FF의 강 너비(블록 단위).").defineInRange("color_0064FF", 10, 1, 256);
      RIVER_WIDTH_00C8FF = builder.comment("rivers.bmp 색상 #00C8FF의 강 너비(블록 단위).").defineInRange("color_00C8FF", 7, 1, 256);
      RIVER_WIDTH_00E1FF = builder.comment("rivers.bmp 색상 #00E1FF의 강 너비(블록 단위).").defineInRange("color_00E1FF", 5, 1, 256);
      RIVER_WIDTH_SCALE = builder.comment("모든 원본 강 너비에 적용되는 전역 배율. 0.5는 이미 여러 픽셀을 차지하는 원본 선을 보정합니다.")
              .defineInRange("widthScale", 0.5, 0.05, 4.0);
      RIVER_MINIMUM_WIDTH_BLOCKS = builder.comment(
                      "원본 강의 최소 생성 너비. 12블록으로 설정하면 4블록 단위 바이옴 샘플에서도 작은 강이 끊기지 않고 이어집니다."
              )
              .defineInRange("minimumWidthBlocks", 12, 1, 64);
      RIVER_GAP_BRIDGE_PIXELS = builder.comment(
                      "비슷한 방향의 강 선 사이에서 연결할 수 있는 최대 누락 원본 픽셀 수. 인접한 독립된 강끼리 연결되지 않도록 2 이하로 유지하세요."
              )
              .defineInRange("gapBridgePixels", 2, 0, 2);
      RIVER_CHANNEL_CONTINENTALNESS = builder.comment(
                      "원본 레이어 강 중심의 대륙성(continentalness) 값. 값이 낮을수록 일반 지형 생성 이전에 안정적인 얕은 수로가 형성됩니다."
              )
              .defineInRange("channelContinentalness", -0.42, -0.8, -0.05);
      RIVER_BANK_FADE_BLOCKS = builder.comment(
                      "각 강둑을 주변 지형으로 자연스럽게 경사지게 만드는 실제 블록 거리. 맵 픽셀에 비례해서는 안 되며, 그렇지 않으면 blocksPerPixel 값이 클 때 강이 과도하게 커집니다."
              )
              .defineInRange("bankFadeBlocks", 48, 2, 128);
      RIVER_MINIMUM_INLAND_BLOCKS = builder.comment(
                      "원본 강의 네 방향 모두에 필요한 육지 여백. 해안선 선이 작은 섬을 강 전용 바이옴으로 만드는 것을 방지합니다."
              )
              .defineInRange("minimumInlandBlocks", 24, 4, 128);
      RIVER_CHANNEL_EDGE_FADE_BLOCKS = builder.comment(
                      "원본 강바닥에서 강둑으로 이어지는 실제 페이드 거리. 점진적인 값을 사용하면 수직으로 깎인 물가 절벽을 방지할 수 있습니다."
              )
              .defineInRange("channelEdgeFadeBlocks", 32, 0, 128);
      builder.pop();
      builder.push("surface_water");
      DESERT_WATER_REDUCTION_ENABLED = builder.comment(
                      "강 레이어에 속하지 않는 한, 맵에 지정된 육지에서 생성되는 지표수 지형을 제거합니다. 맵에 지정된 바다와 지하수는 그대로 유지됩니다."
              )
              .define("enabled", true);
      DESERT_RIVER_WIDTH_SCALE = builder.comment(
                      "사막을 가로지르는 원본 강의 너비 배율. 0.30을 초과하는 값은 사막 강 네트워크가 호수가 되는 것을 방지하기 위해 안전하게 제한됩니다."
              )
              .defineInRange("riverWidthScale", 0.2, 0.0, 1.0);
      DESERT_MINIMUM_RIVER_WIDTH_BLOCKS = builder.comment("사막에서 허용되는, 제한 적용 전 최소 원본 강 너비. 이보다 작은 선은 일반 사막 지형이 됩니다.")
              .defineInRange("minimumRiverWidthBlocks", 20, 1, 128);
      DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS = builder.comment(
                      "살아남은 사막 강의 최종 최대 수면 너비. 넓은 원본 선과 루프가 사막 호수가 되는 것을 방지합니다."
              )
              .defineInRange("maximumRiverWidthBlocks", 18, 4, 64);
      builder.pop();
      SPEC = builder.build();
   }
}