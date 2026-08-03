package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Guides the vanilla ridge (weirdness) axis toward zero on a painted river.
 * Vanilla folds W=0 into PV=-1, its valley range; no fixed height is added.
 */
public record RiverWeirdnessDensity(DensityFunction argument) implements DensityFunction {
   /** Must match the continentalness cross-section's level bed fraction. */
   private static final double RIVER_BED_CORE_RATIO = 0.60;
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
      double waterRadius = Math.max(0.5, (double)width * 0.5);
      double bedRadius = Math.min(waterRadius, Math.max(0.5, waterRadius * RIVER_BED_CORE_RATIO));
      double mappedRelief = ClimateLayers.INSTANCE.terrainRelief(context.blockX(), context.blockZ());
      double explicitMountain = smoothstep(Math.max(0.0, (mappedRelief - 0.35) / 0.65));
      // W/PV previously recovered in only 2..4 blocks while erosion recovered
      // much farther out. That mismatch could create a narrow peak band beside
      // an otherwise flat river. Keep broad valley guidance on flat terrain,
      // but retain the short shoulder where the layer explicitly paints a
      // waterfront mountain.
      double ordinaryFade = Math.min(
         32.0,
         Math.max(16.0, waterRadius + 12.0)
      );
      double configuredFade = Math.min(
         ordinaryFade,
         (double)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get()
      );
      double mountainFade = Math.max(
         2.0,
         Math.min(4.0, (double)EarthShapeServerConfig.RIVER_CHANNEL_EDGE_FADE_BLOCKS.get())
      );
      double edgeFade = lerp(configuredFade, mountainFade, explicitMountain);
      double radius = waterRadius + edgeFade;
      if (distance >= radius) return weirdness;
      if (distance <= bedRadius) return 0.0;

      // Hold W=0 over the same width-proportional bed used by C, then recover
      // through a Gaussian shoulder. This prevents PV relief from roughening
      // the floor while retaining a naturally curved transition into the bank.
      double shoulderDistance = distance - bedRadius;
      double shoulderRadius = radius - bedRadius;
      double sigma = Math.max(1.0, shoulderRadius * 0.42);
      double normalizedDistance = shoulderDistance / sigma;
      double edgeDistance = shoulderRadius / sigma;
      double gaussianDrop = Math.exp(-0.5 * normalizedDistance * normalizedDistance);
      double edgeDrop = Math.exp(-0.5 * edgeDistance * edgeDistance);
      double recovery = (1.0 - gaussianDrop) / (1.0 - edgeDrop);
      recovery = Math.max(0.0, Math.min(1.0, recovery));
      return weirdness * recovery;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double lerp(double from, double to, double amount) {
      return from + (to - from) * amount;
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
