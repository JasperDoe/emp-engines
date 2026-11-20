package dev.empengines.silentraids.tier;

import dev.empengines.silentraids.ApothBridge;
import dev.empengines.silentraids.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Each player's world tier, worked out from their own play time and milestones, and pushed into Apotheosis.
 * Tiers never go down (play time and milestones only ever grow). Manual tier changes are off in Apotheosis'
 * config, so this is the only way a tier changes.
 *
 * <pre>
 *   0 Haven     the start
 *   1 Frontier  5 h
 *   2 Ascent    15 h + been to the Nether
 *   3 Summit    30 h + killed the Wither
 *   4 Pinnacle  60 h + been to the End
 * </pre>
 *
 * Threat = tier + how far the player is through that tier's hours (0..0.99), so sieges get harder within a tier
 * as well as between tiers.
 */
public final class PlayerTier {
    public static final String[] NAMES = {"Haven", "Frontier", "Ascent", "Summit", "Pinnacle"};
    private static final ChatFormatting[] COLOURS = {ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.DARK_PURPLE};
    private static final String KEY = "silentraids.tier";

    public static double hours(ServerPlayer p) {
        return p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72000.0;
    }

    private static boolean hasAdvancement(ServerPlayer p, String id) {
        AdvancementHolder adv = p.server.getAdvancements().get(ResourceLocation.parse(id));
        return adv != null && p.getAdvancements().getOrStartProgress(adv).isDone();
    }

    static boolean beenToNether(ServerPlayer p) { return hasAdvancement(p, "minecraft:story/enter_the_nether"); }
    static boolean killedWither(ServerPlayer p) {
        return p.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.WITHER)) > 0 || hasAdvancement(p, "minecraft:nether/create_beacon");
    }
    static boolean beenToEnd(ServerPlayer p) { return hasAdvancement(p, "minecraft:story/enter_the_end"); }

    private static double[] starts() {
        return new double[]{0, Config.FRONTIER_HOURS.get(), Config.ASCENT_HOURS.get(), Config.SUMMIT_HOURS.get(), Config.PINNACLE_HOURS.get(), Config.PINNACLE_FULL_HOURS.get()};
    }

    /** 0..4 */
    public static int tier(ServerPlayer p) {
        double h = hours(p);
        double[] s = starts();
        int t = 0;
        if (h >= s[1]) t = 1;
        if (t == 1 && h >= s[2] && beenToNether(p)) t = 2;
        if (t == 2 && h >= s[3] && killedWither(p)) t = 3;
        if (t == 3 && h >= s[4] && beenToEnd(p)) t = 4;
        return t;
    }

    /** 0..~4.99: the tier plus progress through it. */
    public static double threat(ServerPlayer p) {
        int t = tier(p);
        double[] s = starts();
        double span = Math.max(0.01, s[t + 1] - s[t]);
        double progress = Math.max(0, Math.min(0.99, (hours(p) - s[t]) / span));
        return t + progress;
    }

    /** What the player has left to do for the next tier, for /silentraids status. */
    public static Component nextGoal(ServerPlayer p) {
        int t = tier(p);
        if (t >= 4) return Component.literal("Top tier reached.");
        double[] s = starts();
        double need = s[t + 1] - hours(p);
        String milestone = switch (t) {
            case 1 -> beenToNether(p) ? null : "visit the Nether";
            case 2 -> killedWither(p) ? null : "kill the Wither";
            case 3 -> beenToEnd(p) ? null : "visit the End";
            default -> null;
        };
        StringBuilder sb = new StringBuilder("Next: ").append(NAMES[t + 1]).append(" - ");
        if (need > 0) sb.append(String.format("%.1f more hours of play", need));
        if (milestone != null) sb.append(need > 0 ? ", and " : "").append(milestone);
        if (need <= 0 && milestone == null) sb.append("any moment now");
        return Component.literal(sb.toString());
    }

    public static int levelCap(Player player) {
        int t = player instanceof ServerPlayer sp ? tier(sp) : player.getPersistentData().getInt(KEY);
        List<? extends Integer> caps = Config.CAPS.get();
        if (caps.isEmpty()) return 10;
        return caps.get(Math.min(t, caps.size() - 1));
    }

    public static Component name(int tier) {
        return Component.literal(NAMES[tier]).withStyle(COLOURS[tier]);
    }

    /** Pushes the tier into Apotheosis when it changes, with a title for promotions. */
    public static void update(ServerPlayer p, boolean announce) {
        int t = tier(p);
        int old = p.getPersistentData().contains(KEY) ? p.getPersistentData().getInt(KEY) : -1;
        // Apotheosis keeps its own copy; set it every time we check (cheap: it returns early when nothing changed),
        // which also puts it back if an admin used /apoth set_world_tier.
        ApothBridge.setTier(p, t);
        if (t == old) return;
        p.getPersistentData().putInt(KEY, t);
        grantTierAdvancements(p, t);
        if (announce && old >= 0 && t > old) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("World Tier: ").append(name(t))));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Gems are richer, night raids are harder, gear can level higher").withStyle(ChatFormatting.GRAY)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            p.sendSystemMessage(Component.literal("Your world tier is now ").append(name(t))
                    .append(Component.literal(". Gear can reach level " + levelCap(p) + ".").withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Apotheosis' tier advancements (its own criteria need affix gear, which this pack doesn't have). */
    private static void grantTierAdvancements(ServerPlayer p, int tier) {
        for (int i = 0; i <= tier; i++) {
            AdvancementHolder adv = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("apotheosis", "progression/" + ApothBridge.TIER_NAMES[i]));
            if (adv == null) continue;
            AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
            for (String c : progress.getRemainingCriteria()) p.getAdvancements().award(adv, c);
        }
    }

    public static final class Events {
        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) update(p, false);
        }

        @SubscribeEvent
        public static void tick(PlayerTickEvent.Post e) {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 600 == 17) update(p, true);
        }
    }

    private PlayerTier() {}
}
