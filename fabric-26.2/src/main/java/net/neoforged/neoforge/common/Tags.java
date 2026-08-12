package net.neoforged.neoforge.common;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Source-compatibility bridge for the NeoForge biome classification surface.
 * Every value delegates to Fabric API's matching conventional biome tag.
 */
public final class Tags {
    private Tags() {}
    public static final class Biomes {
        public static final TagKey<Biome> IS_AQUATIC = ConventionalBiomeTags.IS_AQUATIC;
        public static final TagKey<Biome> IS_BADLANDS = ConventionalBiomeTags.IS_BADLANDS;
        public static final TagKey<Biome> IS_BEACH = ConventionalBiomeTags.IS_BEACH;
        public static final TagKey<Biome> IS_CAVE = ConventionalBiomeTags.IS_CAVE;
        public static final TagKey<Biome> IS_COLD = ConventionalBiomeTags.IS_COLD;
        public static final TagKey<Biome> IS_DENSE_VEGETATION = ConventionalBiomeTags.IS_VEGETATION_DENSE;
        public static final TagKey<Biome> IS_DESERT = ConventionalBiomeTags.IS_DESERT;
        public static final TagKey<Biome> IS_DRY = ConventionalBiomeTags.IS_DRY;
        public static final TagKey<Biome> IS_FOREST = ConventionalBiomeTags.IS_FOREST;
        public static final TagKey<Biome> IS_HILL = ConventionalBiomeTags.IS_HILL;
        public static final TagKey<Biome> IS_HOT = ConventionalBiomeTags.IS_HOT;
        public static final TagKey<Biome> IS_JUNGLE = ConventionalBiomeTags.IS_JUNGLE;
        public static final TagKey<Biome> IS_LUSH = ConventionalBiomeTags.IS_LUSH;
        public static final TagKey<Biome> IS_MAGICAL = ConventionalBiomeTags.IS_MAGICAL;
        public static final TagKey<Biome> IS_MOUNTAIN = ConventionalBiomeTags.IS_MOUNTAIN;
        public static final TagKey<Biome> IS_MOUNTAIN_PEAK = ConventionalBiomeTags.IS_MOUNTAIN_PEAK;
        public static final TagKey<Biome> IS_MOUNTAIN_SLOPE = ConventionalBiomeTags.IS_MOUNTAIN_SLOPE;
        public static final TagKey<Biome> IS_MUSHROOM = ConventionalBiomeTags.IS_MUSHROOM;
        public static final TagKey<Biome> IS_OCEAN = ConventionalBiomeTags.IS_OCEAN;
        public static final TagKey<Biome> IS_OVERWORLD = ConventionalBiomeTags.IS_OVERWORLD;
        public static final TagKey<Biome> IS_PLAINS = ConventionalBiomeTags.IS_PLAINS;
        public static final TagKey<Biome> IS_PLATEAU = ConventionalBiomeTags.IS_PLATEAU;
        public static final TagKey<Biome> IS_RARE = ConventionalBiomeTags.IS_RARE;
        public static final TagKey<Biome> IS_RIVER = ConventionalBiomeTags.IS_RIVER;
        public static final TagKey<Biome> IS_SANDY = ConventionalBiomeTags.IS_SANDY;
        public static final TagKey<Biome> IS_SAVANNA = ConventionalBiomeTags.IS_SAVANNA;
        public static final TagKey<Biome> IS_SNOWY = ConventionalBiomeTags.IS_SNOWY;
        public static final TagKey<Biome> IS_SPARSE_VEGETATION = ConventionalBiomeTags.IS_VEGETATION_SPARSE;
        public static final TagKey<Biome> IS_SPOOKY = ConventionalBiomeTags.IS_SPOOKY;
        public static final TagKey<Biome> IS_SWAMP = ConventionalBiomeTags.IS_SWAMP;
        public static final TagKey<Biome> IS_TAIGA = ConventionalBiomeTags.IS_TAIGA;
        public static final TagKey<Biome> IS_UNDERGROUND = ConventionalBiomeTags.IS_UNDERGROUND;
        public static final TagKey<Biome> IS_WASTELAND = ConventionalBiomeTags.IS_WASTELAND;
        public static final TagKey<Biome> IS_WET = ConventionalBiomeTags.IS_WET;
        private Biomes() {}
    }
}
