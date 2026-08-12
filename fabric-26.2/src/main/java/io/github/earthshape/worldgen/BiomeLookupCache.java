package io.github.earthshape.worldgen;

import io.github.earthshape.map.ClimateLayers;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * Per-worker transient values shared by the two stages of one biome lookup.
 * This intentionally lives outside the mixin package: NeoForge treats that package
 * as mixin-owned and rejects ordinary classes referenced directly from it.
 */
public final class BiomeLookupCache {
   private int x = Integer.MIN_VALUE;
   private int z = Integer.MIN_VALUE;
   private ClimateLayers.TerrainKind terrain;
   private boolean sourceRiver;
   private boolean frozenRiver;
   private ClimateLayers.TreeCover trees;
   private double temperature;
   private int regionalVariant = Integer.MIN_VALUE;
   private Climate.ParameterList<Holder<Biome>> parameterSource0;
   private Climate.ParameterList<Holder<Biome>> candidates0;
   private int candidateGroup0 = Integer.MIN_VALUE;
   private Climate.ParameterList<Holder<Biome>> parameterSource1;
   private Climate.ParameterList<Holder<Biome>> candidates1;
   private int candidateGroup1 = Integer.MIN_VALUE;

   public boolean matches(int x, int z) {
      return this.x == x && this.z == z;
   }

   public ClimateLayers.TerrainKind terrain() {
      return this.terrain;
   }

   public boolean sourceRiver() {
      return this.sourceRiver;
   }

   public boolean frozenRiver() {
      return this.frozenRiver;
   }

   public ClimateLayers.TreeCover trees() {
      return this.trees;
   }

   public double temperature() {
      return this.temperature;
   }

   public void set(
      int x, int z, ClimateLayers.TerrainKind terrain, boolean sourceRiver, boolean frozenRiver,
      ClimateLayers.TreeCover trees, double temperature
   ) {
      this.x = x;
      this.z = z;
      this.terrain = terrain;
      this.sourceRiver = sourceRiver;
      this.frozenRiver = frozenRiver;
      this.trees = trees;
      this.temperature = temperature;
      this.regionalVariant = Integer.MIN_VALUE;
   }

   public int regionalVariant() {
      return this.regionalVariant;
   }

   public void cacheRegionalVariant(int regionalVariant) {
      this.regionalVariant = regionalVariant;
   }

   public Climate.ParameterList<Holder<Biome>> candidates(
      Climate.ParameterList<Holder<Biome>> source, int group
   ) {
      if (this.parameterSource0 == source && this.candidateGroup0 == group) return this.candidates0;
      if (this.parameterSource1 == source && this.candidateGroup1 == group) {
         Climate.ParameterList<Holder<Biome>> hit = this.candidates1;
         this.parameterSource1 = this.parameterSource0;
         this.candidateGroup1 = this.candidateGroup0;
         this.candidates1 = this.candidates0;
         this.parameterSource0 = source;
         this.candidateGroup0 = group;
         this.candidates0 = hit;
         return hit;
      }
      return null;
   }

   public void cacheCandidates(
      Climate.ParameterList<Holder<Biome>> source, int group,
      Climate.ParameterList<Holder<Biome>> candidates
   ) {
      this.parameterSource1 = this.parameterSource0;
      this.candidateGroup1 = this.candidateGroup0;
      this.candidates1 = this.candidates0;
      this.parameterSource0 = source;
      this.candidateGroup0 = group;
      this.candidates0 = candidates;
   }
}
