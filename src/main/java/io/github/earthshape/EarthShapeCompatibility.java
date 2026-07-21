package io.github.earthshape;

import java.util.LinkedHashSet;
import java.util.Set;
import net.neoforged.fml.ModList;

public final class EarthShapeCompatibility {
   private static final Set<String> HARD_WORLDGEN_MODS = Set.of("climate_rivers", "climaterivers");
   private static volatile Set<String> conflicts = Set.of();
   private static volatile boolean terralithLoaded;

   private EarthShapeCompatibility() {
   }

   public static void initialize() {
      terralithLoaded = ModList.get().isLoaded("terralith");
      Set<String> found = new LinkedHashSet<>();

      for (String modId : HARD_WORLDGEN_MODS) {
         if (ModList.get().isLoaded(modId)) {
            found.add(modId);
         }
      }

      conflicts = Set.copyOf(found);
      if (!conflicts.isEmpty()) {
         EarthShape.LOGGER
            .error("[EarthShape] incompatible world-generation mods detected: {}. EarthShape worldgen is disabled; see COMPATIBILITY.md.", conflicts);
      }

      if (terralithLoaded) {
         EarthShape.LOGGER
            .info("[EarthShape] Terralith detected: retaining Terralith-selected biomes and features; EarthShape only supplies map shape and density relief.");
      }
   }

   public static boolean disablesWorldgen() {
      return !conflicts.isEmpty();
   }

   public static boolean isTerralithLoaded() {
      return terralithLoaded;
   }
}
