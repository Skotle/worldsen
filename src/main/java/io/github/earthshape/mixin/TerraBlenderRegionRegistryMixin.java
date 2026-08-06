package io.github.earthshape.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep TerraBlender available for dependency linkage, but prevent every region
 * registration from constructing a parallel terrain/biome selector.
 */
@Pseudo
@Mixin(targets = "terrablender.api.Regions", remap = false)
public abstract class TerraBlenderRegionRegistryMixin {
   @Inject(
      method = {
         "register(Lnet/minecraft/resources/ResourceLocation;Lterrablender/api/Region;)V",
         "register(Lnet/minecraft/resources/ResourceLocation;ILterrablender/api/Region;)V",
         "register(Lterrablender/api/Region;)V"
      },
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private static void earthshape$discardRegionRegistration(CallbackInfo callback) {
      callback.cancel();
   }
}
