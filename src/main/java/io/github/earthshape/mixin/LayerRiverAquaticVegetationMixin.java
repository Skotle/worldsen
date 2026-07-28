package io.github.earthshape.mixin;

import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.KelpFeature;
import net.minecraft.world.level.levelgen.feature.SeagrassFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent ocean vegetation decorators from treating mapped rivers as seabed. */
@Mixin({KelpFeature.class, SeagrassFeature.class})
public final class LayerRiverAquaticVegetationMixin {
   @Inject(
      method = {"place"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void earthshape$skipOceanPlantsInLayerRiver(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> callback) {
      BlockPos origin = context.origin();
      if (RiversMask.INSTANCE.isInlandRiver(origin.getX(), origin.getZ())) {
         callback.setReturnValue(false);
      }
   }
}
