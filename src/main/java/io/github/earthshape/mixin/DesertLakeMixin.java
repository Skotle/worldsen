package io.github.earthshape.mixin;

import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps naturally placed surface lakes out of map land; source rivers remain the exception. */
@Mixin(LakeFeature.class)
public final class DesertLakeMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void earthshape$keepOnlyLayerSurfaceWater(FeaturePlaceContext<LakeFeature.Configuration> context,
            CallbackInfoReturnable<Boolean> callback) {
        BlockPos origin = context.origin();
        // Underground features remain vanilla.  Any surface lake/pool outside a verified
        // source river is disabled so the rivers layer is the sole inland water authority.
        if (origin.getY() < 52) return;
        if (RiversMask.INSTANCE.isInlandRiver(origin.getX(), origin.getZ())) return;
        callback.setReturnValue(false);
    }
}
