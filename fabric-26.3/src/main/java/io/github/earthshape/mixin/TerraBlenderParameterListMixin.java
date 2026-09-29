package io.github.earthshape.mixin;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Occupies TerraBlender's positional lookup signature before its lower-priority
 * mixin can merge the region selector. Every positional request is therefore
 * resolved by Minecraft's original non-positional climate tree.
 */
@Mixin(value = Climate.ParameterList.class, priority = 3000)
public abstract class TerraBlenderParameterListMixin<T> {
   @Shadow
   public abstract T findValue(Climate.TargetPoint point);

   public T findValuePositional(Climate.TargetPoint point, int quartX, int quartY, int quartZ) {
      return this.findValue(point);
   }
}
