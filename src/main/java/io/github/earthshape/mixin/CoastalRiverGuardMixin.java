package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({RiversMask.class})
public final class CoastalRiverGuardMixin {
   @Inject(
      method = {"hasInlandRiverInfluence"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void earthshape$rejectCoastalStroke(int blockX, int blockZ, CallbackInfoReturnable<Boolean> callback) {
      if ((Boolean)callback.getReturnValue()) {
         int margin = Math.max(80, (Integer)EarthShapeServerConfig.RIVER_MINIMUM_INLAND_BLOCKS.get());

         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               if ((dx != 0 || dz != 0) && RiversMask.INSTANCE.sampleLand(blockX + dx * margin, blockZ + dz * margin) < 0.5) {
                  callback.setReturnValue(false);
                  return;
               }
            }
         }
      }
   }
}
