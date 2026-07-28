package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.ContextProvider;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

/**
 * Guides vanilla's erosion axis without adding height directly. Vanilla's
 * TerrainProvider splines remain solely responsible for the resulting elevation.
 */
public record TerrainErosionDensity(DensityFunction argument) implements DensityFunction {
   private static final MapCodec<TerrainErosionDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
         DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(TerrainErosionDensity::argument)
      ).apply(instance, TerrainErosionDensity::new)
   );
   public static final KeyDispatchDataCodec<TerrainErosionDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   public double compute(FunctionContext context) {
      double vanilla = this.argument.compute(context);
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) {
         return vanilla;
      }

      double relief = ClimateLayers.INSTANCE.terrainRelief(context.blockX(), context.blockZ());
      if (relief <= 0.0) return vanilla;

      double coverage = smoothstep(Math.min(1.0, relief * 2.0));
      double mountain = smoothstep(Math.max(0.0, (relief - 0.5) * 2.0));
      double target = -0.55 - 0.27 * mountain;
      return lerp(vanilla, Math.min(vanilla, target), coverage);
   }

   public void fillArray(double[] values, ContextProvider provider) {
      provider.fillAllDirectly(values, this);
   }

   public DensityFunction mapAll(Visitor visitor) {
      return visitor.apply(new TerrainErosionDensity(this.argument.mapAll(visitor)));
   }

   public double minValue() {
      return -1.0;
   }

   public double maxValue() {
      return this.argument.maxValue();
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double lerp(double a, double b, double amount) {
      return a + (b - a) * amount;
   }
}
