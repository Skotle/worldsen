package io.github.earthshape.worldgen;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Adapts the shared map calculation to Minecraft 26.3's compiled samplers. */
public interface MappedDensityFunction extends DensityFunction {
    DensityFunction argument();
    double compute(FunctionContext position, double original);

    record FunctionContext(int blockX, int blockY, int blockZ) {}

    @Override
    default DensitySampler compileSampler(CompileContext compiler) {
        DensitySampler input = argument().compileSampler(compiler);
        return new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                return (float) compute(new FunctionContext(x, y, z), input.sampleValue(context, x, y, z));
            }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                input.sampleVolume(context, output, volume);
                for (int x = 0; x < volume.sizeX(); x++) {
                    for (int y = 0; y < volume.sizeY(); y++) {
                        for (int z = 0; z < volume.sizeZ(); z++) {
                            int index = volume.indexUnchecked(x, y, z);
                            output.set(index, (float) compute(new FunctionContext(
                                volume.blockX(x), volume.blockY(y), volume.blockZ(z)), output.get(index)));
                        }
                    }
                }
            }
        };
    }

    @Override
    default int domainAxes() { return argument().domainAxes() | AXIS_X | AXIS_Z; }

    // A conservative range prevents optimizer clipping of map-guided values.
    @Override
    default Interval range() { return Interval.INFINITE; }
}
