package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.map.RiversMask;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * A broad, shallow pre-generation grade for source rivers.  This is deliberately
 * not a block or biome overwrite: aquifers and surface rules still create the water.
 */
public final class RiverBankGradeDensity implements DensityFunction {
    private static final MapCodec<RiverBankGradeDensity> DATA_CODEC = MapCodec.unit(new RiverBankGradeDensity());
    public static final KeyDispatchDataCodec<RiverBankGradeDensity> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (EarthShapeCompatibility.disablesWorldgen() || !EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()) return 0.0D;
        if (!RiversMask.INSTANCE.hasInlandRiverInfluence(context.blockX(), context.blockZ())) return 0.0D;
        double distance = RiversMask.INSTANCE.riverCentrelineDistance(context.blockX(), context.blockZ());
        int widthBlocks = RiversMask.INSTANCE.effectiveRiverWidthBlocks(context.blockX(), context.blockZ());
        if (widthBlocks == 0) return 0.0D;
        // Colour-derived water width, with a broad graded bank so a 25-block river never
        // becomes a vertical trench at its edge.
        double floorRadius = widthBlocks / 2.0D;
        double distanceBlocks = distance * RiversMask.INSTANCE.blocksPerPixel();
        // Grade the land separately from the narrow water stroke.  This keeps a normal
        // river width while avoiding a vertical bank at the waterline.
        double radius = floorRadius + Math.max(36, EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get());
        if (distanceBlocks >= radius) return 0.0D;
        // A river's centre is a shallow channel, not a canyon.  Clamp even old
        // serverconfig values so this applies to existing 1.0.17 worlds.
        double maximumDrop = Math.min(2, EarthShapeServerConfig.RIVER_MAXIMUM_DEPTH_BLOCKS.get()) / 64.0D;
        if (distanceBlocks <= floorRadius) return -maximumDrop;
        double t = 1.0D - (distanceBlocks - floorRadius) / (radius - floorRadius);
        return -maximumDrop * t * t * (3.0D - 2.0D * t);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
    @Override public double minValue() { return -0.50D; }
    @Override public double maxValue() { return 0.0D; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }

}
