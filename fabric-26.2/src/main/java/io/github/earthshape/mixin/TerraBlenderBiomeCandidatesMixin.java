package io.github.earthshape.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;

/** Prevent TerraBlender from appending registered region biomes to a biome source. */
@Mixin(value = BiomeSource.class, priority = 3000)
public abstract class TerraBlenderBiomeCandidatesMixin {
   public void appendDeferredBiomesList(List<Holder<Biome>> ignored) {
   }
}
