package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer.NoiseBasedAquifer;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({NoiseBasedAquifer.class})
public final class SurfaceAquiferGuardMixin {
   @Inject(
      method = {"computeSubstance"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void earthshape$removeUnmappedSurfaceAquiferWater(FunctionContext context, double substance, CallbackInfoReturnable<BlockState> callback) {
      BlockState result = (BlockState)callback.getReturnValue();
      boolean layerRiver = (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         && RiversMask.INSTANCE.isInlandRiver(context.blockX(), context.blockZ());
      // Aquifer noise is especially likely to return dry air in hot biomes.  Once the
      // density router has opened a painted river at sea level, guarantee its water
      // column instead of letting the desert-water guard erase it again.
      if (layerRiver && substance < 0.0 && result == null && context.blockY() >= 61 && context.blockY() <= 63) {
         callback.setReturnValue(Blocks.WATER.defaultBlockState());
         return;
      }
      if ((Boolean)EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()
         && result != null
         && result.is(Blocks.WATER)
         && context.blockY() >= 52
         && RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ()) >= 0.5
         && !layerRiver) {
         callback.setReturnValue(null);
      }
   }
}
