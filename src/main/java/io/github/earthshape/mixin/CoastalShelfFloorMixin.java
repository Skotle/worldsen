package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Guarantees a continuous shallow-water cross-section at every mapped coast.
 * Vanilla density remains untouched below the one-block shelf cap and outside
 * the configured continental-shelf transition.
 */
@Mixin(NoiseChunk.class)
public abstract class CoastalShelfFloorMixin {
   @Unique
   private static final ThreadLocal<long[]> EARTHSHAPE_SHELF_COLUMN = ThreadLocal.withInitial(
      () -> new long[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, 0L, 0L}
   );

   @Inject(method = "getInterpolatedState", at = @At("RETURN"), cancellable = true)
   private void earthshape$shapeConnectedCoastalShelf(CallbackInfoReturnable<BlockState> callback) {
      if (EarthShapeCompatibility.disablesWorldgen()
         || !(Boolean)EarthShapeServerConfig.CONTINENTS_ENABLED.get()) {
         return;
      }

      NoiseChunk chunk = (NoiseChunk)(Object)this;
      int x = chunk.blockX();
      int z = chunk.blockZ();
      long[] column = EARTHSHAPE_SHELF_COLUMN.get();
      long transformVersion = RiversMask.INSTANCE.mapTransformVersion();
      if (column[0] != x || column[1] != z || column[2] != transformVersion) {
         earthshape$updateShelfColumn(column, x, z, transformVersion);
      }
      if (column[3] == 0L) return;

      int y = chunk.blockY();
      int floorY = (int)column[4];
      if (y == floorY) {
         // One solid cap is enough to close surface caves without replacing the
         // vanilla seabed, ores or caves below it.
         callback.setReturnValue(Blocks.STONE.defaultBlockState());
      } else if (y > floorY && y <= 62) {
         callback.setReturnValue(Blocks.WATER.defaultBlockState());
      } else if (y >= 63) {
         // A positive 3-D noise lobe must not turn the mapped coastal sea into
         // an island or a raised strip above the global water surface.
         callback.setReturnValue(Blocks.AIR.defaultBlockState());
      }
   }

   @Unique
   private static double smootherstep(double value) {
      double t = Math.max(0.0, Math.min(1.0, value));
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
   }

   @Unique
   private static void earthshape$updateShelfColumn(long[] column, int blockX, int blockZ, long version) {
      column[0] = blockX;
      column[1] = blockZ;
      column[2] = version;
      column[3] = 0L;

      if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5
         || RiversMask.INSTANCE.riverMouthOpening(blockX, blockZ) > 0.001) {
         return;
      }

      double distance = RiversMask.INSTANCE.oceanDistanceBlocks(blockX, blockZ);
      double oneMetreWidth = (double)EarthShapeServerConfig.COAST_SHALLOW_SHELF_WIDTH_BLOCKS.get();
      double nearshoreEnd = oneMetreWidth
         + (double)EarthShapeServerConfig.COAST_SHELF_TRANSITION_BLOCKS.get();
      double shelfEnd = Math.max(
         nearshoreEnd,
         (double)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get()
      );
      if (distance > shelfEnd) return;

      double depth;
      if (distance <= oneMetreWidth) {
         depth = 1.0;
      } else if (distance <= nearshoreEnd) {
         // The land-connected nearshore is always between one and five
         // metres deep. Both ends have zero slope, avoiding a ledge where
         // the one-metre water first begins and where open shelf takes over.
         double progress = smootherstep((distance - oneMetreWidth) / Math.max(1.0, nearshoreEnd - oneMetreWidth));
         depth = 1.0 + 4.0 * progress;
      } else {
         int deepFloorY = (Integer)EarthShapeServerConfig.COAST_SHELF_DEEP_FLOOR_Y.get();
         double deepDepth = Math.max(5.0, 62.0 - (double)deepFloorY);
         double progress = smootherstep((distance - nearshoreEnd) / Math.max(1.0, shelfEnd - nearshoreEnd));
         depth = 5.0 + (deepDepth - 5.0) * progress;
      }

      column[3] = 1L;
      column[4] = 62L - (long)Math.round(depth);
   }
}
