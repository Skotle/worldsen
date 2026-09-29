package io.github.earthshape.fabric;

import io.github.earthshape.EarthShape;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.diagnostics.ServerHangWatchdog;
import io.github.earthshape.map.MapCenterManager;
import io.github.earthshape.worldgen.AdditionalBiomeRegistry;
import net.fabricmc.api.ModInitializer;

public final class EarthShapeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EarthShapeServerConfig.load();
        EarthShapeCompatibility.initialize();
        EarthShapeDensityFunctions.register();
        AdditionalBiomeRegistry.register();
        // Do not link Chunky's optional Shape API when the mod is absent.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("chunky")) {
            io.github.earthshape.compat.ChunkyIntegration.registerIfPresent();
        }
        MapCenterManager.register();
        ServerHangWatchdog.register();
        EarthShape.LOGGER.info("[EarthShape] Fabric 26.3 core initialized.");
    }
}
