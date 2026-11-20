package dev.empengines.engines.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.empengines.engines.block.VectorPlanarComputerBlockEntity;
import dev.empengines.engines.block.VectorPlanarComputerBlockEntity.Phase;
import dev.empengines.engines.launch.LaunchPath;
import dev.empengines.engines.launch.LaunchTracker;
import dev.empengines.engines.launch.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Draws the launch, after the clouds and weather (so the clouds can't hide it):
 * <ol>
 *   <li>BEAMS: light comes down from the sky onto each pylon's shard, then the beams' feet slide out (or in)
 *       to a circle round the ship, wide enough that spinning never cuts through it</li>
 *   <li>SPIN: the beams circle faster and faster, smeared by ghost trails, until they blend into one wall of
 *       light round the ship; the far side of the wall is bright and the near side faint, so the ship stands
 *       out against it as a dark silhouette</li>
 *   <li>LAUNCH: the ship is gone upwards in a streak with fading afterimages, and the wall collapses after it</li>
 * </ol>
 * Above the cloud base the beams are drawn without depth testing, so they show through vanilla clouds;
 * where they cross the cloud tops there's a soft flare, for shaderpack clouds drawn later still.
 * Charging has no effects here: the pylons themselves glow ({@link PylonRenderer}).
 */
public final class LaunchRenderer {
    private static final int BEAM = 0xFFA8E6FF;      // pale sky blue
    private static final int CORE = 0xFFFFF4D6;      // warm white
    private static final int GOLD = 0xFFFFC860;
    private static final float SKY = 160f;           // how far above the platform the beams start
    private static final float WIDTH = 0.7f;         // all beam widths
    private static final int GHOSTS = 6;

    private static MultiBufferSource.BufferSource depthBuffers, throughBuffers;

    /** Called from RenderLevelStageEvent. */
    public static void renderAll(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Set<VectorPlanarComputerBlockEntity> active = LaunchTracker.active();
        Minecraft mc = Minecraft.getInstance();
        if (active.isEmpty() || mc.level == null) return;
        if (depthBuffers == null) {
            depthBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
            throughBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
        }
        Vec3 cam = e.getCamera().getPosition();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        Matrix4f m = pose.last().pose();
        Draw d = new Draw(m, depthBuffers.getBuffer(GlowRenderType.GLOW), throughBuffers.getBuffer(GlowRenderType.GLOW_THROUGH), cam);
        for (VectorPlanarComputerBlockEntity be : active) {
            if (be.getLevel() != mc.level || be.platform() == null) continue;
            d.cloud = (float) (be.cloudY() - cam.y);
            render(be, partial, d);
        }
        pose.popPose();
        depthBuffers.endBatch();
        throughBuffers.endBatch();
    }

    /** Where to draw, relative to the camera, and the cloud base height that splits depth-tested from not. */
    private static final class Draw {
        final Matrix4f m;
        final VertexConsumer depth, through;
        final Vec3 cam;
        float cloud;
        Draw(Matrix4f m, VertexConsumer depth, VertexConsumer through, Vec3 cam) { this.m = m; this.depth = depth; this.through = through; this.cam = cam; }

        boolean y0Above(float y) { return y > cloud; }

        Vector3f at(double x, double y, double z) { return new Vector3f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)); }

        /** A beam, split at the cloud base: depth-tested below, drawn through the clouds above. */
        void beam(Vector3f a, Vector3f b, float radius, int colourA, int colourB) {
            radius *= WIDTH;
            boolean aUp = a.y > cloud, bUp = b.y > cloud;
            if (aUp == bUp) {
                GlowGeometry.beam(aUp ? through : depth, m, a, b, radius, colourA, colourB);
                return;
            }
            float s = (cloud - a.y) / (b.y - a.y);
            Vector3f mid = new Vector3f(a).lerp(b, s);
            int cm = mix(colourA, colourB, s);
            GlowGeometry.beam(aUp ? through : depth, m, a, mid, radius, colourA, cm);
            GlowGeometry.beam(bUp ? through : depth, m, mid, b, radius, cm, colourB);
        }

        /** A soft flare on the cloud tops where a beam goes through them. */
        void flare(Vector3f foot, float strength) {
            if (strength <= 0.01f) return;
            GlowGeometry.disc(through, m, foot.x, cloud + 5, foot.z, 3.5f, GlowGeometry.scaleAlpha(CORE, 0.35f * strength));
        }

        /**
         * A wall of light round (cx, cz), from y0 to y1 (camera-relative): bright on the far side from the
         * camera and faint on the near side, fading out towards the top. Split at the cloud base like the beams.
         */
        void wall(float cx, float cz, float r, float y0, float y1, int colour, float alpha) {
            if (alpha <= 0.005f || y1 <= y0) return;
            if (y0 < cloud && y1 > cloud) {
                float s = (cloud - y0) / (y1 - y0);
                wallPart(depth, cx, cz, r, y0, cloud, colour, alpha, alpha * (1 - s));
                wallPart(through, cx, cz, r, cloud, y1, colour, alpha * (1 - s), 0);
            } else wallPart(y0 >= cloud ? through : depth, cx, cz, r, y0, y1, colour, alpha, 0);
        }

        private void wallPart(VertexConsumer vc, float cx, float cz, float r, float y0, float y1, int colour, float a0, float a1) {
            int segs = 48;
            // The camera is at (0, 0, 0): which side of the circle faces it.
            float hx = -cx, hz = -cz;
            float hd = (float) Math.sqrt(hx * hx + hz * hz);
            boolean inside = hd < r * 0.6f;
            for (int i = 0; i < segs; i++) {
                double t0 = 2 * Math.PI * i / segs, t1 = 2 * Math.PI * (i + 1) / segs, tm = (t0 + t1) / 2;
                float facing = inside ? 0 : (float) ((Math.cos(tm) * hx + Math.sin(tm) * hz) / hd);   // +1 = near side
                float k = inside ? 0.6f : (float) Math.pow((1 - facing) / 2, 1.5);
                float lit = 0.08f + 0.92f * k;
                int bottom = GlowGeometry.scaleAlpha(colour, a0 * lit), top = GlowGeometry.scaleAlpha(colour, a1 * lit);
                float x0 = cx + (float) Math.cos(t0) * r, z0 = cz + (float) Math.sin(t0) * r;
                float x1 = cx + (float) Math.cos(t1) * r, z1 = cz + (float) Math.sin(t1) * r;
                GlowGeometry.quad(vc, m, new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z1), new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z0),
                        bottom, bottom, top, top);
            }
        }
    }

    private static void render(VectorPlanarComputerBlockEntity be, float partial, Draw d) {
        Platform p = be.platform();
        Phase phase = be.phase();
        float t = be.phaseTicks() + partial;
        double climb = be.climb();
        Vec3 c = be.shipCentre(partial);
        float py = (float) (p.y() + 1.02 - d.cam.y);
        float r = (float) be.ringRadius();
        Vector3f centre = d.at(c.x, p.y() + 1.02, c.z);
        float top = Math.max(py + SKY, d.cloud + 40);
        float shipBottom = (float) (be.shipBottom(partial) - d.cam.y), shipTop = (float) (be.shipTop(partial) - d.cam.y);

        // Each pylon's shard, and the angle round the ship the beam from it ends up at.
        Vector3f[] gems = new Vector3f[4];
        double[] angle = new double[4];
        int i = 0;
        for (BlockPos pylon : p.pylons()) {
            gems[i] = d.at(pylon.getX() + 0.5, pylon.getY() + PylonRenderer.GEM_Y, pylon.getZ() + 0.5);
            angle[i] = Math.atan2(gems[i].z - centre.z, gems[i].x - centre.x);
            i++;
        }

        switch (phase) {
            case BEAMS -> {
                float k = t / LaunchPath.BEAMS;
                float descend = (float) LaunchPath.easeInOut(Math.min(1, k * 2));
                float slide = (float) LaunchPath.easeInOut(Math.max(0, (k - 0.5f) * 2));
                for (int n = 0; n < 4; n++) {
                    Vector3f ring = new Vector3f(centre.x + (float) Math.cos(angle[n]) * r, py, centre.z + (float) Math.sin(angle[n]) * r);
                    Vector3f foot = new Vector3f(gems[n]).lerp(ring, slide);
                    Vector3f sky = new Vector3f(foot.x, top, foot.z);
                    Vector3f tip = new Vector3f(sky).lerp(foot, descend);
                    d.beam(sky, tip, 0.35f, GlowGeometry.scaleAlpha(BEAM, 0.3f), BEAM);
                    if (descend >= 0.99f) {
                        float flash = 1 - Math.min(1, (k - 0.5f) * 6);
                        GlowGeometry.disc(d.depth, d.m, foot.x, foot.y - (slide > 0 ? 0 : 0.3f), foot.z, 1.2f + 1.2f * slide,
                                GlowGeometry.scaleAlpha(CORE, 0.5f + 0.4f * Math.max(0, flash)));
                    }
                    d.flare(foot, descend);
                }
            }
            case SPIN -> {
                float k = t / LaunchPath.SPIN;
                double spun = 0.02 * t + 0.9 * t * t * t / (3.0 * LaunchPath.SPIN * LaunchPath.SPIN);
                double omega = 0.02 + 0.9 * k * k;                       // radians a tick
                float wall = smooth(0.35f, 0.85f, k);
                float beamAlpha = 1 - 0.65f * wall;
                double gap = Math.min(omega * 0.5, Math.PI / 2 / GHOSTS * 1.2);
                for (int n = 0; n < 4; n++) {
                    for (int g = 0; g <= GHOSTS; g++) {
                        float ghost = g == 0 ? 1 : 0.45f * (1 - g / (GHOSTS + 1f)) * (float) Math.min(1, omega * 4);
                        if (ghost <= 0.01f) continue;
                        double a = angle[n] + spun - g * gap;
                        Vector3f foot = new Vector3f(centre.x + (float) Math.cos(a) * r, py, centre.z + (float) Math.sin(a) * r);
                        d.beam(new Vector3f(foot.x, top, foot.z), foot, 0.35f + 0.15f * k,
                                GlowGeometry.scaleAlpha(BEAM, 0.3f * beamAlpha * ghost), GlowGeometry.scaleAlpha(BEAM, beamAlpha * ghost));
                        if (g == 0) d.flare(foot, beamAlpha);
                    }
                }
                // The wall of light the beams blur into: bright behind the ship, faint in front of it
                float wallTop = shipTop + 6;
                d.wall(centre.x, centre.z, r, py, wallTop, mix(BEAM, CORE, k), 0.6f * wall);
                d.wall(centre.x, centre.z, r, wallTop, top, BEAM, 0.18f * wall);
                GlowGeometry.ring(d.depth, d.m, centre.x, py + 0.02f, centre.z, r - 0.5f, r + 0.5f,
                        GlowGeometry.scaleAlpha(CORE, 0.5f * (0.3f + 0.7f * k)), 0, 0);
            }
            case LAUNCH -> {
                float speed = (float) LaunchPath.speed(t, climb);
                float height = shipTop - shipBottom;
                // The wall collapses after the ship: narrowing, rising and fading
                float collapse = t / 25f;
                if (collapse < 1) {
                    float rr = r * (1 - 0.85f * (float) LaunchPath.easeIn(collapse));
                    float bottom = Math.max(py, shipBottom - 2 - 30 * (1 - collapse));
                    d.wall(centre.x, centre.z, rr, bottom, shipTop + 6 + 25 * collapse, CORE, 0.6f * (1 - collapse));
                }
                // The column of light the ship rode up on, thinning away over the climb
                float fade = 1 - (float) LaunchPath.easeIn(t / LaunchPath.RISE);
                if (fade > 0.01f) {
                    d.beam(new Vector3f(centre.x, py, centre.z), new Vector3f(centre.x, Math.max(top, shipTop) + 40, centre.z), (1.6f + r * 0.2f) * fade,
                            GlowGeometry.scaleAlpha(CORE, 0.8f * fade), GlowGeometry.scaleAlpha(BEAM, 0.3f * fade));
                    GlowGeometry.disc(d.depth, d.m, centre.x, py, centre.z, r + 1, GlowGeometry.scaleAlpha(GOLD, 0.3f * fade));
                }
                // The streak: long while the ship is fast, shrinking as it coasts
                float len = Math.min(90, speed * 9);
                float bright = Math.min(1, 0.25f + speed / 3);
                if (len > 0.5f)
                    d.beam(new Vector3f(centre.x, shipBottom - len, centre.z), new Vector3f(centre.x, shipBottom, centre.z), 1.0f + 0.25f * r * bright,
                            GlowGeometry.scaleAlpha(BEAM, 0f), GlowGeometry.scaleAlpha(CORE, bright));
                // Afterimages: glowing shells where the ship just was
                float blur = Math.min(1, speed / 1.5f);
                for (int g = 1; g <= 5 && blur > 0.05f; g++) {
                    double tp = t - g * 1.2;
                    if (tp < 0) break;
                    float back = (float) (LaunchPath.offset(3, t, climb) - LaunchPath.offset(3, tp, climb));
                    float a = 0.22f * blur * (1 - g / 6f);
                    float y0 = shipBottom - back;
                    GlowGeometry.cylinder(d.y0Above(y0) ? d.through : d.depth, d.m, new Vector3f(centre.x, y0, centre.z),
                            new Vector3f(centre.x, y0 + height, centre.z), r - 2, GlowGeometry.scaleAlpha(CORE, a), GlowGeometry.scaleAlpha(BEAM, a * 0.3f));
                }
                // A soft halo under the hull while it coasts above the clouds
                float drift = Math.max(0, (t - LaunchPath.RISE) / LaunchPath.DRIFT);
                if (drift > 0)
                    GlowGeometry.ring(shipBottom > d.cloud ? d.through : d.depth, d.m, centre.x, shipBottom - 1, centre.z, (r - 2) * 0.6f, r,
                            GlowGeometry.scaleAlpha(CORE, 0.25f * (float) Math.sin(Math.PI * drift)), 0, (float) (t * 0.02));
                // A ripple across the cloud tops where the ship broke through
                double cross = LaunchPath.tickAtHeight(be.cloudY() - be.shipStart().y, climb);
                if (cross >= 0 && t > cross) {
                    float rp = (float) ((t - cross) / 50.0);
                    if (rp < 1) {
                        float radius = r + 4 + 40 * (float) LaunchPath.easeOutQuart(rp);
                        GlowGeometry.ring(d.through, d.m, centre.x, d.cloud + 4, centre.z, radius * 0.8f, radius,
                                GlowGeometry.scaleAlpha(CORE, 0.35f * (1 - rp)), 0, 0);
                    }
                }
            }
            default -> { }
        }
    }

    private static float smooth(float a, float b, float x) {
        float s = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return s * s * (3 - 2 * s);
    }

    private static int mix(int a, int b, float s) {
        s = Math.max(0, Math.min(1, s));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 255, cb = (b >>> shift) & 255;
            out |= Math.round(ca + (cb - ca) * s) << shift;
        }
        return out;
    }

    private LaunchRenderer() {}
}
