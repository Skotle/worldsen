package terrablender.api;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * TerraBlender 4.1 linkage surface. Region registrations are deliberately not
 * consumed by EarthShape, preventing external biome-parameter injection.
 */
public abstract class Region {
   public static final ResourceKey<Biome> DEFERRED_PLACEHOLDER = ResourceKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("terrablender", "deferred_placeholder")
   );

   private final ResourceLocation name;
   private final RegionType type;
   private final int weight;

   public Region(ResourceLocation name, RegionType type, int weight) {
      this.name = name;
      this.type = type;
      this.weight = weight;
   }

   public ResourceLocation getName() { return this.name; }
   public RegionType getType() { return this.type; }
   public int getWeight() { return this.weight; }

   public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
      // Intentionally no-op: EarthShape selects the final biome from its layers.
   }
}
