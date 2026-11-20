package dev.empengines.researchenchanting.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Research resource flying into the table's book: a real item model rises from the container in a slow arc,
 * spinning, and shrinks into the open pages with a soft page sound and a puff of glyphs.
 */
public final class Flights {
    private static final int DURATION = 36;   // ticks per flight
    private static final List<Flight> LIVE = new ArrayList<>();

    private record Flight(BlockPos table, Vec3 from, ItemStack stack, long start) {}
    /** Containers research pulled from lately, per table, and when: glyphs trail from them (Glyphs). */
    private static final java.util.Map<BlockPos, java.util.Map<BlockPos, Long>> SOURCES = new java.util.HashMap<>();

    public static java.util.Collection<BlockPos> recentSources(BlockPos table, long now) {
        java.util.Map<BlockPos, Long> m = SOURCES.get(table);
        if (m == null) return java.util.List.of();
        m.values().removeIf(t -> now - t > 40);
        if (m.isEmpty()) { SOURCES.remove(table); return java.util.List.of(); }
        return java.util.List.copyOf(m.keySet());
    }

    public static void add(BlockPos table, BlockPos from, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.isEmpty()) return;
        LIVE.add(new Flight(table, Vec3.atCenterOf(from).add(0, 0.55, 0), stack.copyWithCount(1), mc.level.getGameTime()));
        SOURCES.computeIfAbsent(table.immutable(), k -> new java.util.HashMap<>()).put(from.immutable(), mc.level.getGameTime());
        if (LIVE.size() > 48) LIVE.remove(0);
    }

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); SOURCES.clear(); return; }
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Iterator<Flight> it = LIVE.iterator();
        while (it.hasNext()) {
            Flight f = it.next();
            float t = (now - f.start + partial) / DURATION;
            if (t >= 1) {
                it.remove();
                Vec3 book = book(f.table);
                mc.level.playLocalSound(book.x, book.y, book.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.35f, 0.9f + mc.level.random.nextFloat() * 0.2f, false);
                Glyphs.burst(mc.level, book, 8);
                continue;
            }
            if (t < 0) continue;
            Vec3 p0 = f.from, p2 = book(f.table);
            Vec3 p1 = p0.add(p2).scale(0.5).add(0, 1.1, 0);
            float u = 1 - t;
            Vec3 at = p0.scale(u * u).add(p1.scale(2 * u * t)).add(p2.scale(t * t));
            float scale = 0.42f * (1 - Mth.clamp((t - 0.7f) / 0.3f, 0, 1));
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(t * 540));
            pose.scale(scale, scale, scale);
            mc.getItemRenderer().renderStatic(f.stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    pose, buffers, mc.level, 0);
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Where the floating book's pages are. */
    private static Vec3 book(BlockPos table) {
        return new Vec3(table.getX() + 0.5, table.getY() + 0.85, table.getZ() + 0.5);
    }

    private Flights() {}
}
