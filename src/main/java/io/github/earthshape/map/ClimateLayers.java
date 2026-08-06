package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class ClimateLayers {
   private static final int TREES_REGION_WIDTH = 5632;
   private static final int TREES_REGION_HEIGHT = 2048;
   private static final int MOUNTAIN_LOW = 10;
   private static final int MOUNTAIN_MID = 11;
   private static final int MOUNTAIN_HIGH = 12;
   private static final int MOUNTAIN_ULTRA = 13;
   private static final ClimateLayers.TerrainKind[] TERRAIN_KIND_VALUES = ClimateLayers.TerrainKind.values();
   private static final ThreadLocal<ClimateLayers.WarpedPointCache> WARPED_POINT_CACHE =
      ThreadLocal.withInitial(ClimateLayers.WarpedPointCache::new);
   private static final ThreadLocal<ClimateLayers.TerrainScalarCache> RELIEF_CACHE =
      ThreadLocal.withInitial(ClimateLayers.TerrainScalarCache::new);
   private static final ThreadLocal<ClimateLayers.TerrainScalarCache> DESERT_CACHE =
      ThreadLocal.withInitial(ClimateLayers.TerrainScalarCache::new);
   /** Small-scale mountain clusters below this size factor merge into their surroundings. */
   private static final double MINIMUM_MOUNTAIN_REGION_SCALE = 0.10;
   /**
    * terrain.bmp contains province-colour crumbs that are too small to represent
    * a stable biome even when an older server config retained the former, very
    * permissive thresholds. These floors remove only connected source-map
    * fragments; broad boundaries and deliberately small but coherent regions stay.
    */
   private static final int MINIMUM_TERRAIN_FRAGMENT_PIXELS = 12;
   private static final int MINIMUM_ISOLATED_TERRAIN_FRAGMENT_PIXELS = 24;
   public static final ClimateLayers INSTANCE = new ClimateLayers();
   private volatile ClimateLayers.Data temperature;
   private volatile ClimateLayers.Data trees;
   private volatile ClimateLayers.Data terrain;
   private volatile ClimateLayers.Data normal;

   private ClimateLayers() {
   }

   public double temperature(int x, int z) {
      ClimateLayers.Data layer = this.temperature();
      ClimateLayers.TemperatureSample sample = sampleFullTemperature(layer, x, z);
      double latitude = latitudeTemperature(z);
      double mapped = sample.value * 2.0 - 1.0;
      return latitude + (mapped - latitude) * sample.coverage;
   }

   public double oceanTemperature(int z) {
      return latitudeTemperature(z);
   }

   public boolean hasLegacyTemperature(int x, int z) {
      double mapX = RiversMask.INSTANCE.mapImageX(x);
      double mapZ = RiversMask.INSTANCE.mapImageZ(z);
      return mapX >= 0.0 && mapZ >= 0.0 && mapX < (double)RiversMask.INSTANCE.width() && mapZ < (double)RiversMask.INSTANCE.height();
   }

   public double vegetation(int x, int z) {
      return sample(this.trees(), x, z) * 2.0 - 1.0;
   }

   public ClimateLayers.TreeCover treeCover(int x, int z) {
      ClimateLayers.Data layer = this.trees();
      long point = warpedTerrainPoint(x, z);
      int sampleX = unpackX(point);
      int sampleZ = unpackZ(point);
      double worldX = RiversMask.INSTANCE.legacyImageX(sampleX, TREES_REGION_WIDTH);
      double worldZ = RiversMask.INSTANCE.legacyImageZ(sampleZ, TREES_REGION_HEIGHT);
      if (!(worldX < 0.0) && !(worldZ < 0.0) && !(worldX >= 5632.0) && !(worldZ >= 2048.0)) {
         int imageX = Math.min(layer.width - 1, (int)(worldX / 5632.0 * (double)layer.width));
         int imageZ = Math.min(layer.height - 1, (int)(worldZ / 2048.0 * (double)layer.height));
         int value = layer.values[imageZ * layer.width + imageX] & 255;
         return value >= 235 ? ClimateLayers.TreeCover.TROPICAL : (value >= 150 ? ClimateLayers.TreeCover.TEMPERATE : ClimateLayers.TreeCover.NONE);
      } else {
         return ClimateLayers.TreeCover.NONE;
      }
   }

   public ClimateLayers.TerrainKind terrainKind(int x, int z) {
      // terrain.bmp may contain WATER/SURROUNDING pixels around the coast.
      // They used to be replaced with the nearest land class below, leaving a
      // one-biome-wide land rim in the ocean. The authoritative land/ocean mask
      // must win before any terrain-layer fallback is considered.
      if (RiversMask.INSTANCE.sampleLayerLand(x, z) < 0.5) {
         return ClimateLayers.TerrainKind.WATER;
      }
      ClimateLayers.Data layer = this.terrain();
      long point = warpedTerrainPoint(x, z);
      int sampleX = unpackX(point);
      int sampleZ = unpackZ(point);
      if (!RiversMask.INSTANCE.isInsideLegacyLayer(sampleX, sampleZ, layer.width, layer.height)) {
         return ClimateLayers.TerrainKind.PLAINS;
      } else {
         int imageX = sourceX(layer, sampleX);
         int imageZ = sourceZ(layer, sampleZ);
         int index = imageZ * layer.width + imageX;
         int code = layer.values[index] & 255;
         ClimateLayers.TerrainKind kind = isMountainElevationCode(code) ? ClimateLayers.TerrainKind.MOUNTAIN : ClimateLayers.TerrainKind.byCode(code);
         // A detailed alpine patch can contain several peak colours while still
         // being only a few blocks wide at a small world scale. Do not expose it
         // as a mountain biome until its connected-region size is large enough.
         if (kind == ClimateLayers.TerrainKind.MOUNTAIN
            && (layer.mountainScale == null || (double)(layer.mountainScale[index] & 255) / 255.0 < MINIMUM_MOUNTAIN_REGION_SCALE)) {
            return surroundingLandKind(layer, imageX, imageZ);
         }
         return kind != ClimateLayers.TerrainKind.CITY && kind != ClimateLayers.TerrainKind.SURROUNDING ? kind : surroundingLandKind(layer, imageX, imageZ);
      }
   }

   /**
    * A river is kept aligned to its unwarped source mask, while the terrain
    * boundary on either side normally receives a small smooth warp.  Where the
    * source terrain image contains a neutral plains seam under that river, the
    * different sampling rules can otherwise expose it as a plains ribbon along
    * an unrelated forest, desert, or wetland.
    *
    * Only repair a plains result outside the actual channel, and only when a
    * clear majority of samples beyond the full channel width agrees on another
    * land family. Genuine plains riverbanks and biome-boundary rivers therefore
    * remain unchanged.
    */
   public ClimateLayers.TerrainKind terrainKindAtRiverbank(int x, int z, ClimateLayers.TerrainKind current) {
      if (current != ClimateLayers.TerrainKind.PLAINS || !RiversMask.INSTANCE.isInlandRiverBank(x, z)) {
         return current;
      }

      int riverWidth = RiversMask.INSTANCE.effectiveRiverWidthBlocks(x, z);
      if (riverWidth <= 0) return current;
      int clearance = riverWidth + Math.max(24, RiversMask.INSTANCE.blocksPerPixel() * 2);
      int diagonal = Math.max(1, (int)Math.round((double)clearance * 0.7071067811865476));
      // Eight four-bit counters fit in one long for all usable terrain kinds.
      // Keeping this allocation-free matters because biome lookup runs repeatedly
      // on worker threads while a pregenerator is active.
      long counts = 0L;
      counts = incrementTerrainCount(counts, this.terrainKind(x + clearance, z));
      counts = incrementTerrainCount(counts, this.terrainKind(x - clearance, z));
      counts = incrementTerrainCount(counts, this.terrainKind(x, z + clearance));
      counts = incrementTerrainCount(counts, this.terrainKind(x, z - clearance));
      counts = incrementTerrainCount(counts, this.terrainKind(x + diagonal, z + diagonal));
      counts = incrementTerrainCount(counts, this.terrainKind(x + diagonal, z - diagonal));
      counts = incrementTerrainCount(counts, this.terrainKind(x - diagonal, z + diagonal));
      counts = incrementTerrainCount(counts, this.terrainKind(x - diagonal, z - diagonal));

      ClimateLayers.TerrainKind winner = current;
      for (ClimateLayers.TerrainKind kind : TERRAIN_KIND_VALUES) {
         if (kind != ClimateLayers.TerrainKind.WATER
            && kind != ClimateLayers.TerrainKind.CITY
            && kind != ClimateLayers.TerrainKind.SURROUNDING
            && terrainCount(counts, kind) > terrainCount(counts, winner)) {
            winner = kind;
         }
      }
      return winner != current && terrainCount(counts, winner) >= 3 ? winner : current;
   }

   private static long incrementTerrainCount(long counts, ClimateLayers.TerrainKind kind) {
      if (kind == ClimateLayers.TerrainKind.WATER
         || kind == ClimateLayers.TerrainKind.CITY
         || kind == ClimateLayers.TerrainKind.SURROUNDING) {
         return counts;
      }
      return counts + (1L << (kind.ordinal() * 4));
   }

   private static int terrainCount(long counts, ClimateLayers.TerrainKind kind) {
      return (int)(counts >>> (kind.ordinal() * 4) & 15L);
   }

   /** White (#FFFFFF) is the sole ultra-high terrain colour. */
   public boolean isUltraMountain(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      long point = warpedTerrainPoint(x, z);
      int sampleX = unpackX(point);
      int sampleZ = unpackZ(point);
      if (!RiversMask.INSTANCE.isInsideLegacyLayer(sampleX, sampleZ, layer.width, layer.height)) return false;
      int index = sourceZ(layer, sampleZ) * layer.width + sourceX(layer, sampleX);
      return (layer.values[index] & 255) == MOUNTAIN_ULTRA
         && layer.mountainScale != null
         && (double)(layer.mountainScale[index] & 255) / 255.0 >= MINIMUM_MOUNTAIN_REGION_SCALE;
   }

   /**
    * #48D0C9 (72,208,201) is the warm edge of the polar blue/teal temperature
    * band.  Band two and colder are therefore the only non-white-source locations
    * allowed to request Frozen Peaks.
    */
   public boolean isPolarTemperatureZone(int x, int z) {
      return this.temperature(x, z) <= -0.50;
   }

   /** Do not warp a climate sample across a terrain-layer boundary. */
   public boolean isTerrainBoundary(int x, int z, int distanceBlocks) {
      ClimateLayers.TerrainKind centre = this.terrainKind(x, z);
      return centre != this.terrainKind(x - distanceBlocks, z)
         || centre != this.terrainKind(x + distanceBlocks, z)
         || centre != this.terrainKind(x, z - distanceBlocks)
         || centre != this.terrainKind(x, z + distanceBlocks);
   }

   /** True only for non-mountain cells in the immediate foothill band. */
   public boolean isNearMountain(int x, int z, int distanceBlocks) {
      return this.terrainKind(x - distanceBlocks, z) == TerrainKind.MOUNTAIN
         || this.terrainKind(x + distanceBlocks, z) == TerrainKind.MOUNTAIN
         || this.terrainKind(x, z - distanceBlocks) == TerrainKind.MOUNTAIN
         || this.terrainKind(x, z + distanceBlocks) == TerrainKind.MOUNTAIN;
   }

   private static boolean isMountainElevationCode(int code) {
      return code >= MOUNTAIN_LOW && code <= MOUNTAIN_ULTRA;
   }

   public double desert(int x, int z) {
      return this.terrainKind(x, z) == ClimateLayers.TerrainKind.DESERT ? 1.0 : 0.0;
   }

   public double steepness(int x, int z) {
      return sample(this.normal(), x, z);
   }

   /**
    * Smooth terrain-map guidance for vanilla's climate splines. Zero leaves
    * vanilla untouched, 0.5 represents hills, and 1.0 represents mountains.
    */
   public double terrainRelief(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      if (layer.relief == null) return 0.0;
      long point = warpedTerrainPoint(x, z);
      long transformVersion = RiversMask.INSTANCE.mapTransformVersion();
      ClimateLayers.TerrainScalarCache cache = RELIEF_CACHE.get();
      if (cache.matches(transformVersion, point)) return cache.value(point);
      double value = sample(layer, layer.relief, unpackX(point), unpackZ(point));
      // A small island often occupies only the ridge centre of its terrain-map
      // class. Giving it a continental mountain's full E/PV signal lifts the
      // entire island as one steep pillar. Keep real relief, but limit it to a
      // broad low-hill strength on small connected land masses.
      if (RiversMask.INSTANCE.isSmallIsland(x, z)) value = Math.min(value, 0.35);
      cache.put(transformVersion, point, value);
      return value;
   }

   /**
    * Smooth coverage for categorical terrain corrections.  The source class is
    * blurred before sampling so a one-pixel class boundary cannot switch a
    * height-guiding density axis in a single world column.
    */
   public double desertInfluence(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      if (layer.desertBlend == null) return 0.0;
      long point = warpedTerrainPoint(x, z);
      long transformVersion = RiversMask.INSTANCE.mapTransformVersion();
      ClimateLayers.TerrainScalarCache cache = DESERT_CACHE.get();
      if (cache.matches(transformVersion, point)) return cache.value(point);
      double value = sample(layer, layer.desertBlend, unpackX(point), unpackZ(point));
      cache.put(transformVersion, point, value);
      return value;
   }

   /**
    * Size-derived height allowance for the exact connected mountain region.
    * Returns -1 outside mapped mountain pixels.
    */
   public double mountainRegionHeightScale(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      long point = warpedTerrainPoint(x, z);
      int sampleX = unpackX(point);
      int sampleZ = unpackZ(point);
      if (layer.mountainScale == null || !RiversMask.INSTANCE.isInsideLegacyLayer(sampleX, sampleZ, layer.width, layer.height)) return -1.0;
      int index = sourceZ(layer, sampleZ) * layer.width + sourceX(layer, sampleX);
      if (!isMountainElevationCode(layer.values[index] & 255)) return -1.0;
      return (double)(layer.mountainScale[index] & 255) / 255.0;
   }

   public boolean isMesaRegion(int blockX, int blockZ) {
      double u = RiversMask.INSTANCE.mapImageX(blockX) / (double)RiversMask.INSTANCE.width();
      double v = RiversMask.INSTANCE.mapImageZ(blockZ) / (double)RiversMask.INSTANCE.height();
      boolean americas = u > 0.05 && u < 0.43 && v > 0.08 && v < 0.88;
      boolean oceania = u > 0.73 && u < 0.97 && v > 0.5 && v < 0.92;
      return americas || oceania;
   }

   private ClimateLayers.Data temperature() {
      ClimateLayers.Data v = this.temperature;
      if (v != null) {
         return v;
      } else {
         synchronized (this) {
            return this.temperature = this.temperature == null ? load("earth_temperature.png", ClimateLayers.Kind.TEMPERATURE) : this.temperature;
         }
      }
   }

   private ClimateLayers.Data trees() {
      ClimateLayers.Data v = this.trees;
      if (v != null) {
         return v;
      } else {
         synchronized (this) {
            return this.trees = this.trees == null ? load("trees.bmp", ClimateLayers.Kind.VEGETATION) : this.trees;
         }
      }
   }

   private ClimateLayers.Data terrain() {
      ClimateLayers.Data v = this.terrain;
      if (v != null) {
         return v;
      } else {
         synchronized (this) {
            return this.terrain = this.terrain == null ? load("terrain.bmp", ClimateLayers.Kind.TERRAIN_CLASS) : this.terrain;
         }
      }
   }

   private ClimateLayers.Data normal() {
      ClimateLayers.Data v = this.normal;
      if (v != null) {
         return v;
      } else {
         synchronized (this) {
            return this.normal = this.normal == null ? load("world_normal.bmp", ClimateLayers.Kind.NORMAL) : this.normal;
         }
      }
   }

   private static double sample(ClimateLayers.Data layer, int blockX, int blockZ) {
      return sample(layer, layer.values, blockX, blockZ);
   }

   private static double sample(ClimateLayers.Data layer, byte[] samples, int blockX, int blockZ) {
      double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
      double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
      if (!(imageX < 0.0) && !(imageZ < 0.0) && !(imageX >= (double)layer.width - 1.0) && !(imageZ >= (double)layer.height - 1.0)) {
         int x = (int)imageX;
         int z = (int)imageZ;
         double tx = imageX - (double)x;
         double tz = imageZ - (double)z;
         return lerp(
            lerp(sampleValue(samples, layer.width, x, z), sampleValue(samples, layer.width, x + 1, z), tx),
            lerp(sampleValue(samples, layer.width, x, z + 1), sampleValue(samples, layer.width, x + 1, z + 1), tx),
            tz
         );
      } else {
         return samples == layer.values ? 0.5 : 0.0;
      }
   }

   private static double sampleValue(byte[] samples, int width, int x, int z) {
      return (double)(samples[z * width + x] & 255) / 255.0;
   }

   private static ClimateLayers.TemperatureSample sampleFullTemperature(ClimateLayers.Data layer, int blockX, int blockZ) {
      double worldX = RiversMask.INSTANCE.mapImageX(blockX);
      double worldZ = RiversMask.INSTANCE.mapImageZ(blockZ);
      double mapWidth = (double)RiversMask.INSTANCE.width();
      double mapHeight = (double)RiversMask.INSTANCE.height();

      // Apply the configured north/south expansion around the equator. This
      // option previously existed only in the config and was never consumed.
      double verticalScale = (Double)EarthShapeServerConfig.TEMPERATURE_VERTICAL_SCALE.get();
      worldZ = (worldZ - mapHeight * 0.5) / verticalScale + mapHeight * 0.5;

      // Continue the equirectangular temperature field across either pole
      // instead of dropping to a latitude-only fallback at Z=0/3400. Crossing
      // a pole reflects latitude and moves longitude by 180 degrees, so mapped
      // isotherms join naturally rather than ending as a straight tundra line.
      double foldedZ = positiveModulo(worldZ, mapHeight * 2.0);
      if (foldedZ >= mapHeight) {
         foldedZ = mapHeight * 2.0 - foldedZ;
         worldX += mapWidth * 0.5;
      }
      worldX = positiveModulo(worldX, mapWidth);
      worldZ = Math.max(0.0, Math.min(mapHeight - 1.001, foldedZ));

      double imageX = Math.max(0.0, Math.min((double)layer.width - 1.001, worldX / (double)RiversMask.INSTANCE.width() * (double)layer.width));
      double imageZ = Math.max(0.0, Math.min((double)layer.height - 1.001, worldZ / (double)RiversMask.INSTANCE.height() * (double)layer.height));
      int x = (int)imageX;
      int z = (int)imageZ;
      double tx = imageX - (double)x;
      double tz = imageZ - (double)z;
      double value = lerp(lerp(layer.value(x, z), layer.value(x + 1, z), tx), lerp(layer.value(x, z + 1), layer.value(x + 1, z + 1), tx), tz);
      double coverage = lerp(lerp(layer.coverage(x, z), layer.coverage(x + 1, z), tx), lerp(layer.coverage(x, z + 1), layer.coverage(x + 1, z + 1), tx), tz);
      return new ClimateLayers.TemperatureSample(value, coverage);
   }

   private static double positiveModulo(double value, double modulus) {
      double result = value % modulus;
      return result < 0.0 ? result + modulus : result;
   }

   private static int sourceX(ClimateLayers.Data layer, int blockX) {
      double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
      return Math.max(0, Math.min(layer.width - 1, (int)imageX));
   }

   private static int sourceZ(ClimateLayers.Data layer, int blockZ) {
      double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
      return Math.max(0, Math.min(layer.height - 1, (int)imageZ));
   }

   private static ClimateLayers.TerrainKind surroundingLandKind(ClimateLayers.Data layer, int centreX, int centreZ) {
      int[] counts = new int[ClimateLayers.TerrainKind.values().length];
      for (int radius = 1; radius <= 48; radius++) {
         java.util.Arrays.fill(counts, 0);
         int minX = centreX - radius;
         int maxX = centreX + radius;
         int minZ = centreZ - radius;
         int maxZ = centreZ + radius;

         // Visit only the square perimeter. The former clipped square scan then
         // discarded every interior point, turning a radius-48 fallback into
         // cubic work and allocating 48 temporary arrays per biome lookup.
         int fromX = Math.max(0, minX);
         int toX = Math.min(layer.width - 1, maxX);
         if (minZ >= 0 && minZ < layer.height) {
            for (int x = fromX; x <= toX; x++) countSurroundingKind(layer, counts, x, minZ);
         }
         if (maxZ != minZ && maxZ >= 0 && maxZ < layer.height) {
            for (int x = fromX; x <= toX; x++) countSurroundingKind(layer, counts, x, maxZ);
         }

         int fromZ = Math.max(0, minZ + 1);
         int toZ = Math.min(layer.height - 1, maxZ - 1);
         if (minX >= 0 && minX < layer.width) {
            for (int z = fromZ; z <= toZ; z++) countSurroundingKind(layer, counts, minX, z);
         }
         if (maxX != minX && maxX >= 0 && maxX < layer.width) {
            for (int z = fromZ; z <= toZ; z++) countSurroundingKind(layer, counts, maxX, z);
         }

         ClimateLayers.TerrainKind result = ClimateLayers.TerrainKind.PLAINS;

         for (ClimateLayers.TerrainKind kind : ClimateLayers.TerrainKind.values()) {
            if (counts[kind.code] > counts[result.code]) {
               result = kind;
            }
         }

         if (counts[result.code] > 0) {
            return result;
         }
      }

      return ClimateLayers.TerrainKind.PLAINS;
   }

   private static void countSurroundingKind(ClimateLayers.Data layer, int[] counts, int x, int z) {
      int code = layer.values[z * layer.width + x] & 255;
      if (isMountainElevationCode(code)) return;
      ClimateLayers.TerrainKind kind = ClimateLayers.TerrainKind.byCode(code);
      if (kind != ClimateLayers.TerrainKind.CITY
         && kind != ClimateLayers.TerrainKind.SURROUNDING
         && kind != ClimateLayers.TerrainKind.WATER
         && kind != ClimateLayers.TerrainKind.DESERT) {
         counts[kind.code]++;
      }
   }

   /**
    * Whether terrain.bmp actually supplies a class at this world column. The
    * coastline and temperature maps are larger than the legacy terrain layer;
    * callers must not treat the uncovered outer area as explicit PLAINS.
    */
   public boolean hasTerrainCoverage(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      long point = warpedTerrainPoint(x, z);
      return RiversMask.INSTANCE.isInsideLegacyLayer(unpackX(point), unpackZ(point), layer.width, layer.height);
   }

   private static ClimateLayers.Data load(String name, ClimateLayers.Kind kind) {
      try {
         ClimateLayers.Data var14x;
         try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/" + name)) {
            if (input == null) {
               throw new IOException("missing " + name);
            }

            BufferedImage image = ImageIO.read(input);
            if (image == null) {
               throw new IOException(name + " is not readable");
            }

            int width = image.getWidth();
            int height = image.getHeight();
            byte[] values = new byte[width * height];
            byte[] coverage = new byte[width * height];
            int[] row = new int[width];

            for (int z = 0; z < height; z++) {
               image.getRGB(0, z, width, 1, row, 0, width);

               for (int x = 0; x < width; x++) {
                  values[z * width + x] = (byte)kind.value(row[x]);
                  coverage[z * width + x] = (byte)kind.coverage(row[x]);
               }
            }

            // HOI4 terrain.bmp contains province-scale colour noise.  Treating every
            // isolated source pixel as a separate biome produces a checkerboard of
            // mountains/forests in-game.  Remove only small islands of a class while
            // leaving genuine multi-pixel ranges and regional boundaries intact.
            byte[] relief = null;
            byte[] mountainScale = null;
            byte[] desertBlend = null;
            if (kind == ClimateLayers.Kind.TERRAIN_CLASS) {
               values = smoothTerrainClasses(values, width, height);
               values = smoothTerrainClasses(values, width, height);
               int isolatedMinimum = Math.max(
                  MINIMUM_ISOLATED_TERRAIN_FRAGMENT_PIXELS,
                  (Integer)EarthShapeServerConfig.TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS.get()
               );
               int generalMinimum = Math.max(
                  MINIMUM_TERRAIN_FRAGMENT_PIXELS,
                  (Integer)EarthShapeServerConfig.TERRAIN_BIOME_MINIMUM_REGION_PIXELS.get()
               );
               values = removeSmallTerrainRegions(
                  values, width, height, isolatedMinimum, true
               );
               values = removeSmallTerrainRegions(
                  values, width, height, generalMinimum, false
               );
               // A few source-map desert pixels occur inside otherwise continuous
               // rocky ranges.  Leaving them intact makes vanilla surface rules put
               // isolated sand flecks between stone peaks.  Merge only pockets fully
               // enclosed by mountains; real desert-to-mountain borders remain intact.
               values = removeMountainDesertPockets(values, width, height, 96);
               mountainScale = new byte[values.length];
               relief = distanceMountainRelief(values, mountainScale, width, height);
               desertBlend = terrainClassBlend(values, width, height, TerrainKind.DESERT);
            }

            EarthShape.LOGGER.info("[EarthShape] {} climate layer loaded: {}x{}.", new Object[]{name, width, height});
            var14x = new ClimateLayers.Data(width, height, values, coverage, relief, mountainScale, desertBlend);
         }

         return var14x;
      } catch (IOException var14) {
         throw new IllegalStateException("EarthShape could not load " + name, var14);
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static long warpedTerrainPoint(int blockX, int blockZ) {
      if (!(Boolean)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_ENABLED.get()) {
         return packPoint(blockX, blockZ);
      }

      int configured = (Integer)EarthShapeServerConfig.BIOME_BOUNDARY_WARP_BLOCKS.get();
      int blocksPerPixel = RiversMask.INSTANCE.blocksPerPixel();
      int strength = Math.min(configured, Math.max(4, blocksPerPixel * 3 / 2));
      long transformVersion = RiversMask.INSTANCE.mapTransformVersion();
      ClimateLayers.WarpedPointCache cache = WARPED_POINT_CACHE.get();
      if (cache.transformVersion != transformVersion || cache.strength != strength) {
         cache.reset(transformVersion, strength);
      }
      int slot = ClimateLayers.WarpedPointCache.slot(blockX, blockZ);
      if (cache.x[slot] == blockX && cache.z[slot] == blockZ) return cache.point[slot];

      long result = computeWarpedTerrainPoint(blockX, blockZ, strength);
      cache.x[slot] = blockX;
      cache.z[slot] = blockZ;
      cache.point[slot] = result;
      return result;
   }

   private static long computeWarpedTerrainPoint(int blockX, int blockZ, int strength) {
      if (strength <= 0 || RiversMask.INSTANCE.isNearInlandRiver(blockX, blockZ, strength + 8)) {
         return packPoint(blockX, blockZ);
      }

      double warpX = smoothNoise(blockX, blockZ, 160, 0x6A09E667F3BCC909L) * 0.68
         + smoothNoise(blockX, blockZ, 64, 0xBB67AE8584CAA73BL) * 0.32;
      double warpZ = smoothNoise(blockX, blockZ, 160, 0x3C6EF372FE94F82BL) * 0.68
         + smoothNoise(blockX, blockZ, 64, 0xA54FF53A5F1D36F1L) * 0.32;
      int warpedX = blockX + (int)Math.round(warpX * (double)strength);
      int warpedZ = blockZ + (int)Math.round(warpZ * (double)strength);

      // terrain.bmp may curve inside a continent, but rivers.bmp remains the
      // authoritative coastline. Never warp a sample across its land/ocean side.
      boolean originalLand = RiversMask.INSTANCE.sampleLayerLand(blockX, blockZ) >= 0.5;
      boolean warpedLand = RiversMask.INSTANCE.sampleLayerLand(warpedX, warpedZ) >= 0.5;
      return originalLand == warpedLand ? packPoint(warpedX, warpedZ) : packPoint(blockX, blockZ);
   }

   private static final class WarpedPointCache {
      private static final int SIZE = 256;
      private final int[] x = new int[SIZE];
      private final int[] z = new int[SIZE];
      private final long[] point = new long[SIZE];
      private long transformVersion = Long.MIN_VALUE;
      private int strength = Integer.MIN_VALUE;

      private WarpedPointCache() {
         java.util.Arrays.fill(this.x, Integer.MIN_VALUE);
         java.util.Arrays.fill(this.z, Integer.MIN_VALUE);
      }

      void reset(long transformVersion, int strength) {
         this.transformVersion = transformVersion;
         this.strength = strength;
         java.util.Arrays.fill(this.x, Integer.MIN_VALUE);
         java.util.Arrays.fill(this.z, Integer.MIN_VALUE);
      }

      static int slot(int blockX, int blockZ) {
         int hash = blockX * 0x9E3779B9 ^ Integer.rotateLeft(blockZ * 0x85EBCA6B, 16);
         return hash & (SIZE - 1);
      }
   }

   private static final class TerrainScalarCache {
      private static final int SIZE = 256;
      private final long[] point = new long[SIZE];
      private final double[] value = new double[SIZE];
      private final boolean[] present = new boolean[SIZE];
      private long transformVersion = Long.MIN_VALUE;

      boolean matches(long transformVersion, long point) {
         if (this.transformVersion != transformVersion) {
            this.transformVersion = transformVersion;
            java.util.Arrays.fill(this.present, false);
            return false;
         }
         int slot = slot(point);
         return this.present[slot] && this.point[slot] == point;
      }

      double value(long point) {
         return this.value[slot(point)];
      }

      void put(long transformVersion, long point, double value) {
         if (this.transformVersion != transformVersion) {
            this.transformVersion = transformVersion;
            java.util.Arrays.fill(this.present, false);
         }
         int slot = slot(point);
         this.point[slot] = point;
         this.value[slot] = value;
         this.present[slot] = true;
      }

      private static int slot(long point) {
         long mixed = point ^ point >>> 33;
         mixed *= 0xff51afd7ed558ccdL;
         return (int)mixed & (SIZE - 1);
      }
   }

   private static double smoothNoise(int blockX, int blockZ, int cellSize, long salt) {
      int cellX = Math.floorDiv(blockX, cellSize);
      int cellZ = Math.floorDiv(blockZ, cellSize);
      double tx = (double)Math.floorMod(blockX, cellSize) / (double)cellSize;
      double tz = (double)Math.floorMod(blockZ, cellSize) / (double)cellSize;
      tx = tx * tx * (3.0 - 2.0 * tx);
      tz = tz * tz * (3.0 - 2.0 * tz);
      double top = lerp(noiseValue(cellX, cellZ, salt), noiseValue(cellX + 1, cellZ, salt), tx);
      double bottom = lerp(noiseValue(cellX, cellZ + 1, salt), noiseValue(cellX + 1, cellZ + 1, salt), tx);
      return lerp(top, bottom, tz);
   }

   private static double noiseValue(int x, int z, long salt) {
      long value = salt ^ (long)x * 341873128712L ^ (long)z * 132897987541L;
      value ^= value >>> 33;
      value *= 0xff51afd7ed558ccdL;
      value ^= value >>> 33;
      value *= 0xc4ceb9fe1a85ec53L;
      value ^= value >>> 33;
      return (double)(value >>> 11 & 2097151L) / 1048575.5 - 1.0;
   }

   private static long packPoint(int x, int z) {
      return (long)x << 32 | (long)z & 0xFFFFFFFFL;
   }

   private static int unpackX(long point) {
      return (int)(point >> 32);
   }

   private static int unpackZ(long point) {
      return (int)point;
   }

   private static byte[] smoothTerrainClasses(byte[] source, int width, int height) {
      byte[] result = source.clone();
      int[] counts = new int[ClimateLayers.TerrainKind.values().length];

      for (int z = 1; z < height - 1; z++) {
         for (int x = 1; x < width - 1; x++) {
            java.util.Arrays.fill(counts, 0);
            int current = source[z * width + x] & 255;
            int currentKind = terrainKindForSmoothing(current);
            if (currentKind < 0) continue;

            for (int dz = -1; dz <= 1; dz++) {
               for (int dx = -1; dx <= 1; dx++) {
                  int kind = terrainKindForSmoothing(source[(z + dz) * width + x + dx] & 255);
                  if (kind >= 0) counts[kind]++;
               }
            }

            int winner = currentKind;
            for (int kind = 0; kind < counts.length; kind++) {
               if (counts[kind] > counts[winner]) winner = kind;
            }

            // Require a clear five-of-nine local majority and only replace a class
            // that has at most two supporting pixels. This prevents broad terrain
            // features from being eroded while removing scattered province fragments.
            if (winner != currentKind && counts[winner] >= 5 && counts[currentKind] <= 2) {
               result[z * width + x] = (byte)representativeTerrainCode(source, width, x, z, winner);
            }
         }
      }

      return result;
   }

   private static int terrainKindForSmoothing(int code) {
      if (code >= MOUNTAIN_LOW && code <= MOUNTAIN_ULTRA) return ClimateLayers.TerrainKind.MOUNTAIN.code;
      ClimateLayers.TerrainKind kind = ClimateLayers.TerrainKind.byCode(code);
      return kind == ClimateLayers.TerrainKind.CITY || kind == ClimateLayers.TerrainKind.SURROUNDING || kind == ClimateLayers.TerrainKind.WATER ? -1 : kind.code;
   }

   private static int representativeTerrainCode(byte[] source, int width, int x, int z, int kind) {
      if (kind != ClimateLayers.TerrainKind.MOUNTAIN.code) return kind;

      int[] mountainCounts = new int[4];
      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            int code = source[(z + dz) * width + x + dx] & 255;
            if (code >= MOUNTAIN_LOW && code <= MOUNTAIN_ULTRA) mountainCounts[code - MOUNTAIN_LOW]++;
         }
      }
      int best = 0;
      for (int index = 1; index < mountainCounts.length; index++) {
         if (mountainCounts[index] > mountainCounts[best]) best = index;
      }
      return MOUNTAIN_LOW + best;
   }

   private static byte[] removeSmallTerrainRegions(byte[] source, int width, int height, int minimumArea, boolean isolatedOnly) {
      if (minimumArea <= 1) return source;
      byte[] result = source.clone();
      boolean[] visited = new boolean[source.length];
      TerrainRegionQueue region = new TerrainRegionQueue();
      int[] neighbours = new int[ClimateLayers.TerrainKind.values().length];

      for (int start = 0; start < source.length; start++) {
         if (visited[start]) continue;
         int terrain = terrainKindForSmoothing(source[start] & 255);
         if (terrain < 0) {
            visited[start] = true;
            continue;
         }

         region.clear();
         region.add(start);
         visited[start] = true;
         java.util.Arrays.fill(neighbours, 0);
         for (int cursor = 0; cursor < region.size(); cursor++) {
            int index = region.get(cursor);
            int x = index % width;
            int z = index / width;
            if (x > 0) collectTerrainNeighbour(source, visited, region, index - 1, terrain, neighbours);
            if (x + 1 < width) collectTerrainNeighbour(source, visited, region, index + 1, terrain, neighbours);
            if (z > 0) collectTerrainNeighbour(source, visited, region, index - width, terrain, neighbours);
            if (z + 1 < height) collectTerrainNeighbour(source, visited, region, index + width, terrain, neighbours);
         }

         int surrounding = -1;
         int surroundingKinds = 0;
         for (int kind = 0; kind < neighbours.length; kind++) {
            if (neighbours[kind] > 0) {
               surroundingKinds++;
               if (surrounding < 0 || neighbours[kind] > neighbours[surrounding]) surrounding = kind;
            }
         }
         if (region.size() < minimumArea && surrounding >= 0 && (!isolatedOnly || surroundingKinds == 1)) {
            for (int cursor = 0; cursor < region.size(); cursor++) result[region.get(cursor)] = (byte)surrounding;
         }
      }
      return result;
   }

   private static void collectTerrainNeighbour(byte[] source, boolean[] visited, TerrainRegionQueue region, int index, int terrain, int[] neighbours) {
      int neighbour = terrainKindForSmoothing(source[index] & 255);
      if (neighbour < 0) return;
      if (neighbour != terrain) {
         neighbours[neighbour]++;
      } else if (!visited[index]) {
         visited[index] = true;
         region.add(index);
      }
   }

   private static byte[] removeMountainDesertPockets(byte[] source, int width, int height, int maximumArea) {
      byte[] result = source.clone();
      boolean[] visited = new boolean[source.length];
      TerrainRegionQueue region = new TerrainRegionQueue();
      for (int start = 0; start < source.length; start++) {
         if (visited[start] || terrainKindForSmoothing(source[start] & 255) != TerrainKind.DESERT.code) continue;
         region.clear();
         region.add(start);
         visited[start] = true;
         int mountainEdges = 0;
         int otherEdges = 0;
         for (int cursor = 0; cursor < region.size(); cursor++) {
            int index = region.get(cursor);
            int x = index % width;
            int z = index / width;
            for (int direction = 0; direction < 4; direction++) {
               int neighbour = switch (direction) {
                  case 0 -> x > 0 ? index - 1 : -1;
                  case 1 -> x + 1 < width ? index + 1 : -1;
                  case 2 -> z > 0 ? index - width : -1;
                  default -> z + 1 < height ? index + width : -1;
               };
               if (neighbour < 0) {
                  otherEdges++;
                  continue;
               }
               int kind = terrainKindForSmoothing(source[neighbour] & 255);
               if (kind == TerrainKind.DESERT.code) {
                  if (!visited[neighbour]) {
                     visited[neighbour] = true;
                     region.add(neighbour);
                  }
               } else if (kind == TerrainKind.MOUNTAIN.code) {
                  mountainEdges++;
               } else {
                  otherEdges++;
               }
            }
         }
         if (region.size() <= maximumArea && mountainEdges > 0 && otherEdges == 0) {
            for (int cursor = 0; cursor < region.size(); cursor++) result[region.get(cursor)] = (byte)MOUNTAIN_LOW;
         }
      }
      return result;
   }

   /**
    * Builds mountain relief from an interior chamfer distance transform. Each
    * connected region is normalized by its own deepest interior distance, so
    * round peaks and long ridge lines rise along their medial axis instead of
    * turning the whole source mask into a blurred plateau.
    */
   private static byte[] distanceMountainRelief(byte[] terrain, byte[] scales, int width, int height) {
      byte[] distance = mountainInteriorDistance(terrain, width, height);
      byte[] relief = new byte[terrain.length];
      boolean[] visited = new boolean[terrain.length];
      TerrainRegionQueue region = new TerrainRegionQueue();
      int blocksPerPixel = RiversMask.INSTANCE.blocksPerPixel();
      int fullHeightSpan = (Integer)EarthShapeServerConfig.MOUNTAIN_REGION_FULL_HEIGHT_SPAN_BLOCKS.get();
      double minimumSpan = 80.0;

      for (int index = 0; index < terrain.length; index++) {
         if (terrainKindForSmoothing(terrain[index] & 255) == TerrainKind.HILLS.code) {
            relief[index] = (byte)128;
         }
      }

      for (int start = 0; start < terrain.length; start++) {
         if (visited[start] || !isMountainElevationCode(terrain[start] & 255)) continue;
         region.clear();
         region.add(start);
         visited[start] = true;
         int maximumDistance = distance[start] & 255;
         for (int cursor = 0; cursor < region.size(); cursor++) {
            int index = region.get(cursor);
            maximumDistance = Math.max(maximumDistance, distance[index] & 255);
            int x = index % width;
            int z = index / width;
            collectMountainNeighbour(terrain, visited, region, x > 0 ? index - 1 : -1);
            collectMountainNeighbour(terrain, visited, region, x + 1 < width ? index + 1 : -1);
            collectMountainNeighbour(terrain, visited, region, z > 0 ? index - width : -1);
            collectMountainNeighbour(terrain, visited, region, z + 1 < height ? index + width : -1);
         }

         // sqrt(area) is the representative source width. Converting it to blocks
         // makes the same bitmap region eligible for more height at larger map scales.
         double spanBlocks = Math.sqrt((double)region.size()) * (double)blocksPerPixel;
         double t = (spanBlocks - minimumSpan) / Math.max(1.0, (double)fullHeightSpan - minimumSpan);
         t = Math.max(0.0, Math.min(1.0, t));
         double scale = t * t * (3.0 - 2.0 * t);
         byte encoded = (byte)Math.round(scale * 255.0);
         int radius = Math.max(0, maximumDistance - 3);
         double sigma = Math.max(1.0, (double)radius * 0.42);
         double edgeValue = radius > 0
            ? Math.exp(-0.5 * ((double)radius / sigma) * ((double)radius / sigma))
            : 0.0;
         // Regions below the biome threshold still disappear continuously, but
         // once a region is large enough to remain a MOUNTAIN biome it must also
         // carry enough physical relief to rise above its surroundings. Using
         // maximumRelief=scale allowed scale~=0.10 regions to keep the mountain
         // biome while contributing almost no E/PV terrain signal.
         double maximumRelief = scale < MINIMUM_MOUNTAIN_REGION_SCALE
            ? 0.0
            : 0.45 + 0.55 * scale;
         for (int cursor = 0; cursor < region.size(); cursor++) {
            int index = region.get(cursor);
            scales[index] = encoded;
            int interiorDistance = Math.max(0, (distance[index] & 255) - 3);
            double peakFactor;
            if (radius == 0) {
               peakFactor = 1.0;
            } else {
               double distanceFromRidge = Math.max(0.0, (double)radius - (double)interiorDistance);
               double normalized = distanceFromRidge / sigma;
               double gaussian = Math.exp(-0.5 * normalized * normalized);
               peakFactor = (gaussian - edgeValue) / Math.max(1.0E-9, 1.0 - edgeValue);
               peakFactor = Math.max(0.0, Math.min(1.0, peakFactor));
            }
            double elevationStrength = switch (terrain[index] & 255) {
               case MOUNTAIN_LOW -> 0.70;
               case MOUNTAIN_MID -> 0.82;
               case MOUNTAIN_HIGH -> 0.92;
               case MOUNTAIN_ULTRA -> 1.00;
               default -> 0.70;
            };
            relief[index] = (byte)Math.round(peakFactor * maximumRelief * elevationStrength * 255.0);
         }
      }
      return relief;
   }

   private static byte[] terrainClassBlend(
      byte[] terrain, int width, int height, ClimateLayers.TerrainKind target
   ) {
      byte[] values = new byte[terrain.length];
      for (int index = 0; index < terrain.length; index++) {
         if (terrainKindForSmoothing(terrain[index] & 255) == target.code) values[index] = (byte)255;
      }
      // Three radius-two passes spread a categorical correction over roughly
      // six source cells. At the default scale this is far wider than a single
      // terrain column and keeps ordinary relief changes below a cliff step.
      for (int pass = 0; pass < 3; pass++) values = boxBlur(values, width, height, 2);
      return values;
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

   private static void collectMountainNeighbour(
      byte[] terrain, boolean[] visited, TerrainRegionQueue region, int index
   ) {
      if (index >= 0 && !visited[index] && isMountainElevationCode(terrain[index] & 255)) {
         visited[index] = true;
         region.add(index);
      }
   }

   private static byte[] mountainInteriorDistance(byte[] terrain, int width, int height) {
      byte[] distance = new byte[terrain.length];
      for (int index = 0; index < terrain.length; index++) {
         if (isMountainElevationCode(terrain[index] & 255)) distance[index] = (byte)255;
      }

      for (int z = 0; z < height; z++) {
         for (int x = 0; x < width; x++) {
            int index = z * width + x;
            if (!isMountainElevationCode(terrain[index] & 255)) continue;
            int best = distance[index] & 255;
            if (x > 0) best = Math.min(best, saturatedDistance(distance[index - 1], 3));
            if (z > 0) {
               best = Math.min(best, saturatedDistance(distance[index - width], 3));
               if (x > 0) best = Math.min(best, saturatedDistance(distance[index - width - 1], 4));
               if (x + 1 < width) best = Math.min(best, saturatedDistance(distance[index - width + 1], 4));
            }
            distance[index] = (byte)best;
         }
      }

      for (int z = height - 1; z >= 0; z--) {
         for (int x = width - 1; x >= 0; x--) {
            int index = z * width + x;
            if (!isMountainElevationCode(terrain[index] & 255)) continue;
            int best = distance[index] & 255;
            if (x + 1 < width) best = Math.min(best, saturatedDistance(distance[index + 1], 3));
            if (z + 1 < height) {
               best = Math.min(best, saturatedDistance(distance[index + width], 3));
               if (x > 0) best = Math.min(best, saturatedDistance(distance[index + width - 1], 4));
               if (x + 1 < width) best = Math.min(best, saturatedDistance(distance[index + width + 1], 4));
            }
            distance[index] = (byte)best;
         }
      }
      return distance;
   }

   private static int saturatedDistance(byte encoded, int cost) {
      return Math.min(255, (encoded & 255) + cost);
   }

   private static final class TerrainRegionQueue {
      private int[] values = new int[64];
      private int size;

      void clear() { this.size = 0; }
      int size() { return this.size; }
      int get(int index) { return this.values[index]; }

      void add(int value) {
         if (this.size == this.values.length) this.values = java.util.Arrays.copyOf(this.values, this.values.length * 2);
         this.values[this.size++] = value;
      }
   }

   private static double latitudeTemperature(int blockZ) {
      double imageZ = RiversMask.INSTANCE.mapImageZ(blockZ);
      double latitude = Math.abs(imageZ / Math.max(1.0, (double)RiversMask.INSTANCE.height() - 1.0) * 2.0 - 1.0);
      return 0.55 - 1.35 * latitude * latitude;
   }

   private static record Data(
      int width,
      int height,
      byte[] values,
      byte[] coverage,
      byte[] relief,
      byte[] mountainScale,
      byte[] desertBlend
   ) {
      double value(int x, int z) {
         return (double)(this.values[z * this.width + x] & 255) / 255.0;
      }

      double coverage(int x, int z) {
         return (double)(this.coverage[z * this.width + x] & 255) / 255.0;
      }
   }

   private static enum Kind {
      LUMINANCE {
         @Override
         int value(int c) {
            return ((c >>> 16 & 0xFF) * 30 + (c >>> 8 & 0xFF) * 59 + (c & 0xFF) * 11) / 100;
         }
      },
      TEMPERATURE {
         @Override
         int value(int c) {
            int band = ClimateLayers.Kind.temperatureBand(c);
            return band < 0 ? 127 : band * 255 / 8;
         }

         @Override
         int coverage(int c) {
            return ClimateLayers.Kind.temperatureBand(c) < 0 ? 0 : 255;
         }
      },
      VEGETATION {
         @Override
         int value(int c) {
            return ClimateLayers.Kind.treeCover(c);
         }
      },
      TERRAIN_CLASS {
         @Override
         int value(int c) {
            return terrainElevationCode(c);
         }
      },
      NORMAL {
         @Override
         int value(int c) {
            double x = ((double)(c >>> 16 & 0xFF) - 127.5) / 127.5;
            double z = ((double)(c >>> 8 & 0xFF) - 127.5) / 127.5;
            return (int)(255.0 * Math.min(1.0, Math.sqrt(x * x + z * z)));
         }
      };

      abstract int value(int var1);

      int coverage(int color) {
         return 255;
      }

      private static int temperatureBand(int color) {
         int r = color >>> 16 & 0xFF;
         int g = color >>> 8 & 0xFF;
         int b = color & 0xFF;
         if (r > 244 && g > 244 && b > 244) {
            return -1;
         } else {
            switch (color & 16777215) {
               case 129:
               case 9787603:
                  return 0;
               case 190:
               case 10341200:
                  return 3;
               case 33470:
               case 16491568:
                  return 5;
               case 4694770:
                  return 1;
               case 4772041:
                  return 2;
               case 14954539:
                  return 8;
               case 16373540:
                  return 4;
               case 16540464:
                  return 6;
               default:
                  int[] palette = new int[]{9787603, 4694770, 4772041, 10341200, 16373540, 16491568, 16540464, 14954539, 129, 190, 33470};
                  int[] bands = new int[]{0, 1, 2, 3, 4, 5, 6, 8, 0, 3, 5};
                  int best = -1;
                  int distance = Integer.MAX_VALUE;

                  for (int i = 0; i < palette.length; i++) {
                     int pr = palette[i] >>> 16 & 0xFF;
                     int pg = palette[i] >>> 8 & 0xFF;
                     int pb = palette[i] & 0xFF;
                     int d = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb);
                     if (d < distance) {
                        distance = d;
                        best = i;
                     }
                  }

                  return distance <= 24000 ? bands[best] : -1;
            }
         }
      }

      private static int treeCover(int color) {
         int rgb = color & 16777215;
         switch (rgb) {
            case 0:
               return 0;
            case 3110936:
               return 190;
            case 5020723:
               return 210;
            case 5767306:
            case 9830655:
               return 255;
            case 16776960:
               return 80;
            default:
               int[] palette = new int[]{0, 3110936, 5020723, 9830655, 5767306, 16776960};
               int[] values = new int[]{0, 190, 210, 255, 255, 80};
               int best = 0;
               int distance = Integer.MAX_VALUE;

               for (int i = 0; i < palette.length; i++) {
                  int d = colourDistance(rgb, palette[i]);
                  if (d < distance) {
                     distance = d;
                     best = i;
                  }
               }

               return values[best];
         }
      }

      private static int terrainElevationCode(int color) {
         int rgb = color & 16777215;
         // Guide C: white is the only ultra-high class; the two brown/grey shades
         // are high and mid-high terrain. #493B0D is accepted alongside the source
         // palette's #493B0F so its near-identical BMP colour cannot become a stray
         // low-altitude snowy peak through nearest-colour fallback.
         return switch (rgb) {
            case 16777215 -> MOUNTAIN_ULTRA;
            case 6050636 -> MOUNTAIN_HIGH;
            case 7359007 -> MOUNTAIN_MID;
            case 4799245, 4799247 -> MOUNTAIN_LOW;
            default -> ClimateLayers.TerrainKind.fromColor(color).code;
         };
      }

      private static int colourDistance(int first, int second) {
         int dr = (first >>> 16 & 0xFF) - (second >>> 16 & 0xFF);
         int dg = (first >>> 8 & 0xFF) - (second >>> 8 & 0xFF);
         int db = (first & 0xFF) - (second & 0xFF);
         return dr * dr * 2 + dg * dg * 4 + db * db;
      }
   }

   private static record TemperatureSample(double value, double coverage) {
   }

   public static enum TerrainKind {
      WATER(0),
      DESERT(1),
      WETLAND(2),
      FOREST(3),
      JUNGLE(4),
      PLAINS(5),
      HILLS(6),
      MOUNTAIN(7),
      CITY(8),
      SURROUNDING(9);

      private final int code;

      private TerrainKind(int code) {
         this.code = code;
      }

      static ClimateLayers.TerrainKind byCode(int code) {
         for (ClimateLayers.TerrainKind kind : values()) {
            if (kind.code == code) {
               return kind;
            }
         }

         return PLAINS;
      }

      static ClimateLayers.TerrainKind fromColor(int color) {
         int rgb = color & 16777215;
         switch (rgb) {
            case 21074:
               return JUNGLE;
            case 22022:
            case 444427:
            case 3834706:
               return FOREST;
            case 532354:
               return WATER;
            case 4799247:
            case 6050636:
            case 11403519:
               return MOUNTAIN;
            case 4953006:
               return WETLAND;
            case 5667867:
            case 8716032:
               return PLAINS;
            case 7359007:
            case 7506281:
            case 8803358:
               return HILLS;
            case 13543779:
            case 16580352:
               return DESERT;
            case 15793920:
               return SURROUNDING;
            case 16711704:
            case 16711807:
            case 16711920:
            case 16777215:
               return CITY;
            default:
               ClimateLayers.TerrainKind[] kinds = new ClimateLayers.TerrainKind[]{
                  JUNGLE,
                  FOREST,
                  FOREST,
                  FOREST,
                  WATER,
                  MOUNTAIN,
                  MOUNTAIN,
                  MOUNTAIN,
                  WETLAND,
                  PLAINS,
                  PLAINS,
                  HILLS,
                  HILLS,
                  HILLS,
                  DESERT,
                  DESERT,
                  SURROUNDING,
                  CITY,
                  CITY,
                  CITY,
                  CITY
               };
               int[] palette = new int[]{
                  21074,
                  22022,
                  444427,
                  3834706,
                  532354,
                  4799247,
                  6050636,
                  11403519,
                  4953006,
                  5667867,
                  8716032,
                  7359007,
                  7506281,
                  8803358,
                  13543779,
                  16580352,
                  15793920,
                  16711704,
                  16711807,
                  16711920,
                  16777215
               };
               ClimateLayers.TerrainKind nearest = PLAINS;
               int distance = Integer.MAX_VALUE;

               for (int i = 0; i < palette.length; i++) {
                  int d = ClimateLayers.Kind.colourDistance(rgb, palette[i]);
                  if (d < distance) {
                     distance = d;
                     nearest = kinds[i];
                  }
               }

               return nearest;
         }
      }
   }

   public static enum TreeCover {
      NONE,
      TEMPERATE,
      TROPICAL;
   }
}
