package io.github.earthshape.worldgen;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.map.EarthMapService;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Blends the pack's normal continentalness climate signal with the Earth map.
 * It is deliberately a wrapper: temperature, humidity, erosion, ridges and the original
 * biome source remain untouched.
 */
public record EarthContinentalnessDensity(DensityFunction original) implements DensityFunction {
    private static final MapCodec<EarthContinentalnessDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(EarthContinentalnessDensity::original)
    ).apply(instance, EarthContinentalnessDensity::new));
    public static final KeyDispatchDataCodec<EarthContinentalnessDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        double base = original.compute(context);
        if (!EarthShapeConfig.ENABLED.get()) return base;
        // A fixed map warp keeps the requested continental silhouette stable across world seeds.
        var signal = EarthMapService.INSTANCE.sample(0L, context.blockX(), context.blockZ());
        double earth = signal.continentalness();
        double strength = EarthShapeConfig.CONTROL_STRENGTH.get();
        double blended = base + (earth - base) * strength;
        // Once the terrain mask is unambiguously land, never leave an ocean/deep-ocean
        // continentalness value behind for the biome source to select on the surface.
        if (signal.signedDistanceBlocks() >= 24.0D) return Math.max(blended, 0.25D);
        // A definite ocean climate prevents land-only structures such as villages from being
        // selected in a mapped sea, even when the original seed's climate was continental.
        if (signal.signedDistanceBlocks() <= -24.0D) return Math.min(blended, -0.70D);
        return blended;
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider) {
        provider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new EarthContinentalnessDensity(original.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return Math.min(original.minValue(), -1.0D);
    }

    @Override
    public double maxValue() {
        return Math.max(original.maxValue(), 1.0D);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
