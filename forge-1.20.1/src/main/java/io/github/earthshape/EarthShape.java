package io.github.earthshape;

import com.mojang.logging.LogUtils;
import io.github.earthshape.map.MapCenterManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.slf4j.Logger;

@Mod(EarthShape.MOD_ID)
public final class EarthShape {
   public static final String MOD_ID = "earthshape";
   public static final Logger LOGGER = LogUtils.getLogger();

   public EarthShape(IEventBus modBus) {
      ModLoadingContext.get().registerConfig(Type.SERVER, EarthShapeServerConfig.SPEC);
      MapCenterManager.register();
      LOGGER.info("[EarthShape] TFC coastline compatibility layer active.");
   }
}
