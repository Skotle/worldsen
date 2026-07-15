package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Lazily loaded grayscale elevation layer matching the rivers.bmp coordinate system. */
public final class HeightmapLayer {
    public static final HeightmapLayer INSTANCE = new HeightmapLayer();

    private volatile Data data;

    private HeightmapLayer() {}

    public double sample(int blockX, int blockZ) {
        Data loaded = data();
        int blocksPerPixel = RiversMask.INSTANCE.blocksPerPixel();
        double imageX = blockX / (double) blocksPerPixel + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel + loaded.height * 0.5D;
        if (imageX < 0.0D || imageZ < 0.0D || imageX >= loaded.width - 1 || imageZ >= loaded.height - 1) return 0.0D;
        int x = (int) Math.floor(imageX);
        int z = (int) Math.floor(imageZ);
        double tx = imageX - x;
        double tz = imageZ - z;
        double a = lerp(loaded.value(x, z), loaded.value(x + 1, z), tx);
        double b = lerp(loaded.value(x, z + 1), loaded.value(x + 1, z + 1), tx);
        return lerp(a, b, tz);
    }

    private Data data() {
        Data current = data;
        if (current != null) return current;
        synchronized (this) {
            if (data == null) data = load();
            return data;
        }
    }

    private static Data load() {
        long started = System.nanoTime();
        try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/heightmap.bmp")) {
            if (input == null) throw new IOException("missing /earthshape/hoi4/heightmap.bmp");
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("heightmap.bmp is not readable");
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] values = new byte[width * height];
            int[] row = new int[width];
            for (int z = 0; z < height; z++) {
                image.getRGB(0, z, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    int rgb = row[x];
                    int gray = (((rgb >>> 16) & 255) * 30 + ((rgb >>> 8) & 255) * 59 + (rgb & 255) * 11) / 100;
                    values[z * width + x] = (byte) gray;
                }
            }
            EarthShape.LOGGER.info("[EarthShape] heightmap.bmp elevation layer loaded: {}x{} in {} ms.",
                    width, height, (System.nanoTime() - started) / 1_000_000L);
            return new Data(width, height, values);
        } catch (IOException exception) {
            throw new IllegalStateException("EarthShape could not load heightmap.bmp", exception);
        }
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private record Data(int width, int height, byte[] values) {
        double value(int x, int z) { return (values[z * width + x] & 255) / 255.0D; }
    }
}
