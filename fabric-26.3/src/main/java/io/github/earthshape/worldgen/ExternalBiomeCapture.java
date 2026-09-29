package io.github.earthshape.worldgen;

import java.util.function.Supplier;

/** Prevents the inner RETURN hook from masking the external result being captured. */
public final class ExternalBiomeCapture {
   private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

   private ExternalBiomeCapture() {
   }

   public static boolean active() {
      return DEPTH.get() > 0;
   }

   public static <T> T run(Supplier<T> action) {
      int previous = DEPTH.get();
      DEPTH.set(previous + 1);
      try {
         return action.get();
      } finally {
         if (previous == 0) {
            DEPTH.remove();
         } else {
            DEPTH.set(previous);
         }
      }
   }
}
