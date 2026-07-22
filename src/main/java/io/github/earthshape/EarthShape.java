package io.github.earthshape;

import com.mojang.logging.LogUtils;
import io.github.earthshape.worldgen.EarthShapeDensityFunctions;
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
      LOGGER.info("[EarthShape] rivers.bmp continentalness rewrite loaded for NeoForge 1.21.1.");
   }
}
