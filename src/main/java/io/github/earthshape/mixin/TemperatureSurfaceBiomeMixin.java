/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.Holder
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.biome.Biomes
 *  net.minecraft.world.level.biome.Climate$Sampler
 *  net.minecraft.world.level.biome.MultiNoiseBiomeSource
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
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

@Mixin(value={MultiNoiseBiomeSource.class}, priority=2000)
public final class TemperatureSurfaceBiomeMixin {
    @Inject(method={"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"}, at={@At(value="RETURN")}, cancellable=true)
    private void earthshape$applySurfaceTemperature(int quartX, int quartY, int quartZ, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
        int x = quartX << 2;
        int y = quartY << 2;
        int z = quartZ << 2;
        if (y >= 48 && TemperatureSurfaceBiomeMixin.isVanilla((Holder<Biome>)((Holder)callback.getReturnValue()))) {
            ClimateLayers layers = ClimateLayers.INSTANCE;
            if (RiversMask.INSTANCE.sampleLand(x, z) < 0.5) {
                callback.setReturnValue(this.ocean(layers.temperature(x, z), (Holder<Biome>)((Holder)callback.getReturnValue())));
            } else {
                double temperature = layers.temperature(x, z);
                if (!RiversMask.INSTANCE.isInlandRiver(x, z)) {
                    callback.setReturnValue(this.land(layers, layers.terrainKind(x, z), temperature, x, z, (Holder<Biome>)((Holder)callback.getReturnValue())));
                }
            }
        }
    }

    private Holder<Biome> land(ClimateLayers layers, ClimateLayers.TerrainKind terrain, double t, int x, int z, Holder<Biome> fallback) {
        int band = TemperatureSurfaceBiomeMixin.temperatureBand(t);
        boolean frozenPeaksAllowed = layers.isUltraMountain(x, z) || layers.isPolarTemperatureZone(x, z);
        return switch (terrain) {
            default -> throw new MatchException(null, null);
            case ClimateLayers.TerrainKind.DESERT -> this.biome((ResourceKey<Biome>)(band <= 1 ? Biomes.SNOWY_PLAINS : (band <= 3 ? Biomes.PLAINS : (band <= 5 ? Biomes.SAVANNA : (band == 6 ? Biomes.SAVANNA_PLATEAU : (band == 7 ? Biomes.BADLANDS : Biomes.DESERT))))), fallback);
            case ClimateLayers.TerrainKind.WETLAND -> this.biome((ResourceKey<Biome>)(band <= 1 ? Biomes.SNOWY_PLAINS : (band >= 5 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP)), fallback);
            case ClimateLayers.TerrainKind.FOREST -> this.forest(band, fallback);
            case ClimateLayers.TerrainKind.JUNGLE -> this.biome((ResourceKey<Biome>)(band <= 1 ? Biomes.SNOWY_TAIGA : (band <= 2 ? Biomes.TAIGA : (band <= 3 ? Biomes.BIRCH_FOREST : (band <= 4 ? Biomes.FOREST : (band == 5 ? Biomes.JUNGLE : (band == 6 ? Biomes.SPARSE_JUNGLE : (band == 7 ? Biomes.WOODED_BADLANDS : Biomes.DESERT))))))), fallback);
            case ClimateLayers.TerrainKind.HILLS -> this.biome((ResourceKey<Biome>)(band == 0 ? Biomes.JAGGED_PEAKS : (band == 1 ? Biomes.SNOWY_SLOPES : (band == 2 ? Biomes.GROVE : (band <= 4 ? Biomes.WINDSWEPT_HILLS : (band == 5 ? Biomes.SPARSE_JUNGLE : (band == 6 ? Biomes.WINDSWEPT_SAVANNA : (band == 7 ? Biomes.ERODED_BADLANDS : Biomes.DESERT))))))), fallback);
            case ClimateLayers.TerrainKind.MOUNTAIN -> this.biome((ResourceKey<Biome>)(frozenPeaksAllowed ? Biomes.FROZEN_PEAKS : Biomes.STONY_PEAKS), fallback);
            case ClimateLayers.TerrainKind.PLAINS, ClimateLayers.TerrainKind.CITY, ClimateLayers.TerrainKind.SURROUNDING -> this.plains(band, fallback);
            case ClimateLayers.TerrainKind.WATER -> this.ocean(band, fallback);
        };
    }

    private Holder<Biome> forest(int band, Holder<Biome> fallback) {
        return this.biome((ResourceKey<Biome>)(band == 0 ? Biomes.ICE_SPIKES : (band == 1 ? Biomes.SNOWY_TAIGA : (band == 2 ? Biomes.OLD_GROWTH_SPRUCE_TAIGA : (band == 3 ? Biomes.OLD_GROWTH_PINE_TAIGA : (band == 4 ? Biomes.FOREST : (band == 5 ? Biomes.JUNGLE : (band == 6 ? Biomes.SPARSE_JUNGLE : (band == 7 ? Biomes.WOODED_BADLANDS : Biomes.DESERT)))))))), fallback);
    }

    private Holder<Biome> plains(int band, Holder<Biome> fallback) {
        return this.biome((ResourceKey<Biome>)(band == 0 ? Biomes.ICE_SPIKES : (band == 1 ? Biomes.SNOWY_PLAINS : (band == 2 ? Biomes.TAIGA : (band == 3 ? Biomes.BIRCH_FOREST : (band == 4 ? Biomes.PLAINS : (band == 5 ? Biomes.SPARSE_JUNGLE : (band == 6 ? Biomes.SAVANNA : (band == 7 ? Biomes.BADLANDS : Biomes.DESERT)))))))), fallback);
    }

    private Holder<Biome> ocean(double t, Holder<Biome> fallback) {
        return this.ocean(TemperatureSurfaceBiomeMixin.temperatureBand(t), fallback);
    }

    private Holder<Biome> ocean(int band, Holder<Biome> fallback) {
        return this.biome((ResourceKey<Biome>)(band == 0 ? Biomes.DEEP_FROZEN_OCEAN : (band == 1 ? Biomes.FROZEN_OCEAN : (band == 2 ? Biomes.DEEP_COLD_OCEAN : (band == 3 ? Biomes.COLD_OCEAN : (band == 4 ? Biomes.OCEAN : (band == 5 ? Biomes.LUKEWARM_OCEAN : Biomes.WARM_OCEAN)))))), fallback);
    }

    private static int temperatureBand(double t) {
        if (t < -0.875) {
            return 0;
        }
        if (t < -0.625) {
            return 1;
        }
        if (t < -0.375) {
            return 2;
        }
        if (t < -0.125) {
            return 3;
        }
        if (t < 0.125) {
            return 4;
        }
        if (t < 0.375) {
            return 5;
        }
        if (t < 0.625) {
            return 6;
        }
        return t < 0.875 ? 7 : 8;
    }

    private Holder<Biome> biome(ResourceKey<Biome> key, Holder<Biome> fallback) {
        return ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().stream().filter(holder -> holder.is(key)).findFirst().orElse(fallback);
    }

    private static boolean isVanilla(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> "minecraft".equals(key.location().getNamespace())).orElse(false);
    }
}
