package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.earthshape.EarthShapeConfig;
import io.github.earthshape.EarthShape;
import io.github.earthshape.map.EarthMapService;
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

        double land = EarthMapService.INSTANCE.sample(0L, context.blockX(), context.blockZ()).landFactor();
        if (FIRST_SAMPLE_LOGGED.compareAndSet(false, true)) {
            EarthShape.LOGGER.info("[EarthShape] Terrain density active; first sample at {}, {}, {}.", context.blockX(), context.blockY(), context.blockZ());
        }
        double oceanFloor = EarthShapeConfig.OCEAN_FLOOR_Y.get();
        double landBase = EarthShapeConfig.LAND_BASE_Y.get();
        // With the defaults, land=0.5 maps to Y=63, exactly the Overworld sea level.
        double targetSurface = oceanFloor + (landBase - oceanFloor) * land;
        EarthEnvironmentSignal environment = EarthMapService.INSTANCE.sampleEnvironment(context.blockX(), context.blockZ());
        if (environment.active()) {
            double mappedHeight = EarthShapeConfig.HEIGHTMAP_MIN_Y.get()
                    + environment.height() * (EarthShapeConfig.HEIGHTMAP_MAX_Y.get() - EarthShapeConfig.HEIGHTMAP_MIN_Y.get());
            // Preserve the SDF-controlled shoreline; heightmap influence begins inside solid land.
            double inland = smootherstep(0.55D, 0.85D, land);
            targetSurface += (mappedHeight - landBase) * inland;
        }
        double shape = (targetSurface - context.blockY()) / EarthShapeConfig.SHAPE_VERTICAL_SCALE.get();

        // Limit vanilla/mod noise so it makes local hills, valleys and caves but cannot create
        // islands in mapped ocean or erase a mapped continent.
        // The map, not the vanilla density, owns the macro terrain. This narrow cap keeps only
        // sub-block-to-small-hill detail and prevents the original router from making noisy ridges.
        double boundedDetail = Math.max(-0.40D, Math.min(0.40D, base));
        // Do not let old config files reintroduce noisy vanilla macro-density. A maximum 0.05
        // strength leaves subtle texture only (roughly one block at the default vertical scale).
        double detailStrength = Math.min(EarthShapeConfig.TERRAIN_DETAIL_STRENGTH.get(), 0.05D);
        double result = shape + boundedDetail * detailStrength;

        if (EarthShapeConfig.STRICT_OCEAN_MASK.get()) {
            // A smooth but absolute ceiling for mapped ocean. Without this, rare positive vanilla
            // density spikes can become large islands even where the PNG is completely black.
            double oceanWeight = 1.0D - smootherstep(0.45D, 0.55D, land);
            double seaLevel = 63.0D;
            double oceanCeiling = (seaLevel - 2.0D - context.blockY()) / EarthShapeConfig.SHAPE_VERTICAL_SCALE.get();
            double capped = Math.min(result, oceanCeiling);
            result = result + (capped - result) * oceanWeight;
        }
        return result;
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
