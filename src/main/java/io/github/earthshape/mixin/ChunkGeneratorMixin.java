package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.map.EarthMapService;
import io.github.earthshape.worldgen.EarthShapeCompatibility;
import io.github.earthshape.worldgen.EarthShapeNoiseRouter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents structure starts in map ocean before a structure can place blocks above the water. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void earthshape$skipMappedOceanStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager,
            CallbackInfo callback
    ) {
        if (!EarthShapeConfig.ENABLED.get() || !((Object) this instanceof NoiseBasedChunkGenerator generator)) return;
        if (!EarthShapeCompatibility.supports(((NoiseBasedChunkGeneratorAccessor) (Object) generator).earthshape$settings())) return;

        EarthShapeNoiseRouter.install(structureState.randomState());
        int centerX = chunk.getPos().getMinBlockX() + 8;
        int centerZ = chunk.getPos().getMinBlockZ() + 8;
        // A distance-only check left the source map's narrow coastal water cells eligible for
        // Terralith's large structures, which could then spill a whole stone/cave complex into
        // open sea.  Structure starts must respect the discrete source-water topology.
        if (!EarthMapService.INSTANCE.isLandPixel(centerX, centerZ)) callback.cancel();
    }

    /**
     * Features are placed after density filling.  A few underwater features can otherwise
     * leave blocks and vegetation at the water surface, which map renderers show as scattered
     * blue islands.  Source-water cells are authoritative: clear any generated blocks above
     * sea level as well, so a Terralith structure started just inland cannot spill into sea.
     */
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void earthshape$sealDecoratedOpenOcean(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            CallbackInfo callback
    ) {
        if (!EarthShapeConfig.ENABLED.get() || !((Object) this instanceof NoiseBasedChunkGenerator generator)) return;
        if (!EarthShapeCompatibility.supports(((NoiseBasedChunkGeneratorAccessor) (Object) generator).earthshape$settings())) return;

        int floorY = EarthShapeConfig.OCEAN_FLOOR_Y.get();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                if (EarthMapService.INSTANCE.isLandPixel(x, z)) continue;
                for (int y = floorY + 1; y <= 63; y++) {
                    position.set(x, y, z);
                    earthshape$replaceBlock(chunk, position, Blocks.WATER.defaultBlockState());
                }
                for (int y = 64; y < level.getMaxBuildHeight(); y++) {
                    position.set(x, y, z);
                    if (!chunk.getBlockState(position).isAir()) {
                        earthshape$replaceBlock(chunk, position, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** ChunkAccess#setBlockState does not discard pending structure block-entity NBT by itself. */
    private static void earthshape$replaceBlock(ChunkAccess chunk, BlockPos position, BlockState state) {
        if (chunk.getBlockState(position).hasBlockEntity() || chunk.getBlockEntityNbt(position) != null) {
            chunk.removeBlockEntity(position);
        }
        chunk.setBlockState(position, state, false);
    }
}
