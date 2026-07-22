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

/** Prevents noise aquifers from becoming surface lakes on mapped land. */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public final class SurfaceAquiferGuardMixin {
   @Inject(method = "computeSubstance", at = @At("RETURN"), cancellable = true)
   private void earthshape$removeUnmappedSurfaceAquiferWater(
      DensityFunction.FunctionContext context,
      double substance,
      CallbackInfoReturnable<BlockState> callback
   ) {
      BlockState result = callback.getReturnValue();
      if ((Boolean)EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()
         && result != null
         && result.is(Blocks.WATER)
         && context.blockY() >= 52
         && RiversMask.INSTANCE.sampleLand(context.blockX(), context.blockZ()) >= 0.5
         && !RiversMask.INSTANCE.isInlandRiver(context.blockX(), context.blockZ())) {
         callback.setReturnValue(null);
      }
   }
}
