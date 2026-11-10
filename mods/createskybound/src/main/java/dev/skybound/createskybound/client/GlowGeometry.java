package dev.skybound.createskybound.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Soft glowing shapes drawn with additive blending ({@link GlowRenderType}).
 * A "beam" is three nested cylinders: a bright core and two wide, faint shells,
 * which reads as one smooth glow instead of hard edges.
 */
public final class GlowGeometry {
    private static final int SEGMENTS = 16;

    /** A glowing beam from a to b. Colours are ARGB; alpha fades from colourA at a to colourB at b. */
    public static void beam(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, float radius, int colourA, int colourB) {
        cylinder(vc, m, a, b, radius * 0.35f, brighten(colourA, 1.0f), brighten(colourB, 1.0f));   // white-hot core
        cylinder(vc, m, a, b, radius, scaleAlpha(colourA, 0.55f), scaleAlpha(colourB, 0.55f));
        cylinder(vc, m, a, b, radius * 2.4f, scaleAlpha(colourA, 0.18f), scaleAlpha(colourB, 0.18f));
        cylinder(vc, m, a, b, radius * 5.0f, scaleAlpha(colourA, 0.06f), scaleAlpha(colourB, 0.06f));
    }

    /** One open cylinder (the render type draws both sides). */
    public static void cylinder(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, float r, int colourA, int colourB) {
        Vector3f dir = new Vector3f(b).sub(a);
        if (dir.lengthSquared() < 1e-6f) return;
        dir.normalize();
        Vector3f u = Math.abs(dir.y) < 0.99f ? new Vector3f(0, 1, 0).cross(dir).normalize() : new Vector3f(1, 0, 0).cross(dir).normalize();
        Vector3f v = new Vector3f(dir).cross(u).normalize();
        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = 2 * Math.PI * i / SEGMENTS, t1 = 2 * Math.PI * (i + 1) / SEGMENTS;
            Vector3f o0 = new Vector3f(u).mul((float) Math.cos(t0) * r).add(new Vector3f(v).mul((float) Math.sin(t0) * r));
            Vector3f o1 = new Vector3f(u).mul((float) Math.cos(t1) * r).add(new Vector3f(v).mul((float) Math.sin(t1) * r));
            Vector3f a0 = new Vector3f(a).add(o0), a1 = new Vector3f(a).add(o1);
            Vector3f b0 = new Vector3f(b).add(o0), b1 = new Vector3f(b).add(o1);
            quad(vc, m, a0, a1, b1, b0, colourA, colourA, colourB, colourB);
        }
    }

    /** A flat glowing ring (annulus) lying in the horizontal plane at height y. */
    public static void ring(VertexConsumer vc, Matrix4f m, float cx, float y, float cz, float inner, float outer, int colourInner, int colourOuter, float spin) {
        int segs = 48;
        for (int i = 0; i < segs; i++) {
            double t0 = spin + 2 * Math.PI * i / segs, t1 = spin + 2 * Math.PI * (i + 1) / segs;
            Vector3f i0 = new Vector3f(cx + (float) Math.cos(t0) * inner, y, cz + (float) Math.sin(t0) * inner);
            Vector3f i1 = new Vector3f(cx + (float) Math.cos(t1) * inner, y, cz + (float) Math.sin(t1) * inner);
            Vector3f o0 = new Vector3f(cx + (float) Math.cos(t0) * outer, y, cz + (float) Math.sin(t0) * outer);
            Vector3f o1 = new Vector3f(cx + (float) Math.cos(t1) * outer, y, cz + (float) Math.sin(t1) * outer);
            quad(vc, m, i0, i1, o1, o0, colourInner, colourInner, colourOuter, colourOuter);
        }
    }

    /** A soft glowing disc (a ring with no hole), brightest at the centre. */
    public static void disc(VertexConsumer vc, Matrix4f m, float cx, float y, float cz, float radius, int colour) {
        ring(vc, m, cx, y, cz, 0.001f, radius, colour, colour & 0x00FFFFFF, 0);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int c0, int c1, int c2, int c3) {
        vertex(vc, m, p0, c0); vertex(vc, m, p1, c1); vertex(vc, m, p2, c2); vertex(vc, m, p3, c3);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, int argb) {
        vc.addVertex(m, p.x, p.y, p.z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    public static int scaleAlpha(int argb, float f) {
        int a = Math.round(((argb >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Mixes the colour towards white (for the core). */
    public static int brighten(int argb, float f) {
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r = Math.round(r + (255 - r) * f * 0.8f);
        g = Math.round(g + (255 - g) * f * 0.8f);
        b = Math.round(b + (255 - b) * f * 0.8f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private GlowGeometry() {}
}
