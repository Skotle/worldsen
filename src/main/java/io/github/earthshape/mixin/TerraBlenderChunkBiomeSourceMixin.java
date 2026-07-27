/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.biome.BiomeResolver
 *  net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.ModifyArg
 */
package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeCompatibility;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value={NoiseBasedChunkGenerator.class}, priority=500)
public abstract class TerraBlenderChunkBiomeSourceMixin {
    @ModifyArg(method={"doCreateBiomes"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"), index=0)
    private BiomeResolver earthshape$restoreBiomeSource(BiomeResolver ignored) {
        return EarthShapeCompatibility.isTerraBlenderLoaded() ? ((NoiseBasedChunkGenerator)(Object)this).getBiomeSource() : ignored;
    }
}
