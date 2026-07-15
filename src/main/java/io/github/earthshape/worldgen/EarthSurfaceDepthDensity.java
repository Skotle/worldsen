package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.map.EarthEnvironmentSignal;
import io.github.earthshape.map.EarthMapService;
import io.github.earthshape.map.EarthSignal;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Keeps multi-noise underground biomes below the map-controlled surface. */
public record EarthSurfaceDepthDensity(DensityFunction original) implements DensityFunction {
    private static final MapCodec<EarthSurfaceDepthDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(EarthSurfaceDepthDensity::original)
    ).apply(instance, EarthSurfaceDepthDensity::new));
    public static final KeyDispatchDataCodec<EarthSurfaceDepthDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (!EarthShapeConfig.ENABLED.get()) return original.compute(context);

        // Vanilla registers lush/dripstone caves at the exact depth climate coordinate 0.
        // Returning 0 everywhere therefore selected cave biomes all the way to open air.
        // Exclude a generous band below the *mapped* surface, then retain vanilla's depth
        // signal in truly underground space so caves can still receive cave biomes.
        EarthSignal mapSignal = EarthMapService.INSTANCE.sample(0L, context.blockX(), context.blockZ());
        double coastWidth = EarthShapeConfig.TERRAIN_COAST_WIDTH_BLOCKS.get();
        double distance = mapSignal.signedDistanceBlocks() - EarthShapeConfig.OCEAN_INSET_BLOCKS.get();
        double land = EarthMapService.INSTANCE.isNarrowWaterPassage(context.blockX(), context.blockZ())
                ? 0.0D
                : smootherstep(-coastWidth, coastWidth, distance);
        double shoreline = smootherstep(0.0D, 0.5D, land);
        double surfaceY = EarthShapeConfig.OCEAN_FLOOR_Y.get()
                + (EarthShapeConfig.LAND_BASE_Y.get() - EarthShapeConfig.OCEAN_FLOOR_Y.get()) * shoreline;

        EarthEnvironmentSignal environment = EarthMapService.INSTANCE.sampleEnvironment(context.blockX(), context.blockZ());
        if (environment.heightActive()) {
            double mappedHeightY = EarthShapeConfig.HEIGHTMAP_MIN_Y.get()
                    + environment.height() * (EarthShapeConfig.HEIGHTMAP_MAX_Y.get() - EarthShapeConfig.HEIGHTMAP_MIN_Y.get());
            double inlandWeight = smootherstep(0.55D, 0.85D, land)
                    * smootherstep(160.0D, 640.0D, mapSignal.signedDistanceBlocks());
            surfaceY += (mappedHeightY - EarthShapeConfig.LAND_BASE_Y.get()) * inlandWeight;
            surfaceY += environment.normalSteepness() * 8.0D * inlandWeight;
        }

        if (context.blockY() >= surfaceY - 32.0D) return -1.0D;
        return original.compute(context);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new EarthSurfaceDepthDensity(original.mapAll(visitor))); }
    @Override public double minValue() { return Math.min(original.minValue(), 0.0D); }
    @Override public double maxValue() { return Math.max(original.maxValue(), 0.0D); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
    private static double smootherstep(double min, double max, double value) {
        double t = Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }
}
