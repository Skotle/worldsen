package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.EarthShape;
import io.github.earthshape.map.EarthMapService;
import io.github.earthshape.map.EarthSignal;
import io.github.earthshape.map.EarthEnvironmentSignal;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts the Earth SDF into a target surface. This guarantees that map ocean cannot retain
 * vanilla mountains and that map land remains connected above sea level. The original final
 * density is retained only as bounded local detail, rather than being allowed to move a coast.
 */
public record EarthTerrainDensity(DensityFunction original) implements DensityFunction {
    private static final AtomicBoolean FIRST_COMPUTE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_MAP_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_SAMPLE_LOGGED = new AtomicBoolean();
    private static final MapCodec<EarthTerrainDensity> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(EarthTerrainDensity::original)
    ).apply(instance, EarthTerrainDensity::new));
    public static final KeyDispatchDataCodec<EarthTerrainDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (FIRST_COMPUTE_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain wrapper reached; enabled={}", EarthShapeConfig.ENABLED.get());
        }
        double base = original.compute(context);
        if (!EarthShapeConfig.ENABLED.get()) return base;

        EarthSignal mapSignal = EarthMapService.INSTANCE.sample(0L, context.blockX(), context.blockZ());
        // Biome continentalness may use a broad transition, but the physical shoreline must
        // follow the raster closely rather than becoming a hundreds-of-blocks-wide mush.
        double land = smootherstep(-24.0D, 24.0D, mapSignal.signedDistanceBlocks());
        if (FIRST_SAMPLE_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain density active; first sample at {}, {}, {}.", context.blockX(), context.blockY(), context.blockZ());
        }
        // Vanilla owns all land density; it has coherent mountains, caves, and surface rules.
        // The map only replaces ocean density with one continuous floor, blending at the coast.
        double oceanFloorShape = (EarthShapeConfig.OCEAN_FLOOR_Y.get() - context.blockY())
                / EarthShapeConfig.SHAPE_VERTICAL_SCALE.get();
        double shoreline = smootherstep(0.25D, 0.75D, land);
        return oceanFloorShape + (base - oceanFloorShape) * shoreline;
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider) {
        provider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        if (FIRST_MAP_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain wrapper wired into the live RandomState.");
        }
        return visitor.apply(new EarthTerrainDensity(original.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return -1000000.0D;
    }

    @Override
    public double maxValue() {
        return 1000000.0D;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private static double smootherstep(double min, double max, double value) {
        double t = Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }
}
