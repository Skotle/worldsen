/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
 *  net.minecraft.world.level.levelgen.feature.LakeFeature
 *  net.minecraft.world.level.levelgen.feature.LakeFeature$Configuration
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LakeFeature.class})
public final class DesertLakeMixin {
    @Inject(method={"place"}, at={@At(value="HEAD")}, cancellable=true)
    private void earthshape$keepOnlyLayerSurfaceWater(FeaturePlaceContext<LakeFeature.Configuration> context, CallbackInfoReturnable<Boolean> callback) {
        BlockPos origin;
        if (((Boolean)EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()).booleanValue() && (origin = context.origin()).getY() >= 52 && RiversMask.INSTANCE.sampleLand(origin.getX(), origin.getZ()) >= 0.5 && !RiversMask.INSTANCE.isInlandRiver(origin.getX(), origin.getZ())) {
            callback.setReturnValue(false);
        }
    }
}
