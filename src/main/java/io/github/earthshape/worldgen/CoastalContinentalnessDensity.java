package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
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
      // Inland-river handling intentionally stops at a mouth so the biome can
      // become ocean. Keep that same mouth physically open in the C-noise field.
      t *= 1.0 - RiversMask.INSTANCE.riverMouthOpening(context.blockX(), context.blockZ());
      // Keep a portion of the original continental noise.  Clamping every
      // water pixel to one C value made an unnaturally level coastal shelf;
      // this only steers the noise toward ocean/land domains instead.
      // At a pure ocean pixel even vanilla's maximum C remains below the
      // ocean cutoff, while a pure land pixel remains above the inland cutoff.
      double maskTarget = -0.85 + 1.20 * t;
      return vanilla * 0.20 + maskTarget * 0.80;
   }

   @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
   @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new CoastalContinentalnessDensity(this.argument.mapAll(visitor))); }
   @Override public double minValue() { return -1.2; }
   @Override public double maxValue() { return 1.0; }
   @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
