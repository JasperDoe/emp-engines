package dev.skybound.expresearch.item;

import dev.skybound.expresearch.ModRegistry;
import dev.skybound.expresearch.research.ResearchNode;
import dev.skybound.expresearch.research.ResearchRegistry;
import dev.skybound.expresearch.research.ResearchService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A finished blueprint. Right-click to learn it (for your whole team). Tradeable until used. */
public class ResearchedBlueprintItem extends Item {
    public ResearchedBlueprintItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation id = stack.get(ModRegistry.RESEARCH.get());
        if (id == null) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        ServerPlayer sp = (ServerPlayer) player;
        if (ResearchRegistry.node(id) == null) {
            sp.displayClientMessage(Component.translatable("expresearch.msg.unknown"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!ResearchService.unlock(sp, id)) {
            sp.displayClientMessage(Component.translatable("expresearch.msg.already_known"), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = stack.get(ModRegistry.RESEARCH.get());
        ResearchNode node = id == null ? null : ResearchRegistry.node(id);
        if (node == null) return super.getName(stack);
        return Component.translatable("item.expresearch.researched_blueprint.named", node.title());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = stack.get(ModRegistry.RESEARCH.get());
        ResearchNode node = id == null ? null : ResearchRegistry.node(id);
        if (node != null && !node.description().isEmpty())
            tooltip.add(Component.literal(node.description()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("expresearch.tooltip.use").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }
}
