package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps normal placed surface features out of source-layer river beds and banks. */
@Mixin(ConfiguredFeature.class)
public final class RiverFeatureGuardMixin {
   @Inject(method = "place", at = @At("HEAD"), cancellable = true)
   private void earthshape$skipFeatureInLayerRiver(
      WorldGenLevel level,
      ChunkGenerator generator,
      RandomSource random,
      BlockPos pos,
      CallbackInfoReturnable<Boolean> callback
   ) {
      if ((Boolean)EarthShapeServerConfig.RIVER_FEATURE_PROTECTION_ENABLED.get()
         && RiversMask.INSTANCE.isInlandRiverBank(pos.getX(), pos.getZ())) {
         callback.setReturnValue(false);
      }
   }
}
