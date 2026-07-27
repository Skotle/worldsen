/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.logging.LogUtils
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.fml.ModContainer
 *  net.neoforged.fml.common.Mod
 *  net.neoforged.fml.config.IConfigSpec
 *  net.neoforged.fml.config.ModConfig$Type
 *  org.slf4j.Logger
 */
package io.github.earthshape;

import com.mojang.logging.LogUtils;
import io.github.earthshape.EarthShapeCompatibility;
import io.github.earthshape.EarthShapeServerConfig;
import io.github.earthshape.worldgen.EarthShapeDensityFunctions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(value="earthshape")
public final class EarthShape {
    public static final String MOD_ID = "earthshape";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EarthShape(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, (IConfigSpec)EarthShapeServerConfig.SPEC);
        EarthShapeCompatibility.initialize();
        EarthShapeDensityFunctions.register(modBus);
        LOGGER.info("[EarthShape] rivers.bmp continentalness rewrite loaded for NeoForge 1.21.1.");
    }
}

