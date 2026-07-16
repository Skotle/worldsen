package io.github.earthshape.mixin;

import io.github.earthshape.worldgen.EarthShapeNoiseRouter;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;
    @Inject(method = "createNoiseChunk", at = @At("HEAD"))
    private void earthshape$installClimateGuidance(ChunkAccess chunk, StructureManager structures, Blender blender, RandomState random, CallbackInfoReturnable<NoiseChunk> callback) {
        boolean overworld = settings.is(NoiseGeneratorSettings.OVERWORLD)
                || settings.unwrapKey().map(key -> key.location().getPath().contains("overworld")).orElse(false);
        if (overworld) EarthShapeNoiseRouter.install(random);
    }
}
