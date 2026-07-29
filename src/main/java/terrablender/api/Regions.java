package terrablender.api;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** No-op 4.1 region registry; registrations are accepted but never applied. */
public final class Regions {
   private Regions() {
   }

   public static void register(ResourceLocation name, Region region) { }
   public static void register(ResourceLocation name, int index, Region region) { }
   public static void register(Region region) { }
   public static void remove(RegionType type, ResourceLocation name) { }
   public static List<Region> get(RegionType type) { return List.of(); }
   public static int getIndex(RegionType type, ResourceLocation location) { return -1; }
   public static int getCount(RegionType type) { return 0; }
}
