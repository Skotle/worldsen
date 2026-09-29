package terrablender;

import net.neoforged.fml.common.Mod;

/**
 * Supplies the TerraBlender 4.1 mod identity after the real implementation has
 * been removed. Its API classes intentionally accept registrations without ever
 * forwarding them to biome or surface-rule world generation.
 */
@Mod("terrablender")
public final class TerraBlenderApiCompatibility {
   public TerraBlenderApiCompatibility() {
   }
}
