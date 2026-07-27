/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.MapCodec
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.level.levelgen.DensityFunction
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.neoforge.registries.DeferredRegister
 */
package io.github.earthshape.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.worldgen.LayerWaterlineDensity;
import io.github.earthshape.worldgen.RiverBankGradeDensity;
import io.github.earthshape.worldgen.RiversContinentsDensity;
import io.github.earthshape.worldgen.TerrainNoiseOffsetDensity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EarthShapeDensityFunctions {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES = DeferredRegister.create((ResourceKey)Registries.DENSITY_FUNCTION_TYPE, (String)"earthshape");

    private EarthShapeDensityFunctions() {
    }

    public static void register(IEventBus eventBus) {
        TYPES.register(eventBus);
    }

    static {
        TYPES.register("rivers_continents", () -> RiversContinentsDensity.CODEC.codec());
        TYPES.register("terrain_noise_offset", () -> TerrainNoiseOffsetDensity.CODEC.codec());
        TYPES.register("river_bank_grade", () -> RiverBankGradeDensity.CODEC.codec());
        TYPES.register("layer_waterline", () -> LayerWaterlineDensity.CODEC.codec());
    }
}

