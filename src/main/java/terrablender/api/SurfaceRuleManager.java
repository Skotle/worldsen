package terrablender.api;

import java.util.List;
import net.minecraft.world.level.levelgen.SurfaceRules;

/** No-op 4.1 surface-rule API; it cannot alter EarthShape's surface rules. */
public final class SurfaceRuleManager {
   private SurfaceRuleManager() {
   }

   public enum RuleCategory { OVERWORLD, NETHER, END }
   public enum RuleStage { BEFORE_BEDROCK, AFTER_BEDROCK }

   public static void addSurfaceRules(RuleCategory category, String namespace, SurfaceRules.RuleSource rules) { }
   public static void addToDefaultSurfaceRulesAtStage(RuleCategory category, RuleStage stage, int priority, SurfaceRules.RuleSource rules) { }
   public static void setDefaultSurfaceRules(RuleCategory category, SurfaceRules.RuleSource rules) { }
   public static void removeSurfaceRules(RuleCategory category, String namespace) { }
   public static SurfaceRules.RuleSource getNamespacedRules(RuleCategory category, SurfaceRules.RuleSource fallback) { return fallback; }
   public static List<SurfaceRules.RuleSource> getDefaultSurfaceRuleAdditionsForStage(RuleCategory category, RuleStage stage) { return List.of(); }
   public static SurfaceRules.RuleSource getDefaultSurfaceRules(RuleCategory category) { return null; }
}
