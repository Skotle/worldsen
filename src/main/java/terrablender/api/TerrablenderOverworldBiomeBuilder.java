package terrablender.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

/**
 * ABI-compatible selector used while dependent mods initialise their regions.
 * Regions are never consumed, so these values cannot reach world generation.
 */
public class TerrablenderOverworldBiomeBuilder extends OverworldBiomeBuilder {
   private final ResourceKey<Biome>[][] beaches;
   private final ResourceKey<Biome>[][] peaks;
   private final ResourceKey<Biome>[][] peakVariants;
   private final ResourceKey<Biome>[][] slopes;
   private final ResourceKey<Biome>[][] slopeVariants;

   public TerrablenderOverworldBiomeBuilder(
      ResourceKey<Biome>[][] oceans, ResourceKey<Biome>[][] middleBiomes, ResourceKey<Biome>[][] middleVariants,
      ResourceKey<Biome>[][] plateaus, ResourceKey<Biome>[][] plateauVariants, ResourceKey<Biome>[][] shattered,
      ResourceKey<Biome>[][] beaches, ResourceKey<Biome>[][] peaks, ResourceKey<Biome>[][] peakVariants,
      ResourceKey<Biome>[][] slopes, ResourceKey<Biome>[][] slopeVariants
   ) {
      this.beaches = beaches;
      this.peaks = peaks;
      this.peakVariants = peakVariants;
      this.slopes = slopes;
      this.slopeVariants = slopeVariants;
   }

   public ResourceKey<Biome> pickBeachBiome(int temperature, int humidity) {
      return this.beaches[temperature][humidity];
   }

   public ResourceKey<Biome> pickPeakBiome(int temperature, int humidity, Climate.Parameter weirdness) {
      ResourceKey<Biome> variant = weirdness.max() < 0L ? null : this.peakVariants[temperature][humidity];
      return variant != null ? variant : this.peaks[temperature][humidity];
   }

   public ResourceKey<Biome> pickSlopeBiome(int temperature, int humidity, Climate.Parameter weirdness) {
      ResourceKey<Biome> variant = weirdness.max() < 0L ? null : this.slopeVariants[temperature][humidity];
      return variant != null ? variant : this.slopes[temperature][humidity];
   }
}
