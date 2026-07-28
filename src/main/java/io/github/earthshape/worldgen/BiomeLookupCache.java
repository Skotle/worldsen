package io.github.earthshape.worldgen;

import io.github.earthshape.map.ClimateLayers;

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

   public boolean matches(int x, int z) {
      return this.x == x && this.z == z;
   }

   public ClimateLayers.TerrainKind terrain() {
      return this.terrain;
   }

   public boolean sourceRiver() {
      return this.sourceRiver;
   }

   public void set(int x, int z, ClimateLayers.TerrainKind terrain, boolean sourceRiver) {
      this.x = x;
      this.z = z;
      this.terrain = terrain;
      this.sourceRiver = sourceRiver;
   }
}
