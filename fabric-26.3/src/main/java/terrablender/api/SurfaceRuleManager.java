package terrablender.api;

import java.util.List;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

/**
 * Inert TerraBlender surface-rule API. Dependent mods may register rules during
 * setup, but EarthShape never stores or applies them to world generation.
 */
public final class SurfaceRuleManager {
   private SurfaceRuleManager() {
   }

   public enum RuleCategory {
      OVERWORLD,
      NETHER,
      END
   }

   public enum RuleStage {
      BEFORE_BEDROCK,
      AFTER_BEDROCK
   }

   public static void addSurfaceRules(RuleCategory category, String namespace, MaterialRule rules) {
   }

   public static void addToDefaultSurfaceRulesAtStage(
      RuleCategory category,
      RuleStage stage,
      int priority,
      MaterialRule rules
   ) {
   }

   public static void setDefaultSurfaceRules(RuleCategory category, MaterialRule rules) {
   }

   public static void removeSurfaceRules(RuleCategory category, String namespace) {
   }

   public static MaterialRule getNamespacedRules(
      RuleCategory category,
      MaterialRule fallback
   ) {
      return fallback;
   }

   public static List<MaterialRule> getDefaultSurfaceRuleAdditionsForStage(
      RuleCategory category,
      RuleStage stage
   ) {
      return List.of();
   }

   public static MaterialRule getDefaultSurfaceRules(RuleCategory category) {
      return null;
   }
}
