package dev.empengines.engines.client;

import dev.empengines.engines.Config;
import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.block.VectorPlanarComputerBlockEntity;
import dev.empengines.engines.launch.Platform;
import dev.empengines.engines.nav.NavigationConsoleMenu;
import dev.empengines.engines.nav.NavigationalDataItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Navigation Console: a brass frame with a black monitor showing the run length, pylon tier, the ship's
 * weight against the platform's limit, the charge and the destination, and a slot for Navigational Data.
 */
public class NavigationConsoleScreen extends AbstractContainerScreen<NavigationConsoleMenu> {
    private static final ResourceLocation TEXTURE = EmpyreanEngines.id("textures/gui/navigation_console.png");
    /** The player's inventory is drawn from vanilla's chest screen so it looks like every other inventory. */
    private static final ResourceLocation VANILLA = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int CONSOLE_H = 122;
    // The monitor's inside, in screen-local pixels
    private static final int MON_X = 16, MON_Y = 24, MON_W = 144, MON_H = 68;
    private static final int TEXT = 0xF0B03C, DIM = 0x8A6420, BAD = 0xFF5040, GOOD = 0x7CFF6A;
    private static final String[] TIERS = {"", "Amber", "Cyan", "Violet"};

    public NavigationConsoleScreen(NavigationConsoleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = NavigationConsoleMenu.WIDTH;
        imageHeight = NavigationConsoleMenu.HEIGHT;
        inventoryLabelX = NavigationConsoleMenu.INV_X;
        inventoryLabelY = NavigationConsoleMenu.INV_TOP + 6;
        titleLabelX = 12;
        titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, CONSOLE_H, 256, 256);
        int ix = leftPos + NavigationConsoleMenu.INV_LEFT, iy = topPos + NavigationConsoleMenu.INV_TOP;
        g.blit(VANILLA, ix, iy, 0, 0, 176, 4);               // the chest screen's top edge
        g.blit(VANILLA, ix, iy + 4, 0, 126, 176, 96);        // its inventory half
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x4A1A12, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        g.drawString(font, Component.translatable("gui.emp_engines.console.nav_slot"),
                NavigationConsoleMenu.NAV_X - 4 - font.width(Component.translatable("gui.emp_engines.console.nav_slot")),
                NavigationConsoleMenu.NAV_Y + 5, 0xE8C27A, false);
        monitor(g);
    }

    /** Everything on the black monitor. */
    private void monitor(GuiGraphics g) {
        int x = MON_X + 4, y = MON_Y + 4;
        VectorPlanarComputerBlockEntity c = menu.console();
        if (c == null) {
            g.drawString(font, "NO SIGNAL", x, y, BAD, false);
            return;
        }
        Platform p = c.platform();
        if (c.phase() != VectorPlanarComputerBlockEntity.Phase.IDLE) {
            g.drawString(font, "> LAUNCH IN PROGRESS", x, y, GOOD, false);
            return;
        }
        if (p == null) {
            g.drawString(font, "> NO PLATFORM", x, y, BAD, false);
            wrap(g, c.problem(), x, y + 12, DIM, 4);
            return;
        }
        int tier = Math.max(1, Math.min(3, p.tier()));
        line(g, x, y, "RUN", runMinutes(tier) + " min  (" + TIERS[tier] + " pylons)", TEXT);

        boolean ship = c.hasShip();
        double mass = c.shipMass(), limit = c.massLimit();
        line(g, x, y + 10, "WEIGHT", ship ? String.format("%.0f / %.0f", mass, limit) : "no ship / " + String.format("%.0f", limit),
                ship && mass > limit ? BAD : TEXT);

        float f = c.chargeFraction();
        line(g, x, y + 20, "CHARGE", Math.round(f * 100) + "%", c.charged() ? GOOD : TEXT);
        int bx = x + 86, by = y + 21, bw = MON_W - 8 - 86;
        g.fill(bx, by, bx + bw, by + 6, 0xFF2A1A08);
        g.fill(bx, by, bx + Math.round(bw * f), by + 6, c.charged() ? 0xFF7CFF6A : 0xFFF0B03C);

        ItemStack drive = menu.getSlot(0).getItem();
        String dest = NavigationalDataItem.targetName(drive);
        line(g, x, y + 30, "DEST", dest == null ? "anywhere" : dest, dest == null ? DIM : TEXT);

        String status;
        int colour;
        if (c.problem() != null && !c.problem().startsWith("Not fully")) { status = c.problem(); colour = BAD; }
        else if (c.charged()) { status = "READY - apply redstone to launch"; colour = GOOD; }
        else if (c.inputRpm() > 0) { status = "Charging: " + c.inputRpm() + " RPM in from " + c.inputCount() + " input" + (c.inputCount() == 1 ? "" : "s"); colour = DIM; }
        else { status = "No rotation reaching the shaft inputs"; colour = DIM; }
        wrap(g, status, x, y + 42, colour, 2);
    }

    private void line(GuiGraphics g, int x, int y, String label, String value, int colour) {
        g.drawString(font, label, x, y, DIM, false);
        g.drawString(font, value, x + 44, y, colour, false);
    }

    private void wrap(GuiGraphics g, String text, int x, int y, int colour, int maxLines) {
        if (text == null) return;
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), MON_W - 8);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 10, colour, false);
    }

    private static int runMinutes(int tier) {
        try { return Config.runMinutes(tier); } catch (IllegalStateException e) { return 30 * tier; }
    }
}
