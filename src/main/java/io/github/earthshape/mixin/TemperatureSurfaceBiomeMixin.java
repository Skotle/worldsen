package io.github.earthshape.mixin;

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

/** Applies the nine-colour global temperature bands to only surface vanilla biomes. */
// Run before the terrain selector: its cancellable RETURN injection otherwise exits the
// method before this full-map temperature selector can see the chosen surface biome.
@Mixin(value = MultiNoiseBiomeSource.class, priority = 2000)
public final class TemperatureSurfaceBiomeMixin {
    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("RETURN"), cancellable = true)
    private void earthshape$applySurfaceTemperature(int quartX, int quartY, int quartZ, Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> callback) {
        int x = quartX << 2, y = quartY << 2, z = quartZ << 2;
        if (y < 48 || !isVanilla(callback.getReturnValue())) return;
        ClimateLayers layers = ClimateLayers.INSTANCE;
        if (RiversMask.INSTANCE.sampleLand(x, z) < 0.5D) {
            // White water in the temperature raster is not a climate class.  Oceans are
            // selected only from geographic latitude, independent of nearby land colours.
            callback.setReturnValue(ocean(layers.oceanTemperature(z), callback.getReturnValue()));
            return;
        }
        double temperature = layers.temperature(x, z);
        // Source rivers retain the real river biome selected by the map layer.
        if (RiversMask.INSTANCE.isInlandRiver(x, z)) return;
        callback.setReturnValue(land(layers.terrainKind(x, z), temperature, callback.getReturnValue()));
    }

    private Holder<Biome> land(ClimateLayers.TerrainKind terrain, double t, Holder<Biome> fallback) {
        int band = temperatureBand(t);
        return switch (terrain) {
            case DESERT -> biome(band <= 1 ? Biomes.SNOWY_PLAINS : band <= 3 ? Biomes.PLAINS
                    : band <= 5 ? Biomes.SAVANNA : band == 6 ? Biomes.SAVANNA_PLATEAU
                    : band == 7 ? Biomes.BADLANDS : Biomes.DESERT, fallback);
            case WETLAND -> biome(band <= 1 ? Biomes.SNOWY_PLAINS : band >= 5 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP, fallback);
            case FOREST -> forest(band, fallback);
            case JUNGLE -> biome(band <= 1 ? Biomes.SNOWY_TAIGA : band <= 2 ? Biomes.TAIGA
                    : band <= 3 ? Biomes.BIRCH_FOREST : band <= 4 ? Biomes.FOREST
                    : band == 5 ? Biomes.JUNGLE : band == 6 ? Biomes.SPARSE_JUNGLE
                    : band == 7 ? Biomes.WOODED_BADLANDS : Biomes.DESERT, fallback);
            case HILLS -> biome(band == 0 ? Biomes.JAGGED_PEAKS : band == 1 ? Biomes.SNOWY_SLOPES
                    : band == 2 ? Biomes.GROVE : band <= 4 ? Biomes.WINDSWEPT_HILLS
                    : band == 5 ? Biomes.SPARSE_JUNGLE : band == 6 ? Biomes.WINDSWEPT_SAVANNA
                    : band == 7 ? Biomes.ERODED_BADLANDS : Biomes.DESERT, fallback);
            case MOUNTAIN -> biome(band == 0 ? Biomes.FROZEN_PEAKS : band == 1 ? Biomes.JAGGED_PEAKS
                    : band == 2 ? Biomes.SNOWY_SLOPES : band <= 4 ? Biomes.STONY_PEAKS
                    : band == 5 ? Biomes.WINDSWEPT_HILLS : band == 6 ? Biomes.WINDSWEPT_SAVANNA
                    : band == 7 ? Biomes.ERODED_BADLANDS : Biomes.DESERT, fallback);
            case PLAINS, CITY, SURROUNDING -> plains(band, fallback);
            case WATER -> ocean(band, fallback);
        };
    }

    private Holder<Biome> forest(int band, Holder<Biome> fallback) {
        return biome(band == 0 ? Biomes.ICE_SPIKES : band == 1 ? Biomes.SNOWY_TAIGA
                : band == 2 ? Biomes.OLD_GROWTH_SPRUCE_TAIGA : band == 3 ? Biomes.OLD_GROWTH_PINE_TAIGA
                : band == 4 ? Biomes.FOREST : band == 5 ? Biomes.JUNGLE : band == 6 ? Biomes.SPARSE_JUNGLE
                : band == 7 ? Biomes.WOODED_BADLANDS : Biomes.DESERT, fallback);
    }

    private Holder<Biome> plains(int band, Holder<Biome> fallback) {
        return biome(band == 0 ? Biomes.ICE_SPIKES : band == 1 ? Biomes.SNOWY_PLAINS
                : band == 2 ? Biomes.TAIGA : band == 3 ? Biomes.BIRCH_FOREST
                : band == 4 ? Biomes.PLAINS : band == 5 ? Biomes.SPARSE_JUNGLE
                : band == 6 ? Biomes.SAVANNA : band == 7 ? Biomes.BADLANDS : Biomes.DESERT, fallback);
    }

    private Holder<Biome> ocean(double t, Holder<Biome> fallback) { return ocean(temperatureBand(t), fallback); }

    private Holder<Biome> ocean(int band, Holder<Biome> fallback) {
        return biome(band == 0 ? Biomes.DEEP_FROZEN_OCEAN : band == 1 ? Biomes.FROZEN_OCEAN
                : band == 2 ? Biomes.DEEP_COLD_OCEAN : band == 3 ? Biomes.COLD_OCEAN
                : band == 4 ? Biomes.OCEAN : band == 5 ? Biomes.LUKEWARM_OCEAN
                : Biomes.WARM_OCEAN, fallback);
    }

    /** Nine raster colours: -30↓, -30~-15, -15~0, 0~15, 15~25, 25~30, 30~35, 35~40, 40↑. */
    private static int temperatureBand(double t) {
        if (t < -0.875D) return 0;
        if (t < -0.625D) return 1;
        if (t < -0.375D) return 2;
        if (t < -0.125D) return 3;
        if (t < 0.125D) return 4;
        if (t < 0.375D) return 5;
        if (t < 0.625D) return 6;
        if (t < 0.875D) return 7;
        return 8;
    }

    private Holder<Biome> biome(ResourceKey<Biome> key, Holder<Biome> fallback) {
        return ((MultiNoiseBiomeSource) (Object) this).possibleBiomes().stream().filter(holder -> holder.is(key)).findFirst().orElse(fallback);
    }

    private static boolean isVanilla(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> "minecraft".equals(key.location().getNamespace())).orElse(false);
    }
}
