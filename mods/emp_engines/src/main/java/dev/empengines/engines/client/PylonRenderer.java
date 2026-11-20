package dev.empengines.engines.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.empengines.engines.block.PylonBlock;
import dev.empengines.engines.block.PylonBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A Resonance Pylon's shard and ring: a crystal shard floating and slowly turning above the brass base,
 * with a broken gold ring orbiting it. Amber for tier 1, cyan for tier 2, violet for tier 3. As the
 * launcher charges ({@link PylonBlock#CHARGE}) the shard glows brighter and the ring spins faster.
 *
 * The base is a block model in Create's style (tools/art/resonance_pylon.py); the shard and ring are drawn here.
 */
public class PylonRenderer implements BlockEntityRenderer<PylonBlockEntity> {
    /** Height of the shard's middle above the block's bottom: where the launch beams land. */
    public static final float GEM_Y = 1.05f;
    static final int AMBER = 0xFFFFA62B, CYAN = 0xFF38E1FF, VIOLET = 0xFFB25BFF, GOLD = 0xFFE9B949;
    private static final int SIDES = 6;
    private static final Vector3f LIGHT = new Vector3f(0.4f, 0.8f, 0.3f).normalize();

    public PylonRenderer(BlockEntityRendererProvider.Context ctx) {}

    public static int colour(int tier) { return tier >= 3 ? VIOLET : tier == 2 ? CYAN : AMBER; }

    @Override
    public void render(PylonBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof PylonBlock block) || be.getLevel() == null) return;
        float charge = state.getValue(PylonBlock.CHARGE) / 4f;
        float time = be.getLevel().getGameTime() + partial;
        float seed = (be.getBlockPos().getX() * 31 + be.getBlockPos().getZ() * 17) % 100;
        int colour = colour(block.tier());

        pose.pushPose();
        pose.translate(0.5f, GEM_Y + 0.05f * (float) Math.sin(time * 0.07f + seed), 0.5f);

        // The shard
        VertexConsumer vc = buffers.getBuffer(GlowRenderType.GEM);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(time * (0.012f + 0.03f * charge) + seed));
        shard(vc, pose.last().pose(), colour, charge);
        pose.popPose();

        // The ring: tilted, its tilt slowly swinging round, spinning on its own axis
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(time * 0.01f + seed * 0.3f));
        pose.mulPose(Axis.XP.rotation(0.38f + 0.06f * (float) Math.sin(time * 0.05f + seed)));
        pose.mulPose(Axis.YP.rotation(time * (0.05f + 0.22f * charge)));
        ring(vc, pose.last().pose(), 0.31f, 0.03f, 0.022f);
        pose.popPose();

        // The glow round the shard, and on the base
        VertexConsumer glow = buffers.getBuffer(GlowRenderType.GLOW);
        Matrix4f m = pose.last().pose();
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * (0.1f + 0.2f * charge));
        float a = (0.12f + 0.6f * charge) * pulse;
        GlowGeometry.beam(glow, m, new Vector3f(0, -0.32f, 0), new Vector3f(0, 0.44f, 0), 0.07f + 0.05f * charge,
                GlowGeometry.scaleAlpha(colour, a), GlowGeometry.scaleAlpha(colour, a));
        GlowGeometry.disc(glow, m, 0, 0.25f - GEM_Y + 0.01f, 0, 0.35f + 0.25f * charge, GlowGeometry.scaleAlpha(colour, 0.15f + 0.35f * charge));
        pose.popPose();
    }

    /** A six-sided crystal: a long point up, a short point down, a slightly uneven waist. */
    private static void shard(VertexConsumer vc, Matrix4f m, int colour, float charge) {
        Vector3f top = new Vector3f(0, 0.44f, 0), bottom = new Vector3f(0, -0.32f, 0);
        Vector3f[] waist = new Vector3f[SIDES];
        for (int i = 0; i < SIDES; i++) {
            double t = 2 * Math.PI * i / SIDES;
            float r = i % 2 == 0 ? 0.17f : 0.14f;
            waist[i] = new Vector3f((float) Math.cos(t) * r, i % 3 == 0 ? 0.04f : 0.0f, (float) Math.sin(t) * r);
        }
        for (int i = 0; i < SIDES; i++) {
            Vector3f a = waist[i], b = waist[(i + 1) % SIDES];
            tri(vc, m, top, a, b, colour, charge, 0.92f);
            tri(vc, m, bottom, b, a, colour, charge, 0.92f);
        }
    }

    /** A gold ring in three arcs (so its spin shows), as a flat band with an inner and outer face. */
    private static void ring(VertexConsumer vc, Matrix4f m, float radius, float halfHeight, float thickness) {
        int segs = 30;
        for (int i = 0; i < segs; i++) {
            if (i % 10 >= 8) continue;   // the gaps
            double t0 = 2 * Math.PI * i / segs, t1 = 2 * Math.PI * (i + 1) / segs;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float ro = radius + thickness, ri = radius;
            Vector3f o0b = new Vector3f(c0 * ro, -halfHeight, s0 * ro), o1b = new Vector3f(c1 * ro, -halfHeight, s1 * ro);
            Vector3f o0t = new Vector3f(c0 * ro, halfHeight, s0 * ro), o1t = new Vector3f(c1 * ro, halfHeight, s1 * ro);
            Vector3f i0b = new Vector3f(c0 * ri, -halfHeight, s0 * ri), i1b = new Vector3f(c1 * ri, -halfHeight, s1 * ri);
            Vector3f i0t = new Vector3f(c0 * ri, halfHeight, s0 * ri), i1t = new Vector3f(c1 * ri, halfHeight, s1 * ri);
            Vector3f out = new Vector3f((c0 + c1) / 2, 0, (s0 + s1) / 2).normalize();
            quad(vc, m, o0b, o1b, o1t, o0t, shade(GOLD, out, 0));
            quad(vc, m, i0b, i1b, i1t, i0t, shade(GOLD, new Vector3f(out).negate(), 0));
            quad(vc, m, o0t, o1t, i1t, i0t, shade(GOLD, new Vector3f(0, 1, 0), 0));
            quad(vc, m, o0b, o1b, i1b, i0b, shade(GOLD, new Vector3f(0, -1, 0), 0));
        }
    }

    private static void tri(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, int colour, float charge, float alpha) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        int col = shade(colour, n, charge);
        col = GlowGeometry.scaleAlpha(col | 0xFF000000, alpha);
        vertex(vc, m, a, col); vertex(vc, m, b, col); vertex(vc, m, c, col); vertex(vc, m, c, col);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int col) {
        vertex(vc, m, a, col); vertex(vc, m, b, col); vertex(vc, m, c, col); vertex(vc, m, d, col);
    }

    /** Facet shading (light from above), brightened towards white by the charge. */
    private static int shade(int argb, Vector3f normal, float charge) {
        float s = 0.5f + 0.5f * Math.max(0, Math.abs(normal.dot(LIGHT)));
        s = Math.min(1.2f, s + 0.25f * charge);
        float w = 0.35f * charge;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r = clamp(Math.round((r * s) * (1 - w) + 255 * w));
        g = clamp(Math.round((g * s) * (1 - w) + 255 * w));
        b = clamp(Math.round((b * s) * (1 - w) + 255 * w));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, int argb) {
        vc.addVertex(m, p.x, p.y, p.z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    @Override
    public AABB getRenderBoundingBox(PylonBlockEntity be) { return be.renderBox(); }

    @Override
    public int getViewDistance() { return 128; }
}
