package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Smoothly maps the coastline layer onto vanilla continentalness, never onto a fixed Y level. */
public record CoastalContinentalnessDensity(DensityFunction argument) implements DensityFunction {
   private static final MapCodec<CoastalContinentalnessDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(CoastalContinentalnessDensity::argument))
         .apply(instance, CoastalContinentalnessDensity::new)
   );
   public static final KeyDispatchDataCodec<CoastalContinentalnessDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   @Override
   public double compute(FunctionContext context) {
      double vanilla = this.argument.compute(context);
      if (EarthShapeCompatibility.disablesWorldgen() || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) return vanilla;
      double t = RiversMask.INSTANCE.sampleCoastalLandness(context.blockX(), context.blockZ());
      t = t * t * (3.0 - 2.0 * t);
      boolean mappedLand = RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ()) >= 0.5;
      // Inland-river handling intentionally stops at a mouth so the biome can
      // become ocean. Keep that same mouth physically open in the C-noise field.
      double mouthOpening = RiversMask.INSTANCE.riverMouthOpening(context.blockX(), context.blockZ());
      t *= 1.0 - mouthOpening;
      // Keep a portion of the original continental noise. Clamping every
      // water pixel to one C value made an unnaturally level coastal shelf;
      // this only steers the noise toward ocean/land domains instead.
      double maskTarget = -0.85 + 1.20 * t;
      double guided = vanilla * 0.20 + maskTarget * 0.80;
      // Keep the exact source mask authoritative without the old 0.95/0.05
      // t-clamp. Those values jumped C by 0.864 in one column. A narrow pair of
      // side limits preserves land/ocean classification while limiting the C
      // seam to 0.10, which the vanilla splines can resolve as an ordinary bank.
      if (mappedLand && mouthOpening <= 0.001) {
         guided = Math.max(-0.10, guided);
      } else if (!mappedLand) {
         guided = Math.min(-0.20, guided);
      }
      double riverInfluence = riverValleyInfluence(context.blockX(), context.blockZ());
      if (riverInfluence > 0.0) {
         guided = applyRiverCrossSection(context.blockX(), context.blockZ(), guided, riverInfluence);
      }
      // Keep broad deserts near one inland elevation. A small remainder of the
      // original C field is retained so large W/PV hills still have a natural
      // base, while local continental bumps can no longer build mountains.
      double desertInfluence = ClimateLayers.INSTANCE.desertInfluence(context.blockX(), context.blockZ());
      double inlandFactor = smoothstep((t - 0.82) / 0.16);
      if (desertInfluence > 0.0 && riverInfluence <= 0.0) {
         guided = lerp(guided, 0.10, 0.85 * desertInfluence * inlandFactor);
      }
      // Ordinary mapped land starts at the normal overworld baseline (surface
      // Y=64).  Keep the coastal blend and river mouths unconstrained so their
      // descent remains noise-shaped instead of becoming a flat terrace.
      if (mappedLand
         && mouthOpening <= 0.001
         && riverInfluence <= 0.0) {
         // Raise the minimum continuously from near-inland C=-0.10 to the
         // ordinary Y=64 baseline instead of switching the floor on at t=0.98.
         return Math.max(lerp(-0.10, 0.06, inlandFactor), guided);
      }
      return guided;
   }

   private static double applyRiverCrossSection(int blockX, int blockZ, double terrain, double valleyInfluence) {
      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ);
      if (width <= 0) return terrain;
      double distance = RiversMask.INSTANCE.riverCentrelineDistance(blockX, blockZ)
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double waterRadius = Math.max(0.5, (double)width * 0.5);

      if (distance <= waterRadius) {
         // The centre keeps the configured channel depth. Toward the water's
         // edge C rises with zero slope at both ends, producing a submerged
         // side slope instead of carrying the low floor into a vertical wall.
         double acrossChannel = smoothstep(distance / waterRadius);
         double centre = (Double)EarthShapeServerConfig.RIVER_CHANNEL_CONTINENTALNESS.get();
         double edge = 0.04;
         return lerp(centre, edge, acrossChannel);
      }

      // Just outside the water, hold the ordinary Y=64 continental baseline;
      // then let the existing valley influence return it to surrounding noise.
      return lerp(terrain, 0.06, valleyInfluence);
   }

   private static double riverValleyInfluence(int blockX, int blockZ) {
      if (!(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         || !RiversMask.INSTANCE.hasInlandRiverInfluence(blockX, blockZ)) {
         return 0.0;
      }
      int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ);
      if (width <= 0) return 0.0;
      double distance = RiversMask.INSTANCE.riverCentrelineDistance(blockX, blockZ)
         * (double)RiversMask.INSTANCE.blocksPerPixel();
      double bankDistance = Math.max(0.0, distance - (double)width * 0.5);
      double supportedDistance = Math.max(
         4.0,
         (double)RiversMask.INSTANCE.blocksPerPixel() * 3.0 - (double)width * 0.5
      );
      double fade = Math.max(
         4.0,
         Math.min((double)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get(), supportedDistance)
      );
      if (bankDistance >= fade) return 0.0;
      double recovery = smoothstep(bankDistance / fade);
      return 1.0 - recovery;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double lerp(double from, double to, double amount) {
      return from + (to - from) * amount;
   }

   @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
   @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new CoastalContinentalnessDensity(this.argument.mapAll(visitor))); }
   @Override public double minValue() { return -1.2; }
   @Override public double maxValue() { return 1.0; }
   @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
