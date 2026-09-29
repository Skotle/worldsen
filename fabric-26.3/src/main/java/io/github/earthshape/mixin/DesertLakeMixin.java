package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
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
   private void earthshape$keepOnlyLayerSurfaceWater(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.util.RandomSource random, net.minecraft.core.BlockPos origin, CallbackInfoReturnable<Boolean> callback) {
      if ((Boolean)EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()) {
            if (origin.getY() >= 52
            && RiversMask.INSTANCE.sampleLayerLand(origin.getX(), origin.getZ()) >= 0.5
            && !RiversMask.INSTANCE.isInlandRiver(origin.getX(), origin.getZ())) {
            callback.setReturnValue(false);
         }
      }
   }
}
