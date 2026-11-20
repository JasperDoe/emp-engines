package dev.empengines.researchenchanting.client;

import dev.empengines.researchenchanting.Config;
import dev.empengines.researchenchanting.focus.Focus;
import dev.empengines.researchenchanting.item.ResearchData;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import dev.empengines.researchenchanting.menu.ResearchTableMenu;
import dev.empengines.researchenchanting.table.Research;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A focus's research tree, drawn over the table's three buttons on a see-through panel (the book, the slots and
 * the inventory stay usable). One node per enchantment level, lines left to right, branches below their parent.
 * Click an open node to research it; a node that would replace the book's enchantment asks for a second click.
 * Drag or scroll to pan. It's the whole screen while a focus is in the table (Esc closes the screen).
 */
final class ResearchTree {
    private static final ResourceLocation DONE = ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained");
    private static final ResourceLocation OPEN = ResourceLocation.withDefaultNamespace("advancements/task_frame_unobtained");
    private static final ResourceLocation ACTIVE = ResourceLocation.withDefaultNamespace("advancements/goal_frame_obtained");
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final int NODE = 18, STEP = 21, PAD = 3;
    private static final int X = ResearchTableScreen.ROW_X, Y = ResearchTableScreen.ROW_Y, W = ResearchTableScreen.ROW_W, H = ResearchTableScreen.ROW_H * 3;

    private final ResearchTableScreen screen;
    private final ResourceLocation focusId;
    private final Focus focus;
    private final int minX, maxX, minY, maxY;
    private double panX, panY;
    private boolean dragged;
    /** A replacing node clicked once: second click within the time confirms. */
    private int confirmLine = -1, confirmLevel, confirmUntil;
    private int clock;

    ResearchTree(ResearchTableScreen screen, ResourceLocation focusId, Focus focus) {
        this.screen = screen;
        this.focusId = focusId;
        this.focus = focus;
        int a = 0, b = 0, c = 0, d = 0;
        for (Focus.Line l : focus.lines()) {
            a = Math.min(a, l.x());
            b = Math.max(b, l.x() + l.max() - 1);
            c = Math.min(c, l.y());
            d = Math.max(d, l.y());
        }
        minX = a; maxX = b; minY = c; maxY = d;
        layout();
    }

    ResourceLocation focusId() { return focusId; }

    void layout() {
        panX = PAD;
        panY = PAD;
    }

    private int x0() { return screen.left() + X; }
    private int y0() { return screen.top() + Y; }

    boolean inside(double mx, double my) {
        return mx >= x0() && my >= y0() && mx < x0() + W && my < y0() + H;
    }

    private ResearchData data() {
        ResearchData d = ResearchFocusItem.data(screen.getMenu().item());
        return d != null && d.focus().equals(focusId) ? d : null;
    }

    private int nodeX(Focus.Line l, int level) { return x0() + (int) panX + (l.x() - minX + level - 1) * STEP; }
    private int nodeY(Focus.Line l) { return y0() + (int) panY + (l.y() - minY) * STEP; }

    void render(GuiGraphics g, int mx, int my) {
        clock++;
        ResearchData d = data();
        if (d == null) { screen.closeTree(); return; }
        Font font = Minecraft.getInstance().font;
        int x0 = x0(), y0 = y0();
        g.fill(x0, y0, x0 + W, y0 + H, 0xD0181410);
        g.renderOutline(x0, y0, W, H, 0xFF3A2E22);
        g.enableScissor(x0 + 1, y0 + 1, x0 + W - 1, y0 + H - 1);

        // connections
        for (Focus.Line l : focus.lines()) {
            int cy = nodeY(l) + NODE / 2;
            for (int lvl = 2; lvl <= l.max(); lvl++) g.fill(nodeX(l, lvl - 1) + NODE, cy, nodeX(l, lvl), cy + 1, 0xFFB8A988);
            l.requires().ifPresent(r -> focus.line(r.enchantment()).ifPresent(p -> {
                int px = nodeX(p, r.level()) + NODE / 2, py = nodeY(p) + NODE;
                int cx = nodeX(l, 1);
                g.fill(px, py, px + 1, cy + 1, 0xFFB8A988);
                g.fill(px, cy, cx, cy + 1, 0xFFB8A988);
            }));
        }

        List<Component> tip = null;
        for (int n = 0; n < focus.lines().size(); n++) {
            Focus.Line l = focus.lines().get(n);
            for (int lvl = 1; lvl <= l.max(); lvl++) {
                int nx = nodeX(l, lvl), ny = nodeY(l);
                Research.State st = Research.state(d, l, lvl);
                ResourceLocation frame = switch (st) { case RESEARCHED -> DONE; case ACTIVE -> ACTIVE; default -> OPEN; };
                g.blitSprite(frame, nx, ny, NODE, NODE);
                g.renderFakeItem(new ItemStack(l.resource()), nx + 1, ny + 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                if (st == Research.State.LOCKED) g.fill(nx + 1, ny + 1, nx + NODE - 1, ny + NODE - 1, 0xA0000000);
                boolean confirming = confirmLine == n && confirmLevel == lvl && clock < confirmUntil;
                if (confirming) g.renderOutline(nx - 1, ny - 1, NODE + 2, NODE + 2, 0xFFFF5050);
                String num = NUMERALS[Math.min(lvl, 10) - 1];
                g.pose().pushPose();
                g.pose().translate(nx + NODE - 1 - font.width(num) * 0.5F, ny + NODE - 5, 0);
                g.pose().scale(0.5F, 0.5F, 1);
                g.drawString(font, num, 0, 0, st == Research.State.LOCKED ? 0x808080 : 0xFFFFFF, true);
                g.pose().popPose();
                if (st == Research.State.ACTIVE) {
                    ResearchData.Active a = d.active().get();
                    int w = (int) ((NODE - 4) * Math.min(1.0, a.paid() / (double) Config.cost(lvl)));
                    g.fill(nx + 2, ny + NODE - 2, nx + NODE - 2, ny + NODE - 1, 0xFF2A2418);
                    g.fill(nx + 2, ny + NODE - 2, nx + 2 + w, ny + NODE - 1, 0xFF80FF20);
                }
                g.pose().popPose();
                if (inside(mx, my) && mx >= nx && my >= ny && mx < nx + NODE && my < ny + NODE) tip = tooltip(d, n, l, lvl, st);
            }
        }
        g.disableScissor();
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    private List<Component> tooltip(ResearchData d, int n, Focus.Line l, int lvl, Research.State st) {
        List<Component> tip = new ArrayList<>();
        tip.add(ResearchFocusItem.enchantName(l.enchantment(), lvl).copy().withStyle(ChatFormatting.WHITE));
        switch (st) {
            case RESEARCHED -> tip.add(Component.translatable("gui.researchenchanting.node.done").withStyle(ChatFormatting.GREEN));
            case ACTIVE -> {
                ResearchData.Active a = d.active().get();
                tip.add(Component.translatable("gui.researchenchanting.node.active", a.paid(), Config.cost(lvl), new ItemStack(l.resource()).getHoverName()).withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable("gui.researchenchanting.node.feed").withStyle(ChatFormatting.GRAY));
            }
            case AVAILABLE, LOCKED -> {
                tip.add(Component.translatable("gui.researchenchanting.node.cost", Config.cost(lvl), new ItemStack(l.resource()).getHoverName()).withStyle(ChatFormatting.GRAY));
                int xp = lvl * Config.XP_PER_LEVEL.get();
                var player = Minecraft.getInstance().player;
                if (xp > 0) tip.add(Component.translatable("gui.researchenchanting.node.xp", xp).withStyle(
                        player.experienceLevel >= xp || player.getAbilities().instabuild ? ChatFormatting.GRAY : ChatFormatting.RED));
                if (st == Research.State.AVAILABLE) {
                    boolean busy = d.active().map(a -> a.paid() > 0).orElse(false);
                    if (busy) tip.add(Component.translatable("gui.researchenchanting.node.busy").withStyle(ChatFormatting.RED));
                    else if (Research.replaces(d, l)) {
                        Component held = ResearchFocusItem.enchantName(d.enchantment().get(), d.level());
                        boolean confirming = confirmLine == n && confirmLevel == lvl && clock < confirmUntil;
                        tip.add(Component.translatable(confirming ? "gui.researchenchanting.node.confirm" : "gui.researchenchanting.node.replaces", held)
                                .withStyle(confirming ? ChatFormatting.RED : ChatFormatting.GOLD));
                    } else tip.add(Component.translatable("gui.researchenchanting.node.click").withStyle(ChatFormatting.YELLOW));
                } else if (lvl == 1 && l.requires().isPresent()) {
                    Focus.Requirement r = l.requires().get();
                    tip.add(Component.translatable("gui.researchenchanting.node.opens_at", ResearchFocusItem.enchantName(r.enchantment(), r.level())).withStyle(ChatFormatting.RED));
                } else {
                    tip.add(Component.translatable("gui.researchenchanting.node.previous").withStyle(ChatFormatting.RED));
                }
            }
        }
        return tip;
    }

    boolean mouseClicked(double mx, double my, int button) {
        dragged = false;
        return true;   // the click lands on release, so dragging doesn't start research
    }

    /** Called by the screen on release inside the panel. */
    boolean dragging(double mx, double my, int button) {
        return button == 0 && inside(mx, my);
    }

    boolean drag(double dx, double dy) {
        dragged = true;
        int tw = (maxX - minX + 1) * STEP, th = (maxY - minY + 1) * STEP;
        panX = tw + PAD * 2 > W ? Mth.clamp(panX + dx, W - tw - PAD, PAD) : PAD;
        panY = th + PAD * 2 > H ? Mth.clamp(panY + dy, H - th - PAD, PAD) : PAD;
        return true;
    }

    boolean mouseScrolled(double sx, double sy) {
        return drag(sx * 12, sy * 12);
    }

    void released(double mx, double my) {
        if (dragged || !inside(mx, my)) return;
        ResearchData d = data();
        if (d == null) return;
        for (int n = 0; n < focus.lines().size(); n++) {
            Focus.Line l = focus.lines().get(n);
            for (int lvl = 1; lvl <= l.max(); lvl++) {
                int nx = nodeX(l, lvl), ny = nodeY(l);
                if (mx < nx || my < ny || mx >= nx + NODE || my >= ny + NODE) continue;
                if (Research.state(d, l, lvl) != Research.State.AVAILABLE) return;
                if (Research.replaces(d, l) && !(confirmLine == n && confirmLevel == lvl && clock < confirmUntil)) {
                    confirmLine = n;
                    confirmLevel = lvl;
                    confirmUntil = clock + 120;
                    return;
                }
                confirmLine = -1;
                screen.send(ResearchTableMenu.RESEARCH + n * 16 + lvl);
                return;
            }
        }
    }
}
