/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer
 *  net.minecraft.world.level.levelgen.DensityFunction$FunctionContext
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Aquifer.NoiseBasedAquifer.class})
public final class SurfaceAquiferGuardMixin {
    @Inject(method={"computeSubstance"}, at={@At(value="RETURN")}, cancellable=true)
    private void earthshape$removeUnmappedSurfaceAquiferWater(DensityFunction.FunctionContext context, double substance, CallbackInfoReturnable<BlockState> callback) {
        BlockState result = (BlockState)callback.getReturnValue();
        boolean layerRiver = SurfaceAquiferGuardMixin.isLayerRiverChannel(context.blockX(), context.blockZ());
        // A mapped channel can be correctly carved while the vanilla random aquifer
        // still chooses air in hot/dry climates. Fill only already-empty, negative
        // density river cells at the normal surface-water band; solid terrain and
        // underground caves are left to vanilla.
        if (layerRiver && result == null && substance < 0.0 && context.blockY() >= 61 && context.blockY() <= 63) {
            callback.setReturnValue(Blocks.WATER.defaultBlockState());
            return;
        }
        if (((Boolean)EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()).booleanValue() && result != null && result.is(Blocks.WATER) && context.blockY() >= 52 && RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ()) >= 0.5 && !layerRiver) {
            callback.setReturnValue(null);
        }
    }

    /**
     * Must match the distance-based profile used by the river density functions.
     * Using only isInlandRiver() here discarded aquifer water on pixel edges and in
     * wider desert strokes after the terrain had already been carved.
     */
    private static boolean isLayerRiverChannel(int x, int z) {
        if (!RiversMask.INSTANCE.hasInlandRiverInfluence(x, z)) {
            return false;
        }
        int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(x, z);
        if (width <= 0) {
            return false;
        }
        double distance = RiversMask.INSTANCE.riverCentrelineDistance(x, z) * (double)RiversMask.INSTANCE.blocksPerPixel();
        return distance <= (double)width / 2.0 + 2.0;
    }
}
