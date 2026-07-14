package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShape;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the codecs used when EarthShape's router wrappers are persisted in world data. */
public final class EarthShapeDensityFunctions {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, EarthShape.MOD_ID);

    static {
        TYPES.register("continentalness", () -> EarthContinentalnessDensity.CODEC.codec());
        TYPES.register("terrain_mask", () -> EarthTerrainDensity.CODEC.codec());
    }

    private EarthShapeDensityFunctions() {}

    public static void register(IEventBus eventBus) {
        TYPES.register(eventBus);
    }
}
