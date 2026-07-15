package io.github.earthshape.map;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeServerConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import javax.imageio.ImageIO;

/** Immutable land/ocean mask read directly from the bundled HOI4 rivers.bmp layer. */
public final class RiversMask {
    public static final RiversMask INSTANCE = new RiversMask();
    public static final int DEFAULT_BLOCKS_PER_PIXEL = 20;

    private volatile Data data;

    private RiversMask() {}

    /** Returns a bilinear land value: 0 for open sea and 1 for land. */
    public double sampleLand(int blockX, int blockZ) {
        Data loaded = data();
        return sampleLand(loaded, blockX, blockZ);
    }

    /**
     * Coast-only low-pass filtering for density functions.  The original layer remains the
     * source of truth, but one 20-block binary BMP edge is too narrow for vanilla's vertical
     * terrain response and produces a cliff.  This spreads only the density transition.
     */
    public double sampleCoastLand(int blockX, int blockZ) {
        Data loaded = data();
        int radius = Math.max(32, blocksPerPixel() * 3);
        double centre = sampleLand(loaded, blockX, blockZ) * 4.0D;
        double sides = sampleLand(loaded, blockX - radius, blockZ)
                + sampleLand(loaded, blockX + radius, blockZ)
                + sampleLand(loaded, blockX, blockZ - radius)
                + sampleLand(loaded, blockX, blockZ + radius);
        return (centre + sides) / 8.0D;
    }

    private double sampleLand(Data loaded, int blockX, int blockZ) {
        int blocksPerPixel = blocksPerPixel();
        double imageX = blockX / (double) blocksPerPixel + loaded.width * 0.5D;
        double imageZ = blockZ / (double) blocksPerPixel + loaded.height * 0.5D;
        if (imageX < 0.0D || imageZ < 0.0D || imageX >= loaded.width - 1 || imageZ >= loaded.height - 1) return 0.0D;
        int x = (int) Math.floor(imageX);
        int z = (int) Math.floor(imageZ);
        double tx = imageX - x;
        double tz = imageZ - z;
        double a = lerp(loaded.land(x, z), loaded.land(x + 1, z), tx);
        double b = lerp(loaded.land(x, z + 1), loaded.land(x + 1, z + 1), tx);
        return lerp(a, b, tz);
    }

    public int blocksPerPixel() { return EarthShapeServerConfig.BLOCKS_PER_PIXEL.get(); }

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
        try (InputStream input = EarthShape.class.getResourceAsStream("/earthshape/hoi4/rivers.bmp")) {
            if (input == null) throw new IOException("missing /earthshape/hoi4/rivers.bmp");
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("rivers.bmp is not readable");
            int width = image.getWidth();
            int height = image.getHeight();
            BitSet land = new BitSet(width * height);
            int[] row = new int[width];
            for (int z = 0; z < height; z++) {
                image.getRGB(0, z, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    int rgb = row[x];
                    int red = (rgb >>> 16) & 255;
                    int green = (rgb >>> 8) & 255;
                    int blue = rgb & 255;
                    // HOI4 rivers.bmp uses neutral RGB 122,122,122 for open water. River
                    // markings remain land, exactly as requested for this source layer.
                    if (red != 122 || green != 122 || blue != 122) land.set(z * width + x);
                }
            }
            EarthShape.LOGGER.info("[EarthShape] rivers.bmp continental mask loaded: {}x{} in {} ms.",
                    width, height, (System.nanoTime() - started) / 1_000_000L);
            return new Data(width, height, land);
        } catch (IOException exception) {
            throw new IllegalStateException("EarthShape could not load rivers.bmp", exception);
        }
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private record Data(int width, int height, BitSet land) {
        double land(int x, int z) { return land.get(z * width + x) ? 1.0D : 0.0D; }
    }
}
