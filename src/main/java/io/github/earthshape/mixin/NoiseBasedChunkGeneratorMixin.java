package io.github.earthshape.mixin;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.map.EarthMapService;
import io.github.earthshape.worldgen.EarthShapeCompatibility;
import io.github.earthshape.worldgen.EarthShapeNoiseRouter;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
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
        if (EarthShapeCompatibility.supports(settings)) {
            EarthShapeNoiseRouter.install(random);
            if (INSTALLED_LOGGED.compareAndSet(false, true)) {
                EarthShape.LOGGER.info("[EarthShape] Live Overworld router installed; generating chunk {}.", chunk.getPos());
            }
        }
    }

    /**
     * The aquifer can still choose air at the boundary between its low-resolution cells even
     * when floodedness noise says "ocean".  Seal those air cells after density generation so a
     * mapped sea is one continuous water column rather than a patchwork of aquifer pockets.
     */
    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void earthshape$sealOceanWaterColumns(
            Blender blender,
            RandomState random,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback
    ) {
        if (!EarthShapeConfig.ENABLED.get() || !EarthShapeCompatibility.supports(settings)) return;
        callback.setReturnValue(callback.getReturnValue().thenApply(this::earthshape$fillMappedOceanAir));
    }

    private ChunkAccess earthshape$fillMappedOceanAir(ChunkAccess chunk) {
        int floorY = EarthShapeConfig.OCEAN_FLOOR_Y.get();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                if (EarthMapService.INSTANCE.isLandPixel(x, z)
                        && EarthMapService.INSTANCE.sample(0L, x, z).signedDistanceBlocks() >= EarthShapeConfig.OCEAN_INSET_BLOCKS.get()) continue;
                for (int y = floorY; y <= 63; y++) {
                    position.set(x, y, z);
                    if (chunk.getBlockState(position).isAir()) {
                        chunk.setBlockState(position, Blocks.WATER.defaultBlockState(), false);
                    }
                }
            }
        }
        return chunk;
    }

    /**
     * Carvers run after the terrain surface has been formed, so ravines and large cave mouths
     * can punch straight through the map-controlled ground.  Keep normal density caves, but
     * disable this surface-breaking carving pass for the shaped Overworld.
     */
    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void earthshape$skipOceanCarvers(
            WorldGenRegion level,
            long seed,
            RandomState random,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback
    ) {
        if (!EarthShapeConfig.ENABLED.get() || !EarthShapeCompatibility.supports(settings)) return;
        callback.cancel();
    }

    /** Retained for compatibility with worlds that invoke another generator's carver path. */
    @Inject(method = "applyCarvers", at = @At("TAIL"))
    private void earthshape$refillOceanAfterCarvers(
            WorldGenRegion level,
            long seed,
            RandomState random,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback
    ) {
        if (EarthShapeConfig.ENABLED.get() && EarthShapeCompatibility.supports(settings)) {
            earthshape$fillMappedOceanAir(chunk);
        }
    }
}
