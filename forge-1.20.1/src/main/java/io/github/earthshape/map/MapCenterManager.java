package io.github.earthshape.map;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

/** Supplies the world seed before TFC begins requesting coastline samples. */
public final class MapCenterManager {
   private static final AtomicBoolean REGISTERED = new AtomicBoolean();

   private MapCenterManager() {
   }

   public static void register() {
      if (REGISTERED.compareAndSet(false, true)) {
         MinecraftForge.EVENT_BUS.addListener(MapCenterManager::onServerAboutToStart);
      }
   }

   private static void onServerAboutToStart(ServerAboutToStartEvent event) {
      RiversMask.INSTANCE.configureMapCenter(event.getServer().getWorldData().worldGenOptions().seed());
   }
}
