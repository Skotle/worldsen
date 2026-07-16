package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.BadlandsRegions;
import io.github.earthshape.map.ClimateLayers;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops vanilla's climate table from placing Badlands outside the real-world whitelist. */
@Mixin(MultiNoiseBiomeSource.class)
abstract class MultiNoiseBiomeSourceMixin {
    @Shadow protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("RETURN"), cancellable = true)
    private void earthshape$restrictBadlands(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
        if (!EarthShapeServerConfig.ENABLE_DETAILED_TERRAIN.get() || BadlandsRegions.containsBlock(x * 4, z * 4) || !isBadlands(callback.getReturnValue())) return;
        this.collectPossibleBiomes().filter(biome -> biome.is(Biomes.DESERT)).findFirst().ifPresent(callback::setReturnValue);
    }

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("RETURN"), cancellable = true)
    private void earthshape$keepTreeBiomesInsideTreeLayer(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
        if (!EarthShapeServerConfig.USE_TREES_LAYER.get() || ClimateLayers.INSTANCE.hasTreeCover(x * 4, z * 4) || !isTreeBiome(callback.getReturnValue())) return;
        this.collectPossibleBiomes().filter(biome -> biome.is(Biomes.PLAINS)).findFirst().ifPresent(callback::setReturnValue);
    }

    private static boolean isBadlands(Holder<Biome> biome) {
        return biome.is(Biomes.BADLANDS) || biome.is(Biomes.WOODED_BADLANDS) || biome.is(Biomes.ERODED_BADLANDS);
    }

    private static boolean isTreeBiome(Holder<Biome> biome) {
        return biome.is(Biomes.FOREST) || biome.is(Biomes.FLOWER_FOREST) || biome.is(Biomes.BIRCH_FOREST)
                || biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
                || biome.is(Biomes.TAIGA) || biome.is(Biomes.SNOWY_TAIGA)
                || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
                || biome.is(Biomes.JUNGLE) || biome.is(Biomes.SPARSE_JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE)
                || biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)
                || biome.is(Biomes.WINDSWEPT_FOREST) || biome.is(Biomes.MANGROVE_SWAMP)
                || biome.is(Biomes.CHERRY_GROVE) || biome.is(Biomes.GROVE);
    }
}
