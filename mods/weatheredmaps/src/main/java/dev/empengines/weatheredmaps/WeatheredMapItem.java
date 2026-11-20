package dev.empengines.weatheredmaps;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

/**
 * A sealed map. Used, it searches for the nearest structure in its tag that no other map has claimed (vanilla's
 * explorer-map search, so each map leads somewhere new) and turns into a filled map with that spot marked. The
 * search only happens here, when a player uses it, never when a chest is filled.
 */
public class WeatheredMapItem extends Item {
    /** Search radius in chunks, as vanilla's cartographer maps use for mansions and monuments. */
    private static final int RADIUS = 64;

    private final TagKey<Structure> target;
    private final Holder<MapDecorationType> marker;

    public WeatheredMapItem(TagKey<Structure> target, Holder<MapDecorationType> marker, Properties props) {
        super(props);
        this.target = target;
        this.marker = marker;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        player.getCooldowns().addCooldown(this, 40);

        BlockPos found;
        try {
            found = server.findNearestMapStructure(target, player.blockPosition(), RADIUS, true);
        } catch (Throwable t) {
            WeatheredMaps.LOGGER.warn("Weathered map search for {} failed", target.location(), t);
            found = null;
        }
        if (found == null) {
            player.displayClientMessage(Component.translatable("item.weatheredmaps.weathered_map.nothing")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }

        ItemStack map = MapItem.create(server, found.getX(), found.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(server, map);
        MapItemSavedData.addTargetDecoration(map, found, "+", marker);
        map.set(DataComponents.ITEM_NAME, Component.translatable(getDescriptionId() + ".unrolled"));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                SoundSource.PLAYERS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("item.weatheredmaps.weathered_map.unrolled")
                .withStyle(ChatFormatting.GOLD), true);

        if (stack.getCount() == 1 && !player.getAbilities().instabuild) return InteractionResultHolder.success(map);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!player.getInventory().add(map)) player.drop(map, false);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.weatheredmaps.weathered_map.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
