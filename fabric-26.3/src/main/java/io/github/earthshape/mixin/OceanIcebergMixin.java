package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.IcebergFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({IcebergFeature.class})
public final class OceanIcebergMixin {
   @Inject(
      method = {"place"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void earthshape$requireFrozenOcean(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.util.RandomSource random, net.minecraft.core.BlockPos origin, CallbackInfoReturnable<Boolean> callback) {
      if (RiversMask.INSTANCE.sampleLand(origin.getX(), origin.getZ()) < 0.25
         && ClimateLayers.INSTANCE.temperature(origin.getX(), origin.getZ()) > (Double)EarthShapeServerConfig.SNOW_TEMPERATURE_THRESHOLD.get()) {
         callback.setReturnValue(false);
      }
   }
}
