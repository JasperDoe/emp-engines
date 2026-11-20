package dev.empengines.tieredfoundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.empengines.tieredfoundry.block.TapBlock;
import dev.empengines.tieredfoundry.block.entity.CastingBlockEntity;
import dev.empengines.tieredfoundry.block.entity.TapBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;

/** The falling stream under a pouring tap. */
public class TapRenderer implements BlockEntityRenderer<TapBlockEntity> {
    public TapRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TapBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        FluidStack fs = be.showing();
        if (fs.isEmpty()) return;
        Direction f = be.getBlockState().getValue(TapBlock.FACING);
        float cx = 0.5F - 0.125F * f.getStepX(), cz = 0.5F - 0.125F * f.getStepZ();
        float bottom = -1 / 16F;
        if (be.getLevel() != null && be.getLevel().getBlockEntity(be.getBlockPos().below()) instanceof CastingBlockEntity c && c.basin()) {
            float fill = c.need() > 0 ? Math.min(1F, c.fluid().getAmount() / (float) c.need()) : 0;
            bottom = -1 + (4 + 11.8F * fill) / 16F;
        }
        float w = 2 / 16F;
        FluidDraw.column(ps.last(), buffers.getBuffer(RenderType.translucent()), fs, cx - w, cz - w, cx + w, cz + w,
                bottom, 4.5F / 16F, LightTexture.FULL_BRIGHT);
    }
}
