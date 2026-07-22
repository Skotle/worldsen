package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class HeightmapLayer {
   public static final HeightmapLayer INSTANCE = new HeightmapLayer();
   private volatile HeightmapLayer.Data data;

   private HeightmapLayer() {
   }

   public double sample(int blockX, int blockZ) {
      HeightmapLayer.Data loaded = this.data();
      double imageX = RiversMask.INSTANCE.legacyImageX(blockX, loaded.width);
      double imageZ = RiversMask.INSTANCE.legacyImageZ(blockZ, loaded.height);
      if (!(imageX < 0.0) && !(imageZ < 0.0) && !(imageX >= (double)(loaded.width - 1)) && !(imageZ >= (double)(loaded.height - 1))) {
         int x = (int)Math.floor(imageX);
         int z = (int)Math.floor(imageZ);
         double tx = imageX - (double)x;
         double tz = imageZ - (double)z;
         double a = lerp(loaded.value(x, z), loaded.value(x + 1, z), tx);
         double b = lerp(loaded.value(x, z + 1), loaded.value(x + 1, z + 1), tx);
         return lerp(a, b, tz);
      } else {
         return 0.32;
      }
   }

   private HeightmapLayer.Data data() {
      HeightmapLayer.Data current = this.data;
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

   private static HeightmapLayer.Data load() {
      long started = System.nanoTime();

      try {
         HeightmapLayer.Data var15x;
         try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/heightmap.bmp")) {
            if (input == null) {
               throw new IOException("missing /earthshape/hoi4/heightmap.bmp");
            }

            BufferedImage image = ImageIO.read(input);
            if (image == null) {
               throw new IOException("heightmap.bmp is not readable");
            }

            int width = image.getWidth();
            int height = image.getHeight();
            byte[] values = new byte[width * height];
            int[] row = new int[width];

            for (int z = 0; z < height; z++) {
               image.getRGB(0, z, width, 1, row, 0, width);

               for (int x = 0; x < width; x++) {
                  int rgb = row[x];
                  int gray = ((rgb >>> 16 & 0xFF) * 30 + (rgb >>> 8 & 0xFF) * 59 + (rgb & 0xFF) * 11) / 100;
                  values[z * width + x] = (byte)gray;
               }
            }

            EarthShape.LOGGER
               .info(
                  "[EarthShape] heightmap.bmp elevation layer loaded: {}x{} in {} ms.", new Object[]{width, height, (System.nanoTime() - started) / 1000000L}
               );
            var15x = new HeightmapLayer.Data(width, height, values);
         }

         return var15x;
      } catch (IOException var15) {
         throw new IllegalStateException("EarthShape could not load heightmap.bmp", var15);
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static record Data(int width, int height, byte[] values) {
      double value(int x, int z) {
         return (double)(this.values[z * this.width + x] & 255) / 255.0;
      }
   }
}
