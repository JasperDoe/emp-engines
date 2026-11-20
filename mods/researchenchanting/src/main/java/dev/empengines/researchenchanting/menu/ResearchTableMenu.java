package dev.empengines.researchenchanting.menu;

import dev.empengines.researchenchanting.Config;
import dev.empengines.researchenchanting.Registry;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.focus.Focus;
import dev.empengines.researchenchanting.item.ResearchData;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import dev.empengines.researchenchanting.table.Echo;
import dev.empengines.researchenchanting.table.Milestones;
import dev.empengines.researchenchanting.table.Research;
import dev.empengines.researchenchanting.table.Shelves;
import dev.empengines.researchenchanting.table.TableData;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The enchanting table's screen, laid out exactly like vanilla's (item slot, lapis slot, three buttons). What the
 * buttons do depends on what's in the item slot:
 * <ul>
 * <li>a plain book: the Echo minigame (repeat the order the buttons light up);</li>
 * <li>a Research Focus: open its research tree, and the research running now;</li>
 * <li>anything else: the enchantments the shelved foci offer for it, one per button, scrolling.</li>
 * </ul>
 * Button ids: 0-2 press an Echo button; {@link #ENCHANT} + option * 16 + level enchants; {@link #RESEARCH} + line * 16
 * + level starts a research.
 */
public class ResearchTableMenu extends AbstractContainerMenu {
    public static final int MODE_EMPTY = 0, MODE_BIND = 1, MODE_RESEARCH = 2, MODE_ENCHANT = 3;
    public static final int ENCHANT = 1000, RESEARCH = 3000;
    public static final int MAX_OPTIONS = 24;

    // data slots
    public static final int D_MODE = 0, D_PHASE = 1, D_ROUND = 2, D_ROUNDS = 3, D_LEN = 4, D_POS = 5, D_PLAY = 6, D_STEP = 7,
            D_SEQ = 8, D_STARVED = D_SEQ + Echo.MAX_LEN, D_OPTIONS = D_STARVED + 1, D_OPTION0 = D_OPTIONS + 1,
            D_SIZE = D_OPTION0 + MAX_OPTIONS * 2;

    private final Container inv;
    private final ContainerLevelAccess access;
    private final TableData table;
    private final Player player;
    public final ContainerData data;
    /** Server: the options the data slots describe. */
    private final List<Holder<Enchantment>> options = new ArrayList<>();
    private ItemStack lastItem = ItemStack.EMPTY;
    private int refreshIn;

    /** Client. */
    public ResearchTableMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ContainerLevelAccess.NULL, null);
        buf.readBlockPos();
    }

    /** Server. */
    public ResearchTableMenu(int id, Inventory playerInv, ContainerLevelAccess access, TableData table) {
        super(Registry.TABLE_MENU.get(), id);
        this.access = access;
        this.table = table;
        this.player = playerInv.player;
        this.inv = table != null ? table.items : new SimpleContainer(2);
        this.data = new SimpleContainerData(D_SIZE);
        addSlot(new Slot(this.inv, TableData.BOOK, 15, 47) {
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(this.inv, TableData.LAPIS, 35, 47) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Tags.Items.ENCHANTING_FUELS); }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c, 8 + c * 18, 142));
        addDataSlots(data);
    }

    public ItemStack item() { return inv.getItem(TableData.BOOK); }
    /** Server: the table this menu looks at. */
    public TableData table() { return table; }
    public int lapis() { return inv.getItem(TableData.LAPIS).getCount(); }
    public int mode() { return data.get(D_MODE); }

    // ---- keeping the data slots current (server) ---------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        if (table != null) refresh();
        super.broadcastChanges();
    }

    private void refresh() {
        ItemStack item = item();
        boolean changed = !ItemStack.isSameItemSameComponents(item, lastItem);
        if (changed) lastItem = item.copy();
        int mode = item.isEmpty() ? MODE_EMPTY
                : item.is(Items.BOOK) ? MODE_BIND
                : item.is(Registry.RESEARCH_FOCUS.get()) ? MODE_RESEARCH : MODE_ENCHANT;
        data.set(D_MODE, mode);
        if (mode == MODE_BIND) refreshBinding(item);
        else if (changed) table.echo = null;
        if (mode == MODE_ENCHANT && (changed || --refreshIn <= 0)) {
            refreshIn = 20;
            access.execute((level, pos) -> refreshOptions(level, pos, item));
        }
        if (mode != MODE_ENCHANT) data.set(D_OPTIONS, 0);
        data.set(D_STARVED, mode == MODE_RESEARCH && table.starved ? 1 : 0);
    }

    private void refreshBinding(ItemStack book) {
        access.execute((level, pos) -> {
            ResourceLocation pending = book.get(Registry.PENDING_FOCUS.get());
            if (pending == null || Foci.get(pending, false) == null) {
                pending = drawFocus(level, pos);
                if (pending == null) return;
                book.set(Registry.PENDING_FOCUS.get(), pending);
                inv.setChanged();
            }
            Echo e = table.echo;
            if (e == null || !e.focus.equals(pending)) {
                int books = Shelves.books(level, pos).size();
                int slower = Math.min(4, books / Config.ECHO_BOOKS_PER_SLOWER.get());
                table.echo = e = new Echo(pending, Config.ECHO_ROUNDS.get(), Config.ECHO_START.get(),
                        Config.ECHO_STEP_TICKS.get() + 3 * slower, level.random);
            }
            data.set(D_PHASE, e.phase());
            data.set(D_ROUND, e.round());
            data.set(D_ROUNDS, e.rounds);
            data.set(D_LEN, e.length());
            data.set(D_POS, e.pos());
            data.set(D_PLAY, e.playId());
            data.set(D_STEP, e.stepTicks);
            for (int i = 0; i < Echo.MAX_LEN; i++) data.set(D_SEQ + i, e.step(i));
        });
    }

    /** Random focus, fresh draws first: foci already shelved around this table are skipped until all have been drawn. */
    private ResourceLocation drawFocus(Level level, BlockPos pos) {
        List<ResourceLocation> open = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Focus> e : Foci.all(false).entrySet()) {
            if (e.getValue().stage().isPresent() && !hasStage(e.getValue().stage().get())) continue;
            open.add(e.getKey());
        }
        if (open.isEmpty()) return null;
        Set<ResourceLocation> shelved = Shelves.shelvedFoci(level, pos);
        List<ResourceLocation> fresh = open.stream().filter(id -> !shelved.contains(id)).toList();
        List<ResourceLocation> from = fresh.isEmpty() ? open : fresh;
        return from.get(level.random.nextInt(from.size()));
    }

    private boolean hasStage(ResourceLocation stage) {
        if (!ModList.get().isLoaded("chapters")) return true;
        try {
            return !(player instanceof ServerPlayer sp) || com.gabinx.chapters.api.ChaptersAPI.hasStage(sp, stage);
        } catch (Throwable t) {
            return true;
        }
    }

    private void refreshOptions(Level level, BlockPos pos, ItemStack item) {
        options.clear();
        net.minecraft.core.Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (Map.Entry<ResourceLocation, Integer> e : Shelves.offered(level, pos).entrySet()) {
            reg.getHolder(ResourceKey.create(Registries.ENCHANTMENT, e.getKey())).ifPresent(h -> {
                if (h.value().canEnchant(item) && options.size() < MAX_OPTIONS) options.add(h);
            });
        }
        Map<ResourceLocation, Integer> offered = Shelves.offered(level, pos);
        options.sort(Comparator.comparing(h -> h.value().description().getString()));
        data.set(D_OPTIONS, options.size());
        for (int i = 0; i < options.size(); i++) {
            Holder<Enchantment> h = options.get(i);
            data.set(D_OPTION0 + i * 2, reg.getId(h.value()));
            data.set(D_OPTION0 + i * 2 + 1, offered.getOrDefault(h.unwrapKey().get().location(), 0));
        }
    }

    // ---- buttons (server) ------------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (table == null || !(p instanceof ServerPlayer sp)) return false;
        if (id >= 0 && id < 3) return press(sp, id);
        if (id >= ENCHANT && id < RESEARCH) return enchant(sp, (id - ENCHANT) / 16, (id - ENCHANT) % 16);
        if (id >= RESEARCH) return research(sp, (id - RESEARCH) / 16, (id - RESEARCH) % 16);
        return false;
    }

    private boolean press(ServerPlayer p, int button) {
        Echo echo = table.echo;
        ItemStack book = item();
        if (echo == null || !book.is(Items.BOOK)) return false;
        boolean free = p.getAbilities().instabuild;
        if (!free && lapis() < 1) return false;
        if (!free && echo.phase() == Echo.IDLE && p.experienceLevel < Config.BIND_XP.get()) return false;
        Echo.Result result = echo.press(button);
        access.execute((level, pos) -> {
            ServerLevel sl = (ServerLevel) level;
            switch (result) {
                case STARTED, RIGHT -> {}
                case ROUND_DONE -> level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
                case WRONG -> {
                    if (!free) inv.getItem(TableData.LAPIS).shrink(1);
                    inv.setChanged();
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.6f);
                    sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.01);
                }
                case SOLVED -> {
                    if (!free) p.giveExperienceLevels(-Config.BIND_XP.get());
                    inv.setItem(TableData.BOOK, ResearchFocusItem.blank(echo.focus));
                    table.echo = null;
                    level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.4f);
                    sl.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 60, 0.3, 0.4, 0.3, 0.8);
                    p.displayClientMessage(Component.translatable("message.researchenchanting.bound", Focus.name(echo.focus)), true);
                    Milestones.award(p, Milestones.BIND_FOCUS);
                }
                default -> {}
            }
        });
        broadcastChanges();
        return true;
    }

    private boolean enchant(ServerPlayer p, int index, int level) {
        if (index < 0 || index >= options.size() || level < 1) return false;
        ItemStack item = item();
        if (item.isEmpty() || item.is(Items.BOOK) || item.is(Registry.RESEARCH_FOCUS.get())) return false;
        Holder<Enchantment> h = options.get(index);
        int max = data.get(D_OPTION0 + index * 2 + 1);
        int current = EnchantmentHelper.getItemEnchantmentLevel(h, item);
        if (level > max || level <= current || !h.value().canEnchant(item)) return false;
        for (Holder<Enchantment> other : EnchantmentHelper.getEnchantmentsForCrafting(item).keySet()) {
            if (!other.equals(h) && !Enchantment.areCompatible(h, other)) return false;
        }
        int xp = level - current;
        int lapis = Config.LAPIS_PER_ENCHANT.get();
        boolean free = p.getAbilities().instabuild;
        if (!free && (p.experienceLevel < xp || lapis() < lapis)) return false;
        if (!free) {
            p.giveExperienceLevels(-xp);
            inv.getItem(TableData.LAPIS).shrink(lapis);
        }
        int paidLapis = free ? 0 : lapis, paidXp = free ? 0 : xp;
        // The table works on it for a few seconds: the menu closes so everyone can watch.
        access.execute((lvl, pos) -> table.startEnchant((ServerLevel) lvl, pos, p, h, level, paidXp, paidLapis));
        return true;
    }

    private boolean research(ServerPlayer p, int lineIndex, int level) {
        ItemStack book = item();
        ResearchData d = ResearchFocusItem.data(book);
        if (d == null) return false;
        Focus focus = Foci.get(d.focus(), false);
        if (focus == null || lineIndex < 0 || lineIndex >= focus.lines().size()) return false;
        Focus.Line line = focus.lines().get(lineIndex);
        if (!Research.available(d, line, level)) return false;
        if (d.active().isPresent()) {
            ResearchData.Active a = d.active().get();
            if (a.enchantment().equals(line.enchantment()) && a.level() == level) return false;
            if (a.paid() > 0) {
                p.displayClientMessage(Component.translatable("message.researchenchanting.finish_first",
                        ResearchFocusItem.enchantName(a.enchantment(), a.level())), true);
                return false;
            }
        }
        int xp = level * Config.XP_PER_LEVEL.get();
        boolean free = p.getAbilities().instabuild;
        if (!free && p.experienceLevel < xp) return false;
        if (!free) p.giveExperienceLevels(-xp);
        if (free) book.set(Registry.RESEARCH.get(), d.complete(line.enchantment(), level));
        // (A different line replaces the book's enchantment once researched; the screen asks first.)
        else book.set(Registry.RESEARCH.get(), d.withActive(new ResearchData.Active(line.enchantment(), level, 0)));
        inv.setChanged();
        access.execute((lvl, pos) -> lvl.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0f, 0.8f));
        if (free) Milestones.award(p, Milestones.RESEARCH_NODE);
        broadcastChanges();
        return true;
    }

    // ---- the rest ---------------------------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player p) {
        return (table == null || table.pending == null) && stillValid(access, p, Blocks.ENCHANTING_TABLE);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return copy;
        ItemStack stack = slot.getItem();
        copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
        } else if (stack.is(Tags.Items.ENCHANTING_FUELS)) {
            if (!moveItemStackTo(stack, 1, 2, true)) return ItemStack.EMPTY;
        } else {
            Slot target = this.slots.get(0);
            if (target.hasItem() || !target.mayPlace(stack)) return ItemStack.EMPTY;
            target.setByPlayer(stack.split(1));
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return copy;
    }
}
