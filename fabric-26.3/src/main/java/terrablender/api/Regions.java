package terrablender.api;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Inert TerraBlender region registry used for dependency compatibility.
 * Registrations are accepted so dependent mods can finish setup, but no
 * region is retained or exposed to world generation.
 */
public final class Regions {
   private Regions() {
   }

   public static void register(ResourceLocation name, Region region) {
   }

   public static void register(ResourceLocation name, int index, Region region) {
   }

   public static void register(Region region) {
   }

   public static void remove(RegionType type, ResourceLocation name) {
   }

   public static List<Region> get(RegionType type) {
      return List.of();
   }

   public static int getIndex(RegionType type, ResourceLocation location) {
      return -1;
   }

   public static int getCount(RegionType type) {
      return 0;
   }
}
