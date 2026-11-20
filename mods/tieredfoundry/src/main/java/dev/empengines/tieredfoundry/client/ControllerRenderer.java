package dev.empengines.tieredfoundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Inside a formed foundry: the items waiting to melt stand on the floor (one per inside block, filling layer by
 * layer, shrinking as they melt), and the molten metal fills the inside from the floor up, its surface showing the
 * top fluid.
 */
public class ControllerRenderer implements BlockEntityRenderer<ControllerBlockEntity> {
    public ControllerRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ControllerBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isFormed() || be.getLevel() == null || !be.sane()) return;
        BlockPos pos = be.getBlockPos(), lo = be.interiorMin(), hi = be.interiorMax();
        int w = hi.getX() - lo.getX() + 1, d = hi.getZ() - lo.getZ() + 1, h = hi.getY() - lo.getY() + 1;
        if (w <= 0 || d <= 0 || h <= 0) return;
        float ox = lo.getX() - pos.getX(), oy = lo.getY() - pos.getY(), oz = lo.getZ() - pos.getZ();

        // molten metal: its height is how full the foundry is
        List<FluidStack> molten = be.molten();
        float level = be.capacity() > 0 ? Math.min(1F, be.stored() / (float) be.capacity()) * h : 0;
        float surface = level > 0 ? Math.max(0.05F, level) : 0;

        // items, one per inside block: x, then z, then up
        long time = be.getLevel().getGameTime();
        for (int i = 0; i < be.slotCount(); i++) {
            ItemStack stack = be.input().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int bx = i % w, bz = (i / w) % d, by = i / (w * d);
            if (by >= h) break;
            float melt = be.progress(i);
            boolean block = stack.getItem() instanceof BlockItem;
            // fixed display: a block model is half a block across at scale 1, a flat item a whole block
            float size = 0.5F * (1 - 0.45F * melt);
            float scale = block ? size * 2 : size;
            ps.pushPose();
            ps.translate(ox + bx + 0.5, oy + by + size * 0.5 + 0.02, oz + bz + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees((i * 67 + bx * 31 + bz * 17) % 360));
            ps.scale(scale, scale, scale);
            int l = LevelRenderer.getLightColor(be.getLevel(), lo.offset(bx, by, bz));
            if (surface > by) l = LightTexture.FULL_BRIGHT;   // glowing in the melt
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                    l, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), (int) (pos.asLong() + i));
            ps.popPose();
        }

        if (surface > 0 && !molten.isEmpty()) {
            FluidStack top = molten.get(molten.size() - 1);
            VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
            float y = oy + Math.min(h - 0.01F, surface);
            for (int bx = 0; bx < w; bx++) for (int bz = 0; bz < d; bz++) {
                FluidDraw.top(ps.last(), vc, top, ox + bx, oz + bz, ox + bx + 1, oz + bz + 1, y, LightTexture.FULL_BRIGHT);
            }
            // the layers show on the inside walls, bottom fluid lowest
            float base = oy;
            for (FluidStack f : molten) {
                float fh = f.getAmount() / (float) be.capacity() * h;
                if (fh <= 0) continue;
                float y2 = Math.min(oy + h - 0.01F, base + fh);
                FluidDraw.walls(ps.last(), vc, f, ox + 0.001F, oz + 0.001F, ox + w - 0.001F, oz + d - 0.001F, base, y2, LightTexture.FULL_BRIGHT);
                base = y2;
                if (base >= oy + h - 0.01F) break;
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(ControllerBlockEntity be) {
        BlockPos lo = be.interiorMin(), hi = be.interiorMax();
        AABB box = new AABB(be.getBlockPos());
        if (be.isFormed()) box = box.minmax(new AABB(lo.getX(), lo.getY(), lo.getZ(), hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1));
        return box.inflate(1);
    }

    @Override
    public boolean shouldRenderOffScreen(ControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
