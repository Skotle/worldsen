package io.github.earthshape;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Shared Fabric-side identity and logger for the map samplers. */
public final class EarthShape {
    public static final String MOD_ID = "earthshape";
    public static final Logger LOGGER = LogUtils.getLogger();

    private EarthShape() {}
}
