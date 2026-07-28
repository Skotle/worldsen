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
      double mapX = (double)x / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.width() * 0.5;
      double mapZ = (double)z / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5;
      return mapX >= 0.0 && mapZ >= 0.0 && mapX < (double)RiversMask.INSTANCE.width() && mapZ < (double)RiversMask.INSTANCE.height();
   }

   public double vegetation(int x, int z) {
      return sample(this.trees(), x, z) * 2.0 - 1.0;
   }

   public ClimateLayers.TreeCover treeCover(int x, int z) {
      ClimateLayers.Data layer = this.trees();
      double worldX = (double)x / (double)RiversMask.INSTANCE.blocksPerPixel() + 2816.0;
      double worldZ = (double)z / (double)RiversMask.INSTANCE.blocksPerPixel() + 1024.0;
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
      ClimateLayers.Data layer = this.terrain();
      if (!RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) {
         return ClimateLayers.TerrainKind.PLAINS;
      } else {
         int imageX = sourceX(layer, x);
         int imageZ = sourceZ(layer, z);
         int code = layer.values[imageZ * layer.width + imageX] & 255;
         ClimateLayers.TerrainKind kind = isMountainElevationCode(code) ? ClimateLayers.TerrainKind.MOUNTAIN : ClimateLayers.TerrainKind.byCode(code);
         return kind != ClimateLayers.TerrainKind.CITY && kind != ClimateLayers.TerrainKind.SURROUNDING ? kind : surroundingLandKind(layer, imageX, imageZ);
      }
   }

   /**
    * Elevation class encoded by terrain.bmp.  It is intentionally separate from the
    * biome family: a warm mountain remains stony/forested until its generated Y level
    * reaches the configured snow altitude.
    */
   public double mountainElevationWeight(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      if (!RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) return 0.0;
      int code = layer.values[sourceZ(layer, z) * layer.width + sourceX(layer, x)] & 255;
      return switch (code) {
         case MOUNTAIN_LOW -> 0.38;
         case MOUNTAIN_MID -> 0.58;
         case MOUNTAIN_HIGH -> 0.78;
         case MOUNTAIN_ULTRA -> 1.0;
         default -> 0.0;
      };
   }

   /**
    * Relative height budget for the connected mountain system containing this point.
    * Small isolated mountain patches deliberately never receive the same summit height
    * as a continent-scale range.
    */
   public double mountainRegionScale(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      if (layer.mountainRegionScale == null || !RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) return 0.0;
      int index = sourceZ(layer, z) * layer.width + sourceX(layer, x);
      return (double)(layer.mountainRegionScale[index] & 255) / 255.0;
   }

   /** White (#FFFFFF) is the sole ultra-high terrain colour. */
   public boolean isUltraMountain(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      return RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)
         && (layer.values[sourceZ(layer, z) * layer.width + sourceX(layer, x)] & 255) == MOUNTAIN_ULTRA;
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

   private static boolean isMountainElevationCode(int code) {
      return code >= MOUNTAIN_LOW && code <= MOUNTAIN_ULTRA;
   }

   public double desert(int x, int z) {
      return this.terrainKind(x, z) == ClimateLayers.TerrainKind.DESERT ? 1.0 : 0.0;
   }

   public double steepness(int x, int z) {
      return sample(this.normal(), x, z);
   }

   public boolean isMesaRegion(int blockX, int blockZ) {
      double u = ((double)blockX / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.width() * 0.5)
         / (double)RiversMask.INSTANCE.width();
      double v = ((double)blockZ / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5)
         / (double)RiversMask.INSTANCE.height();
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
      double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
      double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
      if (!(imageX < 0.0) && !(imageZ < 0.0) && !(imageX >= (double)layer.width - 1.0) && !(imageZ >= (double)layer.height - 1.0)) {
         int x = (int)imageX;
         int z = (int)imageZ;
         double tx = imageX - (double)x;
         double tz = imageZ - (double)z;
         return lerp(lerp(layer.value(x, z), layer.value(x + 1, z), tx), lerp(layer.value(x, z + 1), layer.value(x + 1, z + 1), tx), tz);
      } else {
         return 0.5;
      }
   }

   private static ClimateLayers.TemperatureSample sampleFullTemperature(ClimateLayers.Data layer, int blockX, int blockZ) {
      double worldX = (double)blockX / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.width() * 0.5;
      double worldZ = (double)blockZ / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5;
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

   private static int sourceX(ClimateLayers.Data layer, int blockX) {
      double imageX = RiversMask.INSTANCE.legacyImageX(blockX, layer.width);
      return Math.max(0, Math.min(layer.width - 1, (int)imageX));
   }

   private static int sourceZ(ClimateLayers.Data layer, int blockZ) {
      double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, layer.height);
      return Math.max(0, Math.min(layer.height - 1, (int)imageZ));
   }

   private static ClimateLayers.TerrainKind surroundingLandKind(ClimateLayers.Data layer, int centreX, int centreZ) {
      for (int radius = 1; radius <= 48; radius++) {
         int[] counts = new int[ClimateLayers.TerrainKind.values().length];

         for (int z = Math.max(0, centreZ - radius); z <= Math.min(layer.height - 1, centreZ + radius); z++) {
            for (int x = Math.max(0, centreX - radius); x <= Math.min(layer.width - 1, centreX + radius); x++) {
               if (Math.max(Math.abs(x - centreX), Math.abs(z - centreZ)) == radius) {
                  ClimateLayers.TerrainKind kind = ClimateLayers.TerrainKind.byCode(layer.values[z * layer.width + x] & 255);
                  if (kind != ClimateLayers.TerrainKind.CITY
                     && kind != ClimateLayers.TerrainKind.SURROUNDING
                     && kind != ClimateLayers.TerrainKind.WATER
                     && kind != ClimateLayers.TerrainKind.DESERT) {
                     counts[kind.code]++;
                  }
               }
            }
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
            byte[] mountainRegionScale = null;
            if (kind == ClimateLayers.Kind.TERRAIN_CLASS) {
               values = smoothTerrainClasses(values, width, height);
               values = smoothTerrainClasses(values, width, height);
               values = removeSmallTerrainRegions(
                  values, width, height, (Integer)EarthShapeServerConfig.TERRAIN_BIOME_ISOLATED_MINIMUM_REGION_PIXELS.get(), true
               );
               values = removeSmallTerrainRegions(
                  values, width, height, (Integer)EarthShapeServerConfig.TERRAIN_BIOME_MINIMUM_REGION_PIXELS.get(), false
               );
               // A few source-map desert pixels occur inside otherwise continuous
               // rocky ranges.  Leaving them intact makes vanilla surface rules put
               // isolated sand flecks between stone peaks.  Merge only pockets fully
               // enclosed by mountains; real desert-to-mountain borders remain intact.
               values = removeMountainDesertPockets(values, width, height, 96);
               mountainRegionScale = mountainRegionScales(values, width, height);
            }

            EarthShape.LOGGER.info("[EarthShape] {} climate layer loaded: {}x{}.", new Object[]{name, width, height});
            var14x = new ClimateLayers.Data(width, height, values, coverage, mountainRegionScale);
         }

         return var14x;
      } catch (IOException var14) {
         throw new IllegalStateException("EarthShape could not load " + name, var14);
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
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

   private static byte[] mountainRegionScales(byte[] source, int width, int height) {
      byte[] scales = new byte[source.length];
      boolean[] visited = new boolean[source.length];
      TerrainRegionQueue region = new TerrainRegionQueue();

      for (int start = 0; start < source.length; start++) {
         if (visited[start] || !isMountainElevationCode(source[start] & 255)) continue;
         region.clear();
         region.add(start);
         visited[start] = true;
         for (int cursor = 0; cursor < region.size(); cursor++) {
            int index = region.get(cursor);
            int x = index % width;
            int z = index / width;
            collectMountainNeighbour(source, visited, region, x > 0 ? index - 1 : -1);
            collectMountainNeighbour(source, visited, region, x + 1 < width ? index + 1 : -1);
            collectMountainNeighbour(source, visited, region, z > 0 ? index - width : -1);
            collectMountainNeighbour(source, visited, region, z + 1 < height ? index + width : -1);
         }
         // The square-root response keeps medium ranges distinct while preventing a
         // huge continental range from producing a disproportionately taller peak.
         double size = Math.sqrt((double)region.size()) / 24.0;
         double t = Math.max(0.0, Math.min(1.0, size));
         double scale = 0.35 + 0.65 * (t * t * (3.0 - 2.0 * t));
         byte encoded = (byte)Math.round(scale * 255.0);
         for (int cursor = 0; cursor < region.size(); cursor++) scales[region.get(cursor)] = encoded;
      }
      return scales;
   }

   private static void collectMountainNeighbour(byte[] source, boolean[] visited, TerrainRegionQueue region, int index) {
      if (index >= 0 && !visited[index] && isMountainElevationCode(source[index] & 255)) {
         visited[index] = true;
         region.add(index);
      }
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
      double imageZ = (double)blockZ / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5;
      double latitude = Math.abs(imageZ / Math.max(1.0, (double)RiversMask.INSTANCE.height() - 1.0) * 2.0 - 1.0);
      return 0.55 - 1.35 * latitude * latitude;
   }

   private static record Data(int width, int height, byte[] values, byte[] coverage, byte[] mountainRegionScale) {
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
