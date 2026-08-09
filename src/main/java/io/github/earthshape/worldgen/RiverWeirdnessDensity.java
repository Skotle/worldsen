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
      if (EarthShapeCompatibility.disablesWorldgen()) return weirdness;

      boolean mappedLand = RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ()) >= 0.5;
      double coastalPeakRecovery = 1.0;
      if (mappedLand) {
         // C and E already descend smoothly toward the authoritative coast, but
         // retaining a full mountain W/PV signal in the last land columns can
         // still build razor-thin peaks that the ocean mask cuts vertically.
         // Fade the ridge axis through the same land-side coastal interval.
         double coastalLandness = RiversMask.INSTANCE.sampleCoastalLandness(context.blockX(), context.blockZ());
         coastalPeakRecovery = smootherstep((coastalLandness - 0.48) / 0.44);
         weirdness *= coastalPeakRecovery;
      }

      // A mountain layer cannot rely on E alone: with an unlucky vanilla W/PV
      // sample it receives the mountain biome but remains a flat lowland. Guide
      // W toward the centre of vanilla's Peaks fold (|W| ~= 0.67) in proportion
      // to the region's smooth relief. Vanilla terrain splines still decide Y.
      if ((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()
         && mappedLand
         && ClimateLayers.INSTANCE.terrainKind(context.blockX(), context.blockZ())
            == ClimateLayers.TerrainKind.MOUNTAIN) {
         double relief = ClimateLayers.INSTANCE.terrainRelief(context.blockX(), context.blockZ())
            * coastalPeakRecovery;
         if (relief > 0.0) {
            boolean terralith = EarthShapeCompatibility.isTerralithLoaded();
            // Terralith applies a second, much stronger jaggedness/factor spline
            // to W/PV. Its full peak centre combined with EarthShape's broad
            // mountain mask resembles amplified world generation, so retain a
            // high-slope PV value without forcing every centre to PV~=1.
            double peakTarget = terralith ? 0.45 : 0.67;
            double maximumGuidance = terralith ? 0.32 : 0.72;
            double target = weirdness < 0.0 ? -peakTarget : peakTarget;
            weirdness = lerp(weirdness, target, Math.min(maximumGuidance, relief * maximumGuidance));
            if (terralith) {
               weirdness = Math.max(-0.60, Math.min(0.60, weirdness));
            }
         }
      }

      if (!(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         || !RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) {
         return weirdness;
      }

      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
      if (width <= 0) return weirdness;

      double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ())
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double waterRadius = Math.max(0.5, (double)width * 0.5);
      double bedRadius = Math.min(waterRadius, Math.max(0.5, waterRadius * RIVER_BED_CORE_RATIO));
      // W/PV must recover over the same broad shoulder for every terrain family.
      // The former mountain-only 2..4 block recovery reinstated peaks while C
      // and E were still flat, producing a narrow cliff beside the water.
      double edgeFade = RiverTerrainTransition.distance(context.blockX(), context.blockZ(), width);
      if (distance >= waterRadius + edgeFade) return weirdness;
      if (distance <= bedRadius) return 0.0;

      // W=0 is restricted to the actual level bed. The submerged shoulder joins
      // it to 55..85% of the source noise at the water edge; dry land then
      // recovers from that noise instead of remaining a wide PV=valley plane.
      if (distance < waterRadius) {
         double channelT = (distance - bedRadius) / Math.max(0.5, waterRadius - bedRadius);
         double retained = RiverTerrainTransition.bankNoiseRetention(context.blockX(), context.blockZ());
         return weirdness * retained * smootherstep(channelT);
      }
      double recovery = RiverTerrainTransition.recovery(
         context.blockX(), context.blockZ(), distance - waterRadius, edgeFade
      );
      return weirdness * recovery;
   }

   private static double smootherstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * clamped * (clamped * (clamped * 6.0 - 15.0) + 10.0);
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
