package dev.empengines.tieredfoundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Drawing fluids: tiled in screens, as flat or boxed quads in the world. */
public final class FluidDraw {
    public static TextureAtlasSprite sprite(FluidStack fs) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fs.getFluid());
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fs));
    }

    public static int tint(FluidStack fs) {
        return IClientFluidTypeExtensions.of(fs.getFluid()).getTintColor(fs);
    }

    /** Fills the rectangle with the fluid's still texture, tiled 16 px at a time. */
    public static void gui(GuiGraphics g, FluidStack fs, int x, int y, int w, int h) {
        if (fs.isEmpty() || w <= 0 || h <= 0) return;
        TextureAtlasSprite s = sprite(fs);
        int c = tint(fs);
        float r = (c >> 16 & 255) / 255F, gr = (c >> 8 & 255) / 255F, b = (c & 255) / 255F;
        g.enableScissor(x, y, x + w, y + h);
        for (int tx = 0; tx < w; tx += 16) for (int ty = 0; ty < h; ty += 16) {
            g.blit(x + tx, y + h - ty - 16, 0, 16, 16, s, r, gr, b, 1F);
        }
        g.disableScissor();
    }

    /** An upward face at height y, from (x1,z1) to (x2,z2), in block units (at most one block across). */
    public static void top(PoseStack.Pose pose, VertexConsumer vc, FluidStack fs, float x1, float z1, float x2, float z2, float y, int light) {
        TextureAtlasSprite s = sprite(fs);
        int c = 0xFF000000 | tint(fs);
        // sprite UVs take 0..1 within the sprite: use the fraction of a block, never world/offset coordinates
        float fx = (float) Math.floor(x1), fz = (float) Math.floor(z1);
        float u1 = s.getU(x1 - fx), u2 = s.getU(Math.min(1, x2 - fx)), v1 = s.getV(z1 - fz), v2 = s.getV(Math.min(1, z2 - fz));
        vertex(pose, vc, x1, y, z1, u1, v1, c, light, 0, 1, 0);
        vertex(pose, vc, x1, y, z2, u1, v2, c, light, 0, 1, 0);
        vertex(pose, vc, x2, y, z2, u2, v2, c, light, 0, 1, 0);
        vertex(pose, vc, x2, y, z1, u2, v1, c, light, 0, 1, 0);
    }

    /** The four sides of an upright column (used for a falling stream). */
    public static void column(PoseStack.Pose pose, VertexConsumer vc, FluidStack fs, float x1, float z1, float x2, float z2,
                              float y1, float y2, int light) {
        TextureAtlasSprite s = sprite(fs);
        int c = 0xFF000000 | tint(fs);
        float u1 = s.getU(0.4F), u2 = s.getU(0.6F), v1 = s.getV(0), v2 = s.getV(Math.min(1, y2 - y1));
        // north, south, west, east
        vertex(pose, vc, x2, y2, z1, u1, v1, c, light, 0, 0, -1); vertex(pose, vc, x2, y1, z1, u1, v2, c, light, 0, 0, -1);
        vertex(pose, vc, x1, y1, z1, u2, v2, c, light, 0, 0, -1); vertex(pose, vc, x1, y2, z1, u2, v1, c, light, 0, 0, -1);
        vertex(pose, vc, x1, y2, z2, u1, v1, c, light, 0, 0, 1); vertex(pose, vc, x1, y1, z2, u1, v2, c, light, 0, 0, 1);
        vertex(pose, vc, x2, y1, z2, u2, v2, c, light, 0, 0, 1); vertex(pose, vc, x2, y2, z2, u2, v1, c, light, 0, 0, 1);
        vertex(pose, vc, x1, y2, z1, u1, v1, c, light, -1, 0, 0); vertex(pose, vc, x1, y1, z1, u1, v2, c, light, -1, 0, 0);
        vertex(pose, vc, x1, y1, z2, u2, v2, c, light, -1, 0, 0); vertex(pose, vc, x1, y2, z2, u2, v1, c, light, -1, 0, 0);
        vertex(pose, vc, x2, y2, z2, u1, v1, c, light, 1, 0, 0); vertex(pose, vc, x2, y1, z2, u1, v2, c, light, 1, 0, 0);
        vertex(pose, vc, x2, y1, z1, u2, v2, c, light, 1, 0, 0); vertex(pose, vc, x2, y2, z1, u2, v1, c, light, 1, 0, 0);
    }

    /**
     * The four walls of a box seen from inside (faces point inwards), tiled one block at a time, from y1 to y2.
     * Used for the molten layers inside a foundry.
     */
    public static void walls(PoseStack.Pose pose, VertexConsumer vc, FluidStack fs, float x1, float z1, float x2, float z2,
                             float y1, float y2, int light) {
        // one quad per block: only for spans a foundry can have (far-off coordinates would never finish in floats)
        if (!(x2 - x1 <= 16 && z2 - z1 <= 16 && y2 - y1 <= 16) || Math.abs(x1) > 64 || Math.abs(z1) > 64 || Math.abs(y1) > 64) return;
        TextureAtlasSprite s = sprite(fs);
        int c = 0xFF000000 | tint(fs);
        for (float ya = y1; ya < y2 - 1e-4F; ya = Math.min(y2, (float) Math.floor(ya) + 1)) {
            float yb = Math.min(y2, (float) Math.floor(ya) + 1);
            float v1 = s.getV(1 - (yb - (float) Math.floor(ya))), v2 = s.getV(1 - (ya - (float) Math.floor(ya)));
            for (float xa = x1; xa < x2 - 1e-4F; xa = Math.min(x2, (float) Math.floor(xa) + 1)) {
                float xb = Math.min(x2, (float) Math.floor(xa) + 1);
                float u1 = s.getU(xa - (float) Math.floor(xa)), u2 = s.getU(xb - (float) Math.floor(xa));
                // north wall (z1), facing +z
                vertex(pose, vc, xa, yb, z1, u1, v1, c, light, 0, 0, 1); vertex(pose, vc, xa, ya, z1, u1, v2, c, light, 0, 0, 1);
                vertex(pose, vc, xb, ya, z1, u2, v2, c, light, 0, 0, 1); vertex(pose, vc, xb, yb, z1, u2, v1, c, light, 0, 0, 1);
                // south wall (z2), facing -z
                vertex(pose, vc, xb, yb, z2, u2, v1, c, light, 0, 0, -1); vertex(pose, vc, xb, ya, z2, u2, v2, c, light, 0, 0, -1);
                vertex(pose, vc, xa, ya, z2, u1, v2, c, light, 0, 0, -1); vertex(pose, vc, xa, yb, z2, u1, v1, c, light, 0, 0, -1);
            }
            for (float za = z1; za < z2 - 1e-4F; za = Math.min(z2, (float) Math.floor(za) + 1)) {
                float zb = Math.min(z2, (float) Math.floor(za) + 1);
                float u1 = s.getU(za - (float) Math.floor(za)), u2 = s.getU(zb - (float) Math.floor(za));
                // west wall (x1), facing +x
                vertex(pose, vc, x1, yb, zb, u2, v1, c, light, 1, 0, 0); vertex(pose, vc, x1, ya, zb, u2, v2, c, light, 1, 0, 0);
                vertex(pose, vc, x1, ya, za, u1, v2, c, light, 1, 0, 0); vertex(pose, vc, x1, yb, za, u1, v1, c, light, 1, 0, 0);
                // east wall (x2), facing -x
                vertex(pose, vc, x2, yb, za, u1, v1, c, light, -1, 0, 0); vertex(pose, vc, x2, ya, za, u1, v2, c, light, -1, 0, 0);
                vertex(pose, vc, x2, ya, zb, u2, v2, c, light, -1, 0, 0); vertex(pose, vc, x2, yb, zb, u2, v1, c, light, -1, 0, 0);
            }
            if (yb >= y2) break;
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int argb,
                               int light, float nx, float ny, float nz) {
        // every element, overlay too: buffers swapped in by Sable, Iris or Sodium may expect it
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    private FluidDraw() {}
}
