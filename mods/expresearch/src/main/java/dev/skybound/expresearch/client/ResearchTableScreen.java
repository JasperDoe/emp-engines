package dev.skybound.expresearch.client;

import dev.skybound.expresearch.menu.ResearchTableMenu;
import dev.skybound.expresearch.net.DoResearchPayload;
import dev.skybound.expresearch.research.ResearchCategory;
import dev.skybound.expresearch.research.ResearchNode;
import dev.skybound.expresearch.research.ResearchRegistry;
import dev.skybound.expresearch.research.ResearchService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Advancement-style research screen: category tabs along the top, a pannable
 * tree of nodes, the selected node's cost, and the blueprint slots.
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final int W = 256, H = 258;
    private static final int TREE_X = 8, TREE_Y = 24, TREE_W = 240, TREE_H = 104;
    private static final int CELL = 30, NODE = 24;
    private static final int BTN_X = 192, BTN_Y = 132, BTN_W = 56, BTN_H = 14;

    private static final int BG = 0xFF2B2B2B, PANEL = 0xFF1B1B1B, BORDER = 0xFF555555;
    private static final int DONE = 0xFF4CAF50, READY = 0xFFE0B040, LOCKED = 0xFF555555, LINE = 0xFF808080;

    private List<ResourceLocation> tabs = List.of();
    private ResourceLocation tab;
    private ResourceLocation selected;
    private double panX, panY;
    private boolean dragging;

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        tabs = ResearchRegistry.categories().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, ResearchCategory> e) -> e.getValue().order())
                        .thenComparing(e -> e.getKey().toString()))
                .map(Map.Entry::getKey).toList();
        if (tab == null && !tabs.isEmpty()) selectTab(tabs.get(0));
    }

    private void selectTab(ResourceLocation id) {
        tab = id;
        selected = null;
        // Centre the tree's bounding box in the viewport.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (ResearchNode n : nodesInTab()) {
            minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y()); maxY = Math.max(maxY, n.y());
        }
        if (minX == Integer.MAX_VALUE) { panX = panY = 0; return; }
        double treeW = (maxX - minX) * CELL + NODE, treeH = (maxY - minY) * CELL + NODE;
        panX = (TREE_W - treeW) / 2 - minX * CELL;
        panY = (TREE_H - treeH) / 2 - minY * CELL;
    }

    private List<ResearchNode> nodesInTab() {
        List<ResearchNode> out = new ArrayList<>();
        for (ResearchNode n : ResearchRegistry.nodes().values()) if (n.category().equals(tab)) out.add(n);
        return out;
    }

    private int nodeScreenX(ResearchNode n) { return leftPos + TREE_X + (int) Math.round(panX) + n.x() * CELL; }
    private int nodeScreenY(ResearchNode n) { return topPos + TREE_Y + (int) Math.round(panY) + n.y() * CELL; }

    private static ItemStack icon(ResourceLocation item) {
        return new ItemStack(BuiltInRegistries.ITEM.get(item));
    }

    private int stateColour(ResourceLocation id, ResearchNode n) {
        Set<ResourceLocation> done = ClientResearch.unlocked();
        if (done.contains(id)) return DONE;
        return ResearchService.parentsMet(n, done) ? READY : LOCKED;
    }

    // --- drawing --------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, BG);
        outline(g, x, y, W, H, BORDER);

        // Tabs
        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 8 + i * 30, ty = y + 2;
            boolean active = tabs.get(i).equals(tab);
            g.fill(tx, ty, tx + 28, ty + 20, active ? PANEL : 0xFF3A3A3A);
            outline(g, tx, ty, 28, 20, active ? READY : BORDER);
            ResearchCategory c = ResearchRegistry.categories().get(tabs.get(i));
            if (c != null) g.renderItem(icon(c.icon()), tx + 6, ty + 2);
        }

        // Tree viewport
        g.fill(x + TREE_X, y + TREE_Y, x + TREE_X + TREE_W, y + TREE_Y + TREE_H, PANEL);
        outline(g, x + TREE_X - 1, y + TREE_Y - 1, TREE_W + 2, TREE_H + 2, BORDER);
        g.enableScissor(x + TREE_X, y + TREE_Y, x + TREE_X + TREE_W, y + TREE_Y + TREE_H);
        Map<ResourceLocation, ResearchNode> all = ResearchRegistry.nodes();
        for (ResearchNode n : nodesInTab()) {
            for (ResourceLocation parentId : n.parents()) {
                ResearchNode parent = all.get(parentId);
                if (parent == null || !parent.category().equals(tab)) continue;
                int ax = nodeScreenX(parent) + NODE / 2, ay = nodeScreenY(parent) + NODE / 2;
                int bx = nodeScreenX(n) + NODE / 2, by = nodeScreenY(n) + NODE / 2;
                int midX = (ax + bx) / 2;
                hLine(g, ax, midX, ay, LINE);
                vLine(g, midX, ay, by, LINE);
                hLine(g, midX, bx, by, LINE);
            }
        }
        for (Map.Entry<ResourceLocation, ResearchNode> e : all.entrySet()) {
            ResearchNode n = e.getValue();
            if (!n.category().equals(tab)) continue;
            int nx = nodeScreenX(n), ny = nodeScreenY(n);
            int colour = stateColour(e.getKey(), n);
            g.fill(nx, ny, nx + NODE, ny + NODE, 0xFF262626);
            outline(g, nx, ny, NODE, NODE, colour);
            if (e.getKey().equals(selected)) outline(g, nx - 2, ny - 2, NODE + 4, NODE + 4, 0xFFFFFFFF);
            g.renderItem(icon(n.icon()), nx + 4, ny + 4);
            if (colour == LOCKED) g.fill(nx + 1, ny + 1, nx + NODE - 1, ny + NODE - 1, 0x99000000);
        }
        g.disableScissor();

        // Info panel
        g.fill(x + 8, y + 130, x + 186, y + 170, PANEL);
        ResearchNode node = selected == null ? null : all.get(selected);
        if (node == null) {
            g.drawString(font, Component.translatable("expresearch.screen.pick"), x + 12, y + 134, 0xAAAAAA, false);
        } else {
            g.drawString(font, node.title(), x + 12, y + 134, 0xFFFFFF, false);
            boolean done = ClientResearch.unlocked().contains(selected);
            if (done) {
                g.drawString(font, Component.translatable("expresearch.screen.known").withStyle(ChatFormatting.GREEN), x + 12, y + 146, 0xFFFFFF, false);
            } else {
                boolean enoughXp = minecraft.player.isCreative() || ResearchService.totalXp(minecraft.player) >= node.xp();
                g.drawString(font, Component.translatable("expresearch.screen.xp", node.xp()), x + 12, y + 146, enoughXp ? 0x80FF20 : 0xFF5555, false);
                int sx = x + 12;
                for (ResearchNode.Sample s : node.samples()) {
                    ItemStack st = icon(s.item());
                    st.setCount(Math.max(1, Math.min(99, s.count())));
                    g.renderItem(st, sx, y + 154);
                    g.renderItemDecorations(font, st, sx, y + 154);
                    sx += 18;
                    if (sx > x + 170) break;
                }
            }
        }

        // Research button
        boolean canPress = node != null && !ClientResearch.unlocked().contains(selected)
                && ResearchService.parentsMet(node, ClientResearch.unlocked());
        boolean hover = inside(mouseX, mouseY, x + BTN_X, y + BTN_Y, BTN_W, BTN_H);
        g.fill(x + BTN_X, y + BTN_Y, x + BTN_X + BTN_W, y + BTN_Y + BTN_H, canPress ? (hover ? 0xFF3D7A3F : 0xFF2E5E30) : 0xFF3A3A3A);
        outline(g, x + BTN_X, y + BTN_Y, BTN_W, BTN_H, BORDER);
        Component label = Component.translatable("expresearch.screen.research");
        g.drawString(font, label, x + BTN_X + (BTN_W - font.width(label)) / 2, y + BTN_Y + 3, canPress ? 0xFFFFFF : 0x888888, false);

        // Slot frames and arrow
        slotFrame(g, x + ResearchTableMenu.INPUT_X, y + ResearchTableMenu.INPUT_Y);
        slotFrame(g, x + ResearchTableMenu.OUTPUT_X, y + ResearchTableMenu.OUTPUT_Y);
        g.drawString(font, "→", x + ResearchTableMenu.INPUT_X + 19, y + ResearchTableMenu.INPUT_Y + 4, 0xAAAAAA, false);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slotFrame(g, x + ResearchTableMenu.INV_X + col * 18, y + ResearchTableMenu.INV_Y + row * 18);
        for (int col = 0; col < 9; col++)
            slotFrame(g, x + ResearchTableMenu.INV_X + col * 18, y + ResearchTableMenu.INV_Y + 58);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Title and inventory labels are drawn as part of the panels instead.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        // Node tooltips
        if (inside(mouseX, mouseY, leftPos + TREE_X, topPos + TREE_Y, TREE_W, TREE_H)) {
            for (ResearchNode n : nodesInTab()) {
                if (inside(mouseX, mouseY, nodeScreenX(n), nodeScreenY(n), NODE, NODE)) {
                    List<FormattedCharSequence> lines = new ArrayList<>();
                    lines.add(Component.literal(n.title()).getVisualOrderText());
                    if (!n.description().isEmpty())
                        lines.addAll(font.split(Component.literal(n.description()).withStyle(ChatFormatting.GRAY), 180));
                    g.renderTooltip(font, lines, mouseX, mouseY);
                    break;
                }
            }
        }
        // Tab tooltips
        for (int i = 0; i < tabs.size(); i++) {
            if (inside(mouseX, mouseY, leftPos + 8 + i * 30, topPos + 2, 28, 20)) {
                ResearchCategory c = ResearchRegistry.categories().get(tabs.get(i));
                if (c != null) g.renderTooltip(font, Component.literal(c.title()), mouseX, mouseY);
            }
        }
    }

    // --- input ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < tabs.size(); i++) {
                if (inside(mx, my, leftPos + 8 + i * 30, topPos + 2, 28, 20)) {
                    selectTab(tabs.get(i));
                    return true;
                }
            }
            if (inside(mx, my, leftPos + BTN_X, topPos + BTN_Y, BTN_W, BTN_H) && selected != null) {
                PacketDistributor.sendToServer(new DoResearchPayload(selected));
                return true;
            }
            if (inside(mx, my, leftPos + TREE_X, topPos + TREE_Y, TREE_W, TREE_H)) {
                for (Map.Entry<ResourceLocation, ResearchNode> e : ResearchRegistry.nodes().entrySet()) {
                    if (e.getValue().category().equals(tab)
                            && inside(mx, my, nodeScreenX(e.getValue()), nodeScreenY(e.getValue()), NODE, NODE)) {
                        selected = e.getKey();
                        return true;
                    }
                }
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            panX += dx;
            panY += dy;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    // --- helpers --------------------------------------------------------------

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void hLine(GuiGraphics g, int x1, int x2, int y, int c) {
        g.fill(Math.min(x1, x2), y - 1, Math.max(x1, x2) + 1, y + 1, c);
    }

    private static void vLine(GuiGraphics g, int x, int y1, int y2, int c) {
        g.fill(x - 1, Math.min(y1, y2), x + 1, Math.max(y1, y2) + 1, c);
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        g.fill(x, y, x + 16, y + 16, 0xFF373737);
    }
}
