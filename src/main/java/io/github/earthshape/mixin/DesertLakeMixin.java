package io.github.earthshape.mixin;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The map owns surface water. Vanilla lake features must not add off-layer lakes. */
@Mixin(LakeFeature.class)
public final class DesertLakeMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void earthshape$disableUnmappedLakes(FeaturePlaceContext<LakeFeature.Configuration> context,
                                                  CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }
}
