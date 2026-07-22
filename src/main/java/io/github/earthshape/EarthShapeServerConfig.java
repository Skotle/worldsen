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
   public static final DoubleValue HIGH_ALTITUDE_SNOW_MAX_TEMPERATURE;
   public static final DoubleValue SNOW_TEMPERATURE_THRESHOLD;
   public static final DoubleValue TAIGA_TEMPERATURE_THRESHOLD;
   public static final DoubleValue TEMPERATURE_VERTICAL_SCALE;
   public static final DoubleValue TEMPERATURE_GLOBAL_WEIGHT;
   public static final DoubleValue TEMPERATURE_GLOBAL_OFFSET;
   public static final DoubleValue TEMPERATURE_WEIGHT_POLAR;
   public static final DoubleValue TEMPERATURE_WEIGHT_COLD;
   public static final DoubleValue TEMPERATURE_WEIGHT_COOL;
   public static final DoubleValue TEMPERATURE_WEIGHT_TEMPERATE;
   public static final DoubleValue TEMPERATURE_WEIGHT_MILD;
   public static final DoubleValue TEMPERATURE_WEIGHT_WARM;
   public static final DoubleValue TEMPERATURE_WEIGHT_HOT;
   public static final DoubleValue TEMPERATURE_WEIGHT_TROPICAL;
   public static final DoubleValue TEMPERATURE_WEIGHT_EQUATORIAL;
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
   public static final BooleanValue RIVER_FEATURE_PROTECTION_ENABLED;
   public static final BooleanValue DESERT_WATER_REDUCTION_ENABLED;
   public static final DoubleValue DESERT_RIVER_WIDTH_SCALE;
   public static final IntValue DESERT_MINIMUM_RIVER_WIDTH_BLOCKS;
   public static final IntValue DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS;

   private EarthShapeServerConfig() {
   }

   static {
      Builder builder = new Builder();
      builder.push("map_scale");
      BLOCKS_PER_PIXEL = builder.comment("rivers.bmp의 1픽셀이 나타내는 마인크래프트 블록 수입니다. 기본값 20은 기본 지도 크기(283840 x 141920)를 유지합니다.")
              .defineInRange("blocksPerPixel", 20, 1, 4096);
      builder.pop();
      builder.push("layers");
      CONTINENTS_ENABLED = builder.comment("worldmap_river.png을 육지/해양 경계 마스크로 사용합니다.").define("continentsEnabled", true);
      HEIGHTMAP_ENABLED = builder.comment("heightmap.bmp를 지형 높이와 산악 기복에 적용합니다.").define("heightmapEnabled", true);
      TERRAIN_BIOMES_ENABLED = builder.comment("terrain.bmp의 지상 지형 분류를 우선 적용한 뒤 기후에 맞는 세부 바이옴을 선택합니다.")
         .define("terrainBiomesEnabled", true);
      OCEAN_TEMPERATURE_ENABLED = builder.comment("earth_temperature.png의 색상으로 해양 온도 바이옴을 결정합니다.").define("oceanTemperatureEnabled", true);
      TUNDRA_TEMPERATURE_ENABLED = builder.comment("활성화하면 온도맵의 매우 추운 지대에서 툰드라·타이가·설산 계열을 선택할 수 있습니다.")
         .define("tundraTemperatureEnabled", true);
      TUNDRA_TEMPERATURE_THRESHOLD = builder.comment(
            "툰드라/설원 기후 판정 온도입니다. 값을 높이면 추운 바이옴 범위가 넓어집니다."
         )
         .defineInRange("tundraTemperatureThreshold", -0.25, -1.0, 1.0);
      SNOW_ALTITUDE_BLOCKS = builder.comment(
            "온도맵과 무관하게 고지대 눈을 허용하는 지표 Y 높이입니다."
         )
         .defineInRange("snowAltitudeBlocks", 160, 64, 320);
      HIGH_ALTITUDE_SNOW_MAX_TEMPERATURE = builder.comment(
            "고지대 눈을 허용하는 온도맵 최대값입니다. 기본값 0.75는 약 30°C 경계이며, 열대 고산 지대의 부자연스러운 눈을 막습니다."
         )
         .defineInRange("highAltitudeSnowMaxTemperature", 0.75, -1.0, 1.0);
      SNOW_TEMPERATURE_THRESHOLD = builder.comment(
            "고지대가 아닌 곳에 눈을 허용하는 온도맵 기준입니다. 기본값 -0.625는 보라·파랑 영하 색상 구간입니다."
         )
         .defineInRange("snowTemperatureThreshold", -0.625, -1.0, 0.0);
      TAIGA_TEMPERATURE_THRESHOLD = builder.comment(
            "설원과 일반 온대 사이의 타이가/침엽수림 전이대 온도 기준입니다. 값을 높이면 고위도 생물군계 범위가 넓어집니다."
         )
         .defineInRange("taigaTemperatureThreshold", -0.15, -1.0, 0.5);
      TEMPERATURE_VERTICAL_SCALE = builder.comment(
            "온도맵의 적도 기준 세로 보정 배율입니다. 1.12는 확장 세계지도에서 남부 아프리카의 온대/고온대를 맞춥니다."
         )
         .defineInRange("temperatureVerticalScale", 1.12, 0.75, 1.5);
      builder.push("temperature_weights");
      TEMPERATURE_GLOBAL_WEIGHT = builder.comment(
            "온도맵이 전 세계 기후에 미치는 전체 가중치입니다. 0은 위도 기반, 1은 원본 온도맵, 1보다 크면 온도맵 색상 차이를 더 강하게 적용합니다."
         )
         .defineInRange("globalMapWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_GLOBAL_OFFSET = builder.comment(
            "전 세계 온도 보정값입니다. 양수는 전체적으로 따뜻하게, 음수는 전체적으로 춥게 만듭니다."
         )
         .defineInRange("globalTemperatureOffset", 0.0, -1.0, 1.0);
      TEMPERATURE_WEIGHT_POLAR = builder.comment("온도맵 보라색 극지 구간의 영향 가중치입니다.").defineInRange("polarWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_COLD = builder.comment("온도맵 파란색 한랭 구간의 영향 가중치입니다.").defineInRange("coldWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_COOL = builder.comment("온도맵 하늘색 냉량 구간의 영향 가중치입니다.").defineInRange("coolWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_TEMPERATE = builder.comment("온도맵 청록색 온대 구간의 영향 가중치입니다.").defineInRange("temperateWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_MILD = builder.comment("온도맵 녹색 온난 구간의 영향 가중치입니다.").defineInRange("mildWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_WARM = builder.comment("온도맵 노란색 고온 구간의 영향 가중치입니다.").defineInRange("warmWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_HOT = builder.comment("온도맵 주황색 더운 구간의 영향 가중치입니다.").defineInRange("hotWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_TROPICAL = builder.comment("온도맵 주황빨강 열대 구간의 영향 가중치입니다.").defineInRange("tropicalWeight", 1.0, 0.0, 3.0);
      TEMPERATURE_WEIGHT_EQUATORIAL = builder.comment("온도맵 빨간색 적도 구간의 영향 가중치입니다.").defineInRange("equatorialWeight", 1.0, 0.0, 3.0);
      builder.pop();
      RIVER_BIOMES_ENABLED = builder.comment("강 레이어의 파란 선만 실제 강으로 사용하고, 일반 월드젠 강 바이옴은 억제합니다.")
         .define("riverBiomesEnabled", true);
      BIOME_BOUNDARY_WARP_ENABLED = builder.comment(
            "긴 직선형 바이옴 경계를 완만하게 보정합니다. 강과 해안선 원본 좌표는 바꾸지 않습니다."
         )
         .define("biomeBoundaryWarpEnabled", true);
      BIOME_BOUNDARY_WARP_BLOCKS = builder.comment(
            "온도에 따른 세부 바이옴 경계의 최대 옆방향 보정 폭(블록)입니다. terrain/trees 레이어 영역을 넘지 않도록 제한됩니다."
         )
         .defineInRange("biomeBoundaryWarpBlocks", 12, 0, 64);
      builder.pop();
      builder.push("terrain_shaping");
      COAST_HEIGHT_FADE_BLOCKS = builder.comment("해안에서 내륙 높이맵 기복으로 부드럽게 올라가는 거리(블록)입니다.")
         .defineInRange("coastHeightFadeBlocks", 320, 20, 1024);
      RIVER_HEIGHT_FADE_BLOCKS = builder.comment("강둑에서 주변 지형 높이로 부드럽게 올라가는 거리(블록)입니다.")
         .defineInRange("riverHeightFadeBlocks", 160, 20, 1024);
      HEIGHTMAP_MEDIAN = builder.comment(
            "높이맵의 해수면 기준 중앙값입니다. 낮추면 고지대가 늘고, 높이면 평지가 늘어납니다."
         )
         .defineInRange("heightmapMedian", 0.5, 0.05, 0.95);
      RIVER_MAXIMUM_DEPTH_BLOCKS = builder.comment("레이어 강 바닥이 주변 지형보다 낮아질 수 있는 최대 깊이(블록)입니다.")
         .defineInRange("riverMaximumDepthBlocks", 6, 2, 32);
      builder.pop();
      builder.push("river_widths");
      RIVER_WIDTH_000064 = builder.comment("강 색상 #000064의 기본 폭(블록)입니다.").defineInRange("color_000064", 27, 1, 256);
      RIVER_WIDTH_000096 = builder.comment("강 색상 #000096의 기본 폭(블록)입니다.").defineInRange("color_000096", 22, 1, 256);
      RIVER_WIDTH_0000C8 = builder.comment("강 색상 #0000C8의 기본 폭(블록)입니다.").defineInRange("color_0000C8", 17, 1, 256);
      RIVER_WIDTH_0000FF = builder.comment("강 색상 #0000FF의 기본 폭(블록)입니다.").defineInRange("color_0000FF", 13, 1, 256);
      RIVER_WIDTH_0064FF = builder.comment("강 색상 #0064FF의 기본 폭(블록)입니다.").defineInRange("color_0064FF", 10, 1, 256);
      RIVER_WIDTH_00C8FF = builder.comment("강 색상 #00C8FF의 기본 폭(블록)입니다.").defineInRange("color_00C8FF", 7, 1, 256);
      RIVER_WIDTH_00E1FF = builder.comment("강 색상 #00E1FF의 기본 폭(블록)입니다.").defineInRange("color_00E1FF", 5, 1, 256);
      RIVER_WIDTH_SCALE = builder.comment("모든 강 폭에 적용되는 배율입니다. 원본 선이 여러 픽셀일 수 있어 기본값은 0.5입니다.")
         .defineInRange("widthScale", 0.5, 0.05, 4.0);
      RIVER_MINIMUM_WIDTH_BLOCKS = builder.comment(
            "작은 강이 4블록 바이옴 샘플 사이에서 끊기지 않게 하는 최소 폭(블록)입니다."
         )
         .defineInRange("minimumWidthBlocks", 8, 1, 64);
      RIVER_GAP_BRIDGE_PIXELS = builder.comment(
            "끊긴 원본 강 선을 잇는 최대 픽셀 수입니다. 독립 강줄기 오접속을 막기 위해 2 이하를 권장합니다."
         )
         .defineInRange("gapBridgePixels", 2, 0, 2);
      RIVER_CHANNEL_CONTINENTALNESS = builder.comment(
            "레이어 강 중심의 대륙성 값입니다. 더 낮으면 강 수로가 더 안정적으로 파입니다."
         )
         .defineInRange("channelContinentalness", -0.42, -0.8, -0.05);
      RIVER_BANK_FADE_BLOCKS = builder.comment(
            "강둑을 주변 지형으로 완만하게 연결하는 실제 거리(블록)입니다."
         )
         .defineInRange("bankFadeBlocks", 48, 2, 128);
      RIVER_MINIMUM_INLAND_BLOCKS = builder.comment(
            "강으로 판정되기 위해 필요한 최소 내륙 여백(블록)입니다. 해안선이 강으로 잘못 바뀌는 것을 막습니다."
         )
         .defineInRange("minimumInlandBlocks", 24, 4, 128);
      RIVER_CHANNEL_EDGE_FADE_BLOCKS = builder.comment(
            "강 바닥에서 둑으로 이어지는 완화 거리(블록)입니다. 높일수록 물가 절벽이 줄어듭니다."
         )
         .defineInRange("channelEdgeFadeBlocks", 32, 0, 128);
      RIVER_FEATURE_PROTECTION_ENABLED = builder.comment(
            "레이어 강 중심과 둑에서 일반 지상 피처(나무·암석·잡초 등)를 막아 수로 내부의 지저분한 구조 생성을 방지합니다."
         )
         .define("featureProtectionEnabled", true);
      builder.pop();
      builder.push("surface_water");
      DESERT_WATER_REDUCTION_ENABLED = builder.comment(
            "맵 육지에서 강 레이어 외의 지상 물웅덩이·호수 생성을 막습니다. 해양과 지하수는 유지됩니다."
         )
         .define("enabled", true);
      DESERT_RIVER_WIDTH_SCALE = builder.comment(
            "사막을 지나는 레이어 강의 폭 배율입니다. 큰 값은 사막 강이 호수처럼 넓어지는 원인이 됩니다."
         )
         .defineInRange("riverWidthScale", 0.2, 0.0, 1.0);
      DESERT_MINIMUM_RIVER_WIDTH_BLOCKS = builder.comment("사막에서 강으로 유지되는 원본 선의 최소 폭입니다. 이보다 작으면 일반 사막 지형으로 처리됩니다.")
         .defineInRange("minimumRiverWidthBlocks", 20, 1, 128);
      DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS = builder.comment(
            "사막 강의 최종 최대 폭입니다. 넓은 선·고리 모양이 사막 호수가 되는 것을 막습니다."
         )
         .defineInRange("maximumRiverWidthBlocks", 18, 4, 64);
      builder.pop();
      SPEC = builder.build();
   }
}
