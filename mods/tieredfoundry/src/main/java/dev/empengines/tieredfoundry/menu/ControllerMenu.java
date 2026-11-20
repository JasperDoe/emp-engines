package dev.empengines.tieredfoundry.menu;

import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Controller screen: a scrolling 3 x 5 window onto the foundry's melting slots (one item each), four fuel slots shared
 * by every solid fuel heater, and the player's inventory. The 15 melting slots show the foundry slots from
 * {@code scroll * 3} on. Buttons: 100 + n moves fluid n to the bottom, 200 + r scrolls to row r.
 */
public class ControllerMenu extends AbstractContainerMenu {
    public static final int COLS = 3, ROWS = 5, VISIBLE = COLS * ROWS;
    public static final int WIDTH = 176, HEIGHT = 216;   // vanilla container width
    public static final int INV_X = 8, INV_Y = 134, HOTBAR_Y = 192;
    public static final int FUEL_X = 135, FUEL_Y = 18;
    public static final int GRID_X = 8, GRID_Y = 18;

    public final ControllerBlockEntity be;
    private int scroll;
    private final int meltEnd, fuelEnd;

    public ControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, (ControllerBlockEntity) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public ControllerMenu(int id, Inventory inv, ControllerBlockEntity be) {
        super(Registry.CONTROLLER_MENU.get(), id);
        this.be = be;
        Container dummy = new SimpleContainer(0);
        for (int i = 0; i < VISIBLE; i++) addSlot(new MeltSlot(dummy, i, meltX(i % COLS), meltY(i / COLS)));
        meltEnd = slots.size();
        for (int i = 0; i < ControllerBlockEntity.FUEL_SLOTS; i++) {
            addSlot(new SlotItemHandler(be.fuel(), i, FUEL_X + (i % 2) * 18, FUEL_Y + (i / 2) * 18) {
                @Override public boolean isActive() { return be.isFormed() && be.showsSolid(); }
            });
        }
        fuelEnd = slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    public static int meltX(int col) { return GRID_X + col * 18; }
    public static int meltY(int row) { return GRID_Y + row * 18; }

    public int scroll() { return scroll; }
    public int maxScroll() { return Math.max(0, (be.slotCount() + COLS - 1) / COLS - ROWS); }

    /** Sets the scroll row here; on the client also tell the server (button 200 + row). */
    public void setScroll(int row) { scroll = Math.max(0, Math.min(maxScroll(), row)); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 200) { setScroll(id - 200); broadcastChanges(); return true; }
        if (id >= 100) { be.moveToBottom(id - 100); return true; }
        return false;
    }

    /** One of the 15 visible melting slots; which foundry slot it shows follows the scroll. */
    public final class MeltSlot extends Slot {
        public final int local;

        MeltSlot(Container dummy, int local, int x, int y) {
            super(dummy, local, x, y);
            this.local = local;
        }

        public int foundrySlot() { return scroll * COLS + local; }
        private boolean valid() { return be.isFormed() && foundrySlot() < be.slotCount(); }
        private ItemStackHandler h() { return be.input(); }

        @Override public boolean isActive() { return valid(); }
        @Override public ItemStack getItem() { return foundrySlot() < ControllerBlockEntity.MAX_SLOTS ? h().getStackInSlot(foundrySlot()) : ItemStack.EMPTY; }
        @Override public boolean hasItem() { return !getItem().isEmpty(); }
        @Override public void set(ItemStack stack) { if (foundrySlot() < ControllerBlockEntity.MAX_SLOTS) h().setStackInSlot(foundrySlot(), stack); }
        @Override public void setByPlayer(ItemStack stack) { set(stack); }
        @Override public void setByPlayer(ItemStack newStack, ItemStack oldStack) { set(newStack); }
        @Override public void setChanged() {}
        @Override public int getMaxStackSize() { return 1; }
        @Override public int getMaxStackSize(ItemStack stack) { return 1; }
        @Override public boolean mayPlace(ItemStack stack) { return valid() && h().isItemValid(foundrySlot(), stack); }
        @Override public boolean mayPickup(Player player) { return valid() && hasItem(); }

        @Override
        public ItemStack remove(int amount) {
            if (!hasItem()) return ItemStack.EMPTY;
            return h().extractItem(foundrySlot(), amount, false);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < fuelEnd) {
            ItemStack moving = stack.copy();
            if (!moveItemStackTo(moving, fuelEnd, slots.size(), true)) return ItemStack.EMPTY;
            if (index < meltEnd) slot.set(moving);
            else { stack.setCount(moving.getCount()); if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); }
            return copy;
        }
        boolean moved = false;
        if (be.isFormed() && be.melting(stack) != null) {
            // one item per foundry slot, any of them, not only the ones in view
            ItemStackHandler in = be.input();
            for (int i = 0; i < be.slotCount() && !stack.isEmpty(); i++) {
                if (in.getStackInSlot(i).isEmpty() && in.isItemValid(i, stack)) {
                    in.setStackInSlot(i, stack.split(1));
                    moved = true;
                }
            }
        }
        if (!stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0) moved |= moveItemStackTo(stack, meltEnd, fuelEnd, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        // vanilla's own reach check, which Sable extends to blocks on ships
        return !be.isRemoved() && player.canInteractWithBlock(be.getBlockPos(), 4.0);
    }
}
