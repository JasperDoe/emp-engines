package dev.empengines.tieredfoundry.block.entity;

import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.CastingBlock;
import dev.empengines.tieredfoundry.item.CastItem;
import dev.empengines.tieredfoundry.compat.silentgear.SgHook;
import dev.empengines.tieredfoundry.recipe.CastingRecipe;
import dev.empengines.tieredfoundry.recipe.PartCastingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

/**
 * Casting table or basin. Molten fluid poured in (by a tap, a Create spout or pipe) is taken only if it makes
 * something: on a table the cast decides the shape (ingots, nuggets, plates, gems, Silent Gear parts), a plain item
 * under molten gold becomes a gold cast, Silent Gear gear gets coated. When full it
 * cools for {@code 20 + amount / 6} ticks into the result. Sneak-click with a filled container to set a fluid filter,
 * sneak-click with an empty hand to clear it.
 */
public class CastingBlockEntity extends BlockEntity {
    public static final TagKey<Fluid> MOLTEN_GOLD = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "molten_gold"));

    private ItemStack cast = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack filter = FluidStack.EMPTY;
    private int need;
    /** What the current pour cools into, worked out when it starts. */
    private ItemStack pending = ItemStack.EMPTY;
    /** Whether the item on the table goes when it cools (a sand cast, a plain item under gold, coated gear...). */
    private boolean consumeCast;
    private int cool;

    private final IFluidHandler tank = new Tank();
    private final IItemHandler items = new Items();

    public CastingBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.CASTING_BE.get(), pos, state);
    }

    public boolean basin() { return getBlockState().getBlock() instanceof CastingBlock b && b.basin; }
    public ItemStack cast() { return cast; }
    public ItemStack output() { return output; }
    public FluidStack fluid() { return fluid; }
    public FluidStack filter() { return filter; }
    public int need() { return need; }
    public float cooling() { int t = coolTime(); return t <= 0 ? 0 : Math.min(1F, cool / (float) t); }
    public IFluidHandler tank() { return tank; }
    public IItemHandler items() { return items; }

    private int coolTime() { return need <= 0 ? 0 : 20 + need / 6; }
    private boolean full() { return need > 0 && fluid.getAmount() >= need; }

    // ---- pouring -----------------------------------------------------------------------------------------------------

    private record Plan(ItemStack result, int need, boolean consumeCast) {}

    /**
     * What pouring this fluid in would make. On a table, in this order: a recipe that uses the item on the table
     * itself (Obsidian Key fragment), coating Silent Gear gear, molten gold over a plain ingot/nugget/plate/gem/part
     * (a gold cast of its shape), then recipes for the cast's shape (Silent Gear parts last). In a basin: basin recipes.
     */
    private Plan plan(FluidStack in) {
        if (level == null || in.isEmpty()) return null;
        boolean basin = basin();
        var recipes = level.getRecipeManager();
        if (basin) {
            for (RecipeHolder<CastingRecipe> h : recipes.getAllRecipesFor(Registry.CASTING.get())) {
                if (h.value().accepts(true, null, in)) return new Plan(h.value().result().copy(), h.value().fluid().amount(), false);
            }
            return null;
        }
        if (cast.isEmpty()) return null;
        String pattern = CastItem.patternOf(cast);
        if (pattern == null) {
            for (RecipeHolder<CastingRecipe> h : recipes.getAllRecipesFor(Registry.CASTING.get())) {
                if (h.value().acceptsItem(cast, in)) return new Plan(h.value().result().copy(), h.value().fluid().amount(), true);
            }
            SgHook.Result coated = SgHook.coat(level, cast, in);
            if (coated != null) return new Plan(coated.stack(), coated.amount(), true);
            String shape = CastItem.patternFor(level, cast);
            if (shape != null && in.is(MOLTEN_GOLD)) return new Plan(CastItem.of(Registry.GOLD_CAST.get(), shape), CastItem.GOLD_COST, true);
            return null;
        }
        boolean sand = cast.getItem() instanceof CastItem c && c.sand;
        for (RecipeHolder<CastingRecipe> h : recipes.getAllRecipesFor(Registry.CASTING.get())) {
            if (h.value().accepts(false, pattern, in)) return new Plan(h.value().result().copy(), h.value().fluid().amount(), sand);
        }
        if (pattern.startsWith("item:")) {
            for (RecipeHolder<PartCastingRecipe> h : recipes.getAllRecipesFor(Registry.PART_CASTING.get())) {
                if (!h.value().pattern().equals(pattern)) continue;
                SgHook.Result part = SgHook.part(level, h.value(), in);
                if (part != null) return new Plan(part.stack(), part.amount(), sand);
            }
        }
        return null;
    }

    private int fill(FluidStack in, boolean execute) {
        if (in.isEmpty() || !output.isEmpty() || full()) return 0;
        if (!filter.isEmpty() && !FluidStack.isSameFluid(filter, in)) return 0;
        if (fluid.isEmpty()) {
            Plan p = plan(in);
            if (p == null) return 0;
            int amount = Math.min(in.getAmount(), p.need);
            if (execute) {
                fluid = in.copyWithAmount(amount);
                need = p.need;
                pending = p.result;
                consumeCast = p.consumeCast;
                cool = 0;
                sync();
            }
            return amount;
        }
        if (!FluidStack.isSameFluidSameComponents(fluid, in)) return 0;
        int amount = Math.min(in.getAmount(), need - fluid.getAmount());
        if (execute && amount > 0) { fluid.grow(amount); sync(); }
        return amount;
    }

    public void serverTick() {
        if (!full()) return;
        if (pending.isEmpty()) { reset(); return; }
        if (++cool < coolTime()) return;
        output = pending.copy();
        if (consumeCast) cast = ItemStack.EMPTY;
        reset();
        level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
    }

    private void reset() {
        fluid = FluidStack.EMPTY;
        need = 0;
        cool = 0;
        pending = ItemStack.EMPTY;
        consumeCast = false;
        sync();
    }

    // ---- hands --------------------------------------------------------------------------------------------------------

    /** Right-click handling. Returns whether anything happened. Runs on both sides; the server's result counts. */
    public boolean use(Player player, InteractionHand hand, ItemStack held) {
        boolean client = level.isClientSide;
        if (!output.isEmpty()) {
            if (!client) { give(player, output); output = ItemStack.EMPTY; sync(); }
            return true;
        }
        if (player.isShiftKeyDown()) {
            if (held.isEmpty()) {
                if (filter.isEmpty()) return false;
                if (!client) { filter = FluidStack.EMPTY; player.displayClientMessage(Component.translatable("message.tieredfoundry.filter_cleared"), true); sync(); }
                return true;
            }
            Optional<FluidStack> f = FluidUtil.getFluidContained(held);
            if (f.isPresent() && !f.get().isEmpty()) {
                if (!client) {
                    filter = f.get().copyWithAmount(1);
                    player.displayClientMessage(Component.translatable("message.tieredfoundry.filter_set", filter.getHoverName()), true);
                    sync();
                }
                return true;
            }
            return false;
        }
        if (!fluid.isEmpty()) return false;   // busy pouring or cooling
        if (basin()) return false;
        if (!held.isEmpty() && held.is(ItemTags.SAND) && !cast.isEmpty() && CastItem.patternOf(cast) == null) {
            String pattern = CastItem.patternFor(level, cast);
            if (pattern == null) return false;
            if (!client) {
                give(player, cast);
                cast = CastItem.of(Registry.SAND_CAST.get(), pattern);
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, worldPosition, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8F, 1F);
                sync();
            }
            return true;
        }
        if (!held.isEmpty() && cast.isEmpty()) {
            if (!client) {
                cast = held.copyWithCount(1);
                if (!player.getAbilities().instabuild) held.shrink(1);
                sync();
            }
            return true;
        }
        if (held.isEmpty() && !cast.isEmpty()) {
            if (!client) { give(player, cast); cast = ItemStack.EMPTY; sync(); }
            return true;
        }
        return false;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack.copy())) player.drop(stack.copy(), false);
    }

    public NonNullList<ItemStack> dropList() {
        NonNullList<ItemStack> out = NonNullList.create();
        if (!cast.isEmpty()) out.add(cast);
        if (!output.isEmpty()) out.add(output);
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!cast.isEmpty()) tag.put("Cast", cast.save(reg));
        if (!output.isEmpty()) tag.put("Output", output.save(reg));
        if (!fluid.isEmpty()) tag.put("Fluid", fluid.save(reg));
        if (!filter.isEmpty()) tag.put("Filter", filter.save(reg));
        tag.putInt("Need", need);
        tag.putInt("Cool", cool);
        tag.putBoolean("ConsumeCast", consumeCast);
        if (!pending.isEmpty()) tag.put("Pending", pending.save(reg));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        cast = ItemStack.parseOptional(reg, tag.getCompound("Cast"));
        output = ItemStack.parseOptional(reg, tag.getCompound("Output"));
        fluid = FluidStack.parseOptional(reg, tag.getCompound("Fluid"));
        filter = FluidStack.parseOptional(reg, tag.getCompound("Filter"));
        need = tag.getInt("Need");
        cool = tag.getInt("Cool");
        consumeCast = tag.getBoolean("ConsumeCast");
        pending = ItemStack.parseOptional(reg, tag.getCompound("Pending"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        return saveWithoutMetadata(reg);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- capabilities ------------------------------------------------------------------------------------------------

    private final class Tank implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return fluid; }
        @Override public int getTankCapacity(int t) { return need > 0 ? need : 1000; }
        @Override public boolean isFluidValid(int t, FluidStack s) { return plan(s) != null; }
        @Override public int fill(FluidStack resource, FluidAction action) { return CastingBlockEntity.this.fill(resource, action.execute()); }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    /** Slot 0: the result (take only). Slot 1: the cast (put in or take out while nothing is poured). */
    private final class Items implements IItemHandler {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? output : cast; }
        @Override public int getSlotLimit(int slot) { return 1; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 1 && !basin() && cast.isEmpty() && fluid.isEmpty() && output.isEmpty();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
            if (!simulate) { cast = stack.copyWithCount(1); sync(); }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            if (slot == 0) {
                if (output.isEmpty()) return ItemStack.EMPTY;
                ItemStack out = output.copy();
                if (!simulate) { output = ItemStack.EMPTY; sync(); }
                return out;
            }
            // keep a reusable cast in place for automation; only hand it out when idle and it isn't a cast
            if (cast.isEmpty() || !fluid.isEmpty() || cast.getItem() instanceof CastItem) return ItemStack.EMPTY;
            ItemStack out = cast.copy();
            if (!simulate) { cast = ItemStack.EMPTY; sync(); }
            return out;
        }
    }
}
