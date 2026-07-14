package io.github.earthshape.mixin;

import io.github.earthshape.EarthShape;
import io.github.earthshape.worldgen.EarthShapeNoiseRouter;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installs the live router immediately before a normal Overworld generator creates a NoiseChunk. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    private static final AtomicBoolean INSTALLED_LOGGED = new AtomicBoolean();

    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    @Inject(method = "createNoiseChunk", at = @At("HEAD"))
    private void earthshape$installOverworldRouter(
            ChunkAccess chunk,
            StructureManager structureManager,
            Blender blender,
            RandomState random,
            CallbackInfoReturnable<NoiseChunk> callback
    ) {
        if (settings.is(NoiseGeneratorSettings.OVERWORLD)
                || settings.is(NoiseGeneratorSettings.LARGE_BIOMES)
                || settings.is(NoiseGeneratorSettings.AMPLIFIED)) {
            RandomStateAccessor accessor = (RandomStateAccessor) (Object) random;
            accessor.earthshape$setRouter(EarthShapeNoiseRouter.wrap(random.router()));
            if (INSTALLED_LOGGED.compareAndSet(false, true)) {
                EarthShape.LOGGER.info("[EarthShape] Live Overworld router installed; generating chunk {}.", chunk.getPos());
            }
        }
    }
}
