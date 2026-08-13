package io.github.earthshape.mixin;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TFC features that consume its chunk data require TFC's own chunk generator.
 * The EarthShape replacement of {@code tfc:overworld} intentionally uses the
 * vanilla noise generator, so those features must not run for that preset.
 */
@Pseudo
@Mixin(
   targets = {
      "net.dries007.tfc.world.feature.BouldersFeature",
      "net.dries007.tfc.world.feature.DynamicDensityRandomPatchFeature",
      "net.dries007.tfc.world.feature.ErosionFeature",
      "net.dries007.tfc.world.feature.FissureFeature",
      "net.dries007.tfc.world.feature.HotSpringFeature",
      "net.dries007.tfc.world.feature.LooseRockFeature",
      "net.dries007.tfc.world.feature.NoisyMultipleFeature",
      "net.dries007.tfc.world.feature.SeaStacksFeature",
      "net.dries007.tfc.world.feature.SoilForestAreaFeature",
      "net.dries007.tfc.world.feature.SpringFeature",
      "net.dries007.tfc.world.feature.TidePoolFeature",
      "net.dries007.tfc.world.feature.cave.CaveColumnFeature",
      "net.dries007.tfc.world.feature.cave.CaveSpikesFeature",
      "net.dries007.tfc.world.feature.cave.IceCaveFeature",
      "net.dries007.tfc.world.feature.tree.ForestFeature"
   },
   remap = false
)
public abstract class TfcGeneratorFeatureGuardMixin {
   @Inject(method = "place", at = @At("HEAD"), cancellable = true, remap = false)
   private void earthshape$skipWithoutTfcGenerator(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> callback) {
      if (!earthshape$implementsTfcGeneratorExtension(context.chunkGenerator().getClass())) {
         callback.setReturnValue(false);
      }
   }

   private static boolean earthshape$implementsTfcGeneratorExtension(Class<?> type) {
      while (type != null) {
         for (Class<?> implemented : type.getInterfaces()) {
            if (earthshape$isTfcGeneratorExtension(implemented)) {
               return true;
            }
         }
         type = type.getSuperclass();
      }
      return false;
   }

   private static boolean earthshape$isTfcGeneratorExtension(Class<?> type) {
      if (type.getName().equals("net.dries007.tfc.world.ChunkGeneratorExtension")) {
         return true;
      }
      for (Class<?> parent : type.getInterfaces()) {
         if (earthshape$isTfcGeneratorExtension(parent)) {
            return true;
         }
      }
      return false;
   }
}
