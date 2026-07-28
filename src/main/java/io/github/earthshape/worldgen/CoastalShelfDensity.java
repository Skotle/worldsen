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
 * Supports a one-block-deep shoreline and lowers that support smoothly into
 * vanilla ocean terrain. It does not place water; the vanilla aquifer does.
 */
public final class CoastalShelfDensity implements DensityFunction {
   // seaLevel=63 means the top water block is Y=62. A one-block-deep
   // standable shoreline therefore needs its solid floor at Y=61.
   private static final int SHORE_FLOOR_Y = 61;
   private static final int OUTER_SHELF_FLOOR_Y = 32;
   private static final MapCodec<CoastalShelfDensity> DATA_CODEC = MapCodec.unit(new CoastalShelfDensity());
   public static final KeyDispatchDataCodec<CoastalShelfDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

   public double compute(FunctionContext context) {
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         return 0.0;
      }

      int blockX = context.blockX();
      int blockZ = context.blockZ();
      if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5) return 0.0;

      int fadeBlocks = (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get();
      double proximity = RiversMask.INSTANCE.sampleWaterShoreProximity(blockX, blockZ, fadeBlocks);
      if (proximity <= 0.0) return 0.0;

      double targetFloorY = OUTER_SHELF_FLOOR_Y
         + (double)(SHORE_FLOOR_Y - OUTER_SHELF_FLOOR_Y) * proximity;
      int targetBlockY = (int)Math.ceil(targetFloorY);
      double belowTarget = (double)targetBlockY - (double)context.blockY();
      if (belowTarget < 0.0) return 0.0;

      // Strongest at the coast and below the target floor, then fades with both
      // horizontal shelf distance and vertical distance to avoid a hollow slab.
      double verticalSupport = smoothstep(Math.min(1.0, (belowTarget + 1.0) / 8.0));
      return proximity * (0.72 + 0.28 * verticalSupport);
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
      return 1.0;
   }

   public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      return CODEC;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }
}
