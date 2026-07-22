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

   private RiversMask() {
   }

   public double sampleLand(int blockX, int blockZ) {
      return this.sampleLand(this.data(), blockX, blockZ);
   }

   public double sampleLayerLand(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      int blocksPerPixel = this.blocksPerPixel();
      double imageX = (double)blockX / (double)blocksPerPixel + (double)loaded.width * 0.5;
      double imageZ = (double)blockZ / (double)blocksPerPixel + (double)loaded.height * 0.5;
      return !(imageX < 0.0) && !(imageZ < 0.0) && !(imageX >= (double)loaded.width) && !(imageZ >= (double)loaded.height)
         ? loaded.land((int)Math.floor(imageX), (int)Math.floor(imageZ))
         : 0.0;
   }

   public double sampleCoastLand(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      int radiusBlocks = Math.max(160, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get() / 2);
      int sampleStep = Math.max(1, radiusBlocks / 2);
      int sampleRadius = 2;
      double total = 0.0;
      double weight = 0.0;

      for (int dz = -sampleRadius; dz <= sampleRadius; dz++) {
         for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
            double distance = Math.sqrt((double)(dx * dx + dz * dz));
            if (!(distance > (double)sampleRadius)) {
               double sampleWeight = (double)sampleRadius + 1.0 - distance;
               total += this.sampleLand(loaded, blockX + dx * sampleStep, blockZ + dz * sampleStep) * sampleWeight;
               weight += sampleWeight;
            }
         }
      }

      return total / weight;
   }

   public double sampleHeightmapInlandness(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      int fadeBlocks = Math.max(320, (Integer)EarthShapeServerConfig.COAST_HEIGHT_FADE_BLOCKS.get());
      int x = (int)Math.floor((double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5);
      int z = (int)Math.floor((double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5);
      if (x >= 0 && z >= 0 && x < loaded.width && z < loaded.height) {
         double t = Math.min(1.0, (double)(loaded.coastDistance[z * loaded.width + x] & 255) * (double)this.blocksPerPixel() / (double)fadeBlocks);
         return t * t * (3.0 - 2.0 * t);
      } else {
         return 0.0;
      }
   }

   public double sampleRiverReliefFactor(int blockX, int blockZ) {
      if (!(Boolean)EarthShapeServerConfig.RIVER_BIOMES_ENABLED.get()) {
         return 1.0;
      } else if (!this.hasInlandRiverInfluence(blockX, blockZ)) {
         return 1.0;
      } else {
         double distance = this.riverCentrelineDistance(blockX, blockZ) * (double)this.blocksPerPixel();
         double riverRadius = (double)this.effectiveRiverWidthBlocks(blockX, blockZ) * 0.5;
         int fadeBlocks = Math.max(160, (Integer)EarthShapeServerConfig.RIVER_HEIGHT_FADE_BLOCKS.get());
         double t = Math.max(0.0, Math.min(1.0, (distance - riverRadius) / (double)fadeBlocks));
         return t * t * (3.0 - 2.0 * t);
      }
   }

   private double sampleLand(RiversMask.Data loaded, int blockX, int blockZ) {
      int blocksPerPixel = this.blocksPerPixel();
      double imageX = (double)blockX / (double)blocksPerPixel + (double)loaded.width * 0.5;
      double imageZ = (double)blockZ / (double)blocksPerPixel + (double)loaded.height * 0.5;
      if (!(imageX < 0.0) && !(imageZ < 0.0) && !(imageX >= (double)(loaded.width - 1)) && !(imageZ >= (double)(loaded.height - 1))) {
         int x = (int)Math.floor(imageX);
         int z = (int)Math.floor(imageZ);
         double tx = imageX - (double)x;
         double tz = imageZ - (double)z;
         double a = lerp(loaded.land(x, z), loaded.land(x + 1, z), tx);
         double b = lerp(loaded.land(x, z + 1), loaded.land(x + 1, z + 1), tx);
         return lerp(a, b, tz);
      } else {
         return 0.0;
      }
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
         double halfWidthPixels = (double)Math.max(4, widthBlocks) / (2.0 * (double)this.blocksPerPixel());
         return this.riverCentrelineDistance(blockX, blockZ) <= halfWidthPixels;
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
      // Biomes are sampled every four blocks.  A thinner source stroke becomes a single
      // biome sample and renders as a broken turquoise line instead of a watercourse.
      // Keep three samples across even when an older server config still requests less.
      return width > 0 ? Math.max(width, Math.max(12, (Integer)EarthShapeServerConfig.RIVER_MINIMUM_WIDTH_BLOCKS.get())) : 0;
   }

   public double riverCentrelineDistance(int blockX, int blockZ) {
      RiversMask.Data loaded = this.data();
      double imageX = (double)blockX / (double)this.blocksPerPixel() + (double)loaded.width * 0.5;
      double imageZ = (double)blockZ / (double)this.blocksPerPixel() + (double)loaded.height * 0.5;
      if (!(imageX < 1.0) && !(imageZ < 1.0) && !(imageX >= (double)loaded.width - 1.0) && !(imageZ >= (double)loaded.height - 1.0)) {
         int centreX = (int)Math.floor(imageX);
         int centreZ = (int)Math.floor(imageZ);
         if (!loaded.riverInfluence.get(centreZ * loaded.width + centreX)) {
            return Double.POSITIVE_INFINITY;
         } else {
            double best = Double.POSITIVE_INFINITY;

            for (int z = centreZ - 4; z <= centreZ + 4; z++) {
               for (int x = centreX - 4; x <= centreX + 4; x++) {
                  if (loaded.river(x, z)) {
                     int cornerMask = loaded.riverCornerMask(x, z);
                     if (cornerMask == 0) {
                        best = Math.min(best, Math.sqrt(distanceSquared(imageX, imageZ, (double)x + 0.5, (double)z + 0.5, (double)x + 0.5, (double)z + 0.5)));
                     } else {
                        best = Math.min(best, Math.sqrt(roundedCornerDistanceSquared(imageX, imageZ, x, z, cornerMask)));
                     }

                     for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                           if ((dx > 0 || dx == 0 && dz > 0) && loaded.river(x + dx, z + dz)) {
                              int neighbourCornerMask = loaded.riverCornerMask(x + dx, z + dz);
                              double startX = (double)x + 0.5 + ((cornerMask & neighbourBit(dx, dz)) != 0 ? (double)dx * 0.32 : 0.0);
                              double startZ = (double)z + 0.5 + ((cornerMask & neighbourBit(dx, dz)) != 0 ? (double)dz * 0.32 : 0.0);
                              double endX = (double)(x + dx) + 0.5 + ((neighbourCornerMask & neighbourBit(-dx, -dz)) != 0 ? (double)(-dx) * 0.32 : 0.0);
                              double endZ = (double)(z + dz) + 0.5 + ((neighbourCornerMask & neighbourBit(-dx, -dz)) != 0 ? (double)(-dz) * 0.32 : 0.0);
                              best = Math.min(best, Math.sqrt(distanceSquared(imageX, imageZ, startX, startZ, endX, endZ)));
                           }
                        }
                     }
                  }
               }
            }

            return best;
         }
      } else {
         return Double.POSITIVE_INFINITY;
      }
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

      for (int step = 1; step <= 5; step++) {
         double t = (double)step / 5.0;
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
            BitSet riverInfluence = createRiverInfluence(width, height, land, rivers);
            byte[] coastDistance = createCoastDistance(width, height, land, rivers);
            EarthShape.LOGGER
               .info(
                  "[EarthShape] worldmap_river.png land/ocean and river mask loaded: {}x{} in {} ms.",
                  new Object[]{width, height, (System.nanoTime() - started) / 1000000L}
               );
            var21x = new RiversMask.Data(width, height, land, rivers, riverWidths, riverCorners, riverMouths, riverInfluence, coastDistance);
         }

         return var21x;
      } catch (IOException var21) {
         throw new IllegalStateException("EarthShape could not load worldmap_river.png", var21);
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
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

         if (support >= 16) {
            land.set(index);
         }
      }
   }

   private static BitSet createRiverInfluence(int width, int height, BitSet land, BitSet rivers) {
      BitSet influence = new BitSet(width * height);

      for (int index = rivers.nextSetBit(0); index >= 0; index = rivers.nextSetBit(index + 1)) {
         if (land.get(index)) {
            int centreX = index % width;
            int centreZ = index / width;

            for (int dz = -4; dz <= 4; dz++) {
               for (int dx = -4; dx <= 4; dx++) {
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

   private static void bridgeSmallRiverGaps(int width, int height, BitSet rivers, byte[] riverWidths) {
      int maximumGap = Math.min(2, (Integer)EarthShapeServerConfig.RIVER_GAP_BRIDGE_PIXELS.get());
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
      byte[] coastDistance
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
}
