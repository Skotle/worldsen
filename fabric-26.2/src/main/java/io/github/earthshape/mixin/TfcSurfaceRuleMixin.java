package io.github.earthshape.mixin;

import io.github.earthshape.compat.TfcSurfaceRules;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies TFC desert material to EarthShape's map-selected TFC desert biomes. */
@Mixin(NoiseGeneratorSettings.class)
public abstract class TfcSurfaceRuleMixin {
   private static final ThreadLocal<HolderGetter<Biome>> earthshape$biomes = new ThreadLocal<>();

   @Inject(method = "overworld", at = @At("HEAD"))
   private static void earthshape$captureBiomeLookup(
      BootstrapContext<NoiseGeneratorSettings> context, boolean largeBiomes, boolean amplified,
      CallbackInfoReturnable<NoiseGeneratorSettings> callback
   ) {
      earthshape$biomes.set(context.lookup(Registries.BIOME));
   }

   @Inject(method = "overworld", at = @At("RETURN"))
   private static void earthshape$clearBiomeLookup(
      BootstrapContext<NoiseGeneratorSettings> context, boolean largeBiomes, boolean amplified,
      CallbackInfoReturnable<NoiseGeneratorSettings> callback
   ) {
      earthshape$biomes.remove();
   }

   @ModifyArg(
      method = "overworld",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;<init>(Lnet/minecraft/world/level/levelgen/NoiseSettings;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/levelgen/NoiseRouter;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;Ljava/util/List;IZZZZ)V"),
      index = 4
   )
   private static SurfaceRules.RuleSource earthshape$applyTfcLayerSurfaces(
      SurfaceRules.RuleSource base
   ) {
      HolderGetter<Biome> biomes = earthshape$biomes.get();
      if (biomes != null && FabricLoader.getInstance().isModLoaded("tfc")) {
         return TfcSurfaceRules.apply(base, biomes);
      }
      return base;
   }
}
