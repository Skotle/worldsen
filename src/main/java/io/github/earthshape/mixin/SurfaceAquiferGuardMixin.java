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
      int y = context.blockY();
      boolean forceRiverWater = substance < 0.0 && result == null && y >= 61 && y <= 63;
      boolean removeSurfaceWater = (Boolean)EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()
         && result != null
         && result.is(Blocks.WATER)
         && y >= 52;
      // Do not run a river-centreline search for every density sample in a chunk.
      // It is relevant only to the three forced river-water layers and to a
      // candidate surface-water removal; the per-column cache then shares the
      // exact result between all Y levels of that column.
      boolean layerRiver = (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
         && (forceRiverWater || removeSurfaceWater)
         && RiversMask.INSTANCE.isInlandRiverColumn(context.blockX(), context.blockZ());
      // Aquifer noise is especially likely to return dry air in hot biomes.  Once the
      // density router has opened a painted river at sea level, guarantee its water
      // column instead of letting the desert-water guard erase it again.
      if (layerRiver && forceRiverWater) {
         callback.setReturnValue(Blocks.WATER.defaultBlockState());
         return;
      }
      // The mapped ocean is authoritative. Hot-biome aquifer noise and the
      // land-side surface-water guard must not leave dry holes or vertical water
      // walls in the shelf. Solid density is untouched; only empty cells fill.
      if (substance < 0.0 && y >= 32 && y <= 62 && RiversMask.INSTANCE.sampleLayerLand(context.blockX(), context.blockZ()) < 0.5) {
         callback.setReturnValue(Blocks.WATER.defaultBlockState());
         return;
      }
      if (removeSurfaceWater
         && RiversMask.INSTANCE.sampleLayerLand(context.blockX(), context.blockZ()) >= 0.5
         && !layerRiver) {
         callback.setReturnValue(null);
      }
   }
}
