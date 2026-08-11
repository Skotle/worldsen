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
 * A compact stone support joins the shaped floor to the natural seabed; deeper
 * caves and terrain outside the configured continental shelf remain untouched.
 */
@Mixin(NoiseChunk.class)
public abstract class CoastalShelfFloorMixin {
   @Unique
   private static final ThreadLocal<long[]> EARTHSHAPE_SHELF_COLUMN = ThreadLocal.withInitial(
      () -> new long[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, 0L, 0L, 0L}
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
      long mode = column[3];
      if (mode == 0L) return;

      int y = chunk.blockY();
      int floorY = (int)column[4];
      if (mode == 2L) {
         // A solid block at Y63 has its walkable top at Y64. Only repair an
         // empty/water cell, so ordinary vanilla terrain above this minimum is
         // completely untouched. Wetland surface water is intentionally kept.
         if (y == 63) {
            BlockState result = callback.getReturnValue();
            // Non-wetland surface water has already been removed by
            // SurfaceAquiferGuardMixin. Do not replace remaining water here,
            // because it is intentional wetland water.
            if (result != null && result.isAir()) {
               callback.setReturnValue(Blocks.STONE.defaultBlockState());
            }
         }
         return;
      }
      if (mode == 3L) {
         // Close the channel at the configured lower limit. The aquifer mixin
         // fills only naturally open cells above it, retaining a variable bed
         // while making water deeper than six blocks impossible.
         if (y == floorY) {
            callback.setReturnValue(Blocks.STONE.defaultBlockState());
         }
         return;
      }
      if (mode == 4L) {
         // The first one-to-three land blocks beside water use a solid Y62
         // footing, whose top face is exactly level with the Y63 water surface.
         // Only fill an empty cell; naturally higher slopes remain untouched.
         if (y == 62) {
            BlockState result = callback.getReturnValue();
            if (result != null && result.isAir()) {
               callback.setReturnValue(Blocks.STONE.defaultBlockState());
            }
         }
         return;
      }

      int supportBaseY = (int)column[5];
      if (y == floorY) {
         callback.setReturnValue(Blocks.STONE.defaultBlockState());
      } else if (y >= supportBaseY && y < floorY) {
         // Fill only holes inside the shelf support. Existing stone, ores and
         // other solid density remain vanilla, while caves below the support
         // base remain available instead of being erased to world bottom.
         BlockState result = callback.getReturnValue();
         if (result != null && (result.isAir() || result.is(Blocks.WATER))) {
            callback.setReturnValue(Blocks.STONE.defaultBlockState());
         }
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
      column[5] = Long.MIN_VALUE;

      boolean mappedLand = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5;
      double mouthOpening = RiversMask.INSTANCE.riverMouthOpening(blockX, blockZ);
      if (mappedLand) {
         if (mouthOpening > 0.001) return;
         if ((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
            && RiversMask.INSTANCE.isInlandRiverColumn(blockX, blockZ)) {
            int depth = RiversMask.INSTANCE.riverBedDepthBlocks(blockX, blockZ);
            column[3] = 3L;
            column[4] = 62L - depth;
            return;
         }

         double bankDistance = RiversMask.INSTANCE.surfaceBankDistanceBlocks(blockX, blockZ);
         if (bankDistance > 0.0
            && bankDistance <= (double)RiversMask.INSTANCE.surfaceBankWidthBlocks(blockX, blockZ)) {
            column[3] = 4L;
            column[4] = 62L;
            return;
         }

         column[3] = 2L;
         column[4] = 63L;
         return;
      }

      if (mouthOpening > 0.001) {
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
      int deepFloorY = (Integer)EarthShapeServerConfig.COAST_SHELF_DEEP_FLOOR_Y.get();
      // Join every shaped nearshore floor to one common compact support datum.
      // Keeping the datum eight blocks below the configured deep shelf avoids
      // the former one-block ceiling without sealing the deeper cave system.
      column[5] = Math.min(column[4], Math.max(-64L, (long)deepFloorY - 8L));
   }
}
