package dev.empengines.engines.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * The launcher's render types.
 * <ul>
 *   <li>{@link #GLOW}: additive, unlit, double-sided, no depth writes: overlapping glow layers add up smoothly
 *       instead of hiding each other (lightning's shader and blending, without its depth writes). Depth-tested,
 *       so the ship and the world still hide what's behind them.</li>
 *   <li>{@link #GLOW_THROUGH}: the same but not depth-tested: the parts of the beams above the cloud base, so the
 *       clouds can't swallow them.</li>
 *   <li>{@link #GEM}: plain coloured faces, full bright, for the pylons' shards and rings.</li>
 * </ul>
 */
public final class GlowRenderType extends RenderType {
    public static final RenderType GLOW = create("emp_engines_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    public static final RenderType GLOW_THROUGH = create("emp_engines_glow_through", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    public static final RenderType GEM = create("emp_engines_gem", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            1536, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private GlowRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
