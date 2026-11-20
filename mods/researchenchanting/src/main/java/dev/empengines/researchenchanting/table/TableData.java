package dev.empengines.researchenchanting.table;

import dev.empengines.researchenchanting.Config;
import dev.empengines.researchenchanting.Registry;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.focus.Focus;
import dev.empengines.researchenchanting.item.Notes;
import dev.empengines.researchenchanting.item.ResearchData;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import dev.empengines.researchenchanting.net.ResearchFly;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The research state the mod adds to each vanilla enchanting table: an item slot and a lapis slot that stay in
 * the table (like a furnace), the Echo minigame for a book being bound, and the research that pulls resource
 * from containers within the shelves' reach.
 */
public final class TableData {
    public static final int BOOK = 0, LAPIS = 1;

    private final EnchantingTableBlockEntity table;
    public final SimpleContainer items = new SimpleContainer(2);
    /** Transient: rebuilt when a different book goes in. */
    public Echo echo;
    /** The running research found no resource on its last pull (for the screen's hint). */
    public boolean starved;
    private int ticks;
    private ItemStack lastShown = ItemStack.EMPTY;
    private boolean lastActive;
    /** An enchantment being applied: the menu is closed and the item hangs over the table until it's done. */
    public Pending pending;

    /** Costs are paid up front and refunded if the table is broken before the end. */
    public record Pending(ResourceLocation enchantment, int level, int xp, int lapis, UUID player, long start, int duration) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Enchantment", enchantment.toString());
            t.putInt("Level", level);
            t.putInt("Xp", xp);
            t.putInt("Lapis", lapis);
            t.putUUID("Player", player);
            t.putLong("Start", start);
            t.putInt("Duration", duration);
            return t;
        }

        static Pending load(CompoundTag t) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString("Enchantment"));
            if (id == null || !t.hasUUID("Player")) return null;
            return new Pending(id, t.getInt("Level"), t.getInt("Xp"), t.getInt("Lapis"), t.getUUID("Player"), t.getLong("Start"), Math.max(1, t.getInt("Duration")));
        }
    }

    public TableData(EnchantingTableBlockEntity table) {
        this.table = table;
        items.addListener(c -> changed());
    }

    public ItemStack item() {
        return items.getItem(BOOK);
    }

    /** A book or focus sits in the table: draw vanilla's floating book. Anything else floats as itself. */
    public boolean showsBook() {
        ItemStack s = item();
        return s.is(Items.BOOK) || s.is(Registry.RESEARCH_FOCUS.get());
    }

    /** 0 to 1 through the enchantment being applied, or -1 when the table isn't enchanting. */
    public float enchantProgress(float partial) {
        Level level = table.getLevel();
        if (pending == null || level == null) return -1;
        return Mth.clamp((level.getGameTime() - pending.start() + partial) / pending.duration(), 0, 1);
    }

    /** A focus sits in the table with research under way (clients draw the streams for it). */
    public boolean researching() {
        ResearchData d = ResearchFocusItem.data(item());
        return d != null && d.active().isPresent();
    }

    /** Sends the table to clients now (what floats over it, and whether it's busy). */
    public void sync() {
        Level level = table.getLevel();
        if (level == null || level.isClientSide) return;
        lastShown = item().copyWithCount(1);
        BlockState st = table.getBlockState();
        level.sendBlockUpdated(table.getBlockPos(), st, st, 3);
    }

    private void changed() {
        table.setChanged();
        Level level = table.getLevel();
        // Clients only need to know when the floating item changes, not every research payment.
        if (level != null && !level.isClientSide && !ItemStack.isSameItem(item(), lastShown)) {
            lastShown = item().copyWithCount(1);
            BlockState st = table.getBlockState();
            level.sendBlockUpdated(table.getBlockPos(), st, st, 3);
        }
    }

    // ---- saving and client sync -------------------------------------------------------------------------------

    public void save(CompoundTag tag, HolderLookup.Provider reg) {
        tag.put("ResearchItems", ContainerHelper.saveAllItems(new CompoundTag(), items.getItems(), true, reg));
        if (pending != null) tag.put("Enchanting", pending.save());
    }

    public void load(CompoundTag tag, HolderLookup.Provider reg) {
        if (!tag.contains("ResearchItems")) return;
        NonNullList<ItemStack> list = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("ResearchItems"), list, reg);
        for (int i = 0; i < 2; i++) items.getItems().set(i, list.get(i));
        lastShown = item().copyWithCount(1);
        pending = tag.contains("Enchanting") ? Pending.load(tag.getCompound("Enchanting")) : null;
    }

    /** What clients get: the item in the table, so they can draw it floating. */
    public CompoundTag clientTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        NonNullList<ItemStack> list = NonNullList.withSize(2, ItemStack.EMPTY);
        list.set(BOOK, item().copyWithCount(1));
        tag.put("ResearchItems", ContainerHelper.saveAllItems(new CompoundTag(), list, true, reg));
        if (pending != null) tag.put("Enchanting", pending.save());
        return tag;
    }

    public void dropAll(Level level, BlockPos pos) {
        if (pending != null && level instanceof ServerLevel sl) {
            // Broken mid-enchant: the item comes out as it went in, and the costs come back.
            if (pending.lapis() > 0) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(Items.LAPIS_LAZULI, pending.lapis()));
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(pending.player());
            if (p != null && pending.xp() > 0) p.giveExperienceLevels(pending.xp());
            pending = null;
        }
        Containers.dropContents(level, pos, items);
        items.clearContent();
    }

    // ---- research ---------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity be) {
        if (be instanceof TableAccess a) a.researchenchanting$data().tick((ServerLevel) level, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        boolean active = researching();
        if (active != lastActive) { lastActive = active; sync(); }
        if (pending != null) { enchantTick(level, pos); return; }
        if (++ticks % Config.PULL_INTERVAL.get() != 0) return;
        ItemStack book = item();
        ResearchData data = ResearchFocusItem.data(book);
        if (data == null || data.active().isEmpty()) { starved = false; return; }
        ResearchData.Active a = data.active().get();
        Focus focus = Foci.get(data.focus(), false);
        Focus.Line line = focus == null ? null : focus.line(a.enchantment()).orElse(null);
        if (line == null) return;
        int cost = Config.cost(a.level());
        int paid = a.paid();
        if (paid < cost) {
            int want = Math.min(cost - paid, Math.max(1, (int) Math.ceil(cost / (double) Config.MIN_PULLS.get())));
            BlockPos flewFrom = null;
            ItemStack flew = ItemStack.EMPTY;
            for (BlockPos at : Shelves.containers(level, pos)) {
                if (want <= 0) break;
                IItemHandler h = handler(level, at);
                if (h == null) continue;
                for (int s = 0; s < h.getSlots() && want > 0; s++) {
                    ItemStack in = h.getStackInSlot(s);
                    if (in.isEmpty()) continue;
                    if (in.is(Registry.RESEARCH_NOTES.get())) {
                        Notes n = in.get(Registry.NOTES.get());
                        if (n != null && n.enchantment().equals(a.enchantment()) && n.level() >= a.level()
                                && !h.extractItem(s, 1, false).isEmpty()) {
                            paid = cost;
                            want = 0;
                            flewFrom = at;
                            flew = in.copyWithCount(1);
                        }
                        continue;
                    }
                    if (!in.is(line.resource())) continue;
                    ItemStack got = h.extractItem(s, want, false);
                    if (got.isEmpty()) continue;
                    paid += got.getCount();
                    want -= got.getCount();
                    if (flewFrom == null) { flewFrom = at; flew = got.copyWithCount(1); }
                }
            }
            starved = flewFrom == null;
            if (flewFrom == null) return;
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new ResearchFly(pos, flewFrom, flew));
        }
        if (paid >= cost) {
            book.set(Registry.RESEARCH.get(), data.complete(a.enchantment(), a.level()));
            finished(level, pos, a);
        } else {
            book.set(Registry.RESEARCH.get(), data.withActive(new ResearchData.Active(a.enchantment(), a.level(), paid)));
        }
        table.setChanged();
    }

    // ---- enchanting -------------------------------------------------------------------------------------------

    /** Starts applying an enchantment (costs already paid) and sends everyone looking at the table out of its menu. */
    public void startEnchant(ServerLevel level, BlockPos pos, ServerPlayer by, Holder<Enchantment> ench, int lvl, int xp, int lapis) {
        pending = new Pending(ench.unwrapKey().orElseThrow().location(), lvl, xp, lapis, by.getUUID(), level.getGameTime(), Config.ENCHANT_TICKS.get());
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof dev.empengines.researchenchanting.menu.ResearchTableMenu m && m.table() == this) p.closeContainer();
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
        table.setChanged();
        sync();
    }

    private void enchantTick(ServerLevel level, BlockPos pos) {
        long elapsed = level.getGameTime() - pending.start();
        if (elapsed % 20 == 0 && elapsed < pending.duration()) {
            float p = elapsed / (float) pending.duration();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6f, 0.7f + p * 0.8f);
        }
        if (elapsed < pending.duration()) return;
        Pending done = pending;
        pending = null;
        ItemStack item = item();
        Holder<Enchantment> h = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ResourceKey.create(Registries.ENCHANTMENT, done.enchantment())).orElse(null);
        if (h != null && !item.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(h, item) < done.level()) {
            EnchantmentHelper.updateEnchantments(item, m -> m.set(h, done.level()));
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 1.65, z = pos.getZ() + 0.5;
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.2f);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6f, 1.4f);
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 120, 0.4, 0.4, 0.4, 1.2);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 30, 0.3, 0.3, 0.3, 0.4);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(done.player());
        if (p != null) {
            p.awardStat(Stats.ENCHANT_ITEM);
            CriteriaTriggers.ENCHANTED_ITEM.trigger(p, item, done.xp());
            Milestones.award(p, Milestones.ENCHANT_ITEM);
            if (h != null) p.displayClientMessage(Component.translatable("message.researchenchanting.enchanted",
                    item.getHoverName(), Enchantment.getFullname(h, done.level())), true);
        }
        items.setChanged();
        table.setChanged();
        sync();
    }

    private static IItemHandler handler(ServerLevel level, BlockPos at) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, at, null);
        return h != null ? h : level.getCapability(Capabilities.ItemHandler.BLOCK, at, net.minecraft.core.Direction.UP);
    }

    private static void finished(ServerLevel level, BlockPos pos, ResearchData.Active a) {
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5f, 1.3f);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 60, 0.3, 0.3, 0.3, 0.6);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 16 * 16) continue;
            Milestones.award(p, Milestones.RESEARCH_NODE);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.researchenchanting.researched",
                    ResearchFocusItem.enchantName(a.enchantment(), a.level())), true);
        }
    }
}
