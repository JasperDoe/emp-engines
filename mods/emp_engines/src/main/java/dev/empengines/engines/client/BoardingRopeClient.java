package dev.empengines.engines.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.raid.BoardingRope;
import dev.empengines.engines.raid.BoardingSync;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.List;
import java.util.function.Function;

/**
 * The boarding ropes on the client: drawn with Create Aeronautics' own rope segment model (so they look
 * like its ropes), and walkable. A player whose feet are on a rope is held on top of it and carried along
 * with it as the ships fly; sneak to drop off.
 */
public final class BoardingRopeClient {
    /** How far to the side of the rope your feet can be and still be on it, and how high its top is above its middle. */
    static final double WIDTH = 0.45, TOP = 0.12;

    private static MultiBufferSource.BufferSource buffers;
    private static boolean failed;

    // --- drawing -------------------------------------------------------------------------------------

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || failed) return;
        List<BoardingRope> ropes = BoardingSync.CLIENT_ROPES;
        ClientLevel level = Minecraft.getInstance().level;
        if (ropes.isEmpty() || level == null) return;
        try {
            if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
            float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
            Function<SubLevel, Pose3dc> pose = s -> s instanceof ClientSubLevel c ? c.renderPose(partial) : s.logicalPose();
            Vec3 cam = e.getCamera().getPosition();
            SuperByteBuffer segment = CachedBuffers.partialFacing(SimPartialModels.ROPE, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
            VertexConsumer vc = buffers.getBuffer(RenderType.solid());
            PoseStack ps = e.getPoseStack();
            for (BoardingRope rope : ropes) {
                List<Vec3> pts = rope.points(level, pose);
                for (int i = 1; i < pts.size(); i++) {
                    Vec3 a = pts.get(i - 1), b = pts.get(i), d = b.subtract(a);
                    float len = (float) d.length();
                    if (len < 1e-3) continue;
                    ps.pushPose();
                    ps.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
                    ps.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len)));
                    ps.scale(1, len, 1);
                    ps.translate(-0.5, 0, -0.5);
                    segment.light(LevelRenderer.getLightColor(level, BlockPos.containing(a.lerp(b, 0.5)))).renderInto(ps, vc);
                    ps.popPose();
                }
            }
            buffers.endBatch();
        } catch (Throwable t) {
            failed = true;   // e.g. Create Aeronautics changed its rope model: don't spam every frame
            EmpyreanEngines.LOGGER.error("Sky Raiders: couldn't draw boarding ropes", t);
        }
    }

    // --- walking -------------------------------------------------------------------------------------

    /** After the player has moved this tick: if their feet are on a rope, keep them on top and carry them with it. */
    public static void walk(LocalPlayer p) {
        List<BoardingRope> ropes = BoardingSync.CLIENT_ROPES;
        if (ropes.isEmpty() || p.isSpectator() || p.getAbilities().flying || p.isShiftKeyDown() || p.isPassenger()) return;
        Vec3 feet = p.position();
        if (p.getDeltaMovement().y > 0.25) return;   // jumping up off it
        for (BoardingRope rope : ropes) {
            List<Vec3> now = rope.points(p.level(), SubLevel::logicalPose);
            double[] where = BoardingRope.nearest(now, feet, WIDTH);
            if (where == null) continue;
            Vec3 on = BoardingRope.at(now, where);
            double top = on.y + TOP;
            if (feet.y < top - 0.6 || feet.y > top + 0.35) continue;
            // Carried with the rope: how far this spot on it moved since last tick.
            Vec3 was = BoardingRope.at(rope.points(p.level(), SubLevel::lastPose), where);
            Vec3 carry = on.subtract(was);
            if (carry.lengthSqr() > 16) carry = Vec3.ZERO;   // a jump in the poses, not motion
            p.setPos(feet.x + carry.x, top, feet.z + carry.z);
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, 0, v.z);
            p.setOnGround(true);
            p.resetFallDistance();
            return;
        }
    }

    private BoardingRopeClient() {}
}
