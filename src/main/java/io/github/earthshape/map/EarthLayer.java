package io.github.earthshape.map;

import java.awt.image.BufferedImage;

/** Immutable grayscale map layer using the same centered, bilinear coordinate mapping as EarthMap. */
final class EarthLayer {
    private final int width;
    private final int height;
    private final float[] values;

    private EarthLayer(int width, int height, float[] values) {
        this.width = width;
        this.height = height;
        this.values = values;
    }

    static EarthLayer from(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] values = new float[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = image.getRGB(x, y);
            values[y * width + x] = (((rgb >>> 16) & 255) * 30 + ((rgb >>> 8) & 255) * 59 + (rgb & 255) * 11) / 25500.0F;
        }
        return new EarthLayer(width, height, values);
    }

    int width() { return width; }
    int height() { return height; }

    double sample(double worldX, double worldZ, int blocksPerPixel) {
        double px = worldX / blocksPerPixel + width * 0.5D;
        double py = worldZ / blocksPerPixel + height * 0.5D;
        if (px < 0 || py < 0 || px >= width - 1 || py >= height - 1) return 0.5D;
        int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py);
        double tx = px - x0, ty = py - y0;
        double a = lerp(values[y0 * width + x0], values[y0 * width + x0 + 1], tx);
        double b = lerp(values[(y0 + 1) * width + x0], values[(y0 + 1) * width + x0 + 1], tx);
        return lerp(a, b, ty);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
