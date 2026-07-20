package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class ClimateLayers {
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

   public ClimateLayers.TerrainKind terrainKind(int x, int z) {
      ClimateLayers.Data layer = this.terrain();
      if (!RiversMask.INSTANCE.isInsideLegacyLayer(x, z, layer.width, layer.height)) {
         return ClimateLayers.TerrainKind.PLAINS;
      } else {
         int imageX = sourceX(layer, x);
         int imageZ = sourceZ(layer, z);
         ClimateLayers.TerrainKind kind = ClimateLayers.TerrainKind.byCode(layer.values[imageZ * layer.width + imageX] & 255);
         return kind != ClimateLayers.TerrainKind.CITY && kind != ClimateLayers.TerrainKind.SURROUNDING ? kind : surroundingLandKind(layer, imageX, imageZ);
      }
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
         ClimateLayers.Data var14;
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

            EarthShape.LOGGER.info("[EarthShape] {} climate layer loaded: {}x{}.", new Object[]{name, width, height});
            var14 = new ClimateLayers.Data(width, height, values, coverage);
         }

         return var14;
      } catch (IOException var13) {
         throw new IllegalStateException("EarthShape could not load " + name, var13);
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static double latitudeTemperature(int blockZ) {
      double imageZ = (double)blockZ / (double)RiversMask.INSTANCE.blocksPerPixel() + (double)RiversMask.INSTANCE.height() * 0.5;
      double latitude = Math.abs(imageZ / Math.max(1.0, (double)RiversMask.INSTANCE.height() - 1.0) * 2.0 - 1.0);
      return 0.55 - 1.35 * latitude * latitude;
   }

   private static record Data(int width, int height, byte[] values, byte[] coverage) {
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
            int r = c >>> 16 & 0xFF;
            int g = c >>> 8 & 0xFF;
            int b = c & 0xFF;
            return r < 8 && g < 8 && b < 8 ? 128 : 218;
         }
      },
      TERRAIN_CLASS {
         @Override
         int value(int c) {
            return ClimateLayers.TerrainKind.fromColor(c).code;
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
         int[] palette = new int[]{9787603, 4694770, 4772041, 10341200, 16373540, 16491568, 16540464, 14954539};
         int[] bands = new int[]{0, 1, 2, 3, 4, 5, 6, 8};
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

         return distance <= 7000 ? bands[best] : -1;
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
         return switch (color & 16777215) {
            case 21074 -> JUNGLE;
            case 22022, 444427, 3834706 -> FOREST;
            case 532354 -> WATER;
            case 4799247, 6050636, 11403519 -> MOUNTAIN;
            case 4953006 -> WETLAND;
            case 5667867, 8716032 -> PLAINS;
            case 7359007, 7506281, 8803358 -> HILLS;
            case 13543779, 16580352 -> DESERT;
            case 15793920 -> SURROUNDING;
            case 16711704, 16711807, 16711920, 16777215 -> CITY;
            default -> PLAINS;
         };
      }
   }
}
