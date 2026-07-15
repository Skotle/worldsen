package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.worldgen.EarthSurfaceDepthDensity;
import io.github.earthshape.worldgen.TerralithIntegration;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Terralith cave biome entries from being selected at an EarthShape surface. */
@Mixin(MultiNoiseBiomeSource.class)
abstract class MultiNoiseBiomeSourceMixin {
    private static final float[] SURFACE_DEPTH_RETRIES = {0.05F, -0.05F, -0.15F, 0.10F, -0.30F};

    @Shadow public abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint);

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("RETURN"), cancellable = true)
    private void earthshape$replaceSurfaceCaveBiome(
            int quartX,
            int quartY,
            int quartZ,
            Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> callback
    ) {
        if (!EarthShapeConfig.ENABLED.get() || !TerralithIntegration.isActive()) return;
        int blockX = quartX << 2;
        int blockY = quartY << 2;
        int blockZ = quartZ << 2;
        double surfaceY = EarthSurfaceDepthDensity.mappedSurfaceY(blockX, blockZ);
        if (blockY < surfaceY - 24.0D || blockY > surfaceY + 24.0D || !earthshape$isCave(callback.getReturnValue())) return;

        Climate.TargetPoint point = sampler.sample(quartX, quartY, quartZ);
        // Terralith's surface table is centred on zero, while cave entries occupy several
        // overlapping ranges.  Try nearby surface points in order and only accept a non-cave
        // result; the previous one-shot retry could land on another cave entry and leak it.
        for (float depth : SURFACE_DEPTH_RETRIES) {
            Holder<Biome> replacement = getNoiseBiome(new Climate.TargetPoint(
                    point.temperature(), point.humidity(), point.continentalness(), point.erosion(),
                    Climate.quantizeCoord(depth), point.weirdness()
            ));
            if (!earthshape$isCave(replacement)) {
                callback.setReturnValue(replacement);
                return;
            }
        }
    }

    private static boolean earthshape$isCave(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location().getPath().contains("cave")).orElse(false);
    }
}
