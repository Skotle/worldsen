package io.github.earthshape.mixin;

import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeSource.class)
public interface BiomeSourceAccessor {
   @Accessor("possibleBiomes")
   void earthshape$setPossibleBiomes(Supplier<Set<Holder<Biome>>> possibleBiomes);
}
