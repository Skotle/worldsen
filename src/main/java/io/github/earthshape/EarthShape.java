package io.github.earthshape;

import io.github.earthshape.command.EarthShapeCommands;
import io.github.earthshape.worldgen.EarthShapeDensityFunctions;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/** Entry point for the server-side EarthShape terrain signal service. */
@Mod(EarthShape.MOD_ID)
public final class EarthShape {
    public static final String MOD_ID = "earthshape";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EarthShape(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, EarthShapeConfig.SPEC);
        EarthShapeDensityFunctions.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("[EarthShape] Loaded for NeoForge 1.21.1; Overworld shaping will activate for new chunks.");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        EarthShapeCommands.register(event.getDispatcher());
    }
}
