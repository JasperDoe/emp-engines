package dev.empengines.tieredfoundry.client;

import dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity;
import dev.empengines.tieredfoundry.menu.ControllerMenu;
import dev.empengines.tieredfoundry.recipe.MeltingRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The controller screen, drawn from vanilla's own GUI art (panel, slots, creative scroller), so UI resource packs
 * restyle it like any vanilla container:
 * <pre>
 *  melting grid 3x5 | scroll | molten metal | heat | fuel 2x2 + burn (solid fuel heaters) or lava (lava heaters)
 *  status, two lines
 *  player inventory
 * </pre>
 */
public class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/demo_background.png");
    private static final ResourceLocation INVENTORY = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_OFF = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final int TEXT = 0xFF404040, TEXT_SOFT = 0xFF707070;
    private static final int TOP = ControllerMenu.GRID_Y, H = ControllerMenu.ROWS * 18 - 2;   // inner area y 18..106
    // 176 wide like every vanilla container: grid 8..61, scroll 64, tank 81..119, heat 124, fuel or lava 135..169
    private static final int SCROLL_X = 64, SCROLL_W = 14;
    private static final int TANK_X = 81, TANK_W = 38;
    private static final int HEAT_X = 124, HEAT_W = 6;
    private static final int LAVA_X = 135, LAVA_W = 34;
    private static final int BURN_Y = 56;
    private static final int STATUS_Y = 111, STATUS_LINES = 2;

    private boolean dragging;

    public ControllerScreen(ControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = ControllerMenu.WIDTH;
        imageHeight = ControllerMenu.HEIGHT;
        inventoryLabelY = 100000;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    private ControllerBlockEntity be() { return menu.be; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        List<Component> tip = extraTooltip(mx - leftPos, my - topPos);
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    // ---- background -------------------------------------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        panel(g, x, y, imageWidth, imageHeight);
        ControllerBlockEntity be = be();
        for (Slot s : menu.slots) {
            if (s instanceof ControllerMenu.MeltSlot ms) {
                if (!be.isFormed()) continue;
                boolean real = ms.foundrySlot() < be.slotCount();
                if (real) {
                    slot(g, x + s.x, y + s.y);
                    progress(g, x + s.x, y + s.y, ms.foundrySlot(), s.getItem());
                }
            } else if (s.isActive()) {
                slot(g, x + s.x, y + s.y);
            }
        }
        if (!be.isFormed()) return;

        // scrollbar, like the creative inventory's
        inset(g, x + SCROLL_X, y + TOP, SCROLL_W - 2, H);
        boolean canScroll = menu.maxScroll() > 0;
        int knob = canScroll ? Math.round(menu.scroll() / (float) menu.maxScroll() * (H - 15)) : 0;
        g.blitSprite(canScroll ? SCROLLER : SCROLLER_OFF, x + SCROLL_X, y + TOP + knob, 12, 15);

        // molten metal, bottom fluid first
        inset(g, x + TANK_X, y + TOP, TANK_W, H);
        int cap = Math.max(1, be.capacity());
        int bottom = y + TOP + H;
        for (FluidStack f : be.molten()) {
            int h = Math.max(1, Math.round(f.getAmount() / (float) cap * H));
            h = Math.min(h, bottom - (y + TOP));
            if (h <= 0) break;
            FluidDraw.gui(g, f, x + TANK_X, bottom - h, TANK_W, h);
            bottom -= h;
        }

        // heat
        inset(g, x + HEAT_X, y + TOP, HEAT_W, H);
        int max = Math.max(1, be.maxHeat());
        int hh = Math.round(Math.min(1F, be.heat() / max) * H);
        for (int i = 0; i < hh; i++) {
            g.fill(x + HEAT_X, y + TOP + H - i - 1, x + HEAT_X + HEAT_W, y + TOP + H - i, heatColour(i / (float) H));
        }
        if (be.target() > ControllerBlockEntity.AMBIENT) {
            int ty = y + TOP + H - Math.round(Math.min(1F, be.target() / (float) max) * H);
            g.fill(x + HEAT_X - 1, ty, x + HEAT_X + HEAT_W + 1, ty + 1, 0xFFFFFFFF);
        }

        // the heaters' fuel: solid fuel (slots drawn above, burn left under them) or a lava gauge, never both
        if (be.showsSolid()) {
            inset(g, x + LAVA_X, y + BURN_Y, LAVA_W, 2);
            g.fill(x + LAVA_X, y + BURN_Y, x + LAVA_X + Math.round(be.burnFraction() * LAVA_W), y + BURN_Y + 2, 0xFFFF9A2A);
        } else if (be.showsLava()) {
            inset(g, x + LAVA_X, y + TOP, LAVA_W, H);
            int lh = Math.round(Math.min(1F, be.lava().getFluidAmount() / (float) Math.max(1, be.lava().getCapacity())) * H);
            FluidDraw.gui(g, be.lava().getFluid(), x + LAVA_X, y + TOP + H - lh, LAVA_W, lh);
        }
    }

    private static int heatColour(float t) {
        int gr = Mth.clamp((int) (50 + 190 * t), 0, 255), b = (int) (40 * t);
        return 0xFFFF0000 | gr << 8 | b;
    }

    /** Melting progress fills the slot from the bottom, under the item: orange while melting, blue while too cold, red if it can't melt. */
    private void progress(GuiGraphics g, int x, int y, int slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        MeltingRecipe r = be().melting(stack);
        if (r == null) { g.fill(x, y, x + 16, y + 16, 0x50FF2020); return; }
        if (be().heat() < r.temperature()) { g.fill(x, y, x + 16, y + 16, 0x403A6FD0); return; }   // too cold: blue
        int h = Math.round(Mth.clamp(be().progress(slot), 0, 1) * 16);
        if (h > 0) g.fill(x, y + 16 - h, x + 16, y + 16, 0x80FF8A1E);
    }

    /** A melting slot's item tooltip also says what it melts into, and at what heat. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot instanceof ControllerMenu.MeltSlot && be().isFormed()) {
            MeltingRecipe r = be().melting(stack);
            if (r != null) {
                tip.add(Component.translatable("screen.tieredfoundry.melts_at", r.temperature()).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("screen.tieredfoundry.melts_to", r.amount(be().yield()), r.result().getHoverName()).withStyle(ChatFormatting.GRAY));
                if (be().heat() < r.temperature()) tip.add(Component.translatable("screen.tieredfoundry.too_cold").withStyle(ChatFormatting.BLUE));
            }
        }
        return tip;
    }

    /** Vanilla's plain panel (the demo screen's background), nine-sliced to any size. */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        final int tw = 248, th = 166, c = 4;
        int iw = w - 2 * c, ih = h - 2 * c, sw = tw - 2 * c, sh = th - 2 * c;
        g.blit(PANEL, x, y, 0, 0, c, c);
        g.blit(PANEL, x + w - c, y, tw - c, 0, c, c);
        g.blit(PANEL, x, y + h - c, 0, th - c, c, c);
        g.blit(PANEL, x + w - c, y + h - c, tw - c, th - c, c, c);
        for (int ox = 0; ox < iw; ox += sw) {
            int pw = Math.min(sw, iw - ox);
            g.blit(PANEL, x + c + ox, y, c, 0, pw, c);
            g.blit(PANEL, x + c + ox, y + h - c, c, th - c, pw, c);
            for (int oy = 0; oy < ih; oy += sh) g.blit(PANEL, x + c + ox, y + c + oy, c, c, pw, Math.min(sh, ih - oy));
        }
        for (int oy = 0; oy < ih; oy += sh) {
            int ph = Math.min(sh, ih - oy);
            g.blit(PANEL, x, y + c + oy, 0, c, c, ph);
            g.blit(PANEL, x + w - c, y + c + oy, tw - c, c, c, ph);
        }
    }

    /** One slot from vanilla's inventory texture, around an item position. */
    private static void slot(GuiGraphics g, int x, int y) {
        g.blit(INVENTORY, x - 1, y - 1, 7, 83, 18, 18);
    }

    /** A recessed area in the slot style. */
    private static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x - 1, y - 1, x + w, y + h, 0xFF373737);
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
    }

    // ---- labels ----------------------------------------------------------------------------------------------------

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        ControllerBlockEntity be = be();
        Component title = be.isFormed()
                ? Component.translatable("screen.tieredfoundry.title", tierText(be.effectiveTier()), String.format("%.1f", be.yield()))
                : Component.translatable("screen.tieredfoundry.title_unformed");
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        if (be.isFormed()) {
            List<FormattedCharSequence> lines = font.split(be.status(), imageWidth - 16);
            for (int i = 0; i < Math.min(STATUS_LINES, lines.size()); i++) g.drawString(font, lines.get(i), 8, STATUS_Y + i * 10, TEXT, false);
        } else {
            int ly = 22;
            for (FormattedCharSequence l : font.split(be.status(), imageWidth - 20)) { g.drawString(font, l, 10, ly, TEXT, false); ly += 10; }
            ly += 6;
            for (FormattedCharSequence l : font.split(Component.translatable("screen.tieredfoundry.help"), imageWidth - 20)) {
                g.drawString(font, l, 10, ly, TEXT_SOFT, false);
                ly += 10;
            }
        }
    }

    private static String tierText(float t) {
        return t == (int) t ? String.valueOf((int) t) : String.valueOf(t);
    }

    // ---- tooltips, clicks, scrolling ----------------------------------------------------------------------------------

    private int fluidAt(int rx, int ry) {
        if (!be().isFormed() || rx < TANK_X || rx >= TANK_X + TANK_W || ry < TOP || ry >= TOP + H) return -1;
        int cap = Math.max(1, be().capacity());
        int bottom = TOP + H;
        List<FluidStack> m = be().molten();
        for (int i = 0; i < m.size(); i++) {
            int h = Math.max(1, Math.round(m.get(i).getAmount() / (float) cap * H));
            if (ry >= bottom - h && ry < bottom) return i;
            bottom -= h;
        }
        return -1;
    }

    private List<Component> extraTooltip(int rx, int ry) {
        List<Component> tip = new ArrayList<>();
        ControllerBlockEntity be = be();
        if (!be.isFormed()) return tip;
        if (rx >= TANK_X && rx < TANK_X + TANK_W && ry >= TOP && ry < TOP + H) {
            int i = fluidAt(rx, ry);
            if (i >= 0) {
                FluidStack f = be.molten().get(i);
                tip.add(f.getHoverName());
                tip.add(Component.translatable("screen.tieredfoundry.mb", f.getAmount()).withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable(i == 0 ? "screen.tieredfoundry.pours_first" : "screen.tieredfoundry.click_bottom")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            tip.add(Component.translatable("screen.tieredfoundry.total", be.stored(), be.capacity()).withStyle(ChatFormatting.GRAY));
        } else if (rx >= HEAT_X - 1 && rx < HEAT_X + HEAT_W + 1 && ry >= TOP && ry < TOP + H) {
            tip.add(Component.translatable("screen.tieredfoundry.heat", Math.round(be.heat()), be.target()));
            tip.add(Component.translatable("screen.tieredfoundry.max_heat", be.maxHeat()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("screen.tieredfoundry.heat_loss", String.format(java.util.Locale.ROOT, "%.1f", be.coolPerSecond()))
                    .withStyle(ChatFormatting.GRAY));
            if (be.fanRunning()) tip.add(Component.translatable("screen.tieredfoundry.fan", be.fanRpm()).withStyle(ChatFormatting.AQUA));
            else tip.add(Component.translatable("screen.tieredfoundry.no_fan").withStyle(ChatFormatting.DARK_GRAY));
        } else if (!be.showsSolid() && be.showsLava() && rx >= LAVA_X && rx < LAVA_X + LAVA_W && ry >= TOP && ry < TOP + H) {
            tip.add(Component.translatable("screen.tieredfoundry.lava", be.lava().getFluidAmount(), be.lava().getCapacity()));
            if (be.tier() < 2) tip.add(Component.translatable("status.tieredfoundry.lava_needs_fire_bricks").withStyle(ChatFormatting.RED));
        } else if (ry >= STATUS_Y - 1 && ry < STATUS_Y + 10 * STATUS_LINES && rx < imageWidth - 8
                && font.split(be.status(), imageWidth - 16).size() > STATUS_LINES) {
            tip.add(be.status());
        }
        return tip;
    }

    private void scrollTo(int row) {
        int r = Mth.clamp(row, 0, menu.maxScroll());
        if (r == menu.scroll()) return;
        menu.setScroll(r);
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 200 + r);
    }

    private boolean overGrid(double rx, double ry) {
        return rx >= 6 && rx < SCROLL_X + SCROLL_W && ry >= TOP - 1 && ry < TOP + H + 1;
    }

    private boolean overScroller(double rx, double ry) {
        return rx >= SCROLL_X && rx < SCROLL_X + 12 && ry >= TOP && ry < TOP + H;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (be().isFormed() && overGrid(mx - leftPos, my - topPos) && menu.maxScroll() > 0) {
            scrollTo(menu.scroll() - (int) Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int rx = (int) mx - leftPos, ry = (int) my - topPos;
        int fluid = fluidAt(rx, ry);
        if (fluid > 0) {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 100 + fluid);
            return true;
        }
        if (be().isFormed() && menu.maxScroll() > 0 && overScroller(rx, ry)) {
            dragging = true;
            dragTo(ry);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void dragTo(double ry) {
        float t = (float) ((ry - TOP - 7.5) / (H - 15));
        scrollTo(Math.round(Mth.clamp(t, 0, 1) * menu.maxScroll()));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { dragTo(my - topPos); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }
}
