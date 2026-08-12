package io.github.earthshape;

/** The coastline-only port has no global world-generation incompatibility gate. */
public final class EarthShapeCompatibility {
   private EarthShapeCompatibility() {
   }

   public static boolean disablesWorldgen() {
      return false;
   }
}
