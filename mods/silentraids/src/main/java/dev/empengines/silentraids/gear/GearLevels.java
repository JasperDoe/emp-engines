package dev.empengines.silentraids.gear;

import dev.empengines.silentraids.ApothBridge;
import dev.empengines.silentraids.Config;
import dev.empengines.silentraids.Registry;
import dev.empengines.silentraids.Registry.GearLevel;
import dev.empengines.silentraids.tier.PlayerTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.silentchaos512.gear.api.event.GearRecalculateEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.item.GearTool;
import net.silentchaos512.gear.api.property.GearProperty;
import net.silentchaos512.gear.api.property.GearPropertyValue;
import net.silentchaos512.gear.api.property.NumberPropertyValue;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.item.gear.GearCurioItem;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.GearHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Levels for Silent Gear tools, weapons and armour (not curios).
 *
 * <ul>
 *   <li>XP: tools from mining (more for ores), weapons from damage dealt to monsters (and a kill bonus), each armour
 *       piece from damage its wearer takes from mobs.</li>
 *   <li>Every level adds {@link Config#BONUS_PER_LEVEL} (+5%) to all of the item's stats, applied after Silent Gear
 *       works the stats out, so it survives part swaps, repairs and upgrades (they recalculate from the parts).</li>
 *   <li>The highest level depends on the owner's world tier (3/5/7/9/10). XP keeps counting at the cap, so the next
 *       tier levels it straight away.</li>
 *   <li>Levels 3, 6 and 9 each add an Apotheosis gem socket. A Sigil of Socketing adds one more, once.</li>
 * </ul>
 */
public final class GearLevels {
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    /** Stats a level boosts. Everything else (rarity, enchantability, repair, reach, accuracy) is left alone. */
    private static final List<Supplier<? extends GearProperty<?, ?>>> SCALED = List.of(
            GearProperties.DURABILITY, GearProperties.ARMOR_DURABILITY, GearProperties.HARVEST_SPEED,
            GearProperties.ATTACK_DAMAGE, GearProperties.ATTACK_SPEED, GearProperties.MAGIC_DAMAGE,
            GearProperties.RANGED_DAMAGE, GearProperties.DRAW_SPEED, GearProperties.PROJECTILE_SPEED,
            GearProperties.ARMOR, GearProperties.ARMOR_TOUGHNESS, GearProperties.KNOCKBACK_RESISTANCE, GearProperties.MAGIC_ARMOR);

    public static boolean isLevelledGear(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GearItem && !(stack.getItem() instanceof GearCurioItem);
    }

    public static GearLevel get(ItemStack stack) {
        return stack.getOrDefault(Registry.GEAR_LEVEL.get(), GearLevel.NONE);
    }

    public static int xpForNext(int level) {
        return (int) Math.round(100 * Math.pow(level + 1, 1.5) * Config.XP_SCALE.get());
    }

    /** Adds XP, levelling the item up as far as the owner's tier allows. */
    public static void addXp(ItemStack stack, double amount, ServerPlayer player) {
        if (amount <= 0 || !isLevelledGear(stack)) return;
        int whole = (int) amount;
        if (player.getRandom().nextDouble() < amount - whole) whole++;
        if (whole <= 0) return;

        GearLevel old = get(stack);
        int cap = PlayerTier.levelCap(player);
        int level = old.level();
        long xp = (long) old.xp() + whole;
        while (level < cap && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        if (level >= cap) xp = Math.min(xp, xpForNext(level) - 1L);
        stack.set(Registry.GEAR_LEVEL.get(), new GearLevel(level, (int) xp));
        if (level > old.level()) levelledUp(stack, old.level(), level, player);
    }

    /** Sets the level directly (for /silentraids gearlevel). */
    public static void setLevel(ItemStack stack, int level, ServerPlayer player) {
        int old = get(stack).level();
        stack.set(Registry.GEAR_LEVEL.get(), new GearLevel(level, 0));
        if (level > old) levelledUp(stack, old, level, player);
        else if (level < old) GearData.recalculateGearData(stack, player);
    }

    private static void levelledUp(ItemStack stack, int from, int to, ServerPlayer player) {
        Set<Integer> socketLevels = Set.copyOf(Config.SOCKET_LEVELS.get());
        int newSockets = 0;
        for (int l = from + 1; l <= to; l++) if (socketLevels.contains(l)) newSockets++;
        if (newSockets > 0 && ApothBridge.socketsComponent() != null) {
            ApothBridge.setSockets(stack, ApothBridge.sockets(stack) + newSockets);
        }
        GearData.recalculateGearData(stack, player);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        Component msg = Component.literal("Your ").append(stack.getHoverName()).append(" reached level " + to + "!")
                .withStyle(ChatFormatting.GOLD);
        if (newSockets > 0) msg = msg.copy().append(Component.literal(newSockets == 1 ? " It has a new gem socket." : " It has " + newSockets + " new gem sockets.").withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(msg);
    }

    // --- stat bonus -------------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void applyBonus(ItemStack gear) {
        int level = get(gear).level();
        if (level <= 0) return;
        GearPropertiesData props = gear.get(SgDataComponents.GEAR_PROPERTIES.get());
        if (props == null) return;
        float mult = (float) (1 + (Config.SPEC.isLoaded() ? Config.BONUS_PER_LEVEL.get() : 0.05) * level);

        Map map = new LinkedHashMap(props.properties());
        for (Supplier<? extends GearProperty<?, ?>> s : SCALED) {
            GearProperty<?, ?> property = s.get();
            Object value = map.get(property);
            if (value instanceof NumberPropertyValue n) {
                map.put(property, new NumberPropertyValue(n.value() * mult, n.operation()));
            }
        }
        GearPropertiesData scaled = new GearPropertiesData((Map<GearProperty<?, ?>, GearPropertyValue<?>>) map);
        gear.set(SgDataComponents.GEAR_PROPERTIES.get(), scaled);
        // Silent Gear sets the mining speeds from the stats just before this event; redo it with the boosted stats.
        if (!GearHelper.isBroken(gear) && gear.getItem() instanceof GearTool tool) {
            gear.set(DataComponents.TOOL, tool.createToolProperties(scaled));
        }
    }

    // --- events -----------------------------------------------------------------------------------

    public static final class Events {
        @SubscribeEvent
        public static void recalculated(GearRecalculateEvent.Post e) {
            applyBonus(e.getGear());
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void mined(BlockEvent.BreakEvent e) {
            if (e.isCanceled() || !(e.getPlayer() instanceof ServerPlayer p) || p.isCreative()) return;
            ItemStack tool = p.getMainHandItem();
            if (!isLevelledGear(tool)) return;
            BlockState state = e.getState();
            if (state.getDestroySpeed(e.getLevel(), e.getPos()) <= 0 || !tool.isCorrectToolForDrops(state)) return;
            addXp(tool, state.is(ORES) ? Config.XP_ORE.get() : Config.XP_BLOCK.get(), p);
        }

        @SubscribeEvent
        public static void damaged(LivingDamageEvent.Post e) {
            LivingEntity victim = e.getEntity();
            if (victim.level().isClientSide) return;
            float dealt = e.getNewDamage();
            // A weapon hitting a monster
            if (victim instanceof Enemy && e.getSource().getEntity() instanceof ServerPlayer p && !p.isCreative() && dealt > 0) {
                ItemStack weapon = e.getSource().getWeaponItem();
                if (weapon == null || weapon.isEmpty()) weapon = p.getMainHandItem();
                addXp(weapon, Math.min(dealt, 40f) * Config.XP_DAMAGE.get(), p);
            }
            // Armour worn by a player hit by a mob
            if (victim instanceof ServerPlayer p && !p.isCreative() && e.getSource().getEntity() instanceof LivingEntity attacker && attacker != p) {
                float taken = Math.min(e.getOriginalDamage(), 40f);
                if (taken <= 0) return;
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    addXp(p.getItemBySlot(slot), Math.max(1f, taken * Config.XP_DAMAGE.get()), p);
                }
            }
        }

        @SubscribeEvent
        public static void killed(LivingDeathEvent e) {
            if (e.getEntity() instanceof Enemy && e.getSource().getEntity() instanceof ServerPlayer p && !p.isCreative()) {
                ItemStack weapon = e.getSource().getWeaponItem();
                if (weapon == null || weapon.isEmpty()) weapon = p.getMainHandItem();
                addXp(weapon, Math.min(e.getEntity().getMaxHealth() / 2f, 50f), p);
            }
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent e) {
            ItemStack stack = e.getItemStack();
            List<Component> lines = e.getToolTip();
            if (lines.isEmpty()) return;
            int at = 1;
            if (isLevelledGear(stack)) {
                GearLevel gl = get(stack);
                // Server config: not loaded on the title screen, so fall back to the defaults there.
                boolean cfg = Config.SPEC.isLoaded();
                int next = cfg ? xpForNext(gl.level()) : (int) Math.round(100 * Math.pow(gl.level() + 1, 1.5));
                double bonus = cfg ? Config.BONUS_PER_LEVEL.get() : 0.05;
                lines.add(at++, Component.literal("Level " + gl.level()).withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("  " + gl.xp() + " / " + next + " XP").withStyle(ChatFormatting.DARK_GRAY)));
                if (gl.level() > 0) {
                    int pct = (int) Math.round(bonus * 100 * gl.level());
                    lines.add(at++, Component.literal("+" + pct + "% to all stats").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            if (Boolean.TRUE.equals(stack.get(Registry.SIGIL_USED.get()))) {
                lines.add(at, Component.literal("Sigil socket used").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }

    private GearLevels() {}
}
