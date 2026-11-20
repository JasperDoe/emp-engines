package dev.empengines.tieredfoundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.empengines.tieredfoundry.block.entity.CastingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Shows the cast, the poured metal rising and the cooled result on casting tables and basins. */
public class CastingRenderer implements BlockEntityRenderer<CastingBlockEntity> {
    public CastingRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CastingBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        boolean basin = be.basin();
        if (!basin && !be.cast().isEmpty()) flat(be.cast(), 15.02F / 16, ps, buffers, light, overlay, be);
        ItemStack out = be.output();
        if (!out.isEmpty()) {
            if (basin) block(out, ps, buffers, light, overlay, be);
            else flat(out, 15.1F / 16, ps, buffers, light, overlay, be);
        }
        FluidStack fluid = be.fluid();
        if (!fluid.isEmpty() && be.need() > 0) {
            float fill = Math.min(1F, fluid.getAmount() / (float) be.need());
            var vc = buffers.getBuffer(RenderType.translucent());
            if (basin) FluidDraw.top(ps.last(), vc, fluid, 2 / 16F, 2 / 16F, 14 / 16F, 14 / 16F, (4 + 11.8F * fill) / 16F, LightTexture.FULL_BRIGHT);
            else FluidDraw.top(ps.last(), vc, fluid, 1 / 16F, 1 / 16F, 15 / 16F, 15 / 16F, (15 + 0.9F * fill) / 16F, LightTexture.FULL_BRIGHT);
        }
    }

    private static void flat(ItemStack stack, float y, PoseStack ps, MultiBufferSource buffers, int light, int overlay, CastingBlockEntity be) {
        ps.pushPose();
        ps.translate(0.5, y, 0.5);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        ps.scale(0.875F, 0.875F, 0.875F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ps, buffers, be.getLevel(), 0);
        ps.popPose();
    }

    private static void block(ItemStack stack, PoseStack ps, MultiBufferSource buffers, int light, int overlay, CastingBlockEntity be) {
        ps.pushPose();
        ps.translate(0.5, stack.getItem() instanceof BlockItem ? 0.62 : 0.3, 0.5);
        if (!(stack.getItem() instanceof BlockItem)) ps.mulPose(Axis.XP.rotationDegrees(-90));
        float s = stack.getItem() instanceof BlockItem ? 1.45F : 0.75F;
        ps.scale(s, s, s);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ps, buffers, be.getLevel(), 0);
        ps.popPose();
    }
}
