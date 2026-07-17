package io.github.earthshape.worldgen;

import io.github.earthshape.EarthShape;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the density functions used before normal terrain generation. */
public final class EarthShapeDensityFunctions {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, EarthShape.MOD_ID);

    static {
        TYPES.register("rivers_continents", () -> RiversContinentsDensity.CODEC.codec());
        TYPES.register("heightmap_offset", () -> HeightmapOffsetDensity.CODEC.codec());
        TYPES.register("river_bank_grade", () -> RiverBankGradeDensity.CODEC.codec());
    }

    private EarthShapeDensityFunctions() {}

    public static void register(IEventBus eventBus) { TYPES.register(eventBus); }
}
