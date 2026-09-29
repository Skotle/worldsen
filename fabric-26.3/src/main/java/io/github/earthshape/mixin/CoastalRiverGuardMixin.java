package io.github.earthshape.mixin;

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
      if ((Boolean)callback.getReturnValue() && RiversMask.INSTANCE.isRiverMouth(blockX, blockZ)) {
         // Only a real source-map mouth joins the ocean.  The former 80-block proximity
         // check also rejected legitimate inland strokes beside lakes and narrow coasts,
         // leaving disconnected biome-coloured lines instead of continuous rivers.
         callback.setReturnValue(false);
      }
   }
}
