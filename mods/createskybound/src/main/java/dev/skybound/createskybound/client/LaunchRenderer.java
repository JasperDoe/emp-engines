package dev.skybound.createskybound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity;
import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity.Phase;
import dev.skybound.createskybound.launch.LaunchPath;
import dev.skybound.createskybound.launch.Platform;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the launch: a charge glow on the platform, beams of light descending onto the pylons
 * and closing in, the beams spinning round the ship faster and faster, then the throw: a fading column,
 * a trail that shortens as the ship slows, and a soft ripple where it breaks through the clouds.
 * Everything is soft additive geometry, so it stays smooth and works with shaderpacks.
 */
public class LaunchRenderer implements BlockEntityRenderer<VectorPlanarComputerBlockEntity> {
    private static final int BEAM = 0xFFA8E6FF;      // pale sky blue
    private static final int CORE = 0xFFFFF4D6;      // warm white
    private static final int GOLD = 0xFFFFC860;
    private static final float SKY = 160f;           // how far above the platform the beams start

    public LaunchRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(VectorPlanarComputerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Platform p = be.platform();
        if (p == null) return;
        Phase phase = be.phase();
        float t = be.phaseTicks() + partial;
        BlockPos origin = be.getBlockPos();
        VertexConsumer vc = buffers.getBuffer(GlowRenderType.GLOW);
        Matrix4f m = pose.last().pose();

        // Local coordinates (relative to the computer block)
        float cx = (p.minX() + p.maxX() + 1) / 2f - origin.getX();
        float cz = (p.minZ() + p.maxZ() + 1) / 2f - origin.getZ();
        float py = p.y() + 1.02f - origin.getY();
        float half = Math.max(p.maxX() - p.minX(), p.maxZ() - p.minZ()) / 2f + 0.5f;
        Vector3f[] corners = new Vector3f[4];
        int i = 0;
        for (BlockPos c : p.corners()) corners[i++] = new Vector3f(c.getX() + 0.5f - origin.getX(), py, c.getZ() + 0.5f - origin.getZ());

        if (phase == Phase.IDLE) {
            float charge = be.chargeFraction();
            if (charge <= 0) return;
            // A faint ring that slowly brightens as the platform charges, and small wisps over the pylons
            float pulse = 0.8f + 0.2f * (float) Math.sin((be.getLevel().getGameTime() + partial) * 0.06);
            GlowGeometry.ring(vc, m, cx, py, cz, half * 0.94f, half, GlowGeometry.scaleAlpha(BEAM, 0.25f * charge * pulse), 0, 0);
            GlowGeometry.ring(vc, m, cx, py, cz, 0.2f, half * charge, GlowGeometry.scaleAlpha(BEAM, 0.04f * pulse), GlowGeometry.scaleAlpha(BEAM, 0.10f * pulse), 0);
            for (Vector3f c : corners)
                GlowGeometry.beam(vc, m, c, new Vector3f(c).add(0, 0.8f + 1.4f * charge, 0), 0.08f, GlowGeometry.scaleAlpha(BEAM, 0.5f * charge * pulse), 0);
            return;
        }

        Vec3 shipWorld = be.shipPosition(partial);
        Vector3f ship = new Vector3f((float) (shipWorld.x - origin.getX()), (float) (shipWorld.y - origin.getY()), (float) (shipWorld.z - origin.getZ()));
        double climb = be.climb();
        float shipBase = ship.y - (float) LaunchPath.offset(phase.ordinal() - 1, t, climb) + (float) LaunchPath.LIFT;   // ship's lifted height
        float top = py + SKY;

        switch (phase) {
            case COUNTDOWN -> {
                float k = (float) LaunchPath.easeInOut(t / LaunchPath.COUNTDOWN);
                // The platform lights up from the edge inwards, pylons flare
                GlowGeometry.ring(vc, m, cx, py, cz, half * (1 - k), half, GlowGeometry.scaleAlpha(BEAM, 0.35f), GlowGeometry.scaleAlpha(BEAM, 0.6f), 0);
                for (Vector3f c : corners) {
                    GlowGeometry.beam(vc, m, c, new Vector3f(c).add(0, 4 + 6 * k, 0), 0.15f + 0.1f * k, GlowGeometry.scaleAlpha(BEAM, 0.9f), 0);
                    GlowGeometry.disc(vc, m, c.x, py + 0.01f, c.z, 1.2f + k, GlowGeometry.scaleAlpha(BEAM, 0.6f));
                }
            }
            case BEAMS -> {
                float k = t / LaunchPath.BEAMS;
                float descend = (float) LaunchPath.easeInOut(Math.min(1, k * 1.8f));       // first: light comes down
                float closeIn = (float) LaunchPath.easeInOut(Math.max(0, (k - 0.5f) * 2));  // then: leans in to the ship
                for (Vector3f c : corners) {
                    Vector3f skyEnd = new Vector3f(cx + (c.x - cx) * 0.3f, top, cz + (c.z - cz) * 0.3f);
                    Vector3f groundEnd = new Vector3f(c).lerp(new Vector3f(ship.x, shipBase, ship.z), closeIn * 0.6f);
                    Vector3f tip = new Vector3f(skyEnd).lerp(groundEnd, descend);
                    GlowGeometry.beam(vc, m, skyEnd, tip, 0.35f, GlowGeometry.scaleAlpha(BEAM, 0.3f), BEAM);
                    if (descend >= 0.99f) {
                        GlowGeometry.disc(vc, m, groundEnd.x, Math.max(py, groundEnd.y - 0.2f), groundEnd.z, 2.5f, GlowGeometry.scaleAlpha(CORE, 0.7f));
                    }
                }
                GlowGeometry.ring(vc, m, cx, py, cz, half * 0.9f, half + 0.5f, GlowGeometry.scaleAlpha(BEAM, 0.5f), 0, 0);
            }
            case SPIN -> {
                float k = t / LaunchPath.SPIN;
                double angle = k * k * 30.0;                             // speeds up
                float radius = half * (1.0f - 0.55f * k) + 1;            // closes in
                float glow = 0.6f + 0.4f * k;
                for (int n = 0; n < 4; n++) {
                    double a = angle + n * Math.PI / 2;
                    Vector3f foot = new Vector3f(ship.x + (float) Math.cos(a) * radius, py, ship.z + (float) Math.sin(a) * radius);
                    Vector3f head = new Vector3f(ship.x, top, ship.z);
                    GlowGeometry.beam(vc, m, head, foot, 0.35f + 0.25f * k, GlowGeometry.scaleAlpha(BEAM, 0.3f), GlowGeometry.scaleAlpha(BEAM, glow));
                }
                // A spinning halo round the ship, and the platform blazing
                GlowGeometry.ring(vc, m, ship.x, shipBase - 1, ship.z, radius - 0.6f, radius + 0.4f,
                        GlowGeometry.scaleAlpha(CORE, 0.8f * glow), GlowGeometry.scaleAlpha(BEAM, 0.0f), (float) angle);
                GlowGeometry.disc(vc, m, cx, py, cz, half + 1, GlowGeometry.scaleAlpha(GOLD, 0.25f + 0.35f * k));
                GlowGeometry.beam(vc, m, new Vector3f(cx, py, cz), new Vector3f(ship.x, shipBase - 1, ship.z), 0.6f + 1.2f * k,
                        GlowGeometry.scaleAlpha(CORE, 0.5f * k), GlowGeometry.scaleAlpha(CORE, 0.8f * k));
            }
            case LAUNCH -> {
                // The column of light the ship rode up on, thinning away over the climb
                float fade = 1 - (float) LaunchPath.easeIn(t / LaunchPath.RISE);
                if (fade > 0.01f) {
                    GlowGeometry.beam(vc, m, new Vector3f(cx, py, cz), new Vector3f(cx, Math.max(top, ship.y) + 40, cz), (1.6f + half * 0.3f) * fade,
                            GlowGeometry.scaleAlpha(CORE, 0.8f * fade), GlowGeometry.scaleAlpha(BEAM, 0.3f * fade));
                    GlowGeometry.disc(vc, m, cx, py, cz, half + 1, GlowGeometry.scaleAlpha(GOLD, 0.3f * fade));
                }
                // The trail: long while the ship is fast, shrinking to a glow as it slows
                double speed = LaunchPath.offset(3, t, climb) - LaunchPath.offset(3, Math.max(0, t - 1), climb);
                float len = (float) Math.min(90, speed * 9);
                float bright = (float) Math.min(1, 0.25 + speed / 3);
                if (len > 0.5f)
                    GlowGeometry.beam(vc, m, new Vector3f(ship.x, ship.y - 2 - len, ship.z), new Vector3f(ship.x, ship.y - 2, ship.z), 1.0f + 0.3f * half * bright,
                            GlowGeometry.scaleAlpha(BEAM, 0f), GlowGeometry.scaleAlpha(CORE, bright));
                // A soft halo under the hull while it drifts above the clouds
                float drift = Math.max(0, (t - LaunchPath.RISE) / LaunchPath.DRIFT);
                if (drift > 0)
                    GlowGeometry.ring(vc, m, ship.x, ship.y - 2, ship.z, half * 0.6f, half + 2,
                            GlowGeometry.scaleAlpha(CORE, 0.25f * (float) Math.sin(Math.PI * drift)), 0, (float) (t * 0.02));
                // A ripple across the cloud tops where the ship broke through
                double cross = LaunchPath.tickAtHeight(be.cloudY() - be.shipStart().y, climb);
                if (cross >= 0 && t > cross) {
                    float r = (float) ((t - cross) / 50.0);
                    if (r < 1) {
                        float cyLocal = (float) (be.cloudY() - origin.getY());
                        float radius = half + 4 + 40 * (float) LaunchPath.easeOutQuart(r);
                        GlowGeometry.ring(vc, m, ship.x, cyLocal, ship.z, radius * 0.8f, radius,
                                GlowGeometry.scaleAlpha(CORE, 0.35f * (1 - r)), 0, 0);
                    }
                }
            }
            default -> { }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(VectorPlanarComputerBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 512; }
}
