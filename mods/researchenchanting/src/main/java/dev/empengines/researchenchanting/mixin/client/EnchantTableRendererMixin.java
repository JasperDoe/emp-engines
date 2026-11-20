package dev.empengines.researchenchanting.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.empengines.researchenchanting.ResearchEnchanting;
import dev.empengines.researchenchanting.client.Glyphs;
import dev.empengines.researchenchanting.table.TableAccess;
import dev.empengines.researchenchanting.table.TableData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What floats over an enchanting table: nothing when it's empty, vanilla's book for a book or research focus,
 * and anything else (a sword being enchanted) as its own model, slowly bobbing and turning. While it enchants the
 * item rises and spins up.
 *
 * Around every table, when you come close: a rune circle turning on the ground around it and a ring of vanilla's enchanting glyphs orbiting it. Both brighten while research runs, and while an
 * enchantment is applied the ring tightens around the item, climbs and spins faster.
 */
@Mixin(EnchantTableRenderer.class)
public abstract class EnchantTableRendererMixin {
    @Unique
    private static final ResourceLocation researchenchanting$CIRCLE = ResearchEnchanting.id("textures/misc/rune_circle.png");
    @Unique
    private static final int researchenchanting$RING = 12;

    /** The circle and ring reach past the block: don't cull them while the table itself is just off screen. */
    public net.minecraft.world.phys.AABB getRenderBoundingBox(net.minecraft.world.level.block.entity.BlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(2.1, 0, 2.1).expandTowards(0, 1.0, 0);
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/EnchantingTableBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void researchenchanting$floating(EnchantingTableBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                                             int light, int overlay, CallbackInfo ci) {
        TableData data = ((TableAccess) be).researchenchanting$data();
        float time = be.time + partial;
        float p = data.enchantProgress(partial);
        researchenchanting$magic(be, data, time, p, partial, pose, buffers);
        if (data.showsBook()) return;
        ci.cancel();
        ItemStack item = data.item();
        if (item.isEmpty()) return;
        float spin = time * 1.2F + (p > 0 ? p * p * 1440 : 0);
        float scale = 0.5F + Glyphs.rise(Math.max(0, p)) * 0.1F;
        pose.pushPose();
        pose.translate(0.5, Glyphs.itemHeight(be, data, partial), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, p >= 0 ? LightTexture.FULL_BRIGHT : light,
                overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }

    @Unique
    private static void researchenchanting$magic(EnchantingTableBlockEntity be, TableData data, float time, float p, float partial,
                                                 PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())));
        float near = Mth.clamp((float) (6.0 - dist) / 2.5F, 0, 1);
        boolean enchanting = p >= 0;
        float glow = enchanting ? 1 : data.researching() ? Math.max(near, 0.6F) : near * 0.7F;
        if (glow <= 0.01F) return;
        float e = enchanting ? Glyphs.rise(p) : 0;

        // rune circle on the ground around the table
        float turn = time * 0.25F + (enchanting ? p * p * 360 : 0);
        float size = 2.0F - e * 0.4F;
        pose.pushPose();
        pose.translate(0.5, 0.02, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(researchenchanting$CIRCLE));
        PoseStack.Pose m = pose.last();
        float a = glow * 0.75F;
        researchenchanting$vertex(vc, m, -size, 0, -size, 0, 0, a);
        researchenchanting$vertex(vc, m, -size, 0, size, 0, 1, a);
        researchenchanting$vertex(vc, m, size, 0, size, 1, 1, a);
        researchenchanting$vertex(vc, m, size, 0, -size, 1, 0, a);
        pose.popPose();

        // orbiting glyphs, vanilla's sprites, always facing you
        // The particle atlas belongs to the particle engine, not the model manager: fetch it from the texture manager.
        if (!(mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES) instanceof TextureAtlas atlas)) return;
        VertexConsumer gv = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_PARTICLES));
        double centre = data.showsBook() || data.item().isEmpty() ? 1.0 : Glyphs.itemHeight(be, data, partial);
        float radius = 0.95F - e * 0.45F;
        float speed = 0.6F + (enchanting ? p * p * 9 : 0);
        long seed = be.getBlockPos().asLong();
        for (int i = 0; i < researchenchanting$RING; i++) {
            float ang = (float) (Math.PI * 2 * i / researchenchanting$RING) + time * speed * 0.05F;
            float bob = Mth.sin(time * 0.08F + i * 1.7F) * 0.08F;
            double y = Mth.lerp(e, 0.95, centre) + bob + (i % 2 == 0 ? 0.06 : -0.06);
            char letter = (char) ('a' + (int) Math.floorMod(seed * 31 + i * 7L, 26L));
            TextureAtlasSprite s = atlas.getSprite(ResourceLocation.withDefaultNamespace("sga_" + letter));
            float flicker = 0.75F + 0.25F * Mth.sin(time * 0.2F + i * 2.3F);
            float ga = glow * flicker;
            float q = 0.075F + e * 0.02F;
            pose.pushPose();
            pose.translate(0.5 + Mth.cos(ang) * radius, y, 0.5 + Mth.sin(ang) * radius);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            PoseStack.Pose g = pose.last();
            researchenchanting$glyph(gv, g, -q, -q, s.getU1(), s.getV1(), ga);
            researchenchanting$glyph(gv, g, -q, q, s.getU1(), s.getV0(), ga);
            researchenchanting$glyph(gv, g, q, q, s.getU0(), s.getV0(), ga);
            researchenchanting$glyph(gv, g, q, -q, s.getU0(), s.getV1(), ga);
            pose.popPose();
        }
    }

    @Unique
    private static void researchenchanting$vertex(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z, float u, float v, float a) {
        vc.addVertex(m, x, y, z).setColor(0.85F, 0.8F, 1.0F, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(m, 0, 1, 0);
    }

    @Unique
    private static void researchenchanting$glyph(VertexConsumer vc, PoseStack.Pose m, float x, float y, float u, float v, float a) {
        vc.addVertex(m, x, y, 0).setColor(0.9F, 0.9F, 1.0F, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(m, 0, 0, 1);
    }
}
