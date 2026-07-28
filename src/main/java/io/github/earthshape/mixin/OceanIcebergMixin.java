package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.IcebergFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
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
   private void earthshape$requireFrozenOcean(FeaturePlaceContext<BlockStateConfiguration> context, CallbackInfoReturnable<Boolean> callback) {
      BlockPos origin = context.origin();
      if (RiversMask.INSTANCE.sampleLand(origin.getX(), origin.getZ()) < 0.25
         && ClimateLayers.INSTANCE.temperature(origin.getX(), origin.getZ()) > (Double)EarthShapeServerConfig.SNOW_TEMPERATURE_THRESHOLD.get()) {
         callback.setReturnValue(false);
      }
   }
}
