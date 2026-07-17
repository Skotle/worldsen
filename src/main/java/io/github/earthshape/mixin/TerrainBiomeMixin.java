package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** terrain.bmp is the sole source used to choose land biome classes. */
@Mixin(MultiNoiseBiomeSource.class)
public final class TerrainBiomeMixin {
    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("RETURN"), cancellable = true)
    private void earthshape$chooseTerrainBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> callback) {
        int blockX = quartX << 2;
        int blockZ = quartZ << 2;
        if (EarthShapeCompatibility.disablesWorldgen()) return;
        // Terralith (and other biome additions) may provide its own custom biome at this
        // position.  Preserve it before applying a source-river or terrain.bmp replacement:
        // otherwise a one-pixel river line would erase Terralith's terrain/feature contract.
        if (!isVanillaBiome(callback.getReturnValue())) return;
        if (EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && RiversMask.INSTANCE.isRiverCentreline(blockX, blockZ)) {
            callback.setReturnValue(findBiome(Biomes.RIVER, callback.getReturnValue()));
            return;
        }
        // Let biome-adding mods keep their own selected biome.  EarthShape still owns the
        // continent mask, heightmap density and source-river locations, but it no longer
        // replaces a valid non-vanilla biome with a vanilla terrain.bmp category.
        ClimateLayers layers = ClimateLayers.INSTANCE;
        // With map rivers enabled, vanilla river results are explicitly replaced outside the source centreline.
        if (EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() && callback.getReturnValue().is(Biomes.RIVER)) {
            callback.setReturnValue(mapTerrainBiome(layers, blockX, blockZ, callback.getReturnValue()));
            return;
        }
        // Temperature imagery is intentionally used only for open-ocean biome selection.
        // Land biomes continue to come exclusively from terrain.bmp.
        if (EarthShapeServerConfig.OCEAN_TEMPERATURE_ENABLED.get() && RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.25D) {
            // The expanded map has no source ocean-temperature pixels in its margins.
            // Keep those waters normal ocean; otherwise the old raster edge became a
            // conspicuous rectangular frozen-ocean field.
            ResourceKey<Biome> ocean = Biomes.OCEAN;
            if (layers.hasLegacyTemperature(blockX, blockZ)) {
                double temperature = layers.temperature(blockX, blockZ);
                ocean = temperature > 0.65D ? Biomes.WARM_OCEAN
                        : temperature > 0.15D ? Biomes.LUKEWARM_OCEAN
                        : temperature < -0.50D ? Biomes.FROZEN_OCEAN : Biomes.OCEAN;
            }
            callback.setReturnValue(findBiome(ocean, callback.getReturnValue()));
            return;
        }
        if (EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) callback.setReturnValue(mapTerrainBiome(layers, blockX, blockZ, callback.getReturnValue()));
    }

    private Holder<Biome> mapTerrainBiome(ClimateLayers layers, int blockX, int blockZ, Holder<Biome> fallback) {
        ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
        if (EarthShapeServerConfig.TUNDRA_TEMPERATURE_ENABLED.get()
                && layers.temperature(blockX, blockZ) < EarthShapeServerConfig.TUNDRA_TEMPERATURE_THRESHOLD.get()) {
            ResourceKey<Biome> coldBiome = switch (terrain) {
                case FOREST, JUNGLE -> Biomes.TAIGA;
                case HILLS -> Biomes.SNOWY_SLOPES;
                case MOUNTAIN -> Biomes.FROZEN_PEAKS;
                case PLAINS, WETLAND, CITY, SURROUNDING -> Biomes.SNOWY_PLAINS;
                case DESERT, WATER -> null;
            };
            if (coldBiome != null) return findBiome(coldBiome, fallback);
        }
        ResourceKey<Biome> biome = switch (terrain) {
            case DESERT -> layers.isMesaRegion(blockX, blockZ) ? Biomes.BADLANDS : Biomes.DESERT;
            case WETLAND -> Biomes.SWAMP;
            case FOREST -> Biomes.FOREST;
            case JUNGLE -> Biomes.JUNGLE;
            case HILLS -> Biomes.WINDSWEPT_HILLS;
            case MOUNTAIN -> Biomes.STONY_PEAKS;
            case PLAINS, CITY, SURROUNDING -> Biomes.PLAINS;
            case WATER -> Biomes.OCEAN;
        };
        return findBiome(biome, fallback);
    }

    private Holder<Biome> findBiome(ResourceKey<Biome> key, Holder<Biome> fallback) {
        return ((MultiNoiseBiomeSource) (Object) this).possibleBiomes().stream()
                .filter(holder -> holder.is(key)).findFirst().orElse(fallback);
    }

    private static boolean isVanillaBiome(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> "minecraft".equals(key.location().getNamespace())).orElse(false);
    }
}
