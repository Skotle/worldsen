package io.github.earthshape.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, approximately least-recently-used cache for the filtered biome RTrees.
 * Tree construction remains atomic per group so chunk workers cannot allocate the
 * same large tree concurrently during pregeneration.
 */
public final class FilteredParameterCache {
   private final ConcurrentHashMap<Integer, Climate.ParameterList<Holder<Biome>>> values = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Integer, Long> lastUsed = new ConcurrentHashMap<>();
   private final AtomicLong accessSequence = new AtomicLong();

   public ConcurrentHashMap<Integer, Climate.ParameterList<Holder<Biome>>> values() {
      return this.values;
   }

   public void touch(int group) {
      this.lastUsed.put(group, this.accessSequence.incrementAndGet());
   }

   public void trim(int retainedGroup, int maximumSize) {
      while (this.values.size() > maximumSize) {
         Integer oldestGroup = null;
         long oldestAccess = Long.MAX_VALUE;
         for (Integer cachedGroup : this.values.keySet()) {
            if (cachedGroup == retainedGroup) continue;
            long access = this.lastUsed.getOrDefault(cachedGroup, Long.MIN_VALUE);
            if (access < oldestAccess) {
               oldestAccess = access;
               oldestGroup = cachedGroup;
            }
         }
         if (oldestGroup == null) return;
         Climate.ParameterList<Holder<Biome>> removed = this.values.remove(oldestGroup);
         if (removed != null) this.lastUsed.remove(oldestGroup);
      }
   }
}
