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

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen() || !(Boolean)EarthShapeServerConfig.TERRAIN_NOISE_ENABLED.get()) return 0.0;
      int x = context.blockX();
      int z = context.blockZ();
      double land = RiversMask.INSTANCE.sampleLayerLand(x, z);
      if (land <= 0.0) return 0.0;

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
         // Mountain colour permits a mountain range, but its height and silhouette
         // still come from continuous procedural noise rather than image brightness.
         double ridge = 0.20 + 0.80 * (valueNoise(x, z, 192, 0x17C45L) + 1.0) * 0.5;
         mountain = ridge * (double)EarthShapeServerConfig.MOUNTAIN_NOISE_MAXIMUM_HEIGHT_BLOCKS.get() * (3.0 / 384.0);
      }
      return smoothstep(land) * (rolling * rollingStrength + mountain);
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
}
