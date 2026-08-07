package io.github.earthshape.mixin;

import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defers feature sorting until EarthShape has appended modded biome holders. */
@Mixin(ChunkGenerator.class)
public abstract class AdditionalBiomeFeatureMixin {
   @Inject(method = "validate", at = @At("HEAD"), cancellable = true)
   private void earthshape$deferFeatureValidation(CallbackInfo callback) {
      callback.cancel();
   }
}
