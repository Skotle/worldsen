package io.github.earthshape.mixin;

import io.github.earthshape.EarthShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import net.minecraft.server.level.ChunkMap;

/**
 * C2ME's OpenCL compiler has no emitter for EarthShape's custom density
 * functions. Let C2ME use its built-in per-world CPU fallback instead of
 * aborting world startup when its optional GPU compiler cannot handle them.
 *
 * C2ME loads its configuration during bootstrap, before regular mixins can
 * target that class. Applying the override at ChunkMap construction avoids
 * that bootstrap race while still preceding C2ME's constructor-return hook.
 */
@Mixin(ChunkMap.class)
public abstract class C2meOpenClFallbackMixin {
    @Unique
    private static boolean earthshape$c2meFallbackConfigured;

    @Inject(method = "<init>", at = @At("HEAD"))
    // HEAD runs before super(); the receiver is still uninitialized here.
    // This hook only accesses static configuration and must itself be static.
    private static void earthshape$enableOpenClFallback(CallbackInfo ci) {
        try {
            Class<?> configClass = Class.forName("com.ishland.c2me.opts.accel.opencl.common.Config");
            Field fallbackField = configClass.getDeclaredField("allowIncompatibilityFallback");
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            Object base = unsafe.staticFieldBase(fallbackField);
            long offset = unsafe.staticFieldOffset(fallbackField);
            if (!unsafe.getBooleanVolatile(base, offset)) {
                unsafe.putBooleanVolatile(base, offset, true);
                if (!earthshape$c2meFallbackConfigured) {
                    earthshape$c2meFallbackConfigured = true;
                    EarthShape.LOGGER.info("[EarthShape] Enabled C2ME CPU fallback for unsupported OpenCL density functions.");
                }
            }
        } catch (ClassNotFoundException ignored) {
            // C2ME's optional OpenCL module is not installed.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            EarthShape.LOGGER.warn("[EarthShape] Could not enable C2ME OpenCL compatibility fallback.", exception);
        }
    }
}
