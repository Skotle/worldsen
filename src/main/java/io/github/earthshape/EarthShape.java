package io.github.earthshape;

import com.mojang.logging.LogUtils;
import io.github.earthshape.worldgen.EarthShapeDensityFunctions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Clean rivers.bmp continentalness rewrite entry point. */
@Mod(EarthShape.MOD_ID)
public final class EarthShape {
    public static final String MOD_ID = "earthshape";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EarthShape(IEventBus modBus, ModContainer container) {
        WorldgenCompatibilityGuard.verify();
        container.registerConfig(ModConfig.Type.SERVER, EarthShapeServerConfig.SPEC);
        EarthShapeDensityFunctions.register(modBus);
        LOGGER.info("[EarthShape] rivers.bmp continentalness rewrite loaded for NeoForge 1.21.1.");
    }
}
