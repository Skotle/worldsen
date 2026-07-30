package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.BitSet;
import javax.imageio.ImageIO;

public final class RiversMask {
   public static final RiversMask INSTANCE = new RiversMask();
   public static final int DEFAULT_BLOCKS_PER_PIXEL = 20;
   private static final int RIVER_SEARCH_RADIUS = 4;
   private static final double RIVER_CORNER_TRIM = 0.32;
   private volatile RiversMask.Data data;
   private final ThreadLocal<RiversMask.RiverWidthCache> riverWidthCache = ThreadLocal.withInitial(RiversMask.RiverWidthCache::new);
   private final ThreadLocal<RiversMask.RiverDistanceCache> riverDistanceCache = ThreadLocal.withInitial(RiversMask.RiverDistanceCache::new);

   private RiversMask() {
   }

   public double sampleLand(int blockX, int blockZ) {
      return this.sampleLand(this.data(), blockX, blockZ);
   }

   public double sampleLayerLand(int blockX, int blockZ) {
      // All coastline consumers must use the same sub-pixel field.  Sampling the
      // raw cell here expanded every source pixel into a block-aligned rectangle,
      // especially visible on small islands. Bilinear coverage keeps the original
      // map outline while moving the 0.5 shoreline through a continuous contour.
      return this.sampleLand(this.data(), blockX, blockZ);
   }

   /**
    * A pre-smoothed land mask used only for the continentalness transition.
    * The exact mask above remains authoritative for coastline and biome choice.
    */
   public double sampleCoastalLandness(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      return this.sampleBytes(loaded, loaded.coastalLandness, blockX, blockZ);
   }

   private double sampleLand(RiversMask.Data loaded, int blockX, int blockZ) {
      return this.sampleBytes(loaded, null, blockX, blockZ);
   }

   private double sampleBytes(RiversMask.Data loaded, byte[] values, int blockX, int blockZ) {
      int blocksPerPixel = this.blocksPerPixel();
      double imageX = (double)blockX / (double)blocksPerPixel + (double)loaded.width * 0.5;
      double imageZ = (double)blockZ / (double)blocksPerPixel + (double)loaded.height * 0.5;
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
      int blocksPerPixel = this.blocksPerPixel();
      double imageX = (double)blockX / (double)blocksPerPixel + (double)loaded.width * 0.5;
      double imageZ = (double)blockZ / (double)blocksPerPixel + (double)loaded.height * 0.5;
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
      return (Integer)EarthShapeServerConfig.BLOCKS_PER_PIXEL.get();
   }

   public int width() {
      return this.data().width;
   }

   public int height() {
      return this.data().height;
   }

   public double legacyImageX(int blockX, int legacyWidth) {
      RiversMask.Data loaded = this.data();
      return (double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5 - (double)(loaded.width - legacyWidth) * 0.5;
   }

   public double legacyImageZ(int blockZ, int legacyHeight) {
      RiversMask.Data loaded = this.data();
      return (double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5 - (double)(loaded.height - legacyHeight) * 0.5;
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
      return this.isRiverCentreline(blockX, blockZ) && this.hasInlandRiverInfluence(blockX, blockZ);
   }

   public boolean isRiverMouth(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      int x = (int)Math.floor((double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5);
      int z = (int)Math.floor((double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5);
      return x >= 0
         && z >= 0
         && x < loaded.width
         && z < loaded.height
         && loaded.riverMouths.get(z * loaded.width + x)
         && this.isRiverCentreline(blockX, blockZ);
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
      int x = (int)Math.floor((double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5);
      int z = (int)Math.floor((double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5);
      return x >= 0
         && z >= 0
         && x < loaded.width
         && z < loaded.height
         && loaded.land.get(z * loaded.width + x)
         && loaded.riverInfluence.get(z * loaded.width + x);
   }

   public int riverWidthBlocks(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      RiversMask.RiverWidthCache cache = this.riverWidthCache.get();
      if (cache.data == loaded && cache.blockX == blockX && cache.blockZ == blockZ) {
         return cache.width;
      } else {
         double imageX = (double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5;
         double imageZ = (double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5;
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

            for (int z = centreZ - 4; z <= centreZ + 4; z++) {
               for (int x = centreX - 4; x <= centreX + 4; x++) {
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
      double imageX = (double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5;
      double imageZ = (double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5;
      if (!(imageX < 1.0) && !(imageZ < 1.0) && !(imageX >= (double)loaded.width - 1.0) && !(imageZ >= (double)loaded.height - 1.0)) {
         int centreX = (int)Math.floor(imageX);
         int centreZ = (int)Math.floor(imageZ);
         if (!loaded.riverInfluence.get(centreZ * loaded.width + centreX)) {
            cache.set(loaded, blockX, blockZ, Double.POSITIVE_INFINITY);
            return Double.POSITIVE_INFINITY;
         } else {
            double best = Double.POSITIVE_INFINITY;

            for (int z = centreZ - 4; z <= centreZ + 4; z++) {
               for (int x = centreX - 4; x <= centreX + 4; x++) {
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

            bridgeSmallRiverGaps(width, height, rivers, riverWidths);
            stabilizeRiverWidths(width, height, rivers, riverWidths);
            restoreOnlyInlandRiverPixels(width, height, land, rivers);
            BitSet riverMouths = createRiverMouths(width, height, land, rivers);
            byte[] riverCorners = createRiverCornerMasks(width, height, rivers);
            BitSet riverInfluence = createRiverInfluence(width, height, land, rivers, riverWidths);
            int coastRadiusPixels = Math.max(2, Math.min(12, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get() / Math.max(1, (Integer)EarthShapeServerConfig.BLOCKS_PER_PIXEL.get() * 6)));
            byte[] coastalLandness = createCoastalLandness(width, height, land, coastRadiusPixels);
            EarthShape.LOGGER
               .info(
                  "[EarthShape] worldmap_river.png land/ocean and river mask loaded: {}x{} in {} ms.",
                  new Object[]{width, height, (System.nanoTime() - started) / 1000000L}
               );
            var21x = new RiversMask.Data(width, height, land, rivers, riverWidths, riverCorners, riverMouths, riverInfluence, coastalLandness);
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
      return Math.max(1, (int)Math.ceil((double)widthBlocks / (2.0 * (double)blocksPerPixel)) + 1);
   }

   private static BitSet createRiverMouths(int width, int height, BitSet land, BitSet rivers) {
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
                     if (!land.get(sample) && !rivers.get(sample)) {
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
      int maximumGap = Math.max(3, Math.min(4, (Integer)EarthShapeServerConfig.RIVER_GAP_BRIDGE_PIXELS.get()));
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
            riverWidths[index] = (byte)nearbyWidths[count / 2];
         }
      }
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
      if (red == 255 && green == 255 && blue == 255) {
         return true;
      } else if (red == 122 && green == 122 && blue == 122) {
         return false;
      } else {
         int toLand = (255 - red) * (255 - red) + (255 - green) * (255 - green) + (255 - blue) * (255 - blue);
         int toOcean = (122 - red) * (122 - red) + (122 - green) * (122 - green) + (122 - blue) * (122 - blue);
         return toLand <= toOcean;
      }
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
      byte[] riverWidths,
      byte[] riverCorners,
      BitSet riverMouths,
      BitSet riverInfluence,
      byte[] coastalLandness
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
}
