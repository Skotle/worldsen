package io.github.earthshape;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.ModList;
import net.minecraft.world.level.levelgen.SurfaceRules;

public final class EarthShapeCompatibility {
   // External biome selectors are allowed to initialize and calculate normally.
   // TerrainBiomeMixin discards their returned holder at the final RETURN point,
   // so none of them needs to disable EarthShape's map worldgen globally.
   private static final Set<String> HARD_WORLDGEN_MODS = Set.of();
   private static volatile Set<String> conflicts = Set.of();
   private static volatile boolean terralithLoaded;
   private static volatile boolean terraBlenderLoaded;
   private static volatile boolean biomesOPlentyLoaded;
   private static final Map<String, IdentityHashMap<SurfaceRules.RuleSource, SurfaceRules.RuleSource>> BOP_SURFACE_CACHE = new HashMap<>();
   private static final Set<String> BOP_SURFACE_LOGGED = new LinkedHashSet<>();

   private EarthShapeCompatibility() {
   }

   public static void initialize() {
      terralithLoaded = ModList.get().isLoaded("terralith");
      terraBlenderLoaded = hasRealTerraBlenderImplementation();
      biomesOPlentyLoaded = ModList.get().isLoaded("biomesoplenty");
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
         disableIncompatibleTerralithTerrainModules();
         EarthShape.LOGGER
            .info("[EarthShape] Terralith compatibility active: EarthShape owns C/E/W, disables amplified arch/dune/spike/cliff density additions, and retains tagged Terralith biomes, surface rules, caves, features and structures.");
      }

      if (terraBlenderLoaded) {
         EarthShape.LOGGER.info("[EarthShape] TerraBlender detected: regions may initialize, but EarthShape replaces the completed Overworld biome result at final return.");
      } else if (ModList.get().isLoaded("terrablender")) {
         EarthShape.LOGGER.info("[EarthShape] inert TerraBlender API compatibility active; TerraBlender world generation is not installed.");
      }

      if (biomesOPlentyLoaded) {
         EarthShape.LOGGER.info(
            "[EarthShape] Biomes O' Plenty compatibility active: original glowing_grotto and spider_nest underground climate ranges are restored without enabling TerraBlender's surface selector."
         );
      }

      if (ModList.get().isLoaded("climaterivers") || ModList.get().isLoaded("climate_rivers")) {
         EarthShape.LOGGER.info("[EarthShape] Climate Rivers detected: its parameter candidates may initialize, but its selected river holder is discarded by the final EarthShape selector.");
      }
   }

   public static boolean disablesWorldgen() {
      return !conflicts.isEmpty();
   }

   public static boolean isTerralithLoaded() {
      return terralithLoaded;
   }

   public static boolean isBiomesOPlentyLoaded() {
      return biomesOPlentyLoaded;
   }

   /**
    * Restores BOP's biome-conditional Overworld surface rules after EarthShape
    * removes TerraBlender's positional biome wrapper. The BOP rule returns no
    * state outside BOP biomes, so the vanilla/Terralith base remains authoritative
    * everywhere else.
    */
   public static SurfaceRules.RuleSource compatibleSurfaceRules(SurfaceRules.RuleSource base, String dimensionMethod) {
      if (!biomesOPlentyLoaded) {
         return base;
      }
      synchronized (EarthShapeCompatibility.class) {
         IdentityHashMap<SurfaceRules.RuleSource, SurfaceRules.RuleSource> cache = BOP_SURFACE_CACHE.computeIfAbsent(
            dimensionMethod, ignored -> new IdentityHashMap<>()
         );
         SurfaceRules.RuleSource cached = cache.get(base);
         if (cached != null) {
            return cached;
         }
         try {
            Class<?> rulesClass = Class.forName(
               "biomesoplenty.worldgen.BOPSurfaceRuleData", true, EarthShapeCompatibility.class.getClassLoader()
            );
            SurfaceRules.RuleSource bop = (SurfaceRules.RuleSource)rulesClass.getMethod(dimensionMethod).invoke(null);
            SurfaceRules.RuleSource combined = SurfaceRules.sequence(bop, base);
            cache.put(base, combined);
            if (BOP_SURFACE_LOGGED.add(dimensionMethod)) {
               EarthShape.LOGGER.info("[EarthShape] Biomes O' Plenty {} surface rules restored ahead of the base rules.", dimensionMethod);
            }
            return combined;
         } catch (ReflectiveOperationException | LinkageError exception) {
            if (BOP_SURFACE_LOGGED.add(dimensionMethod)) {
               EarthShape.LOGGER.warn("[EarthShape] Could not restore Biomes O' Plenty {} surface rules; retaining the base rule.", dimensionMethod, exception);
            }
            return base;
         }
      }
   }

   public static boolean isTerraBlenderLoaded() {
      if (terraBlenderLoaded) {
         return true;
      }

      // MultiNoiseBiomeSource can first be queried after additional mod loading
      // has completed. Re-check here so a constructor-time ordering difference
      // can never allow TerraBlender's positional selector to win the first call.
      boolean detected = hasRealTerraBlenderImplementation();
      if (detected) {
         terraBlenderLoaded = true;
      }
      return detected;
   }

   /**
    * Skylands and terrain slabs add solid density independently of the mapped
    * continentalness axis. Disable them for this server session without rewriting
    * the user's terralith.json; every biome/feature/structure module stays intact.
    */
   @SuppressWarnings("unchecked")
   private static void disableIncompatibleTerralithTerrainModules() {
      try {
         ClassLoader loader = EarthShapeCompatibility.class.getClassLoader();
         Class<?> handlerClass = Class.forName("net.stardustlabs.terralith.config.ConfigHandler", false, loader);
         Object state = handlerClass.getMethod("getState").invoke(null);
         if (state == null) {
            EarthShape.LOGGER.warn("[EarthShape] Terralith config was not initialized before compatibility setup; skylands/slabs could not be disabled.");
            return;
         }
         Object modules = state.getClass().getField("modules").get(state);
         Map<String, Boolean> values = (Map<String, Boolean>)modules.getClass().getField("modules").get(modules);
         values.put("skylands", false);
         values.put("terrain_slabs", false);
         EarthShape.LOGGER.info("[EarthShape] Terralith skylands and terrain_slabs disabled in memory for mapped-continent compatibility; terralith.json was not changed.");
      } catch (ReflectiveOperationException | LinkageError exception) {
         EarthShape.LOGGER.warn("[EarthShape] Could not apply Terralith terrain-module compatibility; continuing with layer and biome compatibility only.", exception);
      }
   }

   private static boolean hasRealTerraBlenderImplementation() {
      if (!ModList.get().isLoaded("terrablender")) {
         return false;
      }

      try {
         Class.forName("terrablender.core.TerraBlenderNeoForge", false, EarthShapeCompatibility.class.getClassLoader());
         return true;
      } catch (ClassNotFoundException ignored) {
         return false;
      }
   }
}
