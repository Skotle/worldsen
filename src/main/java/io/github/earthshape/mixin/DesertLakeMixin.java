package io.github.earthshape.mixin;

import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.LakeFeature.Configuration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LakeFeature.class})
public final class DesertLakeMixin {
   @Inject(
      method = {"place"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void earthshape$keepOnlyLayerSurfaceWater(FeaturePlaceContext<Configuration> context, CallbackInfoReturnable<Boolean> callback) {
      BlockPos origin = context.origin();
      if (origin.getY() >= 52) {
         if (!RiversMask.INSTANCE.isInlandRiver(origin.getX(), origin.getZ())) {
            callback.setReturnValue(false);
         }
      }
   }
}
