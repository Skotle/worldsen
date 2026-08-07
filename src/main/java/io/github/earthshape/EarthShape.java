package io.github.earthshape;

import com.mojang.logging.LogUtils;
import io.github.earthshape.compat.ChunkyIntegration;
import io.github.earthshape.diagnostics.ServerHangWatchdog;
import io.github.earthshape.map.MapCenterManager;
import io.github.earthshape.worldgen.EarthShapeDensityFunctions;
import io.github.earthshape.worldgen.AdditionalBiomeRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import org.slf4j.Logger;

@Mod("earthshape")
public final class EarthShape {
   public static final String MOD_ID = "earthshape";
   public static final Logger LOGGER = LogUtils.getLogger();

   public EarthShape(IEventBus modBus, ModContainer container) {
      container.registerConfig(Type.SERVER, EarthShapeServerConfig.SPEC);
      EarthShapeCompatibility.initialize();
      EarthShapeDensityFunctions.register(modBus);
      AdditionalBiomeRegistry.register();
      ChunkyIntegration.registerIfPresent();
      MapCenterManager.register();
      ServerHangWatchdog.register();
      LOGGER.info("[EarthShape] map biome and water layers loaded for NeoForge 1.21.1.");
   }
}
