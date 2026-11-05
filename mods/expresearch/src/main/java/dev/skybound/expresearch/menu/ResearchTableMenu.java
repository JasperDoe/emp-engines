package dev.skybound.expresearch.menu;

import dev.skybound.expresearch.ModRegistry;
import dev.skybound.expresearch.research.ResearchNode;
import dev.skybound.expresearch.research.ResearchRegistry;
import dev.skybound.expresearch.research.ResearchService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Research Table: one input slot (blank blueprint) and one output slot (researched blueprint).
 * Items in the slots are returned to the player when the screen closes, like a crafting table.
 */
public class ResearchTableMenu extends AbstractContainerMenu {
    public static final int INPUT = 0, OUTPUT = 1;
    /** Slot positions, shared with the screen. */
    public static final int INPUT_X = 196, INPUT_Y = 150, OUTPUT_X = 226, OUTPUT_Y = 150, INV_X = 48, INV_Y = 176;

    private final Container table = new SimpleContainer(2) {
        @Override
        public void setChanged() { super.setChanged(); slotsChanged(this); }
    };
    private final BlockPos pos;

    public ResearchTableMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public ResearchTableMenu(int id, Inventory inv, BlockPos pos) {
        super(ModRegistry.RESEARCH_TABLE_MENU.get(), id);
        this.pos = pos;
        addSlot(new Slot(table, INPUT, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return stack.is(ModRegistry.BLUEPRINT.get()); }
        });
        addSlot(new Slot(table, OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
    }

    /** Called from the network handler when the player presses Research. */
    public void tryResearch(ServerPlayer player, ResourceLocation id) {
        ResearchNode node = ResearchRegistry.node(id);
        if (node == null) return;
        Set<ResourceLocation> unlocked = ResearchService.unlocked(player);
        Component problem = null;
        if (unlocked.contains(id)) problem = Component.translatable("expresearch.msg.already_known");
        else if (!ResearchService.parentsMet(node, unlocked)) problem = Component.translatable("expresearch.msg.needs_parents");
        else if (!table.getItem(INPUT).is(ModRegistry.BLUEPRINT.get())) problem = Component.translatable("expresearch.msg.needs_blueprint");
        else if (!table.getItem(OUTPUT).isEmpty()) problem = Component.translatable("expresearch.msg.output_full");
        else if (!player.isCreative() && ResearchService.totalXp(player) < node.xp()) problem = Component.translatable("expresearch.msg.needs_xp", node.xp());
        else if (!player.isCreative() && !ResearchService.hasSamples(player, node)) problem = Component.translatable("expresearch.msg.needs_samples");
        if (problem != null) {
            player.displayClientMessage(problem, true);
            return;
        }
        if (!player.isCreative()) {
            player.giveExperiencePoints(-node.xp());
            ResearchService.takeSamples(player, node);
        }
        table.getItem(INPUT).shrink(1);
        ItemStack result = new ItemStack(ModRegistry.RESEARCHED_BLUEPRINT.get());
        result.set(ModRegistry.RESEARCH.get(), id);
        table.setItem(OUTPUT, result);
        player.level().playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1f, 1f);
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index <= OUTPUT) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(ModRegistry.BLUEPRINT.get())) {
            if (!moveItemStackTo(stack, INPUT, INPUT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, table);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && player.level().getBlockState(pos).is(ModRegistry.RESEARCH_TABLE.get());
    }
}
