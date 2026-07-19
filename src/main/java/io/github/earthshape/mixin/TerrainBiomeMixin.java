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
import net.neoforged.neoforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** terrain.bmp supplies the land class; climate only selects a compatible vanilla variant. */
@Mixin(MultiNoiseBiomeSource.class)
public final class TerrainBiomeMixin {
    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("RETURN"), cancellable = true)
    private void earthshape$chooseTerrainBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> callback) {
        int blockX = quartX << 2;
        int blockY = quartY << 2;
        int blockZ = quartZ << 2;
        if (EarthShapeCompatibility.disablesWorldgen()) return;
        ClimateLayers layers = ClimateLayers.INSTANCE;
        boolean desert = layers.terrainKind(blockX, blockZ) == ClimateLayers.TerrainKind.DESERT;
        boolean sourceRiver = blockY >= 48
                && RiversMask.INSTANCE.isInlandRiver(blockX, blockZ);
        if (sourceRiver && desert && EarthShapeServerConfig.DESERT_WATER_REDUCTION_ENABLED.get()) {
            sourceRiver = desertRiverWidth(RiversMask.INSTANCE.effectiveRiverWidthBlocks(blockX, blockZ)) > 0;
        }
        // On map land, the river layer is the only authority for river/lake biome classes.
        // This also catches biome-mod names such as "*_lake" or "*_river" without touching
        // wetland/swamp biomes that are intentionally selected by terrain.bmp.
        if (EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()
                && RiversMask.INSTANCE.sampleLand(blockX, blockZ) >= 0.5D
                && !sourceRiver && isInlandWaterBiome(callback.getReturnValue())) {
            callback.setReturnValue(mapTerrainBiome(layers, blockX, blockY, blockZ, callback.getReturnValue()));
            return;
        }
        // Terralith (and other biome additions) may provide its own custom biome at this
        // position.  Preserve it before applying a source-river or terrain.bmp replacement:
        // otherwise a one-pixel river line would erase Terralith's terrain/feature contract.
        if (!isVanillaBiome(callback.getReturnValue())) return;
        if (EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get() && blockY <= 32) {
            Holder<Biome> cave = mapCaveBiome(layers, blockX, blockY, blockZ, callback.getReturnValue());
            if (cave != callback.getReturnValue()) {
                callback.setReturnValue(cave);
                return;
            }
        }
        if (sourceRiver) {
            callback.setReturnValue(findBiome(Biomes.RIVER, callback.getReturnValue()));
            return;
        }
        // Let biome-adding mods keep their own selected biome.  EarthShape still owns the
        // continent mask, heightmap density and source-river locations, but it no longer
        // replaces a valid non-vanilla biome with a vanilla terrain.bmp category.
        // With map rivers enabled, vanilla river results are explicitly replaced outside the source centreline.
        // The world map is the sole river authority.  Never retain a noise-selected vanilla
        // river outside a verified source stroke, even if an older serverconfig has the
        // optional source-river feature toggle disabled.
        if (isVanillaRiver(callback.getReturnValue())) {
            callback.setReturnValue(mapTerrainBiome(layers, blockX, blockY, blockZ, callback.getReturnValue()));
            return;
        }
        // Temperature imagery is intentionally used only for open-ocean biome selection.
        // Land biomes continue to come exclusively from terrain.bmp.
        if (EarthShapeServerConfig.OCEAN_TEMPERATURE_ENABLED.get() && RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.25D) {
            // The expanded map has no source ocean-temperature pixels in its margins.
            // Keep those waters normal ocean; otherwise the old raster edge became a
            // conspicuous rectangular frozen-ocean field.
            // Keep expanded-map margins at a neutral ocean temperature, but use the
            // full vanilla ocean family (including deep/cold variants) inside the
            // original temperature-raster coverage.
            double temperature = layers.hasLegacyTemperature(blockX, blockZ)
                    ? layers.temperature(blockX, blockZ) : 0.0D;
            callback.setReturnValue(oceanBiome(temperature, blockX, blockZ, callback.getReturnValue()));
            return;
        }
        if (EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()) callback.setReturnValue(mapTerrainBiome(layers, blockX, blockY, blockZ, callback.getReturnValue()));
    }

    private Holder<Biome> mapTerrainBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> fallback) {
        ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
        double temperature = layers.temperature(blockX, blockZ);
        if (isCoastalLand(blockX, blockZ)) {
            if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) return findBiome(Biomes.STONY_SHORE, fallback);
            return findBiome(temperature < -0.25D ? Biomes.SNOWY_BEACH : Biomes.BEACH, fallback);
        }
        int region = regionalVariant(blockX, blockZ);
        return switch (terrain) {
            case DESERT -> layers.isMesaRegion(blockX, blockZ)
                    ? findBiome(region % 10 == 0 ? Biomes.ERODED_BADLANDS : region % 5 == 0 ? Biomes.WOODED_BADLANDS : Biomes.BADLANDS, fallback)
                    : findBiome(Biomes.DESERT, fallback);
            case WETLAND -> findBiome(temperature > 0.30D ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP, fallback);
            case FOREST -> forestBiome(temperature, region, fallback);
            case JUNGLE -> findBiome(region % 12 == 0 ? Biomes.BAMBOO_JUNGLE : region % 6 == 0 ? Biomes.SPARSE_JUNGLE : Biomes.JUNGLE, fallback);
            case HILLS -> temperature < -0.45D ? findBiome(Biomes.SNOWY_SLOPES, fallback)
                    : temperature < -0.20D ? findBiome(Biomes.GROVE, fallback)
                    : temperature > 0.45D ? findBiome(Biomes.WINDSWEPT_SAVANNA, fallback)
                    : findBiome(region % 5 == 0 ? Biomes.WINDSWEPT_FOREST : Biomes.WINDSWEPT_HILLS, fallback);
            case MOUNTAIN -> temperature < -0.55D ? findBiome(Biomes.FROZEN_PEAKS, fallback)
                    : temperature < -0.25D ? findBiome(Biomes.JAGGED_PEAKS, fallback)
                    : findBiome(Biomes.STONY_PEAKS, fallback);
            case PLAINS, CITY, SURROUNDING -> plainsBiome(temperature, region, fallback);
            case WATER -> oceanBiome(temperature, blockX, blockZ, fallback);
        };
    }

    private Holder<Biome> forestBiome(double temperature, int region, Holder<Biome> fallback) {
        if (temperature < -0.55D) return findBiome(Biomes.SNOWY_TAIGA, fallback);
        if (temperature < -0.25D) return findBiome(region % 6 == 0 ? Biomes.OLD_GROWTH_SPRUCE_TAIGA : region % 4 == 0 ? Biomes.OLD_GROWTH_PINE_TAIGA : Biomes.TAIGA, fallback);
        return findBiome(region % 14 == 0 ? Biomes.FLOWER_FOREST : region % 9 == 0 ? Biomes.BIRCH_FOREST
                : region % 7 == 0 ? Biomes.DARK_FOREST : region % 19 == 0 ? Biomes.OLD_GROWTH_BIRCH_FOREST : Biomes.FOREST, fallback);
    }

    private Holder<Biome> plainsBiome(double temperature, int region, Holder<Biome> fallback) {
        if (temperature < -0.55D) return findBiome(region % 17 == 0 ? Biomes.ICE_SPIKES : Biomes.SNOWY_PLAINS, fallback);
        if (temperature < -0.30D) return findBiome(Biomes.SNOWY_PLAINS, fallback);
        if (temperature > 0.45D) return findBiome(region % 6 == 0 ? Biomes.SAVANNA_PLATEAU : Biomes.SAVANNA, fallback);
        return findBiome(region % 16 == 0 ? Biomes.SUNFLOWER_PLAINS : Biomes.PLAINS, fallback);
    }

    private Holder<Biome> mapCaveBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> fallback) {
        int variant = regionalVariant(blockX, blockZ) % 8;
        if (variant > 1) return fallback;
        ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
        if (blockY < -24 && terrain == ClimateLayers.TerrainKind.MOUNTAIN) return findBiome(Biomes.DEEP_DARK, fallback);
        return terrain == ClimateLayers.TerrainKind.FOREST || terrain == ClimateLayers.TerrainKind.JUNGLE || terrain == ClimateLayers.TerrainKind.WETLAND
                ? findBiome(Biomes.LUSH_CAVES, fallback) : findBiome(Biomes.DRIPSTONE_CAVES, fallback);
    }

    private Holder<Biome> oceanBiome(double temperature, int blockX, int blockZ, Holder<Biome> fallback) {
        boolean deep = isOpenOcean(blockX, blockZ);
        if (temperature > 0.65D) return findBiome(Biomes.WARM_OCEAN, fallback);
        if (temperature > 0.15D) return findBiome(deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN, fallback);
        if (temperature > -0.15D) return findBiome(deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN, fallback);
        if (temperature > -0.50D) return findBiome(deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN, fallback);
        return findBiome(deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN, fallback);
    }

    private static boolean isCoastalLand(int blockX, int blockZ) {
        if (RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.5D) return false;
        int distance = 28;
        return RiversMask.INSTANCE.sampleLand(blockX - distance, blockZ) < 0.5D || RiversMask.INSTANCE.sampleLand(blockX + distance, blockZ) < 0.5D
                || RiversMask.INSTANCE.sampleLand(blockX, blockZ - distance) < 0.5D || RiversMask.INSTANCE.sampleLand(blockX, blockZ + distance) < 0.5D;
    }

    private static boolean isOpenOcean(int blockX, int blockZ) {
        int distance = 180;
        return RiversMask.INSTANCE.sampleLand(blockX - distance, blockZ) < 0.25D && RiversMask.INSTANCE.sampleLand(blockX + distance, blockZ) < 0.25D
                && RiversMask.INSTANCE.sampleLand(blockX, blockZ - distance) < 0.25D && RiversMask.INSTANCE.sampleLand(blockX, blockZ + distance) < 0.25D;
    }

    /** Biome variants span 1024 blocks; never the former eight-chunk mosaic. */
    private static int regionalVariant(int blockX, int blockZ) {
        long value = ((long) (blockX >> 10) * 341873128712L) ^ ((long) (blockZ >> 10) * 132897987541L) ^ 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int) (value ^ value >>> 32) & Integer.MAX_VALUE;
    }

    private Holder<Biome> findBiome(ResourceKey<Biome> key, Holder<Biome> fallback) {
        return ((MultiNoiseBiomeSource) (Object) this).possibleBiomes().stream()
                .filter(holder -> holder.is(key)).findFirst().orElse(fallback);
    }

    private static boolean isVanillaBiome(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> "minecraft".equals(key.location().getNamespace())).orElse(false);
    }

    private static boolean isVanillaRiver(Holder<Biome> biome) {
        return biome.is(Biomes.RIVER) || biome.is(Biomes.FROZEN_RIVER);
    }

    private static boolean isInlandWaterBiome(Holder<Biome> biome) {
        if (biome.is(Tags.Biomes.IS_RIVER)) return true;
        return biome.unwrapKey().map(key -> {
            String path = key.location().getPath();
            return path.contains("river") || path.contains("lake");
        }).orElse(false);
    }

    private static int desertRiverWidth(int sourceWidth) {
        // Existing worlds can retain old serverconfig values, so retain strict safe limits
        // until the user explicitly changes the new desert-water settings.
        int scaled = (int) Math.round(sourceWidth * Math.min(0.30D, EarthShapeServerConfig.DESERT_RIVER_WIDTH_SCALE.get()));
        if (scaled < Math.max(20, EarthShapeServerConfig.DESERT_MINIMUM_RIVER_WIDTH_BLOCKS.get())) return 0;
        return Math.min(scaled, EarthShapeServerConfig.DESERT_MAXIMUM_RIVER_WIDTH_BLOCKS.get());
    }
}
