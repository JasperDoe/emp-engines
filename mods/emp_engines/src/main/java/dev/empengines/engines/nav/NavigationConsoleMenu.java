package dev.empengines.engines.nav;

import dev.empengines.engines.ModRegistry;
import dev.empengines.engines.block.VectorPlanarComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The Navigation Console's screen: the launch readout (drawn by the screen from the block entity, which keeps
 * the client up to date) and one slot for Navigational Data, above the player's inventory.
 */
public class NavigationConsoleMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 224;   // vanilla container width
    public static final int NAV_X = 80, NAV_Y = 100;          // the drive slot, inside the console panel
    public static final int INV_LEFT = 0, INV_TOP = 124;      // the vanilla inventory panel (176 x 100) under the console
    public static final int INV_X = INV_LEFT + 8, INV_Y = INV_TOP + 18;   // player inventory slots; hotbar 58 below

    private final BlockPos pos;
    private final VectorPlanarComputerBlockEntity console;

    /** Client side: opened from the server's packet, which carries the console's position. */
    public NavigationConsoleMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    private NavigationConsoleMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof VectorPlanarComputerBlockEntity be ? be : null, pos);
    }

    /** Server side. */
    public NavigationConsoleMenu(int id, Inventory inv, VectorPlanarComputerBlockEntity be) {
        this(id, inv, be, be.getBlockPos());
    }

    private NavigationConsoleMenu(int id, Inventory inv, VectorPlanarComputerBlockEntity be, BlockPos pos) {
        super(ModRegistry.NAVIGATION_CONSOLE_MENU.get(), id);
        this.pos = pos;
        this.console = be;
        IItemHandler slot = be != null ? be.navSlot() : new ItemStackHandler(1);
        addSlot(new SlotItemHandler(slot, 0, NAV_X, NAV_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return stack.is(ModRegistry.NAVIGATIONAL_DATA.get()); }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
    }

    public BlockPos pos() { return pos; }

    /** The console this screen shows, or null if it's gone. */
    public VectorPlanarComputerBlockEntity console() { return console; }

    @Override
    public boolean stillValid(Player player) {
        return console != null && !console.isRemoved() && player.canInteractWithBlock(pos, 4.0);   // vanilla's reach check, which Sable extends to ships
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(ModRegistry.NAVIGATIONAL_DATA.get())) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
