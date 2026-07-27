/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.MapCodec
 *  net.minecraft.util.KeyDispatchDataCodec
 *  net.minecraft.world.level.levelgen.DensityFunction
 *  net.minecraft.world.level.levelgen.DensityFunction$ContextProvider
 *  net.minecraft.world.level.levelgen.DensityFunction$FunctionContext
 *  net.minecraft.world.level.levelgen.DensityFunction$Visitor
 */
package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.map.ClimateLayers;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class TerrainNoiseOffsetDensity
implements DensityFunction {
    private static final MapCodec<TerrainNoiseOffsetDensity> DATA_CODEC = MapCodec.unit(new TerrainNoiseOffsetDensity());
    public static final KeyDispatchDataCodec<TerrainNoiseOffsetDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    public double compute(DensityFunction.FunctionContext context) {
        int z;
        if (EarthShapeCompatibility.disablesWorldgen() || !((Boolean)EarthShapeServerConfig.TERRAIN_NOISE_ENABLED.get()).booleanValue()) {
            return 0.0;
        }
        int x = context.blockX();
        double land = RiversMask.INSTANCE.sampleLayerLand(x, z = context.blockZ());
        if (land <= 0.0) {
            return 0.0;
        }
        ClimateLayers.TerrainKind terrain = ClimateLayers.INSTANCE.terrainKind(x, z);
        double rolling = TerrainNoiseOffsetDensity.valueNoise(x, z, 384, 272919L) * 0.018 + TerrainNoiseOffsetDensity.valueNoise(x, z, 96, 598395L) * 0.007;
        // Keep erosion on a large wavelength so terrain changes by region rather than
        // creating one-cell terraces. It scales the existing density offset; it never
        // assigns an absolute surface Y value.
        double erosion = TerrainNoiseOffsetDensity.valueNoise(x, z, 768, 1849367L);
        double terrainErosion = switch (terrain) {
            case WETLAND -> 0.78;
            case DESERT -> 0.52;
            case PLAINS, CITY, SURROUNDING -> 0.64;
            case FOREST, JUNGLE -> 0.30;
            case HILLS -> -0.28;
            case MOUNTAIN -> -0.55;
            case WATER -> 0.80;
        };
        double terrainWeight = terrain == ClimateLayers.TerrainKind.MOUNTAIN ? 0.72 : 0.48;
        double effectiveErosion = TerrainNoiseOffsetDensity.lerp(erosion, terrainErosion, terrainWeight);
        // terrain.bmp encodes the mountain belt in four elevation stages (10..13).
        // Respect those stages at the foothill instead of assigning every mountain
        // cell a large minimum weight; the latter created apartment-like vertical
        // walls where plains met the outer mountain colour.
        double mountainWeight = ClimateLayers.INSTANCE.mountainElevationWeight(x, z);
        // Peak centres always retain low erosion, preventing a mapped range from
        // flattening when the regional erosion sample happens to be positive.
        effectiveErosion = TerrainNoiseOffsetDensity.lerp(effectiveErosion, -0.8, mountainWeight);
        double riverWeight = TerrainNoiseOffsetDensity.riverValleyWeight(x, z);
        // Valley floors favour high erosion: flatter and wider, while the bank keeps
        // its original relief through the smoothly decaying weight.
        effectiveErosion = TerrainNoiseOffsetDensity.lerp(effectiveErosion, 0.42, riverWeight);
        double amplitude = TerrainNoiseOffsetDensity.erosionAmplitude(effectiveErosion);
        double rollingStrength = switch (terrain) {
            case ClimateLayers.TerrainKind.WETLAND -> 0.15;
            case ClimateLayers.TerrainKind.DESERT -> 0.45;
            case ClimateLayers.TerrainKind.PLAINS -> 0.65;
            case ClimateLayers.TerrainKind.FOREST, ClimateLayers.TerrainKind.JUNGLE, ClimateLayers.TerrainKind.HILLS -> 0.9;
            case ClimateLayers.TerrainKind.MOUNTAIN -> 0.0;
            default -> 0.6;
        };
        double mountain = 0.0;
        if (terrain == ClimateLayers.TerrainKind.MOUNTAIN) {
            double ridge = 0.2 + 0.8 * (TerrainNoiseOffsetDensity.valueNoise(x, z, 192, 97349L) + 1.0) * 0.5;
            mountain = ridge
                * TerrainNoiseOffsetDensity.smoothstep(mountainWeight)
                * (double)((Integer)EarthShapeServerConfig.MOUNTAIN_NOISE_MAXIMUM_HEIGHT_BLOCKS.get()).intValue()
                * 0.0078125;
        }
        // Erosion changes rolling terrain amplitude. The separate mountain mask owns
        // peak height, so it is deliberately not amplified a second time.
        return TerrainNoiseOffsetDensity.smoothstep(land) * (rolling * rollingStrength * amplitude + mountain);
    }

    private static double riverValleyWeight(int x, int z) {
        if (!RiversMask.INSTANCE.hasInlandRiverInfluence(x, z)) {
            return 0.0;
        }
        int width = RiversMask.INSTANCE.effectiveRiverWidthBlocks(x, z);
        if (width <= 0) {
            return 0.0;
        }
        double distance = RiversMask.INSTANCE.riverCentrelineDistance(x, z) * (double)RiversMask.INSTANCE.blocksPerPixel();
        double radius = (double)width / 2.0 + 48.0;
        return TerrainNoiseOffsetDensity.smoothstep(1.0 - Math.min(1.0, distance / radius));
    }

    private static double erosionAmplitude(double erosion) {
        erosion = Math.max(-1.0, Math.min(1.0, erosion));
        if (erosion >= 0.0) {
            // High erosion compresses relief continuously toward the baseline.
            return Math.pow(1.0 - erosion, 1.5);
        }
        // Low erosion increases relief but remains bounded to avoid density spikes.
        return 1.0 + Math.pow(-erosion, 1.2) * 1.5;
    }

    private static double valueNoise(int x, int z, int cellSize, long salt) {
        int x0 = Math.floorDiv(x, cellSize);
        int z0 = Math.floorDiv(z, cellSize);
        double tx = TerrainNoiseOffsetDensity.smoothstep((double)Math.floorMod(x, cellSize) / (double)cellSize);
        double tz = TerrainNoiseOffsetDensity.smoothstep((double)Math.floorMod(z, cellSize) / (double)cellSize);
        double top = TerrainNoiseOffsetDensity.lerp(TerrainNoiseOffsetDensity.gridValue(x0, z0, salt), TerrainNoiseOffsetDensity.gridValue(x0 + 1, z0, salt), tx);
        double bottom = TerrainNoiseOffsetDensity.lerp(TerrainNoiseOffsetDensity.gridValue(x0, z0 + 1, salt), TerrainNoiseOffsetDensity.gridValue(x0 + 1, z0 + 1, salt), tx);
        return TerrainNoiseOffsetDensity.lerp(top, bottom, tz);
    }

    private static double gridValue(int x, int z, long salt) {
        long value = salt ^ (long)x * 341873128712L ^ (long)z * 132897987541L;
        value ^= value >>> 33;
        value *= -49064778989728563L;
        value ^= value >>> 33;
        value *= -4265267296055464877L;
        value ^= value >>> 33;
        return (double)(value >>> 11 & 0x1FFFFFL) / 1048575.5 - 1.0;
    }

    private static double smoothstep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double lerp(double a, double b, double amount) {
        return a + (b - a) * amount;
    }

    public void fillArray(double[] values, DensityFunction.ContextProvider provider) {
        provider.fillAllDirectly(values, (DensityFunction)this);
    }

    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply((DensityFunction)this);
    }

    public double minValue() {
        return -0.02;
    }

    public double maxValue() {
        return 1.5;
    }

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
