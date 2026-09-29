package io.github.earthshape.fabric;

import com.mojang.serialization.MapCodec;
import io.github.earthshape.EarthShape;
import io.github.earthshape.worldgen.CoastalContinentalnessDensity;
import io.github.earthshape.worldgen.RiverWeirdnessDensity;
import io.github.earthshape.worldgen.TerrainErosionDensity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/** Registers the codecs used by the bundled Overworld density-function JSON. */
final class EarthShapeDensityFunctions {
    private EarthShapeDensityFunctions() {}

    static void register() {
        register("terrain_erosion", TerrainErosionDensity.CODEC);
        register("river_weirdness", RiverWeirdnessDensity.CODEC);
        register("coastal_continentalness", CoastalContinentalnessDensity.CODEC);
    }

    private static void register(String path, MapCodec<? extends DensityFunction> codec) {
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath(EarthShape.MOD_ID, path), codec);
    }
}
