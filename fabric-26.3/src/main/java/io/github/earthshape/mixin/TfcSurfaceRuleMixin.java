package io.github.earthshape.mixin;

import io.github.earthshape.compat.TfcSurfaceRules;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
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
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;<init>(Lnet/minecraft/world/level/levelgen/NoiseSettings;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/levelgen/NoiseRouter;Lnet/minecraft/core/Holder;Ljava/util/List;IZLjava/util/Optional;ZLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings$DebugFunctions;)V"),
      index = 4
   )
   private static net.minecraft.core.Holder<MaterialRule> earthshape$applyTfcLayerSurfaces(
      net.minecraft.core.Holder<MaterialRule> base
   ) {
      HolderGetter<Biome> biomes = earthshape$biomes.get();
      if (biomes != null && FabricLoader.getInstance().isModLoaded("tfc")) {
         return net.minecraft.core.Holder.direct(TfcSurfaceRules.apply(base.value(), biomes));
      }
      return base;
   }
}
