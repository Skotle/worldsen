package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.map.HeightmapLayer;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Adds a conservative map-elevation term to vanilla's existing Overworld depth function. */
public final class HeightmapOffsetDensity implements DensityFunction {
    private static final MapCodec<HeightmapOffsetDensity> DATA_CODEC = MapCodec.unit(new HeightmapOffsetDensity());
    public static final KeyDispatchDataCodec<HeightmapOffsetDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (EarthShapeCompatibility.disablesWorldgen() || !EarthShapeServerConfig.HEIGHTMAP_ENABLED.get()) return 0.0D;
        double elevation = HeightmapLayer.INSTANCE.sample(context.blockX(), context.blockZ());
        // Height pixels close to a coastline are often already high, but applying them directly
        // makes a map-correct coast rise like a cliff.  Continentalness still shapes the shore;
        // raster elevation fades in only after the terrain is safely inland.
        double inland = RiversMask.INSTANCE.sampleHeightmapInlandness(context.blockX(), context.blockZ());
        // A river is selected as a real biome, not painted into an existing chunk.  Withhold
        // mountain relief around its source line so both banks grade down toward its water level.
        double riverRelief = RiversMask.INSTANCE.sampleRiverReliefFactor(context.blockX(), context.blockZ());
        // Force the land immediately beside water down toward the waterline before applying
        // raster uplift.  This is a fixed physical grade (about 8 blocks at the coast and 5
        // beside a layer river), then fades through the existing inland/river relief ranges.
        // It prevents high heightmap pixels from reaching the shoreline at full elevation.
        double coastalSink = -0.125D * (1.0D - inland);
        double riverSink = -0.080D * (1.0D - riverRelief);
        // Keep ordinary terrain around vanilla sea-level land height.  Below the median the
        // negative term grows with deviation; above it, quadratic/quartic terms reserve large
        // uplift for genuine highland pixels instead of lifting entire continents.
        double median = EarthShapeServerConfig.HEIGHTMAP_MEDIAN.get();
        double deviation = elevation - median;
        double lowland = Math.min(0.0D, deviation) * 0.20D + Math.max(0.0D, deviation) * 0.16D;
        double mountain = Math.max(0.0D, Math.min(1.0D, deviation / Math.max(0.01D, 1.0D - median)));
        double highlandLift = mountain * mountain * 0.24D + mountain * mountain * mountain * mountain * 0.30D;
        return coastalSink + riverSink + inland * riverRelief * (lowland + highlandLift);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
    @Override public double minValue() { return -0.20D; }
    @Override public double maxValue() { return 0.95D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
