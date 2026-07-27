/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.util.Pair
 *  net.minecraft.core.Holder
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.biome.Biomes
 *  net.minecraft.world.level.biome.Climate
 *  net.minecraft.world.level.biome.Climate$ParameterList
 *  net.minecraft.world.level.biome.Climate$Sampler
 *  net.minecraft.world.level.biome.Climate$TargetPoint
 *  net.minecraft.world.level.biome.MultiNoiseBiomeSource
 *  net.neoforged.neoforge.common.Tags$Biomes
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package io.github.earthshape.mixin;

import com.mojang.datafixers.util.Pair;
import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.neoforged.neoforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={MultiNoiseBiomeSource.class}, priority=2000)
public abstract class TerrainBiomeMixin {
    private static final AtomicBoolean TERRABLENDER_INTERCEPT_LOGGED = new AtomicBoolean();
    @Unique
    private final ConcurrentHashMap<Integer, Climate.ParameterList<Holder<Biome>>> earthshape$filteredParameterLists = new ConcurrentHashMap();

    @Shadow(remap=false)
    public abstract Climate.ParameterList<Holder<Biome>> parameters();

    @Inject(method={"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private void earthshape$beforeTerraBlender(int quartX, int quartY, int quartZ, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
        if (!EarthShapeCompatibility.disablesWorldgen() && EarthShapeCompatibility.isTerraBlenderLoaded()) {
            int blockX = quartX << 2;
            int blockY = quartY << 2;
            int blockZ = quartZ << 2;
            ClimateLayers layers = ClimateLayers.INSTANCE;
            Climate.TargetPoint point = this.guidedClimatePoint(layers, blockX, blockZ, sampler.sample(quartX, quartY, quartZ));
            Holder<Biome> mapped = this.selectLayerCandidate(layers, blockX, blockY, blockZ, point);
            if (TERRABLENDER_INTERCEPT_LOGGED.compareAndSet(false, true)) {
                EarthShape.LOGGER.info("[EarthShape] TerraBlender biome lookup intercepted; applying EarthShape layer result before TerraBlender's region RTree.");
            }
            callback.setReturnValue(mapped);
        }
    }

    @Inject(method={"getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"}, at={@At(value="RETURN")}, cancellable=true, remap=false)
    private void earthshape$chooseTerrainBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> callback) {
        int blockX = quartX << 2;
        int blockY = quartY << 2;
        int blockZ = quartZ << 2;
        if (!EarthShapeCompatibility.disablesWorldgen()) {
            ClimateLayers layers = ClimateLayers.INSTANCE;
            if (((Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get()).booleanValue()) {
                Climate.TargetPoint point = this.guidedClimatePoint(layers, blockX, blockZ, sampler.sample(quartX, quartY, quartZ));
                callback.setReturnValue(this.selectLayerCandidate(layers, blockX, blockY, blockZ, point));
            }
        }
    }

    private Climate.TargetPoint guidedClimatePoint(ClimateLayers layers, int blockX, int blockZ, Climate.TargetPoint source) {
        ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
        ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
        float temperature = (float)layers.temperature(blockX, blockZ);
        float humidity = -0.08f;
        float continentalness = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5 ? 0.14f : -0.5f;
        float erosion = 0.48f;
        float depth = Climate.unquantizeCoord((long)source.depth()) * 0.2f;
        float weirdness = Climate.unquantizeCoord((long)source.weirdness()) * 0.2f;
        float relief = (float)layers.steepness(blockX, blockZ);
        switch (terrain) {
            case WATER: {
                continentalness = -0.62f;
                break;
            }
            case DESERT: {
                temperature = Math.max(temperature, 0.78f);
                humidity = -0.82f;
                continentalness = 0.12f;
                erosion = 0.34f;
                break;
            }
            case WETLAND: {
                humidity = 0.82f;
                erosion = 0.72f;
                break;
            }
            case FOREST: {
                humidity = trees == ClimateLayers.TreeCover.TROPICAL ? 0.8f : 0.58f;
                erosion = 0.48f - relief * 0.14f;
                break;
            }
            case JUNGLE: {
                temperature = Math.max(temperature, 0.72f);
                humidity = 0.92f;
                erosion = 0.46f;
                break;
            }
            case HILLS: {
                continentalness = 0.24f;
                erosion = -0.55f;
                weirdness = 0.46f;
                break;
            }
            case MOUNTAIN: {
                continentalness = 0.36f;
                erosion = -0.82f;
                weirdness = 0.76f;
                break;
            }
            case PLAINS: 
            case CITY: 
            case SURROUNDING: {
                humidity = -0.06f;
                erosion = 0.64f - relief * 0.12f;
            }
        }
        if (((Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()).booleanValue() && RiversMask.INSTANCE.isInlandRiver(blockX, blockZ)) {
            continentalness = -0.05f;
            erosion = 0.3f;
            depth = 0.0f;
            weirdness = 0.0f;
        }
        return Climate.target((float)temperature, (float)humidity, (float)continentalness, (float)erosion, (float)depth, (float)weirdness);
    }

    private Holder<Biome> selectLayerCandidate(ClimateLayers layers, int blockX, int blockY, int blockZ, Climate.TargetPoint point) {
        boolean frozenPeaksAllowed;
        ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
        boolean sourceRiver = (Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get() != false && RiversMask.INSTANCE.isInlandRiver(blockX, blockZ);
        boolean riverMouth = RiversMask.INSTANCE.isRiverMouth(blockX, blockZ);
        boolean bl = frozenPeaksAllowed = layers.isUltraMountain(blockX, blockZ) || layers.isPolarTemperatureZone(blockX, blockZ);
        int group = (sourceRiver ? 1 : (riverMouth || terrain == ClimateLayers.TerrainKind.WATER ? 2 : terrain.ordinal() + 3)) * 2 + (frozenPeaksAllowed ? 1 : 0);
        Climate.ParameterList candidates = this.earthshape$filteredParameterLists.computeIfAbsent(group, ignored -> this.createFilteredParameterList(terrain, sourceRiver, riverMouth, frozenPeaksAllowed));
        return (Holder)candidates.findValue(point);
    }

    private Climate.ParameterList<Holder<Biome>> createFilteredParameterList(ClimateLayers.TerrainKind terrain, boolean sourceRiver, boolean riverMouth, boolean frozenPeaksAllowed) {
        ArrayList<Pair> allowed = new ArrayList<Pair>();
        for (Pair entry : this.parameters().values()) {
            if (!this.isAllowedTerrainCandidate(terrain, sourceRiver, riverMouth, frozenPeaksAllowed, (Holder<Biome>)((Holder)entry.getSecond()))) continue;
            allowed.add(entry);
        }
        return allowed.isEmpty() ? this.parameters() : new Climate.ParameterList(List.copyOf(allowed));
    }

    private boolean isAllowedTerrainCandidate(ClimateLayers.TerrainKind terrain, boolean sourceRiver, boolean riverMouth, boolean frozenPeaksAllowed, Holder<Biome> biome) {
        if (biome.is(Biomes.FROZEN_PEAKS) && !frozenPeaksAllowed) {
            return false;
        }
        if (sourceRiver) {
            return biome.is(Tags.Biomes.IS_RIVER) || TerrainBiomeMixin.isVanillaRiver(biome);
        }
        if (riverMouth || terrain == ClimateLayers.TerrainKind.WATER) {
            return biome.is(Tags.Biomes.IS_OCEAN);
        }
        return switch (terrain) {
            default -> throw new MatchException(null, null);
            case ClimateLayers.TerrainKind.DESERT -> {
                if (biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_BADLANDS)) {
                    yield true;
                }
                yield false;
            }
            case ClimateLayers.TerrainKind.WETLAND -> biome.is(Tags.Biomes.IS_SWAMP);
            case ClimateLayers.TerrainKind.FOREST -> {
                if (biome.is(Tags.Biomes.IS_FOREST) || biome.is(Tags.Biomes.IS_TAIGA)) {
                    yield true;
                }
                yield false;
            }
            case ClimateLayers.TerrainKind.JUNGLE -> biome.is(Tags.Biomes.IS_JUNGLE);
            case ClimateLayers.TerrainKind.HILLS -> {
                if (biome.is(Tags.Biomes.IS_HILL) || biome.is(Tags.Biomes.IS_MOUNTAIN_SLOPE)) {
                    yield true;
                }
                yield false;
            }
            case ClimateLayers.TerrainKind.MOUNTAIN -> {
                if (frozenPeaksAllowed) {
                    yield biome.is(Biomes.FROZEN_PEAKS);
                }
                yield biome.is(Biomes.STONY_PEAKS);
            }
            case ClimateLayers.TerrainKind.PLAINS, ClimateLayers.TerrainKind.CITY, ClimateLayers.TerrainKind.SURROUNDING -> {
                if (biome.is(Tags.Biomes.IS_PLAINS) || biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES)) {
                    yield true;
                }
                yield false;
            }
            case ClimateLayers.TerrainKind.WATER -> false;
        };
    }

    private Holder<Biome> applyLayerBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> current) {
        boolean riverMouth;
        boolean sourceRiver = RiversMask.INSTANCE.isInlandRiver(blockX, blockZ);
        boolean bl = riverMouth = blockY >= 48 && RiversMask.INSTANCE.isRiverMouth(blockX, blockZ);
        if (riverMouth) {
            return this.oceanBiome(layers.temperature(blockX, blockZ), blockX, blockZ, current);
        }
        if (sourceRiver) {
            return this.findBiome((ResourceKey<Biome>)Biomes.RIVER, current);
        }
        if (blockY < 48) {
            return current;
        }
        if (RiversMask.INSTANCE.sampleLand(blockX, blockZ) >= 0.5 && TerrainBiomeMixin.isInlandWaterBiome(current)) {
            return this.mapTerrainBiome(layers, blockX, blockY, blockZ, current);
        }
        if (!TerrainBiomeMixin.isVanillaBiome(current) && !EarthShapeCompatibility.isTerraBlenderLoaded()) {
            return current;
        }
        if (TerrainBiomeMixin.isVanillaRiver(current)) {
            return this.mapTerrainBiome(layers, blockX, blockY, blockZ, current);
        }
        if (((Boolean)EarthShapeServerConfig.OCEAN_TEMPERATURE_ENABLED.get()).booleanValue() && RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.25) {
            long layerPoint = TerrainBiomeMixin.warpedLayerPoint(blockX, blockZ);
            return this.oceanBiome(layers.temperature(TerrainBiomeMixin.unpackX(layerPoint), TerrainBiomeMixin.unpackZ(layerPoint)), blockX, blockZ, current);
        }
        return (Boolean)EarthShapeServerConfig.TERRAIN_BIOMES_ENABLED.get() != false || EarthShapeCompatibility.isTerraBlenderLoaded() ? this.mapTerrainBiome(layers, blockX, blockY, blockZ, current) : current;
    }

    private Holder<Biome> mapTerrainBiome(ClimateLayers layers, int blockX, int blockY, int blockZ, Holder<Biome> fallback) {
        Holder<Biome> terraBiome;
        long layerPoint = TerrainBiomeMixin.warpedLayerPoint(blockX, blockZ);
        int layerX = TerrainBiomeMixin.unpackX(layerPoint);
        int layerZ = TerrainBiomeMixin.unpackZ(layerPoint);
        ClimateLayers.TerrainKind terrain = this.surfaceTerrain(layers, blockX, blockZ);
        double temperature = layers.temperature(layerX, layerZ);
        boolean snowAllowed = TerrainBiomeMixin.allowsSnow(blockY, temperature);
        boolean frozenPeaksAllowed = layers.isUltraMountain(blockX, blockZ) || layers.isPolarTemperatureZone(blockX, blockZ);
        int region = TerrainBiomeMixin.regionalVariant(blockX, blockZ);
        boolean nextToLayerRiver = RiversMask.INSTANCE.isNearInlandRiver(blockX, blockZ, 32);
        if (!nextToLayerRiver && TerrainBiomeMixin.isCoastalLand(blockX, blockZ)) {
            boolean sandyBeach;
            Holder<Biome> terraBeach = this.terraBlenderTaggedBiome((TagKey<Biome>)Tags.Biomes.IS_BEACH, blockX, blockZ);
            if (terraBeach != null) {
                return terraBeach;
            }
            if (terrain == ClimateLayers.TerrainKind.HILLS || terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
                return this.findBiome((ResourceKey<Biome>)Biomes.STONY_SHORE, fallback);
            }
            boolean bl = sandyBeach = terrain == ClimateLayers.TerrainKind.DESERT || terrain == ClimateLayers.TerrainKind.PLAINS && temperature > 0.2 && region % 5 == 0;
            if (sandyBeach) {
                return this.findBiome((ResourceKey<Biome>)(snowAllowed ? Biomes.SNOWY_BEACH : Biomes.BEACH), fallback);
            }
        }
        if ((terraBiome = this.terraBlenderTerrainBiome(terrain, snowAllowed, frozenPeaksAllowed, blockX, blockZ)) != null) {
            return terraBiome;
        }
        return switch (terrain) {
            default -> throw new MatchException(null, null);
            case ClimateLayers.TerrainKind.DESERT -> {
                if (layers.isMesaRegion(blockX, blockZ)) {
                    yield this.findBiome((ResourceKey<Biome>)(region % 10 == 0 ? Biomes.ERODED_BADLANDS : (region % 5 == 0 ? Biomes.WOODED_BADLANDS : Biomes.BADLANDS)), fallback);
                }
                yield this.findBiome((ResourceKey<Biome>)Biomes.DESERT, fallback);
            }
            case ClimateLayers.TerrainKind.WETLAND -> this.findBiome((ResourceKey<Biome>)(temperature > 0.3 ? Biomes.MANGROVE_SWAMP : Biomes.SWAMP), fallback);
            case ClimateLayers.TerrainKind.FOREST -> this.forestBiome(temperature, snowAllowed, region, fallback);
            case ClimateLayers.TerrainKind.JUNGLE -> this.findBiome((ResourceKey<Biome>)(region % 12 == 0 ? Biomes.BAMBOO_JUNGLE : (region % 6 == 0 ? Biomes.SPARSE_JUNGLE : Biomes.JUNGLE)), fallback);
            case ClimateLayers.TerrainKind.HILLS -> {
                if (snowAllowed) {
                    yield this.findBiome((ResourceKey<Biome>)(temperature < -0.55 ? Biomes.SNOWY_SLOPES : Biomes.GROVE), fallback);
                }
                yield this.findBiome((ResourceKey<Biome>)(temperature > 0.45 ? Biomes.WINDSWEPT_SAVANNA : (region % 5 == 0 ? Biomes.WINDSWEPT_FOREST : Biomes.WINDSWEPT_HILLS)), fallback);
            }
            case ClimateLayers.TerrainKind.MOUNTAIN -> this.findBiome((ResourceKey<Biome>)(frozenPeaksAllowed ? Biomes.FROZEN_PEAKS : Biomes.STONY_PEAKS), fallback);
            case ClimateLayers.TerrainKind.PLAINS, ClimateLayers.TerrainKind.CITY, ClimateLayers.TerrainKind.SURROUNDING -> this.plainsBiome(temperature, snowAllowed, region, fallback);
            case ClimateLayers.TerrainKind.WATER -> this.oceanBiome(temperature, blockX, blockZ, fallback);
        };
    }

    private Holder<Biome> forestBiome(double temperature, boolean snowAllowed, int region, Holder<Biome> fallback) {
        if (snowAllowed && temperature < -0.55) {
            return this.findBiome((ResourceKey<Biome>)Biomes.SNOWY_TAIGA, fallback);
        }
        return snowAllowed && temperature < -0.25 ? this.findBiome((ResourceKey<Biome>)(region % 6 == 0 ? Biomes.OLD_GROWTH_SPRUCE_TAIGA : (region % 4 == 0 ? Biomes.OLD_GROWTH_PINE_TAIGA : Biomes.TAIGA)), fallback) : this.findBiome((ResourceKey<Biome>)(region % 14 == 0 ? Biomes.FLOWER_FOREST : (region % 9 == 0 ? Biomes.BIRCH_FOREST : (region % 7 == 0 ? Biomes.DARK_FOREST : (region % 19 == 0 ? Biomes.OLD_GROWTH_BIRCH_FOREST : Biomes.FOREST)))), fallback);
    }

    private Holder<Biome> plainsBiome(double temperature, boolean snowAllowed, int region, Holder<Biome> fallback) {
        if (snowAllowed && temperature < -0.55) {
            return this.findBiome((ResourceKey<Biome>)(region % 17 == 0 ? Biomes.ICE_SPIKES : Biomes.SNOWY_PLAINS), fallback);
        }
        if (snowAllowed && temperature < -0.3) {
            return this.findBiome((ResourceKey<Biome>)Biomes.SNOWY_PLAINS, fallback);
        }
        return temperature > 0.45 ? this.findBiome((ResourceKey<Biome>)(region % 6 == 0 ? Biomes.SAVANNA_PLATEAU : Biomes.SAVANNA), fallback) : this.findBiome((ResourceKey<Biome>)(region % 16 == 0 ? Biomes.SUNFLOWER_PLAINS : Biomes.PLAINS), fallback);
    }

    private Holder<Biome> oceanBiome(double temperature, int blockX, int blockZ, Holder<Biome> fallback) {
        boolean deep = TerrainBiomeMixin.isOpenOcean(blockX, blockZ);
        Holder<Biome> terraOcean = this.terraBlenderTaggedBiome((TagKey<Biome>)Tags.Biomes.IS_OCEAN, blockX, blockZ);
        if (terraOcean != null) {
            return terraOcean;
        }
        if (temperature > 0.65) {
            return this.findBiome((ResourceKey<Biome>)Biomes.WARM_OCEAN, fallback);
        }
        if (temperature > 0.15) {
            return this.findBiome((ResourceKey<Biome>)(deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN), fallback);
        }
        if (temperature > -0.15) {
            return this.findBiome((ResourceKey<Biome>)(deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN), fallback);
        }
        return temperature > -0.5 ? this.findBiome((ResourceKey<Biome>)(deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN), fallback) : this.findBiome((ResourceKey<Biome>)(deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN), fallback);
    }

    private static boolean isCoastalLand(int blockX, int blockZ) {
        if (RiversMask.INSTANCE.sampleLand(blockX, blockZ) < 0.5) {
            return false;
        }
        int distance = 28;
        return RiversMask.INSTANCE.sampleLand(blockX - distance, blockZ) < 0.5 || RiversMask.INSTANCE.sampleLand(blockX + distance, blockZ) < 0.5 || RiversMask.INSTANCE.sampleLand(blockX, blockZ - distance) < 0.5 || RiversMask.INSTANCE.sampleLand(blockX, blockZ + distance) < 0.5;
    }

    private static boolean isOpenOcean(int blockX, int blockZ) {
        int distance = 180;
        return RiversMask.INSTANCE.sampleLand(blockX - distance, blockZ) < 0.25 && RiversMask.INSTANCE.sampleLand(blockX + distance, blockZ) < 0.25 && RiversMask.INSTANCE.sampleLand(blockX, blockZ - distance) < 0.25 && RiversMask.INSTANCE.sampleLand(blockX, blockZ + distance) < 0.25;
    }

    private static int regionalVariant(int blockX, int blockZ) {
        long value = (long)(blockX >> 10) * 341873128712L ^ (long)(blockZ >> 10) * 132897987541L ^ 0x285B825L;
        value ^= value >>> 33;
        value *= -49064778989728563L;
        value ^= value >>> 33;
        return (int)(value ^ value >>> 32) & Integer.MAX_VALUE;
    }

    private static boolean allowsSnow(int blockY, double temperature) {
        return blockY >= (Integer)EarthShapeServerConfig.SNOW_ALTITUDE_BLOCKS.get() || (Boolean)EarthShapeServerConfig.TUNDRA_TEMPERATURE_ENABLED.get() != false && temperature <= (Double)EarthShapeServerConfig.SNOW_TEMPERATURE_THRESHOLD.get();
    }

    private static long warpedLayerPoint(int blockX, int blockZ) {
        if (!((Boolean)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_ENABLED.get()).booleanValue()) {
            return TerrainBiomeMixin.packPoint(blockX, blockZ);
        }
        int strength = Math.min((Integer)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_BLOCKS.get(), Math.max(4, RiversMask.INSTANCE.blocksPerPixel() * 3 / 4));
        if (strength == 0 || ClimateLayers.INSTANCE.isTerrainBoundary(blockX, blockZ, Math.max(8, strength * 2))) {
            return TerrainBiomeMixin.packPoint(blockX, blockZ);
        }
        int warpedX = blockX + (int)Math.round(TerrainBiomeMixin.smoothNoise(blockX, blockZ, 7640891576956012809L) * (double)strength);
        int warpedZ = blockZ + (int)Math.round(TerrainBiomeMixin.smoothNoise(blockX, blockZ, -4942790177534073029L) * (double)strength);
        return TerrainBiomeMixin.packPoint(warpedX, warpedZ);
    }

    private static long packPoint(int x, int z) {
        return (long)x << 32 | (long)z & 0xFFFFFFFFL;
    }

    private static int unpackX(long point) {
        return (int)(point >> 32);
    }

    private static int unpackZ(long point) {
        return (int)point;
    }

    private static double smoothNoise(int blockX, int blockZ, long salt) {
        int cellSize = 512;
        int cellX = Math.floorDiv(blockX, 512);
        int cellZ = Math.floorDiv(blockZ, 512);
        double x = (double)Math.floorMod(blockX, 512) / 512.0;
        double z = (double)Math.floorMod(blockZ, 512) / 512.0;
        x = x * x * (3.0 - 2.0 * x);
        z = z * z * (3.0 - 2.0 * z);
        double north = TerrainBiomeMixin.lerp(TerrainBiomeMixin.noiseValue(cellX, cellZ, salt), TerrainBiomeMixin.noiseValue(cellX + 1, cellZ, salt), x);
        double south = TerrainBiomeMixin.lerp(TerrainBiomeMixin.noiseValue(cellX, cellZ + 1, salt), TerrainBiomeMixin.noiseValue(cellX + 1, cellZ + 1, salt), x);
        return TerrainBiomeMixin.lerp(north, south, z);
    }

    private static double noiseValue(int x, int z, long salt) {
        long value = (long)x * 341873128712L ^ (long)z * 132897987541L ^ salt;
        value ^= value >>> 33;
        value *= -49064778989728563L;
        value ^= value >>> 33;
        return (double)((int)(value >>> 40) & 0xFFFFFF) / 8388607.5 - 1.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private Holder<Biome> findBiome(ResourceKey<Biome> key, Holder<Biome> fallback) {
        return ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().stream().filter(holder -> holder.is(key)).findFirst().orElse(fallback);
    }

    private Holder<Biome> terraBlenderTerrainBiome(ClimateLayers.TerrainKind terrain, boolean snowAllowed, boolean frozenPeaksAllowed, int blockX, int blockZ) {
        if (!EarthShapeCompatibility.isTerraBlenderLoaded()) {
            return null;
        }
        return switch (terrain) {
            default -> throw new MatchException(null, null);
            case ClimateLayers.TerrainKind.DESERT -> this.terraBlenderTaggedBiome((TagKey<Biome>)(ClimateLayers.INSTANCE.isMesaRegion(blockX, blockZ) ? Tags.Biomes.IS_BADLANDS : Tags.Biomes.IS_DESERT), blockX, blockZ);
            case ClimateLayers.TerrainKind.WETLAND -> this.terraBlenderTaggedBiome((TagKey<Biome>)Tags.Biomes.IS_SWAMP, blockX, blockZ);
            case ClimateLayers.TerrainKind.JUNGLE -> this.terraBlenderTaggedBiome((TagKey<Biome>)Tags.Biomes.IS_JUNGLE, blockX, blockZ);
            case ClimateLayers.TerrainKind.FOREST -> this.terraBlenderTaggedBiome((TagKey<Biome>)(snowAllowed ? Tags.Biomes.IS_TAIGA : Tags.Biomes.IS_FOREST), blockX, blockZ);
            case ClimateLayers.TerrainKind.HILLS -> this.terraBlenderTaggedBiome((TagKey<Biome>)Tags.Biomes.IS_MOUNTAIN_SLOPE, blockX, blockZ);
            case ClimateLayers.TerrainKind.MOUNTAIN -> {
                if (frozenPeaksAllowed) {
                    yield this.terraBlenderTaggedBiome((TagKey<Biome>)Tags.Biomes.IS_MOUNTAIN_PEAK, blockX, blockZ);
                }
                yield null;
            }
            case ClimateLayers.TerrainKind.WATER -> this.terraBlenderTaggedBiome((TagKey<Biome>)Tags.Biomes.IS_OCEAN, blockX, blockZ);
            case ClimateLayers.TerrainKind.PLAINS, ClimateLayers.TerrainKind.CITY, ClimateLayers.TerrainKind.SURROUNDING -> null;
        };
    }

    private Holder<Biome> terraBlenderTaggedBiome(TagKey<Biome> tag, int blockX, int blockZ) {
        if (!EarthShapeCompatibility.isTerraBlenderLoaded()) {
            return null;
        }
        List<Holder<Biome>> candidates = ((MultiNoiseBiomeSource)(Object)this).possibleBiomes().stream().filter(holder -> !TerrainBiomeMixin.isVanillaBiome(holder) && holder.is(tag)).toList();
        return candidates.isEmpty() ? null : candidates.get(TerrainBiomeMixin.regionalVariant(blockX, blockZ) % candidates.size());
    }

    private ClimateLayers.TerrainKind surfaceTerrain(ClimateLayers layers, int blockX, int blockZ) {
        ClimateLayers.TerrainKind terrain = layers.terrainKind(blockX, blockZ);
        if (terrain != ClimateLayers.TerrainKind.PLAINS) {
            return terrain;
        }
        ClimateLayers.TreeCover trees = layers.treeCover(blockX, blockZ);
        return trees == ClimateLayers.TreeCover.TROPICAL ? ClimateLayers.TerrainKind.JUNGLE : (trees == ClimateLayers.TreeCover.TEMPERATE ? ClimateLayers.TerrainKind.FOREST : ClimateLayers.TerrainKind.PLAINS);
    }

    private static boolean isVanillaBiome(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> "minecraft".equals(key.location().getNamespace())).orElse(false);
    }

    private static boolean isVanillaRiver(Holder<Biome> biome) {
        return biome.is(Biomes.RIVER) || biome.is(Biomes.FROZEN_RIVER);
    }

    private static boolean isInlandWaterBiome(Holder<Biome> biome) {
        return biome.is(Tags.Biomes.IS_RIVER) ? true : biome.unwrapKey().map(key -> {
            String path = key.location().getPath();
            return path.contains("river") || path.contains("lake");
        }).orElse(false);
    }
}
