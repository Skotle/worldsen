package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes naturally placed surface lake features in map-designated deserts. */
@Mixin(LakeFeature.class)
public final class DesertLakeMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void earthshape$skipDesertSurfaceLakes(FeaturePlaceContext<LakeFeature.Configuration> context,
            CallbackInfoReturnable<Boolean> callback) {
        if (!EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()) return;
        BlockPos origin = context.origin();
        // LakeFeature lowers its origin by four blocks.  Below this is normally an underground
        // feature, which should remain untouched; map deserts only suppress surface pools.
        if (origin.getY() < 52) return;
        if (ClimateLayers.INSTANCE.terrainKind(origin.getX(), origin.getZ()) != ClimateLayers.TerrainKind.DESERT) return;
        if (RiversMask.INSTANCE.isInlandRiver(origin.getX(), origin.getZ())) return;
        callback.setReturnValue(false);
    }
}
