package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One surface-biome authority: exact land mask, then source river, terrain, temperature. */
@Mixin(MultiNoiseBiomeSource.class)
public final class TerrainBiomeMixin {
    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"), cancellable = true)
    private void earthshape$selectLayerBiome(int quartX, int quartY, int quartZ, Sampler sampler,
                                              CallbackInfoReturnable<Holder<Biome>> callback) {
        int x = quartX << 2;
        int y = quartY << 2;
        int z = quartZ << 2;
        Holder<Biome> fallback = callback.getReturnValue();
        if (y < 48 || EarthShapeCompatibility.disablesWorldgen() || !isVanilla(fallback)) return;

        RiversMask mask = RiversMask.INSTANCE;
        ClimateLayers layers = ClimateLayers.INSTANCE;
        double temperature = EarthShapeServerConfig.TEMPERATURE_ENABLED.get() ? layers.temperature(x, z) : 0.0D;
        if (EarthShapeServerConfig.RIVERS_ENABLED.get() && mask.isInlandRiver(x, z)) {
            callback.setReturnValue(find(Biomes.RIVER, fallback));
            return;
        }
        if (mask.sampleLayerLand(x, z) < 0.5D) {
            callback.setReturnValue(ocean(temperature, fallback));
            return;
        }
        if (!EarthShapeServerConfig.TERRAIN_ENABLED.get()) return;
        callback.setReturnValue(land(layers.terrainKind(x, z), temperature, x, z, fallback));
    }

    private Holder<Biome> land(ClimateLayers.TerrainKind terrain, double temperature, int blockX, int blockZ, Holder<Biome> fallback) {
        int band = band(temperature);
        return switch (terrain) {
            // Heat alone does not create a mesa.  The terrain layer must say desert
            // and the source location must be one of the requested mesa regions.
            case DESERT -> find(band <= 2 ? Biomes.SNOWY_PLAINS : isMesaRegion(blockX, blockZ) && band >= 7 ? Biomes.BADLANDS : Biomes.DESERT, fallback);
            case WETLAND -> find(band >= 6 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP, fallback);
            case FOREST -> find(band <= 1 ? Biomes.SNOWY_TAIGA : band <= 3 ? Biomes.TAIGA : band >= 6 ? Biomes.SPARSE_JUNGLE : Biomes.FOREST, fallback);
            case JUNGLE -> find(band <= 2 ? Biomes.TAIGA : band >= 5 ? Biomes.JUNGLE : Biomes.FOREST, fallback);
            case HILLS -> find(band <= 1 ? Biomes.SNOWY_SLOPES : band >= 6 ? Biomes.WINDSWEPT_SAVANNA : Biomes.WINDSWEPT_HILLS, fallback);
            case MOUNTAIN -> find(band <= 1 ? Biomes.FROZEN_PEAKS : band <= 3 ? Biomes.JAGGED_PEAKS : Biomes.STONY_PEAKS, fallback);
            case WATER -> find(band <= 2 ? Biomes.SNOWY_PLAINS : band >= 6 ? Biomes.SAVANNA : Biomes.PLAINS, fallback);
            case PLAINS, CITY, SURROUNDING -> find(band <= 1 ? Biomes.SNOWY_PLAINS : band <= 3 ? Biomes.TAIGA : band >= 6 ? Biomes.SAVANNA : Biomes.PLAINS, fallback);
        };
    }

    private Holder<Biome> ocean(double temperature, Holder<Biome> fallback) {
        int band = band(temperature);
        if (band <= 1) return find(Biomes.FROZEN_OCEAN, fallback);
        if (band <= 3) return find(Biomes.COLD_OCEAN, fallback);
        if (band <= 5) return find(Biomes.OCEAN, fallback);
        return find(band == 6 ? Biomes.LUKEWARM_OCEAN : Biomes.WARM_OCEAN, fallback);
    }

    private Holder<Biome> find(ResourceKey<Biome> key, Holder<Biome> fallback) {
        return ((MultiNoiseBiomeSource) (Object) this).possibleBiomes().stream().filter(holder -> holder.is(key)).findFirst().orElse(fallback);
    }

    private static int band(double temperature) {
        if (temperature < -0.60D) return 0;
        if (temperature < -0.35D) return 1;
        if (temperature < -0.10D) return 2;
        if (temperature < 0.20D) return 3;
        if (temperature < 0.45D) return 4;
        if (temperature < 0.65D) return 5;
        if (temperature < 0.82D) return 6;
        return 7;
    }

    private static boolean isMesaRegion(int blockX, int blockZ) {
        double u = (blockX / (double) RiversMask.INSTANCE.blocksPerPixel() + RiversMask.INSTANCE.width() * 0.5D) / RiversMask.INSTANCE.width();
        double v = (blockZ / (double) RiversMask.INSTANCE.blocksPerPixel() + RiversMask.INSTANCE.height() * 0.5D) / RiversMask.INSTANCE.height();
        boolean americas = u > 0.04D && u < 0.43D && v > 0.10D && v < 0.87D;
        boolean oceania = u > 0.74D && u < 0.98D && v > 0.50D && v < 0.91D;
        return americas || oceania;
    }

    private static boolean isVanilla(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location().getNamespace().equals("minecraft")).orElse(false);
    }
}
