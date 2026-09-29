package io.github.earthshape.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves TerraBlender's API classes available to dependent mods, but prevents
 * its server-start initializer from activating positional biome trees, appended
 * biome candidates, End selectors, or namespaced surface-rule categories.
 */
@Pseudo
@Mixin(targets = "terrablender.util.LevelUtils", priority = 3000, remap = false)
public abstract class TerraBlenderInitializationMixin {
   @Inject(
      method = "initializeOnServerStart(Lnet/minecraft/server/MinecraftServer;)V",
      at = @At("HEAD"),
      cancellable = true,
      require = 1
   )
   private static void earthshape$disableTerraBlenderWorldgen(CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(
      method = "initializeBiomes(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/chunk/ChunkGenerator;J)V",
      at = @At("HEAD"),
      cancellable = true,
      require = 1
   )
   private static void earthshape$disableTerraBlenderBiomeInitialization(CallbackInfo callback) {
      callback.cancel();
   }
}
