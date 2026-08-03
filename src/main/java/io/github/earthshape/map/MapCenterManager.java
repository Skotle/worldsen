package io.github.earthshape.map;

import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Supplies the world seed before spawn chunks begin requesting map samples. */
public final class MapCenterManager {
   private static final AtomicBoolean REGISTERED = new AtomicBoolean();

   private MapCenterManager() {
   }

   public static void register() {
      if (!REGISTERED.compareAndSet(false, true)) return;
      NeoForge.EVENT_BUS.addListener(MapCenterManager::onServerAboutToStart);
   }

   private static void onServerAboutToStart(ServerAboutToStartEvent event) {
      RiversMask.INSTANCE.configureMapCenter(event.getServer().getWorldData().worldGenOptions().seed());
   }
}
