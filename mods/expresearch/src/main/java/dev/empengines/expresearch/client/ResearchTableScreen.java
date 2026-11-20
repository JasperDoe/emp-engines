package dev.empengines.expresearch.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.empengines.expresearch.menu.ResearchTableMenu;
import dev.empengines.expresearch.net.DoResearchPayload;
import dev.empengines.expresearch.research.ResearchCategory;
import dev.empengines.expresearch.research.ResearchNode;
import dev.empengines.expresearch.research.ResearchRegistry;
import dev.empengines.expresearch.research.ResearchService;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Advancement-style research screen: category tabs above the window, a pannable tree of nodes, the selected
 * node's cost, and the blueprint slots.
 *
 * Built only from vanilla's own GUI art (the plain panel, the inventory's slots, the
 * advancement tabs and frames, the button), so whatever restyles vanilla screens restyles this one too: the
 * grey, light and dark /uitheme looks all apply. The few flat areas (the tree and info insets) take their colours
 * from the panel texture in use.
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    // vanilla's container width, so the inventory sits in the usual place
    private static final int W = 176, H = 217;
    private static final int TREE_X = 7, TREE_Y = 7, TREE_W = 162, TREE_H = 78;
    private static final int INFO_X = 7, INFO_Y = 89, INFO_W = 104, INFO_H = 40;
    private static final int CELL = 30, NODE = 26;
    private static final int BTN_X = 115, BTN_Y = 89, BTN_W = 54, BTN_H = 16;
    private static final int TAB_W = 28, TAB_H = 32, TAB_STEP = 32;

    private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/demo_background.png");
    private static final ResourceLocation INVENTORY = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_OFF = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final ResourceLocation BUTTON_HI = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation FRAME_DONE = ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained");
    private static final ResourceLocation FRAME_OPEN = ResourceLocation.withDefaultNamespace("advancements/task_frame_unobtained");
    private static final ResourceLocation FRAME_READY = ResourceLocation.withDefaultNamespace("advancements/goal_frame_unobtained");

    /** Colours read off the panel texture: inset fill, inset shadow edge, inset light edge, text. */
    private int inset = 0xFF8B8B8B, insetDark = 0xFF373737, insetLight = 0xFFFFFFFF, text = 0x404040;

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
        topPos = Math.max(topPos, TAB_H - 4);   // room for the tabs above the window
        readPalette();
        tabs = ResearchRegistry.categories().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, ResearchCategory> e) -> e.getValue().order())
                        .thenComparing(e -> e.getKey().toString()))
                .map(Map.Entry::getKey).toList();
        if (tab == null && !tabs.isEmpty()) selectTab(tabs.get(0));
    }

    /** Takes the inset and text colours from the panel texture of the active resource packs. */
    private void readPalette() {
        try (InputStream in = minecraft.getResourceManager().open(PANEL); NativeImage img = NativeImage.read(in)) {
            int base = argb(img.getPixelRGBA(img.getWidth() / 2, img.getHeight() / 2));
            inset = shade(base, 0.7F);
            insetDark = shade(base, 0.28F);
            insetLight = shade(base, 1.3F);
            int lum = ((base >> 16 & 255) * 3 + (base >> 8 & 255) * 6 + (base & 255)) / 10;
            text = lum < 110 ? 0xE0E0E0 : 0x404040;
        } catch (Exception ignored) {
            // vanilla's colours stay
        }
    }

    /** NativeImage pixels are ABGR. */
    private static int argb(int abgr) {
        return (abgr & 0xFF00FF00) | (abgr >> 16 & 0xFF) | (abgr & 0xFF) << 16;
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, (int) ((c >> 16 & 255) * f)), g = Math.min(255, (int) ((c >> 8 & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        if (f > 1) { r = Math.min(255, r + 24); g = Math.min(255, g + 24); b = Math.min(255, b + 24); }
        return 0xFF000000 | r << 16 | g << 8 | b;
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
    private int tabX(int i) { return leftPos + 6 + i * TAB_STEP; }
    private int tabY() { return topPos - TAB_H + 4; }

    private static ItemStack icon(ResourceLocation item) {
        return new ItemStack(BuiltInRegistries.ITEM.get(item));
    }

    private enum State { DONE, READY, LOCKED }

    private State state(ResourceLocation id, ResearchNode n) {
        Set<ResourceLocation> done = ClientResearch.unlocked();
        if (done.contains(id)) return State.DONE;
        return ResearchService.parentsMet(n, done) ? State.READY : State.LOCKED;
    }

    // --- drawing --------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        RenderSystem.enableBlend();

        // unselected tabs sit behind the window, the selected one in front
        for (int i = 0; i < tabs.size(); i++) if (!tabs.get(i).equals(tab)) drawTab(g, i, false);
        panel(g, x, y, W, H);
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).equals(tab)) drawTab(g, i, true);

        // Tree viewport
        insetBox(g, x + TREE_X, y + TREE_Y, TREE_W, TREE_H);
        g.enableScissor(x + TREE_X, y + TREE_Y, x + TREE_X + TREE_W, y + TREE_Y + TREE_H);
        Map<ResourceLocation, ResearchNode> all = ResearchRegistry.nodes();
        // lines like the advancement screen's: white with a dark edge
        for (int pass = 0; pass < 2; pass++) {
            for (ResearchNode n : nodesInTab()) {
                for (ResourceLocation parentId : n.parents()) {
                    ResearchNode parent = all.get(parentId);
                    if (parent == null || !parent.category().equals(tab)) continue;
                    int ax = nodeScreenX(parent) + NODE / 2, ay = nodeScreenY(parent) + NODE / 2;
                    int bx = nodeScreenX(n) + NODE / 2, by = nodeScreenY(n) + NODE / 2;
                    int midX = (ax + bx) / 2;
                    int c = pass == 0 ? 0xFF000000 : 0xFFFFFFFF, w = pass == 0 ? 2 : 1;
                    hLine(g, ax, midX, ay, w, c);
                    vLine(g, midX, ay, by, w, c);
                    hLine(g, midX, bx, by, w, c);
                }
            }
        }
        for (Map.Entry<ResourceLocation, ResearchNode> e : all.entrySet()) {
            ResearchNode n = e.getValue();
            if (!n.category().equals(tab)) continue;
            int nx = nodeScreenX(n), ny = nodeScreenY(n);
            State st = state(e.getKey(), n);
            g.blitSprite(st == State.DONE ? FRAME_DONE : st == State.READY ? FRAME_READY : FRAME_OPEN, nx, ny, NODE, NODE);
            g.renderFakeItem(icon(n.icon()), nx + 5, ny + 5);
            if (st == State.LOCKED) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(nx + 2, ny + 2, nx + NODE - 2, ny + NODE - 2, 0x99000000);
                g.pose().popPose();
            }
            if (e.getKey().equals(selected)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                outline(g, nx - 1, ny - 1, NODE + 2, NODE + 2, 0xFFFFFFFF);
                g.pose().popPose();
            }
        }
        g.disableScissor();

        // Info panel
        insetBox(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H);
        int tx = x + INFO_X + 3, tw = INFO_W - 6;
        ResearchNode node = selected == null ? null : all.get(selected);
        if (node == null) {
            g.drawWordWrap(font, Component.translatable("expresearch.screen.pick"), tx, y + INFO_Y + 3, tw, text);
        } else {
            g.drawString(font, font.plainSubstrByWidth(node.title(), tw), tx, y + INFO_Y + 3, text, false);
            boolean done = ClientResearch.unlocked().contains(selected);
            if (done) {
                g.drawString(font, Component.translatable("expresearch.screen.known").withStyle(ChatFormatting.GREEN), tx, y + INFO_Y + 13, text, true);
            } else {
                boolean enoughXp = minecraft.player.isCreative() || ResearchService.totalXp(minecraft.player) >= node.xp();
                g.drawString(font, Component.translatable("expresearch.screen.xp", node.xp()), tx, y + INFO_Y + 13, enoughXp ? 0x80FF20 : 0xFF5555, true);
                int sx = tx - 1;
                for (ResearchNode.Sample s : node.samples()) {
                    if (sx + 16 > x + INFO_X + INFO_W) break;
                    ItemStack st = icon(s.item());
                    st.setCount(Math.max(1, Math.min(99, s.count())));
                    g.renderItem(st, sx, y + INFO_Y + 23);
                    g.renderItemDecorations(font, st, sx, y + INFO_Y + 23);
                    sx += 18;
                }
            }
        }

        // Research button: vanilla's button sprite
        boolean canPress = node != null && !ClientResearch.unlocked().contains(selected)
                && ResearchService.parentsMet(node, ClientResearch.unlocked());
        boolean hover = inside(mouseX, mouseY, x + BTN_X, y + BTN_Y, BTN_W, BTN_H);
        g.blitSprite(!canPress ? BUTTON_OFF : hover ? BUTTON_HI : BUTTON, x + BTN_X, y + BTN_Y, BTN_W, BTN_H);
        Component label = Component.translatable("expresearch.screen.research");
        g.drawCenteredString(font, label, x + BTN_X + BTN_W / 2, y + BTN_Y + 4, canPress ? 0xFFFFFF : 0xA0A0A0);

        // Slots and the furnace's arrow between them
        slot(g, x + ResearchTableMenu.INPUT_X, y + ResearchTableMenu.INPUT_Y);
        slot(g, x + ResearchTableMenu.OUTPUT_X, y + ResearchTableMenu.OUTPUT_Y);
        // a small arrow between them, in the panel's text colour
        int ax = x + ResearchTableMenu.INPUT_X + 19, ay = y + ResearchTableMenu.INPUT_Y + 7, ac = 0xFF000000 | text;
        g.fill(ax, ay, ax + 7, ay + 2, ac);
        for (int k = 0; k < 4; k++) g.fill(ax + 7 + k, ay - 3 + k, ax + 8 + k, ay + 5 - k, ac);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slot(g, x + ResearchTableMenu.INV_X + col * 18, y + ResearchTableMenu.INV_Y + row * 18);
        for (int col = 0; col < 9; col++)
            slot(g, x + ResearchTableMenu.INV_X + col * 18, y + ResearchTableMenu.INV_Y + 58);
        RenderSystem.disableBlend();
    }

    private void drawTab(GuiGraphics g, int i, boolean active) {
        String pos = i == 0 ? "left" : i == tabs.size() - 1 && i > 0 ? "right" : "middle";
        ResourceLocation sprite = ResourceLocation.withDefaultNamespace("advancements/tab_above_" + pos + (active ? "_selected" : ""));
        int tx = tabX(i), ty = tabY();
        g.blitSprite(sprite, tx, ty, TAB_W, TAB_H);
        ResearchCategory c = ResearchRegistry.categories().get(tabs.get(i));
        if (c != null) g.renderFakeItem(icon(c.icon()), tx + 6, ty + 9);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Labels are drawn with the panels.
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
            if (inside(mouseX, mouseY, tabX(i), tabY(), TAB_W, TAB_H - 4)) {
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
                if (inside(mx, my, tabX(i), tabY(), TAB_W, TAB_H - 4)) {
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
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        // the tabs stick out above the window
        return super.hasClickedOutside(mx, my, left, top, button) && !(my >= tabY() && my < top && mx >= left && mx < left + W);
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
            for (int oy = 0; oy < ih; oy += sh) {
                int ph = Math.min(sh, ih - oy);
                g.blit(PANEL, x + c + ox, y + c + oy, c, c, pw, ph);
            }
        }
        for (int oy = 0; oy < ih; oy += sh) {
            int ph = Math.min(sh, ih - oy);
            g.blit(PANEL, x, y + c + oy, 0, c, c, ph);
            g.blit(PANEL, x + w - c, y + c + oy, tw - c, c, c, ph);
        }
    }

    /** A recessed area in the slot style: dark top-left edge, light bottom-right edge. */
    private void insetBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, insetLight);
        g.fill(x - 1, y - 1, x + w, y + h, insetDark);
        g.fill(x, y, x + w, y + h, inset);
    }

    /** One slot from vanilla's inventory screen texture. */
    private static void slot(GuiGraphics g, int x, int y) {
        g.blit(INVENTORY, x - 1, y - 1, 7, 83, 18, 18);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void hLine(GuiGraphics g, int x1, int x2, int y, int w, int c) {
        g.fill(Math.min(x1, x2) - w + 1, y - w, Math.max(x1, x2) + w, y + w, c);
    }

    private static void vLine(GuiGraphics g, int x, int y1, int y2, int w, int c) {
        g.fill(x - w, Math.min(y1, y2) - w + 1, x + w, Math.max(y1, y2) + w, c);
    }
}
