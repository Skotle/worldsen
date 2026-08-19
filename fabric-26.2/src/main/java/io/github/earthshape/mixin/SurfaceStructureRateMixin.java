package io.github.earthshape.mixin;

import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.worldgen.StructureLayerCompatibility;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies a deterministic rate limit only after a surface structure has been selected. */
@Mixin(Structure.class)
public abstract class SurfaceStructureRateMixin {
   @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
   private void earthshape$limitSurfaceStructureRate(
      Holder<Structure> holder, ResourceKey<Structure> key, RegistryAccess registryAccess,
      ChunkGenerator chunkGenerator, BiomeSource biomeSource, RandomState randomState,
      StructureTemplateManager templateManager, long seed, ChunkPos chunkPos, int references,
      LevelHeightAccessor heightAccessor, Predicate<Holder<Biome>> validBiome,
      CallbackInfoReturnable<StructureStart> callback
   ) {
      Structure structure = (Structure)(Object)this;
      Identifier structureId = registryAccess.lookupOrThrow(Registries.STRUCTURE).getKey(structure);
      boolean deadSeaArch = isDeadSeaArch(structureId);
      if (structure.step() == GenerationStep.Decoration.SURFACE_STRUCTURES
         && !StructureLayerCompatibility.isAllowed(registryAccess, structure, chunkPos)) {
         callback.setReturnValue(StructureStart.INVALID_START);
         return;
      }

      // BWG's enormous Dead Sea arches run during RAW_GENERATION rather than
      // SURFACE_STRUCTURES, so the ordinary surface-structure limiter never
      // sees them. Retain the rare landmark at one percent of BWG's candidates.
      double rate = deadSeaArch ? 0.01 : (Double)EarthShapeServerConfig.SURFACE_STRUCTURE_RATE.get();
      if (rate >= 1.0 || (!deadSeaArch && structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES)) return;

      long value = seed ^ ((long)chunkPos.x() * 341873128712L) ^ ((long)chunkPos.z() * 132897987541L)
         ^ (long)(structureId == null ? structure.getClass().getName().hashCode() : structureId.hashCode()) * 42317861L;
      value ^= value >>> 33;
      value *= -49064778989728563L;
      value ^= value >>> 33;
      double roll = (double)(value >>> 11) * 0x1.0p-53;
      if (roll >= rate) callback.setReturnValue(StructureStart.INVALID_START);
   }

   private static boolean isDeadSeaArch(Identifier id) {
      if (id == null) return false;
      return "biomeswevegone".equals(id.getNamespace()) && "dripstone_arch".equals(id.getPath())
         || "byg".equals(id.getNamespace()) && "stone_arch".equals(id.getPath());
   }
}
