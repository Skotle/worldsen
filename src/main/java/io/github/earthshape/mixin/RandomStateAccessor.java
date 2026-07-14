package io.github.earthshape.mixin;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mutable only during RandomState construction, before any chunk sees the router. */
@Mixin(RandomState.class)
public interface RandomStateAccessor {
    @Mutable
    @Accessor("router")
    void earthshape$setRouter(NoiseRouter router);
}
