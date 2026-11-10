package dev.skybound.createskybound.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Additive, unlit, double-sided and no depth writes: overlapping glow layers add up
 * smoothly instead of hiding each other. (Lightning's shader and blending, without its depth writes.)
 */
public final class GlowRenderType extends RenderType {
    public static final RenderType GLOW = create("createskybound_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private GlowRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
