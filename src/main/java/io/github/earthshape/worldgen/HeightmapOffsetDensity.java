package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.HeightmapLayer;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

public final class HeightmapOffsetDensity implements DensityFunction {
   private static final MapCodec<HeightmapOffsetDensity> DATA_CODEC = MapCodec.unit(new HeightmapOffsetDensity());
   public static final KeyDispatchDataCodec<HeightmapOffsetDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   public double compute(FunctionContext context) {
      if (!EarthShapeCompatibility.disablesWorldgen() && (Boolean)EarthShapeServerConfig.HEIGHTMAP_ENABLED.get()) {
         double elevation = HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ());
         double inland = RiversMask.INSTANCE.sampleHeightmapInlandness(context.blockX(), context.blockZ());
         double riverRelief = RiversMask.INSTANCE.sampleRiverReliefFactor(context.blockX(), context.blockZ());
         // Preserve the original height-map slope.  River relief can only be removed
         // where the source terrain is gentle; steep source pixels retain their shape
         // instead of becoming a wide, artificial terrace or canyon wall.
         double carvedRelief = 1.0 - riverRelief;
         carvedRelief *= slopeDamping(context.blockX(), context.blockZ());
         riverRelief = 1.0 - carvedRelief;
         double median = (Double)EarthShapeServerConfig.HEIGHTMAP_MEDIAN.get();
         // Rivers may soften lowland terrain, but must not erase the actual mountain
         // relief from the source height map.  Restore that relief smoothly in highlands.
         double highlandProtection = smoothstep((elevation - (median + 0.14)) / 0.20);
         riverRelief = riverRelief + (1.0 - riverRelief) * highlandProtection;
         double deviation = elevation - median;
         double lowland = Math.max(0.0, deviation) * 0.16;
         double mountain = Math.max(0.0, Math.min(1.0, deviation / Math.max(0.01, 1.0 - median)));
         double highlandLift = mountain * mountain * 0.24 + mountain * mountain * mountain * mountain * 0.3;
         // The source height map intentionally keeps broad lowlands close to a common
         // elevation.  Add only a small, continuous two-scale relief there so a plain
         // does not stay perfectly flat for hundreds of blocks.  It is suppressed at
         // coasts, in river corridors, and as the source terrain becomes mountainous.
         double plainness = 1.0 - smoothstep((deviation - 0.10) / 0.18);
         double rollingRelief = plainness * (0.008 + valueNoise(context.blockX(), context.blockZ(), 224, 0x6A09E667F3BCC909L) * 0.014
            + valueNoise(context.blockX(), context.blockZ(), 64, 0xBB67AE8584CAA73BL) * 0.005);
         return inland * riverRelief * (lowland + highlandLift + rollingRelief);
      } else {
         return 0.0;
      }
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(this);
   }

   public double minValue() {
      return 0.0;
   }

   public double maxValue() {
      return 0.95;
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }

   private static double smoothstep(double value) {
      value = Math.max(0.0, Math.min(1.0, value));
      return value * value * (3.0 - 2.0 * value);
   }

   private static double slopeDamping(int x, int z) {
      double slope = Math.abs(HeightmapLayer.INSTANCE.sample(x + 1, z) - HeightmapLayer.INSTANCE.sample(x - 1, z))
         + Math.abs(HeightmapLayer.INSTANCE.sample(x, z + 1) - HeightmapLayer.INSTANCE.sample(x, z - 1));
      // Height-map values are normalized; convert their local difference back to
      // source-pixel magnitude before applying the documented 1 / (1 + slope * 0.5).
      return 1.0 / (1.0 + slope * 255.0 * 0.5);
   }

   /**
    * Smooth deterministic value noise in the [0, 1] range.  Unlike per-column
    * randomness this interpolates across cell borders, so the added plain relief
    * remains broad and natural rather than creating a block or chunk grid.
    */
   private static double valueNoise(int x, int z, int cellSize, long seed) {
      int x0 = Math.floorDiv(x, cellSize);
      int z0 = Math.floorDiv(z, cellSize);
      double fx = (double)Math.floorMod(x, cellSize) / (double)cellSize;
      double fz = (double)Math.floorMod(z, cellSize) / (double)cellSize;
      double sx = smoothstep(fx);
      double sz = smoothstep(fz);
      double north = lerp(gridValue(x0, z0, seed), gridValue(x0 + 1, z0, seed), sx);
      double south = lerp(gridValue(x0, z0 + 1, seed), gridValue(x0 + 1, z0 + 1, seed), sx);
      return lerp(north, south, sz);
   }

   private static double gridValue(int x, int z, long seed) {
      long value = seed ^ (long)x * 341873128712L ^ (long)z * 132897987541L;
      value ^= value >>> 33;
      value *= -49064778989728563L;
      value ^= value >>> 33;
      value *= -4265267296055464877L;
      value ^= value >>> 33;
      return (double)(value & 0x1FFFFFL) / 2097151.0;
   }

   private static double lerp(double from, double to, double amount) {
      return from + (to - from) * amount;
   }
}
