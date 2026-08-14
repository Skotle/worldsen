package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.SplittableRandom;
import javax.imageio.ImageIO;

public final class RiversMask {
   public static final RiversMask INSTANCE = new RiversMask();
   public static final int DEFAULT_BLOCKS_PER_PIXEL = 20;
   private static final int RIVER_SEARCH_RADIUS = 4;
   private static final double RIVER_CORNER_TRIM = 0.32;
   private volatile RiversMask.Data data;
   private volatile ThreadLocal<RiversMask.LandSampleCache> landSampleCache = ThreadLocal.withInitial(RiversMask.LandSampleCache::new);
   private volatile ThreadLocal<RiversMask.RiverWidthCache> riverWidthCache = ThreadLocal.withInitial(RiversMask.RiverWidthCache::new);
   private volatile ThreadLocal<RiversMask.RiverDistanceCache> riverDistanceCache = ThreadLocal.withInitial(RiversMask.RiverDistanceCache::new);
   private volatile ThreadLocal<RiversMask.RiverColumnCache> riverColumnCache = ThreadLocal.withInitial(RiversMask.RiverColumnCache::new);
   private volatile ThreadLocal<RiversMask.RiverBiomeCache> riverBiomeCache = ThreadLocal.withInitial(RiversMask.RiverBiomeCache::new);
   private volatile ThreadLocal<RiversMask.ScalarSampleCache> coastalLandnessCache = ThreadLocal.withInitial(RiversMask.ScalarSampleCache::new);
   private volatile ThreadLocal<RiversMask.ScalarSampleCache> riverMouthCache = ThreadLocal.withInitial(RiversMask.ScalarSampleCache::new);
   private volatile ThreadLocal<RiversMask.ScalarSampleCache> surfaceBankDistanceCache = ThreadLocal.withInitial(RiversMask.ScalarSampleCache::new);
   private volatile long mapCenterSeed;
   private volatile long mapTransformVersion;
   private volatile double selectedCenterX = Double.NaN;
   private volatile double selectedCenterZ = Double.NaN;
   private volatile double blocksToMapPixels;
   private volatile int activeBlocksPerPixel;
   private volatile boolean mapCenterReady;

   private RiversMask() {
   }

   public synchronized void configureMapCenter(long worldSeed) {
      this.mapCenterSeed = worldSeed;
      this.mapCenterReady = false;
      this.selectedCenterX = Double.NaN;
      this.selectedCenterZ = Double.NaN;
      this.activeBlocksPerPixel = 0;
      // A client can open multiple worlds in one JVM. Replace the ThreadLocal
      // containers so a column cached for the previous centre cannot leak into
      // the next world's coordinate transform.
      this.landSampleCache = ThreadLocal.withInitial(RiversMask.LandSampleCache::new);
      this.riverWidthCache = ThreadLocal.withInitial(RiversMask.RiverWidthCache::new);
      this.riverDistanceCache = ThreadLocal.withInitial(RiversMask.RiverDistanceCache::new);
      this.riverColumnCache = ThreadLocal.withInitial(RiversMask.RiverColumnCache::new);
      this.riverBiomeCache = ThreadLocal.withInitial(RiversMask.RiverBiomeCache::new);
      this.coastalLandnessCache = ThreadLocal.withInitial(RiversMask.ScalarSampleCache::new);
      this.riverMouthCache = ThreadLocal.withInitial(RiversMask.ScalarSampleCache::new);
      this.surfaceBankDistanceCache = ThreadLocal.withInitial(RiversMask.ScalarSampleCache::new);
      this.mapTransformVersion++;
   }

   public double sampleLand(int blockX, int blockZ) {
      return this.sampleExactLand(this.data(), blockX, blockZ);
   }

   public double sampleLayerLand(int blockX, int blockZ) {
      // This remains an authoritative binary coastline classification. The
      // continuous contour below only changes the shape of the boundary; callers
      // still receive either land or water, never an ambiguous partial value.
      return this.sampleExactLand(this.data(), blockX, blockZ);
   }

   /**
    * Land that must remain a treeless permanent snowfield. This covers the
    * southern 520-block map border and the whole connected Antarctic land mass,
    * even where that continent extends farther north than the border band.
    */
   public boolean isPermanentSouthernSnowLand(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      if (this.sampleExactLand(loaded, blockX, blockZ) < 0.5) return false;
      double sourceX = this.mapImageX(blockX, loaded);
      double sourceZ = this.mapImageZ(blockZ, loaded);
      if (sourceX < 0.0 || sourceZ < 0.0 || sourceX >= loaded.width || sourceZ >= loaded.height) return false;
      double southEdgeDistance = ((double)loaded.height - sourceZ) * (double)this.blocksPerPixel();
      return southEdgeDistance <= 520.0 || loaded.southernSnow.isAntarctica((int)Math.floor(sourceX), (int)Math.floor(sourceZ));
   }

   /**
    * Chunky-only selection mask.  It retains mapped land, enclosed inland water,
    * and sea passages narrower than ten source pixels; it never participates in
    * terrain, biome, or actual coastline generation.
    */
   public double samplePregenerationLand(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      int x = (int)Math.floor(this.mapImageX(blockX, loaded));
      int z = (int)Math.floor(this.mapImageZ(blockZ, loaded));
      return x >= 0 && z >= 0 && x < loaded.width && z < loaded.height && loaded.pregeneration.get(z * loaded.width + x) ? 1.0 : 0.0;
   }

   /**
    * Fast Chunky-shape query for one 16x16 candidate chunk. Convert the chunk
    * bounds to source pixels once, then use BitSet row scans instead of calling
    * samplePregenerationLand for every source cell touched by the chunk.
    */
   public boolean intersectsPregenerationChunk(int minBlockX, int minBlockZ) {
      RiversMask.Data loaded = this.data();
      int minX = (int)Math.floor(this.mapImageX(minBlockX, loaded));
      int maxX = (int)Math.floor(this.mapImageX(minBlockX + 15, loaded));
      int minZ = (int)Math.floor(this.mapImageZ(minBlockZ, loaded));
      int maxZ = (int)Math.floor(this.mapImageZ(minBlockZ + 15, loaded));
      if (maxX < 0 || maxZ < 0 || minX >= loaded.width || minZ >= loaded.height) return false;

      minX = Math.max(0, minX);
      maxX = Math.min(loaded.width - 1, maxX);
      minZ = Math.max(0, minZ);
      maxZ = Math.min(loaded.height - 1, maxZ);
      for (int z = minZ; z <= maxZ; z++) {
         int rowStart = z * loaded.width + minX;
         int hit = loaded.pregeneration.nextSetBit(rowStart);
         if (hit >= rowStart && hit <= z * loaded.width + maxX) return true;
      }
      return false;
   }

   /**
    * A pre-smoothed land mask used only for the continentalness transition.
    * The exact mask above remains authoritative for coastline and biome choice.
    */
   public double sampleCoastalLandness(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.ScalarSampleCache cache = this.coastalLandnessCache.get();
      int slot = RiversMask.ScalarSampleCache.slot(blockX, blockZ);
      if (cache.data == loaded && cache.x[slot] == blockX && cache.z[slot] == blockZ) return cache.value[slot];
      // The authoritative coastline is displaced by coastWarpNoise in
      // sampleExactLand. Read its transition field through the same coordinate
      // transform; otherwise the height fade can finish beside the old raster
      // edge and restore inland relief directly against the new water edge.
      double value = this.sampleCoastAlignedBytes(loaded, loaded.coastalLandness, blockX, blockZ);
      cache.set(loaded, slot, blockX, blockZ, value);
      return value;
   }

   /**
    * Approximate distance from a mapped ocean column to the nearest source-map
    * land, in blocks.  The precomputed chamfer field is interpolated here so
    * continentalness can descend across the shelf without repeating an
    * expensive coastline search for every density sample.
    */
   public double oceanDistanceBlocks(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      if (this.sampleExactLand(loaded, blockX, blockZ) >= 0.5) return 0.0;
      // Values use three units per source pixel (4 on a diagonal).
      return this.sampleCoastAlignedBytes(loaded, loaded.oceanDistance, blockX, blockZ)
         * 255.0 / 3.0 * (double)this.blocksPerPixel();
   }

   /**
    * Distance across mapped land from an actual ocean or inland-river surface.
    * Only the first three blocks are reported. This is intentionally measured
    * in world blocks rather than source pixels, so increasing map scale never
    * turns the climbable water edge into a wide flat strip.
    */
   public double surfaceBankDistanceBlocks(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.ScalarSampleCache cache = this.surfaceBankDistanceCache.get();
      int slot = RiversMask.ScalarSampleCache.slot(blockX, blockZ);
      if (cache.data == loaded && cache.x[slot] == blockX && cache.z[slot] == blockZ) {
         return cache.value[slot];
      }

      double nearest = Double.POSITIVE_INFINITY;
      if (this.sampleExactLand(loaded, blockX, blockZ) >= 0.5) {
         if (this.hasInlandRiverInfluence(blockX, blockZ)) {
            int width = this.effectiveRiverWidthBlocks(blockX, blockZ);
            if (width > 0) {
               double centreDistance = this.riverCentrelineDistance(blockX, blockZ)
                  * (double)this.blocksPerPixel();
               double bankDistance = centreDistance - (double)width * 0.5;
               if (bankDistance > 0.0 && bankDistance <= 3.0) nearest = bankDistance;
            }
         }

         // The blurred field is a cheap inland rejection. Exact binary samples
         // below still determine the final distance to the warped coastline.
         if (this.sampleCoastalLandness(blockX, blockZ) < 0.999) {
            for (int dz = -3; dz <= 3; dz++) {
               for (int dx = -3; dx <= 3; dx++) {
                  int distanceSquared = dx * dx + dz * dz;
                  if (distanceSquared == 0 || distanceSquared > 9) continue;
                  if (this.sampleExactLand(loaded, blockX + dx, blockZ + dz) < 0.5) {
                     nearest = Math.min(nearest, Math.sqrt((double)distanceSquared));
                  }
               }
            }
         }
      }

      double result = nearest <= 3.0 ? nearest : -1.0;
      cache.set(loaded, slot, blockX, blockZ, result);
      return result;
   }

   /** Coherent one-to-three-block landing width; never changes per individual block. */
   public int surfaceBankWidthBlocks(int blockX, int blockZ) {
      double noise = coastWarpNoise(
         blockX, blockZ, 0x6A09E667F3BCC909L ^ this.mapCenterSeed
      );
      return noise < -0.28 ? 1 : (noise > 0.28 ? 3 : 2);
   }

   /**
    * Coherent, seed-stable multiplier for the offshore continental shelf.
    * It changes only over broad coastal reaches, rather than per block, so a
    * shelf remains a single smooth slope while different coasts receive
    * naturally different run-out lengths.
    */
   public double coastShelfFadeScale(int blockX, int blockZ) {
      if (!(Boolean)EarthShapeServerConfig.COAST_SHELF_VARIATION_ENABLED.get()) return 1.0;
      double minimum = (Double)EarthShapeServerConfig.COAST_SHELF_VARIATION_MIN_SCALE.get();
      double maximum = (Double)EarthShapeServerConfig.COAST_SHELF_VARIATION_MAX_SCALE.get();
      if (maximum < minimum) {
         double swap = minimum;
         minimum = maximum;
         maximum = swap;
      }
      // A 768-block wavelength keeps the selector coherent across a shore
      // reach; smooth interpolation prevents visible square selector cells.
      int cellSize = 768;
      int cellX = Math.floorDiv(blockX, cellSize);
      int cellZ = Math.floorDiv(blockZ, cellSize);
      double tx = smoothstep((double)Math.floorMod(blockX, cellSize) / (double)cellSize);
      double tz = smoothstep((double)Math.floorMod(blockZ, cellSize) / (double)cellSize);
      long salt = 0xD1B54A32D192ED03L ^ this.mapCenterSeed;
      double top = lerp(axialValue(cellX, cellZ, salt), axialValue(cellX + 1, cellZ, salt), tx);
      double bottom = lerp(axialValue(cellX, cellZ + 1, salt), axialValue(cellX + 1, cellZ + 1, salt), tx);
      double normalized = (lerp(top, bottom, tz) + 1.0) * 0.5;
      return lerp(minimum, maximum, normalized);
   }

   /**
    * Variable visible river depth. The centre meanders between three and six
    * blocks while the submerged shoulder rises toward one block at the bank.
    */
   public int riverBedDepthBlocks(int blockX, int blockZ) {
      int maximum = Math.min(6, (Integer)EarthShapeServerConfig.RIVER_MAXIMUM_DEPTH_BLOCKS.get());
      int width = this.effectiveRiverWidthBlocks(blockX, blockZ);
      if (width <= 0) return maximum;

      double noise = coastWarpNoise(
         blockX, blockZ, 0xBB67AE8584CAA73BL ^ Long.rotateLeft(this.mapCenterSeed, 23)
      );
      double normalized = Math.max(0.0, Math.min(1.0, noise * 0.5 + 0.5));
      int centreDepth = Math.min(maximum, 3 + (int)Math.round(normalized * 3.0));
      double radius = Math.max(0.5, (double)width * 0.5);
      double distance = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
      double shoulder = smoothstep((distance / radius - 0.58) / 0.42);
      int depth = (int)Math.round(lerp((double)centreDepth, 1.0, shoulder));
      return Math.max(1, Math.min(maximum, depth));
   }

   /** True when the authoritative land pixel belongs to a small connected land mass. */
   public boolean isSmallIsland(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      double sourceX = this.mapImageX(blockX, loaded);
      double sourceZ = this.mapImageZ(blockZ, loaded);
      int imageX = (int)Math.floor(sourceX);
      int imageZ = (int)Math.floor(sourceZ);
      return imageX >= 0 && imageZ >= 0 && imageX < loaded.width && imageZ < loaded.height
         && loaded.land.get(imageZ * loaded.width + imageX)
         && loaded.continentRegions.tier(imageX, imageZ) == 0;
   }

   private double sampleExactLand(RiversMask.Data loaded, int blockX, int blockZ) {
      RiversMask.LandSampleCache cache = this.landSampleCache.get();
      if (cache.data != loaded) cache.reset(loaded);
      int cacheSlot = RiversMask.LandSampleCache.slot(blockX, blockZ);
      if (cache.x[cacheSlot] == blockX && cache.z[cacheSlot] == blockZ) return cache.land[cacheSlot];
      double sourceX = this.mapImageX(blockX, loaded);
      double sourceZ = this.mapImageZ(blockZ, loaded);
      int originalX = (int)Math.floor(sourceX);
      int originalZ = (int)Math.floor(sourceZ);

      // Displace the contour by less than half a source pixel. Keep the amount
      // in source-pixel units: reducing it as blocksPerPixel increased was the
      // reason large-scale worlds exposed the original raster cells so clearly.
      // River influence cells stay unwarped so channels and mouths still meet
      // the source coast used by the river-distance field.
      boolean nearRiver = originalX >= 0
         && originalZ >= 0
         && originalX < loaded.width
         && originalZ < loaded.height
         && loaded.riverInfluence.get(originalZ * loaded.width + originalX);
      if (!nearRiver) {
         double amplitudePixels = 0.38;
         sourceX += coastWarpNoise(blockX, blockZ, 0x243F6A8885A308D3L) * amplitudePixels;
         sourceZ += coastWarpNoise(blockX, blockZ, 0x13198A2E03707344L) * amplitudePixels;
      }

      // Treat raster values as samples at pixel centres and interpolate the four
      // neighbouring samples with a C1-continuous curve. Thresholding the result
      // keeps land/ocean binary and layer-controlled, while joining coastline
      // points as rounded curves instead of enlarged square steps.
      double gridX = sourceX - 0.5;
      double gridZ = sourceZ - 0.5;
      int x = (int)Math.floor(gridX);
      int z = (int)Math.floor(gridZ);
      double tx = smoothstep(gridX - (double)x);
      double tz = smoothstep(gridZ - (double)z);
      double top = lerp(sampleLandValue(loaded, x, z), sampleLandValue(loaded, x + 1, z), tx);
      double bottom = lerp(sampleLandValue(loaded, x, z + 1), sampleLandValue(loaded, x + 1, z + 1), tx);
      double land = lerp(top, bottom, tz) >= 0.5 ? 1.0 : 0.0;
      cache.x[cacheSlot] = blockX;
      cache.z[cacheSlot] = blockZ;
      cache.land[cacheSlot] = land;
      return land;
   }

   private static double sampleLandValue(RiversMask.Data loaded, int x, int z) {
      return x >= 0 && z >= 0 && x < loaded.width && z < loaded.height ? loaded.land(x, z) : 0.0;
   }

   private static double smoothstep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double coastWarpNoise(int blockX, int blockZ, long salt) {
      int cellSize = 96;
      int cellX = Math.floorDiv(blockX, cellSize);
      int cellZ = Math.floorDiv(blockZ, cellSize);
      double tx = (double)Math.floorMod(blockX, cellSize) / (double)cellSize;
      double tz = (double)Math.floorMod(blockZ, cellSize) / (double)cellSize;
      tx = tx * tx * (3.0 - 2.0 * tx);
      tz = tz * tz * (3.0 - 2.0 * tz);
      double top = lerp(axialValue(cellX, cellZ, salt), axialValue(cellX + 1, cellZ, salt), tx);
      double bottom = lerp(axialValue(cellX, cellZ + 1, salt), axialValue(cellX + 1, cellZ + 1, salt), tx);
      return lerp(top, bottom, tz);
   }

   private double sampleBytes(RiversMask.Data loaded, byte[] values, int blockX, int blockZ) {
      double imageX = this.mapImageX(blockX, loaded);
      double imageZ = this.mapImageZ(blockZ, loaded);
      return sampleBytesAt(loaded, values, imageX, imageZ);
   }

   private double sampleCoastAlignedBytes(RiversMask.Data loaded, byte[] values, int blockX, int blockZ) {
      double imageX = this.mapImageX(blockX, loaded);
      double imageZ = this.mapImageZ(blockZ, loaded);
      int originalX = (int)Math.floor(imageX);
      int originalZ = (int)Math.floor(imageZ);
      boolean nearRiver = originalX >= 0
         && originalZ >= 0
         && originalX < loaded.width
         && originalZ < loaded.height
         && loaded.riverInfluence.get(originalZ * loaded.width + originalX);
      if (!nearRiver) {
         double amplitudePixels = 0.38;
         imageX += coastWarpNoise(blockX, blockZ, 0x243F6A8885A308D3L) * amplitudePixels;
         imageZ += coastWarpNoise(blockX, blockZ, 0x13198A2E03707344L) * amplitudePixels;
      }
      return sampleBytesAt(loaded, values, imageX, imageZ);
   }

   private static double sampleBytesAt(RiversMask.Data loaded, byte[] values, double imageX, double imageZ) {
      if (!(imageX < 0.0) && !(imageZ < 0.0) && !(imageX >= (double)(loaded.width - 1)) && !(imageZ >= (double)(loaded.height - 1))) {
         int x = (int)Math.floor(imageX);
         int z = (int)Math.floor(imageZ);
         double tx = imageX - (double)x;
         double tz = imageZ - (double)z;
         double a = lerp(sampleValue(loaded, values, x, z), sampleValue(loaded, values, x + 1, z), tx);
         double b = lerp(sampleValue(loaded, values, x, z + 1), sampleValue(loaded, values, x + 1, z + 1), tx);
         return lerp(a, b, tz);
      } else {
         return 0.0;
      }
   }

   private static double sampleValue(RiversMask.Data loaded, byte[] values, int x, int z) {
      return values == null ? loaded.land(x, z) : (double)(values[z * loaded.width + x] & 255) / 255.0;
   }

   private double sampleReliefLand(RiversMask.Data loaded, int blockX, int blockZ) {
      double imageX = this.mapImageX(blockX, loaded);
      double imageZ = this.mapImageZ(blockZ, loaded);
      if (!(imageX < 0.0) && !(imageZ < 0.0) && !(imageX >= (double)(loaded.width - 1)) && !(imageZ >= (double)(loaded.height - 1))) {
         int x = (int)Math.floor(imageX);
         int z = (int)Math.floor(imageZ);
         double tx = imageX - (double)x;
         double tz = imageZ - (double)z;
         double a = lerp(loaded.reliefLand(x, z), loaded.reliefLand(x + 1, z), tx);
         double b = lerp(loaded.reliefLand(x, z + 1), loaded.reliefLand(x + 1, z + 1), tx);
         return lerp(a, b, tz);
      } else {
         return 0.0;
      }
   }

   public int blocksPerPixel() {
      int active = this.activeBlocksPerPixel;
      return active > 0 ? active : (Integer)EarthShapeServerConfig.BLOCKS_PER_PIXEL.get();
   }

   public int width() {
      return this.data().width;
   }

   public int height() {
      return this.data().height;
   }

   public double mapImageX(int blockX) {
      return this.mapImageX(blockX, this.data());
   }

   public double mapImageZ(int blockZ) {
      return this.mapImageZ(blockZ, this.data());
   }

   /** Changes whenever the world seed selects a new source-map origin. */
   public long mapTransformVersion() {
      return this.mapTransformVersion;
   }

   private double mapImageX(int blockX, RiversMask.Data loaded) {
      this.ensureMapCenter(loaded);
      return (double)blockX * this.blocksToMapPixels + this.selectedCenterX;
   }

   private double mapImageZ(int blockZ, RiversMask.Data loaded) {
      this.ensureMapCenter(loaded);
      return (double)blockZ * this.blocksToMapPixels + this.selectedCenterZ;
   }

   private void ensureMapCenter(RiversMask.Data loaded) {
      if (this.mapCenterReady) return;
      synchronized (this) {
         if (this.mapCenterReady) return;
         int configuredBlocksPerPixel = this.blocksPerPixel();
         this.activeBlocksPerPixel = configuredBlocksPerPixel;
         this.blocksToMapPixels = 1.0 / (double)configuredBlocksPerPixel;
         if (!(Boolean)EarthShapeServerConfig.RANDOM_MAP_CENTER_ENABLED.get()) {
            this.selectedCenterX = (double)loaded.width * 0.5;
            this.selectedCenterZ = (double)loaded.height * 0.5;
            this.mapCenterReady = true;
            EarthShape.LOGGER.info(
               "[EarthShape] map centre fixed at source ({}, {}); map translation=(0, 0) blocks, distance=0 blocks; world spawn remains (0, 0).",
               this.selectedCenterX, this.selectedCenterZ
            );
            return;
         }

         int minX = Math.max(0, Math.min(loaded.width - 1, (Integer)EarthShapeServerConfig.RANDOM_MAP_CENTER_MIN_X.get()));
         int maxX = Math.max(0, Math.min(loaded.width - 1, (Integer)EarthShapeServerConfig.RANDOM_MAP_CENTER_MAX_X.get()));
         int minZ = Math.max(0, Math.min(loaded.height - 1, (Integer)EarthShapeServerConfig.RANDOM_MAP_CENTER_MIN_Z.get()));
         int maxZ = Math.max(0, Math.min(loaded.height - 1, (Integer)EarthShapeServerConfig.RANDOM_MAP_CENTER_MAX_Z.get()));
         if (minX > maxX) { int swap = minX; minX = maxX; maxX = swap; }
         if (minZ > maxZ) { int swap = minZ; minZ = maxZ; maxZ = swap; }

         SplittableRandom random = new SplittableRandom(mixCenterSeed(this.mapCenterSeed));
         LandCentreSelection selection = selectRandomLandCentre(loaded, minX, maxX, minZ, maxZ, random);
         boolean expandedToFullMap = false;
         if (selection == null) {
            // A restrictive custom range may contain only ocean. Preserve the
            // land-only guarantee by widening to the full source map instead of
            // silently placing the world origin at sea.
            selection = selectRandomLandCentre(loaded, 0, loaded.width - 1, 0, loaded.height - 1, random);
            expandedToFullMap = true;
         }
         if (selection == null) {
            throw new IllegalStateException("EarthShape world map contains no land pixel for a random map centre");
         }
         int sourceX = selection.x();
         int sourceZ = selection.z();
         this.selectedCenterX = (double)sourceX + 0.5;
         this.selectedCenterZ = (double)sourceZ + 0.5;
         double sourceDeltaX = this.selectedCenterX - (double)loaded.width * 0.5;
         double sourceDeltaZ = this.selectedCenterZ - (double)loaded.height * 0.5;
         double blocksPerPixel = (double)configuredBlocksPerPixel;
         long mapTranslationX = Math.round(-sourceDeltaX * blocksPerPixel);
         long mapTranslationZ = Math.round(-sourceDeltaZ * blocksPerPixel);
         long translationDistance = Math.round(Math.hypot((double)mapTranslationX, (double)mapTranslationZ));
         this.mapCenterReady = true;
         EarthShape.LOGGER.info(
            "[EarthShape] random map centre selected on land at source pixel ({}, {}) from {} eligible land pixels in X={}..{}, Z={}..{}{}; source offset=({}, {}) pixels; map translation=({}, {}) blocks, distance={} blocks; world spawn remains (0, 0).",
            sourceX, sourceZ, selection.candidates(),
            expandedToFullMap ? 0 : minX, expandedToFullMap ? loaded.width - 1 : maxX,
            expandedToFullMap ? 0 : minZ, expandedToFullMap ? loaded.height - 1 : maxZ,
            expandedToFullMap ? " (configured range had no land; expanded to full map)" : "",
            sourceDeltaX, sourceDeltaZ, mapTranslationX, mapTranslationZ, translationDistance
         );
      }
   }

   /** Uniform reservoir sampling over set land bits without allocating a list. */
   private static LandCentreSelection selectRandomLandCentre(
      RiversMask.Data loaded, int minX, int maxX, int minZ, int maxZ, SplittableRandom random
   ) {
      long candidates = 0L;
      int selectedX = -1;
      int selectedZ = -1;
      for (int z = minZ; z <= maxZ; z++) {
         int rowEnd = z * loaded.width + maxX;
         for (int index = loaded.land.nextSetBit(z * loaded.width + minX);
              index >= 0 && index <= rowEnd;
              index = loaded.land.nextSetBit(index + 1)) {
            candidates++;
            if (random.nextLong(candidates) == 0L) {
               selectedX = index % loaded.width;
               selectedZ = z;
            }
         }
      }
      return candidates == 0L ? null : new LandCentreSelection(selectedX, selectedZ, candidates);
   }

   private record LandCentreSelection(int x, int z, long candidates) {}

   private static long mixCenterSeed(long value) {
      value ^= 0x6A09E667F3BCC909L;
      value ^= value >>> 33;
      value *= 0xff51afd7ed558ccdL;
      value ^= value >>> 33;
      value *= 0xc4ceb9fe1a85ec53L;
      return value ^ value >>> 33;
   }

   public double legacyImageX(int blockX, int legacyWidth) {
      RiversMask.Data loaded = this.data();
      return this.mapImageX(blockX, loaded) - (double)(loaded.width - legacyWidth) * 0.5;
   }

   public double legacyImageZ(int blockZ, int legacyHeight) {
      RiversMask.Data loaded = this.data();
      return this.mapImageZ(blockZ, loaded) - (double)(loaded.height - legacyHeight) * 0.5;
   }

   public boolean isInsideLegacyLayer(int blockX, int blockZ, int legacyWidth, int legacyHeight) {
      double x = this.legacyImageX(blockX, legacyWidth);
      double z = this.legacyImageZ(blockZ, legacyHeight);
      return x >= 0.0 && z >= 0.0 && x < (double)legacyWidth && z < (double)legacyHeight;
   }

   public boolean isRiverCentreline(int blockX, int blockZ) {
      int widthBlocks = this.effectiveRiverWidthBlocks(blockX, blockZ);
      if (widthBlocks == 0) {
         return false;
      } else {
         double distanceBlocks = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
         // A source stroke supplies only the path centre. Its physical water core
         // is exactly the configured width, independent of map scale or sampling.
         return distanceBlocks <= (double)widthBlocks * 0.5;
      }
   }

   public boolean isInlandRiver(int blockX, int blockZ) {
      return this.hasInlandRiverInfluence(blockX, blockZ) && this.isRiverCentreline(blockX, blockZ);
   }

   /**
    * River footprint used only by quart-resolution biome selection. The biome
    * lattice is sampled every four blocks, so an exact water-radius test can
    * leave the outer water blocks assigned to the neighbouring land biome.
    * One lattice cell of padding keeps the whole channel in RIVER/FROZEN_RIVER
    * without widening the physical channel or its aquifer water columns.
   */
   public boolean isInlandRiverBiome(int blockX, int blockZ) {
      return this.inlandRiverBiomeState(blockX, blockZ) != 0;
   }

   /** Frozen state shared by the complete connected river network. */
   public boolean isFrozenInlandRiverBiome(int blockX, int blockZ) {
      return this.inlandRiverBiomeState(blockX, blockZ) == 2;
   }

   /**
    * Computes ordinary/frozen river ownership together. Biome lookup asks both
    * questions consecutively, so caching one combined state avoids repeating the
    * mouth test, exact coastline sample and two 9x9 source searches.
    */
   private int inlandRiverBiomeState(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.RiverBiomeCache cache = this.riverBiomeCache.get();
      if (cache.data == loaded && cache.blockX == blockX && cache.blockZ == blockZ) return cache.state;

      // Biome ownership is stricter than physical channel guidance. Painted
      // river ink may continue past the detected mouth into a sea pixel; that
      // extension must remain ocean even though the channel density stays open.
      if (this.riverMouthOpening(blockX, blockZ) > 0.0) return cache.set(loaded, blockX, blockZ, 0);

      int imageX = (int)Math.floor(this.mapImageX(blockX, loaded));
      int imageZ = (int)Math.floor(this.mapImageZ(blockZ, loaded));
      if (!inside(imageX, imageZ, loaded.width, loaded.height)) return cache.set(loaded, blockX, blockZ, 0);
      int index = imageZ * loaded.width + imageX;
      if (!loaded.riverInfluence.get(index)) return cache.set(loaded, blockX, blockZ, 0);

      // Most river columns must remain on authoritative land. A genuine inland
      // centreline can contain blue source pixels that the land mask cannot
      // restore, however; accept those only when the precomputed inland flood
      // reaches them before a mouth. Strait-only lines and post-mouth sea ink do
      // not belong to inlandRivers and therefore remain ocean.
      int nearestRiver = nearestRiverSourceIndex(loaded, blockX, blockZ);
      if (this.sampleExactLand(loaded, blockX, blockZ) < 0.5
         && (nearestRiver < 0 || !loaded.inlandRivers.get(nearestRiver))) {
         return cache.set(loaded, blockX, blockZ, 0);
      }

      // At a fork, thinning a wide painted junction leaves some source cells as
      // water-coloured influence rather than exact centreline or restored land.
      // riverInfluence was already built only from centrelines supported by nearby
      // land, so accept its full physical-width footprint here. Sea extensions
      // remain excluded by that prefilter and the mouth-opening test above.
      int widthBlocks = this.effectiveRiverWidthBlocks(blockX, blockZ);
      if (widthBlocks <= 0) return cache.set(loaded, blockX, blockZ, 0);
      double distanceBlocks = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
      if (distanceBlocks > (double)widthBlocks * 0.5 + 4.0) {
         return cache.set(loaded, blockX, blockZ, 0);
      }
      return cache.set(loaded, blockX, blockZ, nearestRiver >= 0 && loaded.frozenRivers.get(nearestRiver) ? 2 : 1);
   }

   private int nearestRiverSourceIndex(RiversMask.Data loaded, int blockX, int blockZ) {
      double imageX = this.mapImageX(blockX, loaded);
      double imageZ = this.mapImageZ(blockZ, loaded);
      int centreX = (int)Math.floor(imageX);
      int centreZ = (int)Math.floor(imageZ);
      int nearest = -1;
      double best = Double.POSITIVE_INFINITY;
      for (int z = centreZ - RIVER_SEARCH_RADIUS; z <= centreZ + RIVER_SEARCH_RADIUS; z++) {
         for (int x = centreX - RIVER_SEARCH_RADIUS; x <= centreX + RIVER_SEARCH_RADIUS; x++) {
            if (!loaded.river(x, z)) continue;
            double dx = imageX - ((double)x + 0.5);
            double dz = imageZ - ((double)z + 0.5);
            double distance = dx * dx + dz * dz;
            if (distance < best) {
               best = distance;
               nearest = z * loaded.width + x;
            }
         }
      }
      return nearest;
   }

   /**
    * Exact river result cached per horizontal column for the aquifer. Aquifers ask
    * the same x/z repeatedly at many Y levels; without this cache every level
    * performs the centreline's 9x9 source-pixel search again.
    */
   public boolean isInlandRiverColumn(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.RiverColumnCache cache = this.riverColumnCache.get();
      int slot = RiversMask.RiverColumnCache.slot(blockX, blockZ);
      if (cache.data == loaded && cache.x[slot] == blockX && cache.z[slot] == blockZ) {
         return cache.river[slot];
      }
      boolean river = this.isInlandRiver(blockX, blockZ);
      cache.data = loaded;
      cache.x[slot] = blockX;
      cache.z[slot] = blockZ;
      cache.river[slot] = river;
      return river;
   }

   public boolean isRiverMouth(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      int x = (int)Math.floor(this.mapImageX(blockX, loaded));
      int z = (int)Math.floor(this.mapImageZ(blockZ, loaded));
      return x >= 0
         && z >= 0
         && x < loaded.width
         && z < loaded.height
         && loaded.riverMouths.get(z * loaded.width + x)
         && this.isRiverCentreline(blockX, blockZ);
   }

   /**
    * Continuous opening factor for an actual painted river mouth.  Mouth cells
    * are deliberately excluded from inland-river biome selection, but their
    * physical channel must still join the ocean rather than ending in a land
    * continentalness seam.
    */
   public double riverMouthOpening(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.ScalarSampleCache cache = this.riverMouthCache.get();
      int slot = RiversMask.ScalarSampleCache.slot(blockX, blockZ);
      if (cache.data == loaded && cache.x[slot] == blockX && cache.z[slot] == blockZ) return cache.value[slot];
      double value = this.computeRiverMouthOpening(loaded, blockX, blockZ);
      cache.set(loaded, slot, blockX, blockZ, value);
      return value;
   }

   private double computeRiverMouthOpening(RiversMask.Data loaded, int blockX, int blockZ) {
      double imageX = this.mapImageX(blockX, loaded);
      double imageZ = this.mapImageZ(blockZ, loaded);
      int centreX = (int)Math.floor(imageX);
      int centreZ = (int)Math.floor(imageZ);
      if (centreX < 1 || centreZ < 1 || centreX >= loaded.width - 1 || centreZ >= loaded.height - 1) return 0.0;
      // The precomputed influence covers every source cell reachable by the
      // four-pixel mouth search. Almost every world column is outside it, so do
      // not scan 81 BitSet entries for ordinary land and open ocean.
      if (!loaded.riverInfluence.get(centreZ * loaded.width + centreX)) return 0.0;

      double strongest = 0.0;
      for (int z = Math.max(0, centreZ - 4); z <= Math.min(loaded.height - 1, centreZ + 4); z++) {
         for (int x = Math.max(0, centreX - 4); x <= Math.min(loaded.width - 1, centreX + 4); x++) {
            int index = z * loaded.width + x;
            if (!loaded.riverMouths.get(index)) continue;
            int width = loaded.riverWidth(x, z);
            if (width <= 0) continue;
            double dx = (imageX - ((double)x + 0.5)) * (double)this.blocksPerPixel();
            double dz = (imageZ - ((double)z + 0.5)) * (double)this.blocksPerPixel();
            double radius = (double)width * 0.5
               + Math.max(2.0, (double)EarthShapeServerConfig.RIVER_CHANNEL_EDGE_FADE_BLOCKS.get());
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= radius) continue;
            double t = 1.0 - distance / radius;
            t = t * t * (3.0 - 2.0 * t);
            strongest = Math.max(strongest, t);
         }
      }
      return strongest;
   }

   public boolean isInlandRiverBank(int blockX, int blockZ) {
      if (!this.hasInlandRiverInfluence(blockX, blockZ)) {
         return false;
      } else {
         int width = this.effectiveRiverWidthBlocks(blockX, blockZ);
         if (width == 0) {
            return false;
         } else {
            double distanceBlocks = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
            return distanceBlocks <= Math.max(12.0, (double)width * 0.5 + 12.0);
         }
      }
   }

   public boolean isNearInlandRiver(int blockX, int blockZ, int exclusionBlocks) {
      if (!this.hasInlandRiverInfluence(blockX, blockZ)) {
         return false;
      } else {
         int width = this.effectiveRiverWidthBlocks(blockX, blockZ);
         if (width == 0) {
            return false;
         } else {
            double distanceBlocks = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
            return distanceBlocks <= Math.max((double)exclusionBlocks, (double)width * 0.5);
         }
      }
   }

   public boolean hasInlandRiverInfluence(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      int x = (int)Math.floor(this.mapImageX(blockX, loaded));
      int z = (int)Math.floor(this.mapImageZ(blockZ, loaded));
      return x >= 0
         && z >= 0
         && x < loaded.width
         && z < loaded.height
         && loaded.riverInfluence.get(z * loaded.width + x)
         // Source river ink is not white, so a centreline pixel can remain
         // absent from the land mask in narrow corridors. Do not drop that
         // valid centreline; only the explicitly detected mouth belongs to sea.
         && (loaded.land.get(z * loaded.width + x)
            || loaded.rivers.get(z * loaded.width + x) && !loaded.riverMouths.get(z * loaded.width + x));
   }

   public int riverWidthBlocks(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.RiverWidthCache cache = this.riverWidthCache.get();
      if (cache.data == loaded && cache.blockX == blockX && cache.blockZ == blockZ) {
         return cache.width;
      } else {
         double imageX = this.mapImageX(blockX, loaded);
         double imageZ = this.mapImageZ(blockZ, loaded);
         int width = 0;
         if (!(imageX < 1.0) && !(imageZ < 1.0) && !(imageX >= (double)loaded.width - 1.0) && !(imageZ >= (double)loaded.height - 1.0)) {
            int centreX = (int)Math.floor(imageX);
            int centreZ = (int)Math.floor(imageZ);
            if (!loaded.riverInfluence.get(centreZ * loaded.width + centreX)) {
               cache.data = loaded;
               cache.blockX = blockX;
               cache.blockZ = blockZ;
               cache.width = 0;
               return 0;
            }

            double best = Double.POSITIVE_INFINITY;

            for (int z = centreZ - RIVER_SEARCH_RADIUS; z <= centreZ + RIVER_SEARCH_RADIUS; z++) {
               for (int x = centreX - RIVER_SEARCH_RADIUS; x <= centreX + RIVER_SEARCH_RADIUS; x++) {
                  int candidate = loaded.riverWidth(x, z);
                  if (candidate != 0) {
                     double distance = distanceSquared(imageX, imageZ, (double)x + 0.5, (double)z + 0.5, (double)x + 0.5, (double)z + 0.5);
                     if (distance < best) {
                        best = distance;
                        width = candidate;
                     }
                  }
               }
            }
         }

         cache.data = loaded;
         cache.blockX = blockX;
         cache.blockZ = blockZ;
         cache.width = width;
         return width;
      }
   }

   public int effectiveRiverWidthBlocks(int blockX, int blockZ) {
      int width = this.riverWidthBlocks(blockX, blockZ);
      // Do not inflate narrow configured channels to a global minimum. Every river
      // colour is interpreted as a centreline plus its exact configured block width.
      return width;
   }

   public double riverCentrelineDistance(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.RiverDistanceCache cache = this.riverDistanceCache.get();
      if (cache.data == loaded && cache.blockX == blockX && cache.blockZ == blockZ) return cache.distance;
      double imageX = this.mapImageX(blockX, loaded);
      double imageZ = this.mapImageZ(blockZ, loaded);
      if (!(imageX < 1.0) && !(imageZ < 1.0) && !(imageX >= (double)loaded.width - 1.0) && !(imageZ >= (double)loaded.height - 1.0)) {
         int centreX = (int)Math.floor(imageX);
         int centreZ = (int)Math.floor(imageZ);
         if (!loaded.riverInfluence.get(centreZ * loaded.width + centreX)) {
            cache.set(loaded, blockX, blockZ, Double.POSITIVE_INFINITY);
            return Double.POSITIVE_INFINITY;
         } else {
            double best = Double.POSITIVE_INFINITY;

            for (int z = centreZ - RIVER_SEARCH_RADIUS; z <= centreZ + RIVER_SEARCH_RADIUS; z++) {
               for (int x = centreX - RIVER_SEARCH_RADIUS; x <= centreX + RIVER_SEARCH_RADIUS; x++) {
                  if (loaded.river(x, z)) {
                     int cornerMask = loaded.riverCornerMask(x, z);
                     if (cornerMask == 0) {
                        double pathX = riverPathX(loaded, x, z);
                        double pathZ = riverPathZ(loaded, x, z);
                        best = Math.min(best, Math.sqrt(distanceSquared(imageX, imageZ, pathX, pathZ, pathX, pathZ)));
                     } else {
                        best = Math.min(best, Math.sqrt(roundedCornerDistanceSquared(imageX, imageZ, x, z, cornerMask)));
                     }

                     for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                           if ((dx > 0 || dx == 0 && dz > 0) && loaded.river(x + dx, z + dz)) {
                              int neighbourCornerMask = loaded.riverCornerMask(x + dx, z + dz);
                              double startX = riverPathX(loaded, x, z) + ((cornerMask & neighbourBit(dx, dz)) != 0 ? (double)dx * 0.32 : 0.0);
                              double startZ = riverPathZ(loaded, x, z) + ((cornerMask & neighbourBit(dx, dz)) != 0 ? (double)dz * 0.32 : 0.0);
                              double endX = riverPathX(loaded, x + dx, z + dz) + ((neighbourCornerMask & neighbourBit(-dx, -dz)) != 0 ? (double)(-dx) * 0.32 : 0.0);
                              double endZ = riverPathZ(loaded, x + dx, z + dz) + ((neighbourCornerMask & neighbourBit(-dx, -dz)) != 0 ? (double)(-dz) * 0.32 : 0.0);
                              best = Math.min(best, Math.sqrt(distanceSquared(imageX, imageZ, startX, startZ, endX, endZ)));
                           }
                        }
                     }
                  }
               }
            }

            cache.set(loaded, blockX, blockZ, best);
            return best;
         }
      } else {
         cache.set(loaded, blockX, blockZ, Double.POSITIVE_INFINITY);
         return Double.POSITIVE_INFINITY;
      }
   }

   /**
    * A one-pixel-wide vertical/horizontal source stroke otherwise becomes a visually
    * endless ruler-straight river at world scale.  Offset only simple axial runs by a
    * sub-pixel, smoothly varying amount; junctions and already diagonal/cornered data
    * are left untouched so the layer's topology remains authoritative.
    */
   private static double riverPathX(RiversMask.Data data, int x, int z) {
      boolean northSouth = data.river(x, z - 1) || data.river(x, z + 1);
      boolean eastWest = data.river(x - 1, z) || data.river(x + 1, z);
      return (double)x + 0.5 + (northSouth && !eastWest ? meanderAmplitude() * axialNoise(z, x, 0x51A7L) : 0.0);
   }

   private static double riverPathZ(RiversMask.Data data, int x, int z) {
      boolean northSouth = data.river(x, z - 1) || data.river(x, z + 1);
      boolean eastWest = data.river(x - 1, z) || data.river(x + 1, z);
      return (double)z + 0.5 + (eastWest && !northSouth ? meanderAmplitude() * axialNoise(x, z, 0xA715L) : 0.0);
   }

   private static double axialNoise(int coordinate, int line, long salt) {
      int cellSize = Math.max(1, Math.min(8, 96 / Math.max(1, RiversMask.INSTANCE.blocksPerPixel())));
      int cell = Math.floorDiv(coordinate, cellSize);
      double t = (double)Math.floorMod(coordinate, cellSize) / (double)cellSize;
      t = t * t * (3.0 - 2.0 * t);
      return lerp(axialValue(cell, line, salt), axialValue(cell + 1, line, salt), t);
   }

   /**
    * Source-pixel offset for a simple axial line. Both the wavelength above and
    * this amplitude are expressed relative to map scale, so an enlarged world map
    * retains gentle bends instead of turning each source segment into a ruler line.
    */
   private static double meanderAmplitude() {
      return Math.min(0.38, 0.18 + (double)RiversMask.INSTANCE.blocksPerPixel() / 1024.0);
   }

   private static double axialValue(int cell, int line, long salt) {
      long value = salt ^ (long)cell * 341873128712L ^ (long)line * 132897987541L;
      value ^= value >>> 33;
      value *= 0xff51afd7ed558ccdL;
      value ^= value >>> 33;
      return (double)(value >>> 11 & 2097151L) / 1048575.5 - 1.0;
   }

   private static double distanceSquared(double px, double pz, double ax, double az, double bx, double bz) {
      double dx = bx - ax;
      double dz = bz - az;
      double length = dx * dx + dz * dz;
      if (length == 0.0) {
         double ox = px - ax;
         double oz = pz - az;
         return ox * ox + oz * oz;
      } else {
         double t = Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (pz - az) * dz) / length));
         double ox = px - (ax + t * dx);
         double oz = pz - (az + t * dz);
         return ox * ox + oz * oz;
      }
   }

   private static double roundedCornerDistanceSquared(double px, double pz, int x, int z, int cornerMask) {
      int first = Integer.numberOfTrailingZeros(cornerMask & 0xFF);
      int second = Integer.numberOfTrailingZeros(cornerMask & 0xFF & ~(1 << first));
      int firstX = neighbourX(first);
      int firstZ = neighbourZ(first);
      int secondX = neighbourX(second);
      int secondZ = neighbourZ(second);
      double startX = (double)x + 0.5 + (double)firstX * 0.32;
      double startZ = (double)z + 0.5 + (double)firstZ * 0.32;
      double endX = (double)x + 0.5 + (double)secondX * 0.32;
      double endZ = (double)z + 0.5 + (double)secondZ * 0.32;
      double controlX = (double)x + 0.5;
      double controlZ = (double)z + 0.5;
      double best = Double.POSITIVE_INFINITY;
      double previousX = startX;
      double previousZ = startZ;

      for (int step = 1; step <= 9; step++) {
         double t = (double)step / 9.0;
         double inverse = 1.0 - t;
         double nextX = inverse * inverse * startX + 2.0 * inverse * t * controlX + t * t * endX;
         double nextZ = inverse * inverse * startZ + 2.0 * inverse * t * controlZ + t * t * endZ;
         best = Math.min(best, distanceSquared(px, pz, previousX, previousZ, nextX, nextZ));
         previousX = nextX;
         previousZ = nextZ;
      }

      return best;
   }

   private static int neighbourBit(int dx, int dz) {
      int index = (dz + 1) * 3 + dx + 1;
      return 1 << (index > 4 ? index - 1 : index);
   }

   private static int neighbourX(int bit) {
      int index = bit >= 4 ? bit + 1 : bit;
      return index % 3 - 1;
   }

   private static int neighbourZ(int bit) {
      int index = bit >= 4 ? bit + 1 : bit;
      return index / 3 - 1;
   }

   private RiversMask.Data data() {
      RiversMask.Data current = this.data;
      if (current != null) {
         return current;
      } else {
         synchronized (this) {
            if (this.data == null) {
               this.data = load();
            }

            return this.data;
         }
      }
   }

   private static RiversMask.Data load() {
      long started = System.nanoTime();

      try {
         RiversMask.Data var21x;
         try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/worldmap_river.png")) {
            if (input == null) {
               throw new IOException("missing /earthshape/hoi4/worldmap_river.png");
            }

            BufferedImage image = ImageIO.read(input);
            if (image == null) {
               throw new IOException("worldmap_river.png is not readable");
            }

            int width = image.getWidth();
            int height = image.getHeight();
            BitSet land = new BitSet(width * height);
            BitSet rivers = new BitSet(width * height);
            byte[] riverWidths = new byte[width * height];
            int[] row = new int[width];

            for (int z = 0; z < height; z++) {
               image.getRGB(0, z, width, 1, row, 0, width);

               for (int x = 0; x < width; x++) {
                  int rgb = row[x];
                  int red = rgb >>> 16 & 0xFF;
                  int green = rgb >>> 8 & 0xFF;
                  int blue = rgb & 0xFF;
                  int riverWidth = riverWidthForColor(red, green, blue);
                  if (riverWidth == 0 && isFullMapLand(red, green, blue)) {
                     land.set(z * width + x);
                  }

                  if (riverWidth > 0) {
                     int index = z * width + x;
                     rivers.set(index);
                     riverWidths[index] = (byte)riverWidth;
                  }
               }
            }

            BitSet sourceRivers = rivers;
            BitSet riverCentrelines = thinRiverCentrelines(width, height, sourceRivers);
            bridgeSmallRiverGaps(width, height, riverCentrelines, riverWidths);
            stabilizeRiverWidths(width, height, riverCentrelines, riverWidths);
            restoreOnlyInlandRiverPixels(width, height, land, sourceRivers);
            RiversMask.OceanProximity openOcean = RiversMask.OceanProximity.create(width, height, land);
            BitSet riverMouths = createRiverMouths(width, height, land, riverCentrelines, sourceRivers, openOcean);
            BitSet inlandRivers = createInlandRiverCentrelines(width, height, land, riverCentrelines, riverMouths);
            BitSet frozenRivers = createFrozenRiverComponents(width, height, riverCentrelines);
            byte[] riverCorners = createRiverCornerMasks(width, height, riverCentrelines);
            BitSet riverInfluence = createRiverInfluence(width, height, land, riverCentrelines, riverWidths);
            int coastRadiusPixels = Math.max(2, Math.min(12, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get() / Math.max(1, (Integer)EarthShapeServerConfig.BLOCKS_PER_PIXEL.get() * 6)));
            byte[] coastalLandness = createCoastalLandness(width, height, land, coastRadiusPixels);
            byte[] oceanDistance = createOceanDistance(width, height, land);
            BitSet pregeneration = createPregenerationMask(width, height, land, openOcean);
            SouthernSnowRegion southernSnow = SouthernSnowRegion.create(width, height, land);
            ContinentRegions continentRegions = ContinentRegions.create(width, height, land);
            EarthShape.LOGGER
               .info(
                  "[EarthShape] worldmap_river.png land/ocean and river mask loaded: {}x{} in {} ms.",
                  new Object[]{width, height, (System.nanoTime() - started) / 1000000L}
               );
            var21x = new RiversMask.Data(
               width, height, land, riverCentrelines, inlandRivers, frozenRivers, riverWidths, riverCorners, riverMouths, riverInfluence, coastalLandness, oceanDistance, pregeneration, southernSnow, continentRegions
            );
         }

         return var21x;
      } catch (IOException var21) {
         throw new IllegalStateException("EarthShape could not load worldmap_river.png", var21);
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static byte[] createCoastalLandness(int width, int height, BitSet land, int radius) {
      byte[] values = new byte[width * height];
      for (int index = land.nextSetBit(0); index >= 0; index = land.nextSetBit(index + 1)) values[index] = (byte)255;
      for (int pass = 0; pass < 3; pass++) values = boxBlur(values, width, height, radius);
      return values;
   }

   private static byte[] createOceanDistance(int width, int height, BitSet land) {
      byte[] distance = new byte[width * height];
      Arrays.fill(distance, (byte)255);
      for (int index = land.nextSetBit(0); index >= 0; index = land.nextSetBit(index + 1)) distance[index] = 0;

      // 3/4 chamfer metric: inexpensive at map-load time and much rounder than
      // Manhattan distance.  Saturation still covers 85 source pixels, well
      // beyond the configured coastal transition at normal map scales.
      for (int z = 0; z < height; z++) {
         for (int x = 0; x < width; x++) {
            int index = z * width + x;
            int value = distance[index] & 255;
            if (x > 0) value = Math.min(value, (distance[index - 1] & 255) + 3);
            if (z > 0) value = Math.min(value, (distance[index - width] & 255) + 3);
            if (x > 0 && z > 0) value = Math.min(value, (distance[index - width - 1] & 255) + 4);
            if (x + 1 < width && z > 0) value = Math.min(value, (distance[index - width + 1] & 255) + 4);
            distance[index] = (byte)Math.min(255, value);
         }
      }
      for (int z = height - 1; z >= 0; z--) {
         for (int x = width - 1; x >= 0; x--) {
            int index = z * width + x;
            int value = distance[index] & 255;
            if (x + 1 < width) value = Math.min(value, (distance[index + 1] & 255) + 3);
            if (z + 1 < height) value = Math.min(value, (distance[index + width] & 255) + 3);
            if (x + 1 < width && z + 1 < height) value = Math.min(value, (distance[index + width + 1] & 255) + 4);
            if (x > 0 && z + 1 < height) value = Math.min(value, (distance[index + width - 1] & 255) + 4);
            distance[index] = (byte)Math.min(255, value);
         }
      }
      return distance;
   }

   private static BitSet createPregenerationMask(
      int width, int height, BitSet land, RiversMask.OceanProximity ocean
   ) {
      BitSet selection = (BitSet)land.clone();

      // Water which has no connection to the map-edge ocean is an independent
      // inland sea. It must be generated with its surrounding continent.
      for (int index = 0; index < width * height; index++) {
         if (!land.get(index) && !ocean.isNearOpenOcean(index % width, index / width)) {
            selection.set(index);
         }
      }

      addNarrowStraits(selection, width, height, land, true);
      addNarrowStraits(selection, width, height, land, false);
      return selection;
   }

   /** Adds water cells bounded by land on both sides with fewer than 10 pixels between the shores. */
   private static void addNarrowStraits(BitSet selection, int width, int height, BitSet land, boolean horizontal) {
      int outer = horizontal ? height : width;
      int inner = horizontal ? width : height;
      byte[] nearestLand = new byte[inner];

      for (int line = 0; line < outer; line++) {
         int distance = 11;
         for (int point = 0; point < inner; point++) {
            int index = horizontal ? line * width + point : point * width + line;
            distance = land.get(index) ? 0 : Math.min(11, distance + 1);
            nearestLand[point] = (byte)distance;
         }

         distance = 11;
         for (int point = inner - 1; point >= 0; point--) {
            int index = horizontal ? line * width + point : point * width + line;
            distance = land.get(index) ? 0 : Math.min(11, distance + 1);
            if (!land.get(index) && (nearestLand[point] & 0xFF) + distance <= 10) {
               selection.set(index);
            }
         }
      }
   }

   private static byte[] boxBlur(byte[] source, int width, int height, int radius) {
      byte[] horizontal = new byte[source.length];
      for (int z = 0; z < height; z++) {
         int row = z * width;
         int sum = 0;
         for (int x = 0; x <= Math.min(width - 1, radius); x++) sum += source[row + x] & 255;
         for (int x = 0; x < width; x++) {
            if (x > 0) {
               int remove = x - radius - 1;
               int add = x + radius;
               if (remove >= 0) sum -= source[row + remove] & 255;
               if (add < width) sum += source[row + add] & 255;
            }
            int count = Math.min(width - 1, x + radius) - Math.max(0, x - radius) + 1;
            horizontal[row + x] = (byte)(sum / count);
         }
      }
      byte[] result = new byte[source.length];
      for (int x = 0; x < width; x++) {
         int sum = 0;
         for (int z = 0; z <= Math.min(height - 1, radius); z++) sum += horizontal[z * width + x] & 255;
         for (int z = 0; z < height; z++) {
            if (z > 0) {
               int remove = z - radius - 1;
               int add = z + radius;
               if (remove >= 0) sum -= horizontal[remove * width + x] & 255;
               if (add < height) sum += horizontal[add * width + x] & 255;
            }
            int count = Math.min(height - 1, z + radius) - Math.max(0, z - radius) + 1;
            result[z * width + x] = (byte)(sum / count);
         }
      }
      return result;
   }

   private static void removeCoastalRiverInk(int width, BitSet rivers, byte[] riverWidths, RiversMask.OceanProximity ocean) {
      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         int x = index % width;
         int z = index / width;
         if (ocean.isNearOpenOcean(x, z)) {
            rivers.clear(index);
            riverWidths[index] = 0;
         }
      }
   }

   private static void restoreOnlyInlandRiverPixels(int width, int height, BitSet land, BitSet rivers) {
      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         int x = index % width;
         int z = index / width;
         int support = 0;

         for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
               int sx = x + dx;
               int sz = z + dz;
               if (sx >= 0 && sz >= 0 && sx < width && sz < height && land.get(sz * width + sx)) {
                  support++;
               }
            }
         }

         // A river pixel at the coast can have plenty of land in its 5x5
         // neighbourhood. Restoring it anyway creates a one-pixel land plug that
         // becomes a tiny island after scaling. Keep every river pixel touching
         // non-river water as water so the ocean reaches the river channel.
         if (support >= 16 && !touchesNonRiverWater(x, z, width, height, land, rivers, 2)) {
            land.set(index);
         }
      }
   }

   /**
    * Assign one frozen state to every connected source-river network. The mean
    * latitude is used once for the entire component, so a river cannot alternate
    * between RIVER and FROZEN_RIVER as local climate samples vary downstream.
    */
   private static BitSet createFrozenRiverComponents(int width, int height, BitSet rivers) {
      BitSet visited = new BitSet(width * height);
      BitSet frozen = new BitSet(width * height);
      IntQueue component = new IntQueue();
      double threshold = (Double)EarthShapeServerConfig.SNOW_TEMPERATURE_THRESHOLD.get();
      for (int start = rivers.nextSetBit(0); start >= 0; start = rivers.nextSetBit(start + 1)) {
         if (visited.get(start)) continue;
         component.clear();
         component.add(start);
         visited.set(start);
         long sumZ = 0L;
         for (int cursor = 0; cursor < component.size(); cursor++) {
            int index = component.get(cursor);
            int x = index % width;
            int z = index / width;
            sumZ += z;
            for (int dz = -1; dz <= 1; dz++) {
               for (int dx = -1; dx <= 1; dx++) {
                  if (dx == 0 && dz == 0) continue;
                  int nx = x + dx;
                  int nz = z + dz;
                  if (!inside(nx, nz, width, height)) continue;
                  int neighbour = nz * width + nx;
                  if (rivers.get(neighbour) && !visited.get(neighbour)) {
                     visited.set(neighbour);
                     component.add(neighbour);
                  }
               }
            }
         }
         double meanZ = (double)sumZ / (double)component.size();
         double latitude = Math.abs(meanZ / Math.max(1.0, (double)height - 1.0) * 2.0 - 1.0);
         double temperature = 0.55 - 1.35 * latitude * latitude;
         if (temperature <= threshold) {
            for (int cursor = 0; cursor < component.size(); cursor++) frozen.set(component.get(cursor));
         }
      }
      return frozen;
   }

   /**
    * Flood river centrelines outward from pixels that were positively restored
    * as inland land. Detected mouth pixels are included but act as flood stops,
    * so painted river ink beyond a coast cannot become river biome in the sea.
    * A blue line lying only in a narrow strait has no inland seed and is excluded.
    */
   private static BitSet createInlandRiverCentrelines(
      int width, int height, BitSet land, BitSet rivers, BitSet riverMouths
   ) {
      BitSet inland = new BitSet(width * height);
      IntQueue queue = new IntQueue();
      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         if (land.get(index)) {
            inland.set(index);
            queue.add(index);
         }
      }
      for (int cursor = 0; cursor < queue.size(); cursor++) {
         int index = queue.get(cursor);
         if (riverMouths.get(index)) continue;
         int x = index % width;
         int z = index / width;
         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               if (dx == 0 && dz == 0) continue;
               int nx = x + dx;
               int nz = z + dz;
               if (!inside(nx, nz, width, height)) continue;
               int neighbour = nz * width + nx;
               if (rivers.get(neighbour) && !inland.get(neighbour)) {
                  inland.set(neighbour);
                  queue.add(neighbour);
               }
            }
         }
      }
      return inland;
   }

   private static boolean touchesNonRiverWater(
      int centreX, int centreZ, int width, int height, BitSet land, BitSet rivers, int radius
   ) {
      for (int dz = -radius; dz <= radius; dz++) {
         for (int dx = -radius; dx <= radius; dx++) {
            int x = centreX + dx;
            int z = centreZ + dz;
            if (inside(x, z, width, height)) {
               int index = z * width + x;
               if (!land.get(index) && !rivers.get(index)) return true;
            }
         }
      }
      return false;
   }

   private static BitSet createRiverInfluence(int width, int height, BitSet land, BitSet rivers, byte[] riverWidths) {
            BitSet influence = new BitSet(width * height);

      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         // Some valid painted river pixels remain water-coloured at the immediate
         // shoreline or in a thin land corridor. They still need a channel; the
         // separate open-ocean removal keeps sea-only ink out of this set.
         if (land.get(index) || touchesLand(index % width, index / width, width, height, land, 2)) {
            int centreX = index % width;
            int centreZ = index / width;

            // Keep the runtime distance lookup active only where this source
            // stroke can physically reach after its configured width is applied.
            // A fixed +/-4 source-pixel area becomes hundreds of blocks wide when
            // map scale rises and was the cause of river-section CPS drops.
            int radius = riverInfluenceRadiusPixels(riverWidths[index] & 255);
            for (int dz = -radius; dz <= radius; dz++) {
               for (int dx = -radius; dx <= radius; dx++) {
                  int x = centreX + dx;
                  int z = centreZ + dz;
                  if (inside(x, z, width, height)) {
                     influence.set(z * width + x);
                  }
               }
            }
         }
      }

      return influence;
   }

   private static boolean touchesLand(int centreX, int centreZ, int width, int height, BitSet land, int radius) {
      for (int dz = -radius; dz <= radius; dz++) {
         for (int dx = -radius; dx <= radius; dx++) {
            int x = centreX + dx;
            int z = centreZ + dz;
            if (inside(x, z, width, height) && land.get(z * width + x)) return true;
         }
      }
      return false;
   }

   private static int riverInfluenceRadiusPixels(int widthBlocks) {
      int blocksPerPixel = Math.max(1, RiversMask.INSTANCE.blocksPerPixel());
      // Terrain erosion also needs the centreline through the configured bank
      // band. Keep that lookup bounded by RIVER_SEARCH_RADIUS so widening the
      // flat approach does not reintroduce the former large per-column scans.
      double reachBlocks = (double)widthBlocks * 0.5
         + Math.max(2.0, (double)EarthShapeServerConfig.RIVER_BANK_FADE_BLOCKS.get());
      return Math.max(1, Math.min(RIVER_SEARCH_RADIUS, (int)Math.ceil(reachBlocks / (double)blocksPerPixel) + 1));
   }

   private static BitSet createRiverMouths(
      int width, int height, BitSet land, BitSet rivers, BitSet sourceRivers,
      RiversMask.OceanProximity openOcean
   ) {
      BitSet mouths = new BitSet(width * height);

      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         if (land.get(index)) {
            int x = index % width;
            int z = index / width;

            for (int dz = -2; dz <= 2; dz++) {
               for (int dx = -2; dx <= 2; dx++) {
                  int sampleX = x + dx;
                  int sampleZ = z + dz;
                  if (inside(sampleX, sampleZ, width, height)) {
                     int sample = sampleZ * width + sampleX;
                     // A wide junction contains source-river ink that disappears
                     // from the one-cell centreline after thinning. It is still
                     // river, not open sea, and must never create a false mouth.
                     // An enclosed lake or inland sea is not a river mouth. The
                     // old test treated every non-river water pixel as ocean,
                     // stopped the inland-river flood there, and left cold river
                     // sections to resolve as DEEP_FROZEN_OCEAN. Only water
                     // connected to the map-edge ocean may terminate a river.
                     if (!land.get(sample)
                        && !sourceRivers.get(sample)
                        && openOcean.isNearOpenOcean(sampleX, sampleZ)) {
                        mouths.set(index);
                        int var13 = 3;
                        break;
                     }
                  }
               }
            }
         }
      }

      return mouths;
   }

   private static byte[] createRiverCornerMasks(int width, int height, BitSet rivers) {
      byte[] corners = new byte[width * height];

      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         int x = index % width;
         int z = index / width;
         int mask = 0;
         int count = 0;

         // Raster lines at a right-angle corner also touch diagonally.  Counting all
         // eight neighbours therefore sees three pixels and used to leave the corner
         // as a hard L.  Prefer the four cardinal directions for a real turn, then
         // keep the old eight-neighbour fallback for diagonal-only source lines.
         boolean west = inside(x - 1, z, width, height) && rivers.get(z * width + x - 1);
         boolean east = inside(x + 1, z, width, height) && rivers.get(z * width + x + 1);
         boolean north = inside(x, z - 1, width, height) && rivers.get((z - 1) * width + x);
         boolean south = inside(x, z + 1, width, height) && rivers.get((z + 1) * width + x);
         int cardinalCount = (west ? 1 : 0) + (east ? 1 : 0) + (north ? 1 : 0) + (south ? 1 : 0);
         if (cardinalCount == 2 && !(west && east) && !(north && south)) {
            int cardinalMask = 0;
            if (west) cardinalMask |= neighbourBit(-1, 0);
            if (east) cardinalMask |= neighbourBit(1, 0);
            if (north) cardinalMask |= neighbourBit(0, -1);
            if (south) cardinalMask |= neighbourBit(0, 1);
            corners[index] = (byte)cardinalMask;
            continue;
         }

         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               if ((dx != 0 || dz != 0) && inside(x + dx, z + dz, width, height) && rivers.get((z + dz) * width + x + dx)) {
                  mask |= neighbourBit(dx, dz);
                  count++;
               }
            }
         }

         if (count == 2) {
            int first = Integer.numberOfTrailingZeros(mask);
            int second = Integer.numberOfTrailingZeros(mask & ~(1 << first));
            int dot = neighbourX(first) * neighbourX(second) + neighbourZ(first) * neighbourZ(second);
            if (dot > -1) {
               corners[index] = (byte)mask;
            }
         }
      }

      return corners;
   }

   private static byte[] createCoastDistance(int width, int height, BitSet land, BitSet rivers) {
      byte[] distance = new byte[width * height];
      Arrays.fill(distance, (byte)127);

      for (int z = 0; z < height; z++) {
         for (int x = 0; x < width; x++) {
            int index = z * width + x;
            if (!land.get(index) && !rivers.get(index)) {
               distance[index] = 0;
            } else {
               int best = distance[index] & 255;
               if (x > 0) {
                  best = Math.min(best, (distance[index - 1] & 255) + 1);
               }

               if (z > 0) {
                  best = Math.min(best, (distance[index - width] & 255) + 1);
                  if (x > 0) {
                     best = Math.min(best, (distance[index - width - 1] & 255) + 1);
                  }

                  if (x + 1 < width) {
                     best = Math.min(best, (distance[index - width + 1] & 255) + 1);
                  }
               }

               distance[index] = (byte)Math.min(127, best);
            }
         }
      }

      for (int z = height - 1; z >= 0; z--) {
         for (int xx = width - 1; xx >= 0; xx--) {
            int index = z * width + xx;
            int bestx = distance[index] & 255;
            if (xx + 1 < width) {
               bestx = Math.min(bestx, (distance[index + 1] & 255) + 1);
            }

            if (z + 1 < height) {
               bestx = Math.min(bestx, (distance[index + width] & 255) + 1);
               if (xx > 0) {
                  bestx = Math.min(bestx, (distance[index + width - 1] & 255) + 1);
               }

               if (xx + 1 < width) {
                  bestx = Math.min(bestx, (distance[index + width + 1] & 255) + 1);
               }
            }

            distance[index] = (byte)Math.min(127, bestx);
         }
      }

      return distance;
   }

   private static byte[] createWaterCoastDistance(int width, int height, BitSet land) {
      byte[] distance = new byte[width * height];
      Arrays.fill(distance, (byte)127);

      for (int z = 0; z < height; z++) {
         for (int x = 0; x < width; x++) {
            int index = z * width + x;
            if (!land.get(index)) {
               boolean touchesLand = false;
               for (int dz = -1; dz <= 1 && !touchesLand; dz++) {
                  for (int dx = -1; dx <= 1; dx++) {
                     int nx = x + dx;
                     int nz = z + dz;
                     if ((dx != 0 || dz != 0) && inside(nx, nz, width, height) && land.get(nz * width + nx)) {
                        touchesLand = true;
                        break;
                     }
                  }
               }
               if (touchesLand) distance[index] = 0;
            }
         }
      }

      for (int z = 0; z < height; z++) {
         for (int x = 0; x < width; x++) {
            int index = z * width + x;
            if (!land.get(index)) {
               int best = distance[index] & 255;
               if (x > 0 && !land.get(index - 1)) best = Math.min(best, (distance[index - 1] & 255) + 1);
               if (z > 0) {
                  if (!land.get(index - width)) best = Math.min(best, (distance[index - width] & 255) + 1);
                  if (x > 0 && !land.get(index - width - 1)) best = Math.min(best, (distance[index - width - 1] & 255) + 1);
                  if (x + 1 < width && !land.get(index - width + 1)) best = Math.min(best, (distance[index - width + 1] & 255) + 1);
               }
               distance[index] = (byte)Math.min(127, best);
            }
         }
      }

      for (int z = height - 1; z >= 0; z--) {
         for (int x = width - 1; x >= 0; x--) {
            int index = z * width + x;
            if (!land.get(index)) {
               int best = distance[index] & 255;
               if (x + 1 < width && !land.get(index + 1)) best = Math.min(best, (distance[index + 1] & 255) + 1);
               if (z + 1 < height) {
                  if (!land.get(index + width)) best = Math.min(best, (distance[index + width] & 255) + 1);
                  if (x > 0 && !land.get(index + width - 1)) best = Math.min(best, (distance[index + width - 1] & 255) + 1);
                  if (x + 1 < width && !land.get(index + width + 1)) best = Math.min(best, (distance[index + width + 1] & 255) + 1);
               }
               distance[index] = (byte)Math.min(127, best);
            }
         }
      }

      return distance;
   }

   private static void bridgeSmallRiverGaps(int width, int height, BitSet rivers, byte[] riverWidths) {
      // Fine one-pixel source lines can lose several pixels during raster export.
      // Join only similarly directed endpoints, but allow a short four-pixel bridge so
      // those gaps do not become disconnected river segments in-game.
      int configuredGap = (Integer)EarthShapeServerConfig.RIVER_GAP_BRIDGE_PIXELS.get();
      int maximumGap = configuredGap == 0 ? 0 : 4;
      if (maximumGap > 0) {
         BitSet sourceRivers = (BitSet)rivers.clone();

         for (int index = sourceRivers.nextSetBit(0); index >= 0; index = sourceRivers.nextSetBit(index + 1)) {
            int x = index % width;
            int z = index / width;
            if (riverNeighbours(x, z, width, height, sourceRivers) <= 2) {
               for (int dz = -maximumGap - 1; dz <= maximumGap + 1; dz++) {
                  for (int dx = -maximumGap - 1; dx <= maximumGap + 1; dx++) {
                     if (dx != 0 || dz != 0) {
                        double length = Math.sqrt((double)(dx * dx + dz * dz));
                        if (!(length < 2.0) && !(length > (double)maximumGap + 1.0)) {
                           int targetX = x + dx;
                           int targetZ = z + dz;
                           if (inside(targetX, targetZ, width, height)) {
                              int target = targetZ * width + targetX;
                              if (sourceRivers.get(target)
                                 && riverNeighbours(targetX, targetZ, width, height, sourceRivers) <= 2
                                 && continuesInDirection(x, z, -dx, -dz, width, height, sourceRivers)
                                 && continuesInDirection(targetX, targetZ, dx, dz, width, height, sourceRivers)
                                 && clearLine(x, z, targetX, targetZ, width, sourceRivers)) {
                                 byte bridgeWidth = (byte)Math.min(riverWidths[index] & 255, riverWidths[target] & 255);
                                 paintLine(x, z, targetX, targetZ, width, rivers, riverWidths, bridgeWidth);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void stabilizeRiverWidths(int width, int height, BitSet rivers, byte[] riverWidths) {
      byte[] source = (byte[])riverWidths.clone();
      int[] nearbyWidths = new int[9];

      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         int x = index % width;
         int z = index / width;
         int count = 0;

         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               int sampleX = x + dx;
               int sampleZ = z + dz;
               if (inside(sampleX, sampleZ, width, height) && rivers.get(sampleZ * width + sampleX)) {
                  nearbyWidths[count++] = source[sampleZ * width + sampleX] & 255;
               }
            }
         }

         if (count >= 3) {
            Arrays.sort(nearbyWidths, 0, count);
            // Median filtering may remove an isolated overly-wide source
            // pixel, but must never inflate a narrow configured tributary just
            // because it runs beside or joins a wider river.
            riverWidths[index] = (byte)Math.min(source[index] & 255, nearbyWidths[count / 2]);
         }
      }
   }

   /**
    * Zhang-Suen thinning preserves branches and connections while reducing a
    * multi-pixel raster stroke to one centreline. Without this step the source
    * ink width was added to the configured block width, most visibly in dense
    * European river networks.
    */
   private static BitSet thinRiverCentrelines(int width, int height, BitSet source) {
      BitSet result = (BitSet)source.clone();
      BitSet remove = new BitSet(width * height);
      boolean changed;
      do {
         changed = thinningPass(result, remove, width, height, true);
         result.andNot(remove);
         remove.clear();
         boolean secondChanged = thinningPass(result, remove, width, height, false);
         result.andNot(remove);
         remove.clear();
         changed |= secondChanged;
      } while (changed);
      return result;
   }

   private static boolean thinningPass(BitSet rivers, BitSet remove, int width, int height, boolean first) {
      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         int x = index % width;
         int z = index / width;
         if (x <= 0 || z <= 0 || x + 1 >= width || z + 1 >= height) continue;

         boolean north = rivers.get(index - width);
         boolean northEast = rivers.get(index - width + 1);
         boolean east = rivers.get(index + 1);
         boolean southEast = rivers.get(index + width + 1);
         boolean south = rivers.get(index + width);
         boolean southWest = rivers.get(index + width - 1);
         boolean west = rivers.get(index - 1);
         boolean northWest = rivers.get(index - width - 1);
         int neighbours = (north ? 1 : 0) + (northEast ? 1 : 0) + (east ? 1 : 0) + (southEast ? 1 : 0)
            + (south ? 1 : 0) + (southWest ? 1 : 0) + (west ? 1 : 0) + (northWest ? 1 : 0);
         if (neighbours < 2 || neighbours > 6) continue;

         int transitions = (!north && northEast ? 1 : 0)
            + (!northEast && east ? 1 : 0)
            + (!east && southEast ? 1 : 0)
            + (!southEast && south ? 1 : 0)
            + (!south && southWest ? 1 : 0)
            + (!southWest && west ? 1 : 0)
            + (!west && northWest ? 1 : 0)
            + (!northWest && north ? 1 : 0);
         if (transitions != 1) continue;

         boolean keepByFirstTriplet = first
            ? north && east && south
            : north && east && west;
         boolean keepBySecondTriplet = first
            ? east && south && west
            : north && south && west;
         if (!keepByFirstTriplet && !keepBySecondTriplet) remove.set(index);
      }
      return !remove.isEmpty();
   }

   private static boolean continuesInDirection(int x, int z, int directionX, int directionZ, int width, int height, BitSet rivers) {
      double directionLength = Math.sqrt((double)(directionX * directionX + directionZ * directionZ));

      for (int neighbourZ = -1; neighbourZ <= 1; neighbourZ++) {
         for (int neighbourX = -1; neighbourX <= 1; neighbourX++) {
            if (neighbourX != 0 || neighbourZ != 0) {
               int nextX = x + neighbourX;
               int nextZ = z + neighbourZ;
               if (inside(nextX, nextZ, width, height)
                  && rivers.get(nextZ * width + nextX)
                  && (double)(neighbourX * directionX + neighbourZ * directionZ)
                        / (Math.sqrt((double)(neighbourX * neighbourX + neighbourZ * neighbourZ)) * directionLength)
                     >= 0.707) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean clearLine(int startX, int startZ, int endX, int endZ, int width, BitSet rivers) {
      int dx = Math.abs(endX - startX);
      int sx = startX < endX ? 1 : -1;
      int dz = -Math.abs(endZ - startZ);
      int sz = startZ < endZ ? 1 : -1;
      int error = dx + dz;
      int x = startX;
      int z = startZ;

      while (x != endX || z != endZ) {
         int twiceError = 2 * error;
         if (twiceError >= dz) {
            error += dz;
            x += sx;
         }

         if (twiceError <= dx) {
            error += dx;
            z += sz;
         }

         if (x == endX && z == endZ) {
            return true;
         }

         if (rivers.get(z * width + x)) {
            return false;
         }
      }

      return true;
   }

   private static void paintLine(int startX, int startZ, int endX, int endZ, int width, BitSet rivers, byte[] riverWidths, byte bridgeWidth) {
      int dx = Math.abs(endX - startX);
      int sx = startX < endX ? 1 : -1;
      int dz = -Math.abs(endZ - startZ);
      int sz = startZ < endZ ? 1 : -1;
      int error = dx + dz;
      int x = startX;
      int z = startZ;

      while (x != endX || z != endZ) {
         int twiceError = 2 * error;
         if (twiceError >= dz) {
            error += dz;
            x += sx;
         }

         if (twiceError <= dx) {
            error += dx;
            z += sz;
         }

         if (x == endX && z == endZ) {
            return;
         }

         int index = z * width + x;
         rivers.set(index);
         riverWidths[index] = bridgeWidth;
      }
   }

   private static int riverNeighbours(int x, int z, int width, int height, BitSet rivers) {
      int count = 0;

      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            if (dx != 0 || dz != 0) {
               int px = x + dx;
               int pz = z + dz;
               if (inside(px, pz, width, height) && rivers.get(pz * width + px)) {
                  count++;
               }
            }
         }
      }

      return count;
   }

   private static boolean inside(int x, int z, int width, int height) {
      return x >= 0 && z >= 0 && x < width && z < height;
   }

   private static boolean isFullMapOcean(int red, int green, int blue) {
      return Math.abs(red - green) <= 2 && Math.abs(green - blue) <= 2 && red >= 100 && red <= 170;
   }

   private static boolean isFullMapLand(int red, int green, int blue) {
      // Land is white and ocean is middle grey in worldmap_river.png. The full
      // 6000x3400 image contains antialiased coast pixels (254, 253 ... 191),
      // especially outside the legacy 5632x2048 crop. Requiring exact white
      // turned that half-pixel contour into frozen ocean, so iceberg features
      // traced the intended shoreline while the coast itself disappeared.
      // Threshold at the midpoint of the two source colours. River colours are
      // handled before this method and chromatic palette noise stays excluded.
      return Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 2
         && (red + green + blue) / 3 >= 189;
   }

   private static int riverWidthForColor(int red, int green, int blue) {
      int configuredWidth = configuredRiverWidth(red, green, blue);
      if (configuredWidth == 0) {
         configuredWidth = antialiasedRiverWidth(red, green, blue);
      }

      return configuredWidth == 0 ? 0 : Math.max(1, (int)Math.round((double)configuredWidth * (Double)EarthShapeServerConfig.RIVER_WIDTH_SCALE.get()));
   }

   private static int antialiasedRiverWidth(int red, int green, int blue) {
      int[][] palette = new int[][]{{0, 0, 100}, {0, 0, 150}, {0, 0, 200}, {0, 0, 255}, {0, 100, 255}, {0, 200, 255}, {0, 225, 255}};
      int[][] backgrounds = new int[][]{{255, 255, 255}, {128, 128, 128}};
      double nearestDistance = Double.POSITIVE_INFINITY;
      double nearestAlpha = 0.0;
      int[] nearest = null;

      for (int[] colour : palette) {
         for (int[] background : backgrounds) {
            double dr = (double)(colour[0] - background[0]);
            double dg = (double)(colour[1] - background[1]);
            double db = (double)(colour[2] - background[2]);
            double length = dr * dr + dg * dg + db * db;
            double alpha = Math.max(
               0.0, Math.min(1.0, ((double)(red - background[0]) * dr + (double)(green - background[1]) * dg + (double)(blue - background[2]) * db) / length)
            );
            double er = (double)red - ((double)background[0] + alpha * dr);
            double eg = (double)green - ((double)background[1] + alpha * dg);
            double eb = (double)blue - ((double)background[2] + alpha * db);
            double distance = er * er + eg * eg + eb * eb;
            if (distance < nearestDistance) {
               nearestDistance = distance;
               nearestAlpha = alpha;
               nearest = colour;
            }
         }
      }

      return nearestAlpha >= 0.04 && nearestDistance <= 64.0 ? configuredRiverWidth(nearest[0], nearest[1], nearest[2]) : 0;
   }

   private static int configuredRiverWidth(int red, int green, int blue) {
      if (red != 0) {
         return 0;
      } else {
         return switch (green << 8 | blue) {
            case 100 -> EarthShapeServerConfig.RIVER_WIDTH_000064.get();
            case 150 -> EarthShapeServerConfig.RIVER_WIDTH_000096.get();
            case 200 -> EarthShapeServerConfig.RIVER_WIDTH_0000C8.get();
            case 255 -> EarthShapeServerConfig.RIVER_WIDTH_0000FF.get();
            case 25855 -> EarthShapeServerConfig.RIVER_WIDTH_0064FF.get();
            case 51455 -> EarthShapeServerConfig.RIVER_WIDTH_00C8FF.get();
            case 57855 -> EarthShapeServerConfig.RIVER_WIDTH_00E1FF.get();
            default -> 0;
         };
      }
   }

   private static record Data(
      int width,
      int height,
      BitSet land,
      BitSet rivers,
      BitSet inlandRivers,
      BitSet frozenRivers,
      byte[] riverWidths,
      byte[] riverCorners,
      BitSet riverMouths,
      BitSet riverInfluence,
      byte[] coastalLandness,
      byte[] oceanDistance,
      BitSet pregeneration,
      SouthernSnowRegion southernSnow,
      ContinentRegions continentRegions
   ) {
      double land(int x, int z) {
         return this.land.get(z * this.width + x) ? 1.0 : 0.0;
      }

      double reliefLand(int x, int z) {
         return !this.land.get(z * this.width + x) && !this.rivers.get(z * this.width + x) ? 0.0 : 1.0;
      }

      boolean river(int x, int z) {
         return x >= 0 && z >= 0 && x < this.width && z < this.height && this.rivers.get(z * this.width + x);
      }

      int riverWidth(int x, int z) {
         return x >= 0 && z >= 0 && x < this.width && z < this.height ? this.riverWidths[z * this.width + x] & 0xFF : 0;
      }

      int riverCornerMask(int x, int z) {
         return x >= 0 && z >= 0 && x < this.width && z < this.height ? this.riverCorners[z * this.width + x] & 0xFF : 0;
      }
   }

   /**
    * Coarse connected-component map for land masses.  Keeping this at one cell per
    * 8x8 source pixels avoids a second full-resolution label map while still making
    * islands, regional land masses, and continents distinct height domains.
    */
   private static final class ContinentRegions {
      private static final int CELL_PIXELS = 8;
      private static final int ISLAND_MAX_CELLS = 128;
      private static final int REGIONAL_MAX_CELLS = 2048;
      private final int width;
      private final int height;
      private final byte[] tiers;

      private ContinentRegions(int width, int height, byte[] tiers) {
         this.width = width;
         this.height = height;
         this.tiers = tiers;
      }

      static RiversMask.ContinentRegions create(int sourceWidth, int sourceHeight, BitSet land) {
         int width = (sourceWidth + CELL_PIXELS - 1) / CELL_PIXELS;
         int height = (sourceHeight + CELL_PIXELS - 1) / CELL_PIXELS;
         int cells = width * height;
         BitSet landCells = new BitSet(cells);
         for (int source = land.nextSetBit(0); source >= 0; source = land.nextSetBit(source + 1)) {
            int x = source % sourceWidth / CELL_PIXELS;
            int z = source / sourceWidth / CELL_PIXELS;
            landCells.set(z * width + x);
         }
         byte[] tiers = new byte[cells];
         Arrays.fill(tiers, (byte)-1);
         BitSet visited = new BitSet(cells);
         IntQueue region = new IntQueue();
         for (int start = landCells.nextSetBit(0); start >= 0; start = landCells.nextSetBit(start + 1)) {
            if (visited.get(start)) continue;
            region.clear();
            region.add(start);
            visited.set(start);
            for (int cursor = 0; cursor < region.size(); cursor++) {
               int index = region.get(cursor);
               int x = index % width;
               int z = index / width;
               addLandNeighbour(landCells, visited, region, x > 0 ? index - 1 : -1);
               addLandNeighbour(landCells, visited, region, x + 1 < width ? index + 1 : -1);
               addLandNeighbour(landCells, visited, region, z > 0 ? index - width : -1);
               addLandNeighbour(landCells, visited, region, z + 1 < height ? index + width : -1);
            }
            byte tier = (byte)(region.size() <= ISLAND_MAX_CELLS ? 0 : (region.size() <= REGIONAL_MAX_CELLS ? 1 : 2));
            for (int cursor = 0; cursor < region.size(); cursor++) tiers[region.get(cursor)] = tier;
         }
         return new RiversMask.ContinentRegions(width, height, tiers);
      }

      int tier(int sourceX, int sourceZ) {
         if (sourceX < 0 || sourceZ < 0) return 0;
         int x = sourceX / CELL_PIXELS;
         int z = sourceZ / CELL_PIXELS;
         return x >= 0 && z >= 0 && x < this.width && z < this.height && this.tiers[z * this.width + x] >= 0 ? this.tiers[z * this.width + x] : 0;
      }

      private static void addLandNeighbour(BitSet land, BitSet visited, RiversMask.IntQueue region, int index) {
         if (index >= 0 && land.get(index) && !visited.get(index)) {
            visited.set(index);
            region.add(index);
         }
      }
   }

   private static final class IntQueue {
      private int[] values = new int[256];
      private int size;

      void clear() { this.size = 0; }
      int size() { return this.size; }
      int get(int index) { return this.values[index]; }
      void add(int value) {
         if (this.size == this.values.length) this.values = Arrays.copyOf(this.values, this.values.length * 2);
         this.values[this.size++] = value;
      }
   }

   private static final class OceanProximity {
      private static final int SCALE = 4;
      private static final int SHORE_BAND_CELLS = 3;
      private final int width;
      private final int height;
      private final byte[] distance;

      private OceanProximity(int width, int height, byte[] distance) {
         this.width = width;
         this.height = height;
         this.distance = distance;
      }

      static RiversMask.OceanProximity create(int sourceWidth, int sourceHeight, BitSet land) {
         int width = (sourceWidth + 4 - 1) / 4;
         int height = (sourceHeight + 4 - 1) / 4;
         int cells = width * height;
         BitSet water = new BitSet(cells);

         for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
               int landCount = 0;

               for (int dz = 0; dz < 4; dz++) {
                  for (int dx = 0; dx < 4; dx++) {
                     int px = x * 4 + dx;
                     int pz = z * 4 + dz;
                     if (px < sourceWidth && pz < sourceHeight && land.get(pz * sourceWidth + px)) {
                        landCount++;
                     }
                  }
               }

               if (landCount <= 8) {
                  water.set(z * width + x);
               }
            }
         }

         BitSet openOcean = new BitSet(cells);
         int[] queue = new int[cells];
         int head = 0;
         int tail = 0;

         for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
               if (x == 0 || z == 0 || x == width - 1 || z == height - 1) {
                  int index = z * width + x;
                  if (water.get(index) && !openOcean.get(index)) {
                     openOcean.set(index);
                     queue[tail++] = index;
                  }
               }
            }
         }

         while (head < tail) {
            int index = queue[head++];
            int xx = index % width;
            int z = index / width;
            if (xx > 0) {
               tail = floodWater(index - 1, water, openOcean, queue, tail);
            }

            if (xx + 1 < width) {
               tail = floodWater(index + 1, water, openOcean, queue, tail);
            }

            if (z > 0) {
               tail = floodWater(index - width, water, openOcean, queue, tail);
            }

            if (z + 1 < height) {
               tail = floodWater(index + width, water, openOcean, queue, tail);
            }
         }

         byte[] distance = new byte[cells];
         Arrays.fill(distance, (byte)127);
         head = 0;
         tail = 0;

         for (int indexx = openOcean.nextSetBit(0); indexx >= 0; indexx = openOcean.nextSetBit(indexx + 1)) {
            distance[indexx] = 0;
            queue[tail++] = indexx;
         }

         while (head < tail) {
            int indexx = queue[head++];
            int current = distance[indexx] & 255;
            if (current < 3) {
               int xxx = indexx % width;
               int zx = indexx / width;
               if (xxx > 0) {
                  tail = floodDistance(indexx - 1, current, distance, queue, tail);
               }

               if (xxx + 1 < width) {
                  tail = floodDistance(indexx + 1, current, distance, queue, tail);
               }

               if (zx > 0) {
                  tail = floodDistance(indexx - width, current, distance, queue, tail);
               }

               if (zx + 1 < height) {
                  tail = floodDistance(indexx + width, current, distance, queue, tail);
               }
            }
         }

         return new RiversMask.OceanProximity(width, height, distance);
      }

      boolean isNearOpenOcean(int sourceX, int sourceZ) {
         int x = Math.max(0, Math.min(this.width - 1, sourceX / 4));
         int z = Math.max(0, Math.min(this.height - 1, sourceZ / 4));
         return (this.distance[z * this.width + x] & 255) <= 3;
      }

      private static int floodWater(int index, BitSet water, BitSet openOcean, int[] queue, int tail) {
         if (water.get(index) && !openOcean.get(index)) {
            openOcean.set(index);
            queue[tail++] = index;
         }

         return tail;
      }

      private static int floodDistance(int index, int current, byte[] distance, int[] queue, int tail) {
         if ((distance[index] & 255) == 127) {
            distance[index] = (byte)(current + 1);
            queue[tail++] = index;
         }

         return tail;
      }
   }

   private static final class LandSampleCache {
      private static final int SIZE = 256;
      private RiversMask.Data data;
      private final int[] x = new int[SIZE];
      private final int[] z = new int[SIZE];
      private final double[] land = new double[SIZE];

      private LandSampleCache() {
         Arrays.fill(this.x, Integer.MIN_VALUE);
         Arrays.fill(this.z, Integer.MIN_VALUE);
      }

      void reset(RiversMask.Data data) {
         this.data = data;
         Arrays.fill(this.x, Integer.MIN_VALUE);
         Arrays.fill(this.z, Integer.MIN_VALUE);
      }

      static int slot(int blockX, int blockZ) {
         int hash = blockX * 0x9E3779B9 ^ Integer.rotateLeft(blockZ * 0x85EBCA6B, 16);
         return hash & (SIZE - 1);
      }
   }

   /** Coarse component map used only to identify the Antarctic continent. */
   private static final class SouthernSnowRegion {
      private static final int CELL_PIXELS = 8;
      private final int width;
      private final int height;
      private final BitSet antarctica;

      private SouthernSnowRegion(int width, int height, BitSet antarctica) {
         this.width = width;
         this.height = height;
         this.antarctica = antarctica;
      }

      static SouthernSnowRegion create(int sourceWidth, int sourceHeight, BitSet land) {
         int width = (sourceWidth + CELL_PIXELS - 1) / CELL_PIXELS;
         int height = (sourceHeight + CELL_PIXELS - 1) / CELL_PIXELS;
         BitSet landCells = new BitSet(width * height);
         for (int source = land.nextSetBit(0); source >= 0; source = land.nextSetBit(source + 1)) {
            int x = source % sourceWidth / CELL_PIXELS;
            int z = source / sourceWidth / CELL_PIXELS;
            landCells.set(z * width + x);
         }

         BitSet visited = new BitSet(width * height);
         BitSet antarctica = new BitSet(width * height);
         IntQueue region = new IntQueue();
         int bestSize = 0;
         // Antarctica is the largest land component reaching the southernmost
         // 10% of the source map. Southern islands still receive snow inside the
         // exact 520-block band, but are not promoted wholesale to a continent.
         int southernStart = height * 9 / 10;
         for (int start = landCells.nextSetBit(0); start >= 0; start = landCells.nextSetBit(start + 1)) {
            if (visited.get(start)) continue;
            region.clear();
            region.add(start);
            visited.set(start);
            boolean reachesSouth = false;
            for (int cursor = 0; cursor < region.size(); cursor++) {
               int index = region.get(cursor);
               int x = index % width;
               int z = index / width;
               if (z >= southernStart) reachesSouth = true;
               ContinentRegions.addLandNeighbour(landCells, visited, region, x > 0 ? index - 1 : -1);
               ContinentRegions.addLandNeighbour(landCells, visited, region, x + 1 < width ? index + 1 : -1);
               ContinentRegions.addLandNeighbour(landCells, visited, region, z > 0 ? index - width : -1);
               ContinentRegions.addLandNeighbour(landCells, visited, region, z + 1 < height ? index + width : -1);
            }
            if (reachesSouth && region.size() > bestSize) {
               antarctica.clear();
               for (int cursor = 0; cursor < region.size(); cursor++) antarctica.set(region.get(cursor));
               bestSize = region.size();
            }
         }
         return new SouthernSnowRegion(width, height, antarctica);
      }

      boolean isAntarctica(int sourceX, int sourceZ) {
         int x = sourceX / CELL_PIXELS;
         int z = sourceZ / CELL_PIXELS;
         return x >= 0 && z >= 0 && x < this.width && z < this.height && this.antarctica.get(z * this.width + x);
      }
   }

   private static final class RiverBiomeCache {
      private RiversMask.Data data;
      private int blockX = Integer.MIN_VALUE;
      private int blockZ = Integer.MIN_VALUE;
      private int state;

      int set(RiversMask.Data data, int blockX, int blockZ, int state) {
         this.data = data;
         this.blockX = blockX;
         this.blockZ = blockZ;
         this.state = state;
         return state;
      }
   }

   private static final class ScalarSampleCache {
      private static final int SIZE = 256;
      private RiversMask.Data data;
      private final int[] x = new int[SIZE];
      private final int[] z = new int[SIZE];
      private final double[] value = new double[SIZE];

      private ScalarSampleCache() {
         Arrays.fill(this.x, Integer.MIN_VALUE);
         Arrays.fill(this.z, Integer.MIN_VALUE);
      }

      void set(RiversMask.Data data, int slot, int blockX, int blockZ, double value) {
         if (this.data != data) {
            this.data = data;
            Arrays.fill(this.x, Integer.MIN_VALUE);
            Arrays.fill(this.z, Integer.MIN_VALUE);
         }
         this.x[slot] = blockX;
         this.z[slot] = blockZ;
         this.value[slot] = value;
      }

      static int slot(int blockX, int blockZ) {
         int hash = blockX * 0x9E3779B9 ^ Integer.rotateLeft(blockZ * 0x85EBCA6B, 16);
         return hash & (SIZE - 1);
      }
   }

   private static final class RiverWidthCache {
      private RiversMask.Data data;
      private int blockX;
      private int blockZ;
      private int width;
   }

   private static final class RiverDistanceCache {
      private RiversMask.Data data;
      private int blockX;
      private int blockZ;
      private double distance;

      void set(RiversMask.Data data, int blockX, int blockZ, double distance) {
         this.data = data;
         this.blockX = blockX;
         this.blockZ = blockZ;
         this.distance = distance;
      }
   }

   private static final class RiverColumnCache {
      private static final int SIZE = 512;
      private final int[] x = new int[SIZE];
      private final int[] z = new int[SIZE];
      private final boolean[] river = new boolean[SIZE];
      private RiversMask.Data data;

      private RiverColumnCache() {
         Arrays.fill(this.x, Integer.MIN_VALUE);
         Arrays.fill(this.z, Integer.MIN_VALUE);
      }

      static int slot(int blockX, int blockZ) {
         int hash = blockX * 0x9E3779B9 ^ Integer.rotateLeft(blockZ * 0x85EBCA6B, 16);
         return hash & (SIZE - 1);
      }
   }
}
