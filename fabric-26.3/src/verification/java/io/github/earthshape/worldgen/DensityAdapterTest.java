package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Checks the 26.3 adapter's volume indexing against independent point samples. */
public final class DensityAdapterTest {
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedDensityFunction mapped = new MappedDensityFunction() {
            public DensityFunction argument() { return new CoordinateInput(); }
            public double compute(FunctionContext p, double input) {
                return input * 2 + p.blockX() * 7 - p.blockY() * 3 + p.blockZ() * 11;
            }
            public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
            public MapCodec<? extends DensityFunction> codec() { throw new UnsupportedOperationException(); }
        };
        DensitySampler sampler = mapped.compileSampler(null);
        // Unequal dimensions, negative coordinates, and unequal strides expose
        // transposed axes and incorrect assumptions about contiguous world blocks.
        DensityVolume volume = new DensityVolume(3, 4, 5, -37, -12, 19, 4, 8, 2);
        DensityBuffer output = DensityBuffer.createUnpooled(volume.size());
        sampler.sampleVolume(null, output, volume);
        int checked = 0;
        for (int x = 0; x < volume.sizeX(); x++) {
            for (int y = 0; y < volume.sizeY(); y++) {
                for (int z = 0; z < volume.sizeZ(); z++) {
                    int bx = volume.blockX(x), by = volume.blockY(y), bz = volume.blockZ(z);
                    float expected = 9 * bx + by + 17 * bz;
                    float point = sampler.sampleValue(null, bx, by, bz);
                    float bulk = output.get(volume.indexUnchecked(x, y, z));
                    if (point != expected || bulk != expected) {
                        throw new AssertionError("Sampler mismatch at " + bx + "," + by + "," + bz
                            + ": expected=" + expected + " point=" + point + " bulk=" + bulk);
                    }
                    checked++;
                }
            }
        }
        if (mapped.domainAxes() != DensityFunction.ALL_AXES) throw new AssertionError("Lost input axes");
        System.out.println("PASS: " + checked + " independent point/volume samples and domain axes");
    }

    private record CoordinateInput() implements DensityFunction {
        public DensitySampler compileSampler(CompileContext ignored) {
            return new DensitySampler() {
                public float sampleValue(SamplerContext ignored, int x, int y, int z) {
                    return x + 2 * y + 3 * z;
                }
                public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                    DensitySampler.sampleVolumeNaive(context, output, volume, this);
                }
            };
        }
        public DensityFunction rewriteChildren(DfRewriteRule ignored) { return this; }
        public Interval range() { return Interval.INFINITE; }
        public int domainAxes() { return ALL_AXES; }
        public MapCodec<? extends DensityFunction> codec() { throw new UnsupportedOperationException(); }
    }
}
