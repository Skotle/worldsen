package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies EarthShape's coastal shelf after TFC has filled its custom noise
 * columns and before TFC's surface manager decorates the resulting seabed.
 * This is intentionally limited to the coast: TFC remains responsible for its
 * biome, climate, inland terrain, rivers, rock layers and aquifers.
 */
@Pseudo
@Mixin(targets = "net.dries007.tfc.world.ChunkNoiseFiller", remap = false)
public abstract class TfcCoastlineMixin {
   private static final int SEA_LEVEL = 63;

   @Shadow(remap = false)
   private ProtoChunk chunk;

   @Shadow(remap = false)
   private int chunkMinX;

   @Shadow(remap = false)
   private int chunkMinZ;

   @Inject(method = "fillFromNoise", at = @At("RETURN"), remap = false)
   private void earthshape$applyTfcCoastline(CallbackInfo callback) {
      if (EarthShapeCompatibility.disablesWorldgen()) {
         return;
      }

      int minY = this.chunk.getMinBuildHeight();
      int maxY = this.chunk.getMaxBuildHeight() - 1;
      BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
      for (int localZ = 0; localZ < 16; localZ++) {
         int blockZ = this.chunkMinZ + localZ;
         for (int localX = 0; localX < 16; localX++) {
            int blockX = this.chunkMinX + localX;
            if (RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5
               || RiversMask.INSTANCE.riverMouthOpening(blockX, blockZ) > 0.001) {
               continue;
            }

            double distance = RiversMask.INSTANCE.oceanDistanceBlocks(blockX, blockZ);
            double shallowWidth = (Integer)EarthShapeServerConfig.COAST_SHALLOW_SHELF_WIDTH_BLOCKS.get();
            double transitionEnd = shallowWidth + (Integer)EarthShapeServerConfig.COAST_SHELF_TRANSITION_BLOCKS.get();
            double shelfEnd = Math.max(transitionEnd, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get());
            if (distance > shelfEnd) {
               continue;
            }

            double depth;
            if (distance <= shallowWidth) {
               depth = 1.0;
            } else if (distance <= transitionEnd) {
               depth = 1.0 + 4.0 * earthshape$smootherstep((distance - shallowWidth) / Math.max(1.0, transitionEnd - shallowWidth));
            } else {
               int deepFloor = (Integer)EarthShapeServerConfig.COAST_SHELF_DEEP_FLOOR_Y.get();
               double deepDepth = Math.max(5.0, 62.0 - deepFloor);
               depth = 5.0 + (deepDepth - 5.0) * earthshape$smootherstep((distance - transitionEnd) / Math.max(1.0, shelfEnd - transitionEnd));
            }

            int floorY = Math.max(minY, SEA_LEVEL - 1 - (int)Math.round(depth));
            for (int y = floorY; y <= Math.min(SEA_LEVEL - 1, maxY); y++) {
               position.set(blockX, y, blockZ);
               this.chunk.setBlockState(position, y == floorY ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState(), false);
            }
            for (int y = Math.max(SEA_LEVEL, minY); y <= maxY; y++) {
               position.set(blockX, y, blockZ);
               this.chunk.setBlockState(position, Blocks.AIR.defaultBlockState(), false);
            }
         }
      }
   }

   private static double earthshape$smootherstep(double value) {
      double t = Math.max(0.0, Math.min(1.0, value));
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
   }
}
