package io.github.earthshape.map;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Supplies the world seed before spawn chunks begin requesting map samples. */
public final class MapCenterManager {
   private static final AtomicBoolean REGISTERED = new AtomicBoolean();

   private MapCenterManager() {
   }

   public static void register() {
      if (!REGISTERED.compareAndSet(false, true)) return;
      // SERVER_STARTING is the Fabric lifecycle point before the server begins
      // ticking and therefore before spawn preparation requests map samples.
      ServerLifecycleEvents.SERVER_STARTING.register(MapCenterManager::onServerStarting);
   }

   private static void onServerStarting(net.minecraft.server.MinecraftServer server) {
      RiversMask.INSTANCE.configureMapCenter(server.getWorldGenSettings().options().seed());
   }
}
