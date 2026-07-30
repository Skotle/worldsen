package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Guides the vanilla ridge (weirdness) axis toward zero on a painted river.
 * Vanilla folds W=0 into PV=-1, its valley range; no fixed height is added.
 */
public record RiverWeirdnessDensity(DensityFunction argument) implements DensityFunction {
   private static final MapCodec<RiverWeirdnessDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(RiverWeirdnessDensity::argument))
         .apply(instance, RiverWeirdnessDensity::new)
   );
   public static final KeyDispatchDataCodec<RiverWeirdnessDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   @Override
   public double compute(FunctionContext context) {
      double weirdness = this.argument.compute(context);
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         || !RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) {
         return weirdness;
      }

      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
      if (width <= 0) return weirdness;

      double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ())
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double radius = width * 0.5 + Math.max(2.0, (double)EarthShapeServerConfig.RIVER_CHANNEL_EDGE_FADE_BLOCKS.get());
      if (distance >= radius) return weirdness;

      // 0 at the centreline and 1 at the edge.  Multiplying W by this
      // increasing recovery function makes the folded PV value decrease
      // continuously toward its Valleys minimum along the river line.
      double t = Math.max(0.0, Math.min(1.0, distance / Math.max(1.0, radius)));
      double recovery = t * t * (3.0 - 2.0 * t);
      return weirdness * recovery;
   }

   @Override
   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   @Override
   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(new RiverWeirdnessDensity(this.argument.mapAll(visitor)));
   }

   @Override
   public double minValue() { return this.argument.minValue(); }

   @Override
   public double maxValue() { return this.argument.maxValue(); }

   @Override
   public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
