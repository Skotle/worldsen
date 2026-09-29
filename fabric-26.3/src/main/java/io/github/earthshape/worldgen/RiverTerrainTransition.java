package io.github.earthshape.worldgen;

import io.github.earthshape.map.ClimateLayers;

/**
 * One automatic recovery span shared by the river C, E and W guides.
 *
 * <p>The distance grows with mapped relief and channel width. This prevents a
 * fixed config value from ending beside a tall hill and returning all of the
 * remaining vanilla height at once.</p>
 */
final class RiverTerrainTransition {
   private RiverTerrainTransition() {}

   static double distance(int blockX, int blockZ, int riverWidth) {
      double relief = clamp01(ClimateLayers.INSTANCE.terrainRelief(blockX, blockZ));
      double shapedRelief = relief * relief * (3.0 - 2.0 * relief);
      // Keep guidance local. The former 96..256 block span erased the climate
      // axes across complete mountain regions, leaving only a mountain biome on
      // flat ground. Flat land uses about 32 blocks and full relief stays below
      // 80 blocks even beside a broad channel.
      return Math.min(80.0, 32.0 + 32.0 * shapedRelief + (double)riverWidth * 0.25);
   }

   static double bankNoiseRetention(int blockX, int blockZ) {
      double relief = clamp01(ClimateLayers.INSTANCE.terrainRelief(blockX, blockZ));
      double shapedRelief = relief * relief * (3.0 - 2.0 * relief);
      // Preserve most of a mapped mountain at the river edge; lowlands retain
      // enough source noise to avoid a manufactured flat bank.
      return 0.55 + 0.30 * shapedRelief;
   }

   static double recovery(int blockX, int blockZ, double bankDistance, double transitionDistance) {
      double t = clamp01(bankDistance / Math.max(1.0, transitionDistance));
      double smooth = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
      double retained = bankNoiseRetention(blockX, blockZ);
      return retained + (1.0 - retained) * smooth;
   }

   private static double clamp01(double value) {
      return Math.max(0.0, Math.min(1.0, value));
   }
}
