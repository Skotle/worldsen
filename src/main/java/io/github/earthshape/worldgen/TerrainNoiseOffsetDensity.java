package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

/**
 * Adds only continuous procedural relief. terrain.bmp may nominate a mountain range,
 * but never supplies elevation; all actual height comes from the noise below.
 */
public final class TerrainNoiseOffsetDensity implements DensityFunction {
   private static final MapCodec<TerrainNoiseOffsetDensity> DATA_CODEC = MapCodec.unit(new TerrainNoiseOffsetDensity());
   public static final KeyDispatchDataCodec<TerrainNoiseOffsetDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
   private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen() || !(Boolean)EarthShapeServerConfig.TERRAIN_NOISE_ENABLED.get()) return 0.0;
      int x = context.blockX();
      int z = context.blockZ();
      ColumnCache cache = this.columnCache.get();
      if (cache.x == x && cache.z == z) return cache.value;
      double land = RiversMask.INSTANCE.sampleLayerLand(x, z);
      if (land <= 0.0) {
         cache.set(x, z, 0.0);
         return 0.0;
      }

      // No layer-derived height floor, coastal ramp, or terrain flattening is applied
      // here.  Vanilla density noise remains the base terrain.  The terrain layer only
      // selects the *kind* of continuous relief to add for its biome family.
      ClimateLayers.TerrainKind terrain = ClimateLayers.INSTANCE.terrainKind(x, z);
      double rolling = valueNoise(x, z, 384, 0x42A17L) * 0.018 + valueNoise(x, z, 96, 0x9217BL) * 0.007;
      double rollingStrength = switch (terrain) {
         case WETLAND -> 0.15;
         case DESERT -> 0.45;
         case PLAINS -> 0.65;
         case FOREST, JUNGLE, HILLS -> 0.9;
         case MOUNTAIN -> 0.0;
         default -> 0.6;
      };

      double mountain = 0.0;
      if (terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
         // Build a broad highland first, then vary only its top.  The old 192-block
         // ridge multiplied the whole configured height at every local crest, making
         // mountain pixels rise as narrow walls and spires.  The terrain elevation
         // class controls the plateau level; low-frequency noise only shapes its
         // gentle summit and shallow saddles.
         double elevation = ClimateLayers.INSTANCE.mountainElevationWeight(x, z);
         double regionScale = ClimateLayers.INSTANCE.mountainRegionScale(x, z);
         double broadSummit = valueNoise(x, z, 896, 0x17C45L);
         double summitDetail = valueNoise(x, z, 320, 0x61A9DL);
         double plateau = 0.28 + 0.44 * elevation;
         double variation = broadSummit * (0.055 + 0.035 * elevation)
            + summitDetail * 0.018;
         mountain = Math.max(0.0, plateau + variation)
            * (double)RiversMask.INSTANCE.continentMountainMaximumHeightBlocks(x, z)
            / 192.0
            * regionScale;
      }
      double result = smoothstep(land) * (rolling * rollingStrength + mountain);
      cache.set(x, z, result);
      return result;
   }

   private static double valueNoise(int x, int z, int cellSize, long salt) {
      int x0 = Math.floorDiv(x, cellSize);
      int z0 = Math.floorDiv(z, cellSize);
      double tx = smoothstep((double)Math.floorMod(x, cellSize) / (double)cellSize);
      double tz = smoothstep((double)Math.floorMod(z, cellSize) / (double)cellSize);
      double top = lerp(gridValue(x0, z0, salt), gridValue(x0 + 1, z0, salt), tx);
      double bottom = lerp(gridValue(x0, z0 + 1, salt), gridValue(x0 + 1, z0 + 1, salt), tx);
      return lerp(top, bottom, tz);
   }

   private static double gridValue(int x, int z, long salt) {
      long value = salt ^ (long)x * 341873128712L ^ (long)z * 132897987541L;
      value ^= value >>> 33;
      value *= 0xff51afd7ed558ccdL;
      value ^= value >>> 33;
      value *= 0xc4ceb9fe1a85ec53L;
      value ^= value >>> 33;
      return (double)(value >>> 11 & 2097151L) / 1048575.5 - 1.0;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double lerp(double a, double b, double amount) {
      return a + (b - a) * amount;
   }

   public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
   public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
   public double minValue() { return -0.02; }
   public double maxValue() { return 1.5; }
   public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }

   private static final class ColumnCache {
      private int x = Integer.MIN_VALUE;
      private int z = Integer.MIN_VALUE;
      private double value;
      void set(int x, int z, double value) {
         this.x = x;
         this.z = z;
         this.value = value;
      }
   }
}
