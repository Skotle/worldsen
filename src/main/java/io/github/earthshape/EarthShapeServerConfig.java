package io.github.earthshape;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned world-generation settings. They are stored per world in serverconfig. */
public final class EarthShapeServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BLOCKS_PER_PIXEL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rivers_continents");
        BLOCKS_PER_PIXEL = builder
                .comment("Minecraft blocks represented by one rivers.bmp pixel. 20 preserves the bundled map's 283840 x 141920 block size.")
                .defineInRange("blocksPerPixel", 20, 1, 4096);
        builder.pop();
        SPEC = builder.build();
    }

    private EarthShapeServerConfig() {}
}
