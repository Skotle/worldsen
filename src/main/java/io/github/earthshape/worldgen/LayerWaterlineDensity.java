package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
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

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen() || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         return 0.0;
      }

      // The river mask guides continentalness and height-map relief only.  Carving a
      // fixed Y cap here made every bank fall vertically to an artificial floor.
      // Let the normal density/noise pipeline form the actual valley and waterline.
      return 0.0;
   }

   public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
   public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
   public double minValue() { return -3.0; }
   public double maxValue() { return 2.0; }
   public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
