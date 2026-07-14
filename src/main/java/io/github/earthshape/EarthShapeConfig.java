package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tunables intentionally limited to continental placement; biome and decoration settings stay vanilla/pack-owned. */
public final class EarthShapeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue BLOCKS_PER_PIXEL;
    public static final ModConfigSpec.IntValue COAST_WIDTH_BLOCKS;
    public static final ModConfigSpec.DoubleValue CONTROL_STRENGTH;
    public static final ModConfigSpec.IntValue WARP_STRENGTH_BLOCKS;
    public static final ModConfigSpec.IntValue WARP_SCALE_BLOCKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("earthshape");
        ENABLED = b.comment("Enable EarthShape's Overworld continentalness and terrain-density wrappers.")
                .define("enabled", true);
        BLOCKS_PER_PIXEL = b.comment("Horizontal scale of earth_continentalness.png.")
                .defineInRange("blocksPerPixel", 16, 1, 4096);
        COAST_WIDTH_BLOCKS = b.comment("Width of the smooth coast transition, in blocks.")
                .defineInRange("coastWidthBlocks", 256, 16, 4096);
        CONTROL_STRENGTH = b.comment("Blend weight for EarthShape continentalness and terrain masking.")
                .defineInRange("controlStrength", 0.80D, 0.0D, 1.0D);
        WARP_STRENGTH_BLOCKS = b.comment("Maximum deterministic coastline domain warp.")
                .defineInRange("warpStrengthBlocks", 64, 0, 512);
        WARP_SCALE_BLOCKS = b.comment("Low-frequency domain warp wavelength.")
                .defineInRange("warpScaleBlocks", 1536, 64, 16384);
        b.pop();
        SPEC = b.build();
    }

    private EarthShapeConfig() {}
}
