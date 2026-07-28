package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

/**
 * Gives source-layer water a shallow floor and grades only the immediate land bank.
 * It never injects water above vanilla sea level; the normal aquifer remains responsible
 * for water placement.
 */
public final class LayerWaterlineDensity implements DensityFunction {
   private static final MapCodec<LayerWaterlineDensity> DATA_CODEC = MapCodec.unit(new LayerWaterlineDensity());
   public static final KeyDispatchDataCodec<LayerWaterlineDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
   private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen() || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         return 0.0;
      }

      if (!(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()) return 0.0;
      int x = context.blockX();
      int z = context.blockZ();
      ColumnCache cache = this.columnCache.get();
      if (cache.x != x || cache.z != z) {
         cache.x = x;
         cache.z = z;
         cache.width = RiversMask.INSTANCE.hasInlandRiverInfluence(x, z) ? RiversMask.INSTANCE.effectiveRiverWidthBlocks(x, z) : 0;
         cache.distance = cache.width > 0
            ? RiversMask.INSTANCE.riverCentrelineDistance(x, z) * (double)RiversMask.INSTANCE.blocksPerPixel()
            : Double.POSITIVE_INFINITY;
      }
      int width = cache.width;
      if (width <= 0) return 0.0;
      double distance = cache.distance;
      double floorRadius = (double)width * 0.5;
      double bankRadius = floorRadius + (double)Math.max(24, Math.min(64, (Integer)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get()));
      if (distance >= bankRadius) return 0.0;
      double bankWeight = smoothstep(1.0 - distance / bankRadius);
      double coreWeight = smoothstep(1.0 - Math.min(1.0, distance / Math.max(1.0, floorRadius)));
      // Only the painted core receives the decisive waterline carve.  The bank is a
      // short shoulder, so this cannot turn hot-region rivers into broad lakes.
      double channelWeight = 0.18 * bankWeight + 0.82 * coreWeight;
      int y = context.blockY();
      if (y > 63) {
         double aboveSea = Math.min(1.0, ((double)y - 63.0) / 32.0);
         return -1.35 * channelWeight * (0.55 + 0.45 * aboveSea);
      }
      if (y < 61) return 0.72 * coreWeight;
      return 0.0;
   }

   private static double smoothstep(double value) {
      value = Math.max(0.0, Math.min(1.0, value));
      return value * value * (3.0 - 2.0 * value);
   }

   public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
   public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
   public double minValue() { return -3.0; }
   public double maxValue() { return 2.0; }
   public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }

   private static final class ColumnCache {
      private int x = Integer.MIN_VALUE;
      private int z = Integer.MIN_VALUE;
      private int width;
      private double distance;
   }
}
