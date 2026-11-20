package dev.empengines.silentraids.loot;

import dev.empengines.silentraids.ApothBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Gems, not gear: Apotheosis affix items never drop from mobs or show up in trades (chest loot is switched off in
 * the pack's loot-modifier overrides). Raid Elites drop a gem, Invaders several, and both can drop a Sigil of
 * Socketing; Apotheosis' own Elites and Invaders out in the world drop gems too, but no sigils (sigils only come
 * from blood moons and quests).
 */
public final class LootRules {
    private static final String RAIDER = "silentraids.raider", KIND = "silentraids.kind", THREAT = "silentraids.threat";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void drops(LivingDropsEvent e) {
        e.getDrops().removeIf(ie -> ApothBridge.isAffixItem(ie.getItem()));
        if (!ApothBridge.loaded() || !(e.getSource().getEntity() instanceof ServerPlayer killer)) return;

        LivingEntity dead = e.getEntity();
        var data = dead.getPersistentData();
        int gems = 0;
        double sigil = 0;
        if (data.contains(RAIDER)) {
            String kind = data.getString(KIND);
            if ("elite".equals(kind)) { gems = 1; sigil = 0.10; }
            else if ("invader".equals(kind)) { gems = 2 + (int) (data.getDouble(THREAT) / 2); sigil = 0.5; }
        } else if (data.contains(ApothBridge.BOSS_KEY)) {
            gems = 2;
        } else if (data.contains(ApothBridge.MINIBOSS_KEY)) {
            gems = 1;
        }
        for (int i = 0; i < gems; i++) drop(e, ApothBridge.randomGem(killer));
        if (sigil > 0 && killer.getRandom().nextDouble() < sigil) drop(e, ApothBridge.sigil(1));
    }

    private static void drop(LivingDropsEvent e, ItemStack stack) {
        if (stack.isEmpty()) return;
        LivingEntity dead = e.getEntity();
        ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5, dead.getZ(), stack);
        item.setDefaultPickUpDelay();
        e.getDrops().add(item);
    }

    /** Wandering Traders' Apotheosis affix trades are taken out when you open the trade screen. */
    @SubscribeEvent
    public static void trade(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !(e.getTarget() instanceof AbstractVillager trader) || !ApothBridge.loaded()) return;
        MerchantOffers offers = trader.getOffers();
        offers.removeIf(o -> ApothBridge.isAffixItem(o.getResult()));
    }

    private LootRules() {}
}
