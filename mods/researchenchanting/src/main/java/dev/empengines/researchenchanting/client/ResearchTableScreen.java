package dev.empengines.researchenchanting.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.empengines.researchenchanting.Config;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.focus.Focus;
import dev.empengines.researchenchanting.item.ResearchData;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import dev.empengines.researchenchanting.menu.ResearchTableMenu;
import dev.empengines.researchenchanting.table.Echo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The research screen: vanilla's enchanting screen (same texture, sprites, book and layout, so UI resource packs
 * restyle it). The three buttons play the Echo minigame for a book, open the research tree for a focus, or list
 * the enchantments on offer for anything else. The tree draws over the three buttons ({@link ResearchTree}).
 * When nothing can happen, the buttons show a short hint saying what to do instead.
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final ResourceLocation BG = ResourceLocation.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    private static final ResourceLocation BOOK_TEX = ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");
    static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot");
    static final ResourceLocation SLOT_HI = ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot_highlighted");
    static final ResourceLocation SLOT_OFF = ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot_disabled");
    private static final ResourceLocation LAPIS_1 = ResourceLocation.withDefaultNamespace("container/enchanting_table/level_1");
    private static final ResourceLocation LAPIS_1_OFF = ResourceLocation.withDefaultNamespace("container/enchanting_table/level_1_disabled");
    private static final Style GLYPHS = Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"));
    /** Each Echo button's rune and tone. */
    private static final String[] RUNES = {"kel", "ors", "vyx"};
    private static final float[] TONES = {0.75F, 1.0F, 1.33F};

    // vanilla's colours on these buttons
    static final int TEXT = 0x685E4A, TEXT_HI = 0xFFFF80, TEXT_OFF = (TEXT & 0xFEFEFE) >> 1;
    static final int COST = 0x80FF20, COST_OFF = 0x408010;
    static final int ROW_X = 60, ROW_Y = 14, ROW_W = 108, ROW_H = 19;

    private final RandomSource random = RandomSource.create();
    private BookModel bookModel;
    private float flip, oFlip, flipT, flipA, open, oOpen;
    private ItemStack last = ItemStack.EMPTY;
    private int ticks;

    private int scroll;
    /** Chosen level per enchantment registry id (enchanting mode); defaults to the highest on offer. */
    private final Map<Integer, Integer> chosen = new HashMap<>();
    private ResearchTree tree;

    // Echo playback (client side)
    private int lastPlay = -1, playStart, lastTone = -1, flashRow = -1, flashUntil;
    private static final int LEAD = 12;

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        bookModel = new BookModel(minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
        if (tree != null) tree.layout();
    }

    void send(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    int data(int i) { return menu.data.get(i); }

    // ---- input -----------------------------------------------------------------------------------------------

    private int hoveredRow(double mx, double my) {
        for (int r = 0; r < 3; r++) {
            double dx = mx - (leftPos + ROW_X), dy = my - (topPos + ROW_Y + ROW_H * r);
            if (dx >= 0 && dy >= 0 && dx < ROW_W && dy < ROW_H) return r;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tree != null && tree.inside(mx, my)) return tree.mouseClicked(mx, my, button);
        int row = hoveredRow(mx, my);
        if (row >= 0) {
            switch (menu.mode()) {
                case ResearchTableMenu.MODE_BIND -> {
                    if (button == 0 && echoClickable()) {
                        tone(row);
                        flashRow = row;
                        flashUntil = ticks + 6;
                        send(row);
                        return true;
                    }
                }
                case ResearchTableMenu.MODE_ENCHANT -> {
                    int i = scroll + row;
                    if (i < options()) {
                        if (button == 1) { lowerLevel(i); return true; }
                        if (button == 0 && enchantState(i) == null) {
                            send(ResearchTableMenu.ENCHANT + i * 16 + chosenLevel(i));
                            return true;
                        }
                    }
                }
                default -> {}
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tree != null && tree.inside(mx, my)) return tree.mouseScrolled(sx, sy);
        if (menu.mode() == ResearchTableMenu.MODE_ENCHANT && options() > 3) {
            scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, options() - 3);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tree != null && tree.dragging(mx, my, button)) return tree.drag(dx, dy);
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (tree != null && button == 0 && tree.inside(mx, my)) { tree.released(mx, my); return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return super.keyPressed(key, scan, mods);
    }

    ResearchData focus() {
        ResearchData d = ResearchFocusItem.data(menu.item());
        return d != null && Foci.get(d.focus(), true) != null ? d : null;
    }

    void openTree() {
        ResearchData d = focus();
        if (d != null) tree = new ResearchTree(this, d.focus(), Foci.get(d.focus(), true));
    }

    void closeTree() { tree = null; }

    int left() { return leftPos; }
    int top() { return topPos; }

    // ---- ticking -------------------------------------------------------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        ItemStack item = menu.item();
        if (!ItemStack.matches(item, last)) {
            if (!ItemStack.isSameItem(item, last)) { scroll = 0; chosen.clear(); }
            last = item.copy();
            do flipT += random.nextInt(4) - random.nextInt(4); while (flip <= flipT + 1.0F && flip >= flipT - 1.0F);
        }
        // A focus in the table: its research tree is the screen, there is nothing else to do with it here.
        if (menu.mode() == ResearchTableMenu.MODE_RESEARCH) {
            ResearchData d = focus();
            if (d == null) tree = null;
            else if (tree == null || !tree.focusId().equals(d.focus())) openTree();
        } else tree = null;
        if (scroll > Math.max(0, options() - 3)) scroll = Math.max(0, options() - 3);
        tickEcho();
        oFlip = flip;
        oOpen = open;
        open = Mth.clamp(open + (menu.mode() != ResearchTableMenu.MODE_EMPTY ? 0.2F : -0.2F), 0.0F, 1.0F);
        float f = Mth.clamp((flipT - flip) * 0.4F, -0.2F, 0.2F);
        flipA += (f - flipA) * 0.9F;
        flip += flipA;
    }

    // ---- Echo ----------------------------------------------------------------------------------------------------

    private void tickEcho() {
        if (menu.mode() != ResearchTableMenu.MODE_BIND || data(ResearchTableMenu.D_PHASE) != Echo.PLAYING) { lastPlay = -1; return; }
        int play = data(ResearchTableMenu.D_PLAY);
        if (play != lastPlay) {
            lastPlay = play;
            playStart = ticks;
            lastTone = -1;
        }
        int idx = playbackIndex();
        if (idx >= 0 && idx < data(ResearchTableMenu.D_LEN) && idx != lastTone) {
            lastTone = idx;
            tone(data(ResearchTableMenu.D_SEQ + idx));
            // the book flips a page with each step
            flipT += random.nextBoolean() ? 1 : -1;
        }
    }

    private int playbackIndex() {
        int t = ticks - playStart - LEAD;
        return t < 0 ? -1 : t / Math.max(1, data(ResearchTableMenu.D_STEP));
    }

    private boolean playingBack() {
        return data(ResearchTableMenu.D_PHASE) == Echo.PLAYING && playbackIndex() < data(ResearchTableMenu.D_LEN);
    }

    /** The row the player just pressed, or -1. */
    private int pressedRow() {
        return flashRow >= 0 && ticks < flashUntil ? flashRow : -1;
    }

    /** The row the table is showing in its playback, or -1. */
    private int playedRow() {
        if (data(ResearchTableMenu.D_PHASE) != Echo.PLAYING) return -1;
        int idx = playbackIndex();
        if (idx < 0 || idx >= data(ResearchTableMenu.D_LEN)) return -1;
        int step = Math.max(1, data(ResearchTableMenu.D_STEP));
        int within = ticks - playStart - LEAD - idx * step;
        return within < step * 0.7F ? data(ResearchTableMenu.D_SEQ + idx) : -1;
    }

    private Component echoBlocked() {
        if (minecraft.player.getAbilities().instabuild) return null;
        if (menu.lapis() < 1) return Component.translatable("gui.researchenchanting.hint_lapis");
        if (data(ResearchTableMenu.D_PHASE) == Echo.IDLE && minecraft.player.experienceLevel < Config.BIND_XP.get()) {
            return Component.translatable("gui.researchenchanting.hint_xp", Config.BIND_XP.get());
        }
        return null;
    }

    private boolean echoClickable() {
        return echoBlocked() == null && !playingBack();
    }

    private void tone(int row) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, TONES[Mth.clamp(row, 0, 2)]));
    }

    // ---- drawing -----------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (tree != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            tree.render(g, mx, my);
            g.pose().popPose();
            if (tree != null && tree.inside(mx, my)) return;
        }
        renderTooltip(g, mx, my);
        int row = tree == null ? hoveredRow(mx, my) : -1;
        if (row >= 0) {
            List<Component> tip = rowTooltip(row);
            if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        Component status = status();
        if (status != null) {
            String s = font.plainSubstrByWidth(status.getString(), imageWidth - 16 - font.width(title));
            Integer c = status.getStyle().getColor() == null ? null : status.getStyle().getColor().getValue();
            g.drawString(font, s, imageWidth - 8 - font.width(s), titleLabelY, c != null ? c : 0x404040, false);
        }
    }

    /** Right of the title: what the minigame or the tree wants from you. */
    private Component status() {
        if (tree != null) {
            ResearchData d = focus();
            if (data(ResearchTableMenu.D_STARVED) != 0 && d != null && d.active().isPresent()) {
                Focus f = Foci.get(d.focus(), true);
                Component what = f == null ? Component.empty()
                        : f.line(d.active().get().enchantment()).map(l -> new ItemStack(l.resource()).getHoverName()).orElse(Component.empty());
                return Component.translatable("gui.researchenchanting.hint_starved", what).withStyle(ChatFormatting.DARK_RED);
            }
            return Component.translatable("gui.researchenchanting.tree_title", Focus.name(tree.focusId()));
        }
        if (menu.mode() != ResearchTableMenu.MODE_BIND) return null;
        if (data(ResearchTableMenu.D_PHASE) == Echo.IDLE) return Component.translatable("gui.researchenchanting.echo_begin");
        int round = data(ResearchTableMenu.D_ROUND), rounds = data(ResearchTableMenu.D_ROUNDS);
        if (playingBack()) return Component.translatable("gui.researchenchanting.echo_watch", round, rounds);
        return Component.translatable("gui.researchenchanting.echo_turn", round, rounds, data(ResearchTableMenu.D_POS), data(ResearchTableMenu.D_LEN));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderBook(g, partial);
        int hover = tree == null ? hoveredRow(mx, my) : -1;
        Component hint = hint();
        for (int r = 0; r < 3; r++) {
            int x = leftPos + ROW_X, y = topPos + ROW_Y + ROW_H * r;
            if (hint != null) { sprite(g, SLOT_OFF, x, y); continue; }
            switch (menu.mode()) {
                case ResearchTableMenu.MODE_BIND -> drawEchoRow(g, r, x, y, hover == r);
                case ResearchTableMenu.MODE_RESEARCH -> { if (tree == null) sprite(g, SLOT_OFF, x, y); }
                case ResearchTableMenu.MODE_ENCHANT -> drawEnchantRow(g, r, x, y, hover == r);
                default -> sprite(g, SLOT_OFF, x, y);
            }
        }
        if (hint != null) {
            g.drawWordWrap(font, hint, leftPos + ROW_X + 5, topPos + ROW_Y + 5, ROW_W - 10, TEXT);
        }
        if (hint == null && menu.mode() == ResearchTableMenu.MODE_ENCHANT && options() > 3) {
            if (scroll > 0) g.drawString(font, "▲", leftPos + 50, topPos + 15, TEXT, false);
            if (scroll < options() - 3) g.drawString(font, "▼", leftPos + 50, topPos + 62, TEXT, false);
        }
    }

    /** When nothing can be done here, the buttons are replaced by a line saying what to do. */
    private Component hint() {
        return switch (menu.mode()) {
            case ResearchTableMenu.MODE_EMPTY -> Component.translatable("gui.researchenchanting.hint_empty");
            case ResearchTableMenu.MODE_ENCHANT -> options() == 0 ? Component.translatable("gui.researchenchanting.hint_no_books") : null;
            default -> null;
        };
    }

    static void sprite(GuiGraphics g, ResourceLocation s, int x, int y) {
        RenderSystem.enableBlend();
        g.blitSprite(s, x, y, ROW_W, ROW_H);
        RenderSystem.disableBlend();
    }

    // Echo rows: one rune each, lit in turn during playback

    private static final int PLAY_BLUE = 0x6CC4FF;

    private void drawEchoRow(GuiGraphics g, int r, int x, int y, boolean hover) {
        boolean played = playedRow() == r;       // the table showing its sequence: blue
        boolean pressed = pressedRow() == r;     // your own press: yellow
        boolean blocked = echoBlocked() != null;
        boolean clickable = echoClickable();
        sprite(g, blocked && !played ? SLOT_OFF : played || pressed || (hover && clickable) ? SLOT_HI : SLOT, x, y);
        if (played) g.fill(x + 1, y + 1, x + ROW_W - 1, y + ROW_H - 1, 0x603C8CFF);
        int col = played ? PLAY_BLUE : blocked ? TEXT_OFF : pressed || (hover && clickable) ? TEXT_HI : TEXT;
        Component rune = Component.literal(RUNES[r]).withStyle(GLYPHS);
        g.pose().pushPose();
        g.pose().translate(x + ROW_W / 2.0F - font.width(rune), y + 3, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, rune, 0, 0, col, played);
        g.pose().popPose();
        if (played) g.fill(x + 2, y + 2, x + 4, y + ROW_H - 2, 0xFF000000 | PLAY_BLUE);
        else if (pressed) g.fill(x + 2, y + 2, x + 4, y + ROW_H - 2, 0xFFFFFF80);
    }

    // enchanting rows

    int options() {
        return Mth.clamp(data(ResearchTableMenu.D_OPTIONS), 0, ResearchTableMenu.MAX_OPTIONS);
    }

    private Optional<Holder.Reference<Enchantment>> holder(int i) {
        int id = data(ResearchTableMenu.D_OPTION0 + i * 2);
        return minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id);
    }

    private int maxLevel(int i) { return data(ResearchTableMenu.D_OPTION0 + i * 2 + 1); }

    private int chosenLevel(int i) {
        int id = data(ResearchTableMenu.D_OPTION0 + i * 2);
        int max = maxLevel(i);
        return Mth.clamp(chosen.getOrDefault(id, max), 1, max);
    }

    private void lowerLevel(int i) {
        int id = data(ResearchTableMenu.D_OPTION0 + i * 2);
        int current = holder(i).map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, menu.item())).orElse(0);
        int lvl = chosenLevel(i) - 1;
        if (lvl <= current || lvl < 1) lvl = maxLevel(i);
        chosen.put(id, lvl);
    }

    private int xpCost(int i) {
        int current = holder(i).map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, menu.item())).orElse(0);
        return Math.max(0, chosenLevel(i) - current);
    }

    /** Why option i can't be applied, or null if it can. */
    private Component enchantState(int i) {
        Optional<Holder.Reference<Enchantment>> oh = holder(i);
        if (oh.isEmpty()) return Component.literal("?");
        Holder<Enchantment> h = oh.get();
        ItemStack item = menu.item();
        int current = EnchantmentHelper.getItemEnchantmentLevel(h, item);
        if (current >= chosenLevel(i)) return Component.translatable("gui.researchenchanting.already", Enchantment.getFullname(h, current));
        for (Holder<Enchantment> other : EnchantmentHelper.getEnchantmentsForCrafting(item).keySet()) {
            if (!other.equals(h) && !Enchantment.areCompatible(h, other)) {
                return Component.translatable("gui.researchenchanting.conflicts", Enchantment.getFullname(other, EnchantmentHelper.getItemEnchantmentLevel(other, item)));
            }
        }
        if (minecraft.player.getAbilities().instabuild) return null;
        if (minecraft.player.experienceLevel < xpCost(i)) return Component.translatable("container.enchant.level.requirement", xpCost(i));
        if (menu.lapis() < Config.LAPIS_PER_ENCHANT.get()) return Component.translatable("gui.researchenchanting.need_lapis", Config.LAPIS_PER_ENCHANT.get());
        return null;
    }

    private void drawEnchantRow(GuiGraphics g, int r, int x, int y, boolean hover) {
        int i = scroll + r;
        if (i >= options()) { sprite(g, SLOT_OFF, x, y); return; }
        Optional<Holder.Reference<Enchantment>> h = holder(i);
        boolean ok = enchantState(i) == null;
        sprite(g, ok ? (hover ? SLOT_HI : SLOT) : SLOT_OFF, x, y);
        RenderSystem.enableBlend();
        g.blitSprite(ok ? LAPIS_1 : LAPIS_1_OFF, x + 1, y + 1, 16, 16);
        RenderSystem.disableBlend();
        String cost = String.valueOf(xpCost(i));
        String name = h.map(e -> Enchantment.getFullname(e, chosenLevel(i)).getString()).orElse("?");
        g.drawString(font, font.plainSubstrByWidth(name, 84 - font.width(cost)), x + 20, y + 3, ok ? (hover ? TEXT_HI : TEXT) : TEXT_OFF, false);
        g.drawString(font, cost, x + 106 - font.width(cost), y + 10, ok ? COST : COST_OFF);
    }

    // tooltips

    private List<Component> rowTooltip(int row) {
        List<Component> tip = new ArrayList<>();
        switch (menu.mode()) {
            case ResearchTableMenu.MODE_BIND -> {
                tip.add(Component.translatable("gui.researchenchanting.echo_title").withStyle(ChatFormatting.WHITE));
                tip.add(Component.translatable("gui.researchenchanting.echo_rules").withStyle(ChatFormatting.GRAY));
                Component blocked = echoBlocked();
                if (blocked != null) tip.add(blocked.copy().withStyle(ChatFormatting.RED));
                else {
                    tip.add(Component.empty());
                    tip.add(Component.translatable("gui.researchenchanting.echo_cost", Config.BIND_XP.get()).withStyle(ChatFormatting.GRAY));
                }
            }
            case ResearchTableMenu.MODE_RESEARCH -> {
                ResearchData d = ResearchFocusItem.data(menu.item());
                if (d == null) break;
                if (row == 0) tip.add(Component.translatable("gui.researchenchanting.open_tree_tip").withStyle(ChatFormatting.GRAY));
                if (row >= 1 && d.active().isPresent()) {
                    ResearchData.Active a = d.active().get();
                    Focus f = Foci.get(d.focus(), true);
                    tip.add(ResearchFocusItem.enchantName(a.enchantment(), a.level()).copy().withStyle(ChatFormatting.WHITE));
                    if (f != null) f.line(a.enchantment()).ifPresent(l -> tip.add(Component.translatable("gui.researchenchanting.feed",
                            Config.cost(a.level()) - a.paid(), new ItemStack(l.resource()).getHoverName()).withStyle(ChatFormatting.GRAY)));
                }
            }
            case ResearchTableMenu.MODE_ENCHANT -> {
                int i = scroll + row;
                if (i >= options()) break;
                holder(i).ifPresent(h -> {
                    tip.add(Enchantment.getFullname(h, chosenLevel(i)).copy().withStyle(ChatFormatting.WHITE));
                    Component why = enchantState(i);
                    if (why != null) tip.add(why.copy().withStyle(ChatFormatting.RED));
                    else tip.add(Component.translatable("gui.researchenchanting.enchant_cost", xpCost(i), Config.LAPIS_PER_ENCHANT.get()).withStyle(ChatFormatting.GRAY));
                    if (maxLevel(i) > 1) tip.add(Component.translatable("gui.researchenchanting.lower").withStyle(ChatFormatting.DARK_GRAY));
                });
            }
            default -> {}
        }
        return tip;
    }

    // vanilla's book, copied from EnchantmentScreen

    private void renderBook(GuiGraphics g, float partial) {
        float o = Mth.lerp(partial, oOpen, open);
        float fl = Mth.lerp(partial, oFlip, flip);
        Lighting.setupForEntityInInventory();
        g.pose().pushPose();
        g.pose().translate(leftPos + 33.0F, topPos + 31.0F, 100.0F);
        g.pose().scale(-40.0F, 40.0F, 40.0F);
        g.pose().mulPose(Axis.XP.rotationDegrees(25.0F));
        g.pose().translate((1.0F - o) * 0.2F, (1.0F - o) * 0.1F, (1.0F - o) * 0.25F);
        g.pose().mulPose(Axis.YP.rotationDegrees(-(1.0F - o) * 90.0F - 90.0F));
        g.pose().mulPose(Axis.XP.rotationDegrees(180.0F));
        float left = Mth.clamp(Mth.frac(fl + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float right = Mth.clamp(Mth.frac(fl + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        bookModel.setupAnim(0.0F, left, right, o);
        VertexConsumer vc = g.bufferSource().getBuffer(bookModel.renderType(BOOK_TEX));
        bookModel.renderToBuffer(g.pose(), vc, 15728880, OverlayTexture.NO_OVERLAY);
        g.flush();
        g.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
