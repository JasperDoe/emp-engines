package dev.empengines.chunkupkeep.economy;

import dev.empengines.chunkupkeep.Config;
import dev.empengines.chunkupkeep.LedgerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** One-time joining money and a small daily login bonus, paid into the player's bank account. */
public final class Income {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        LedgerData data = LedgerData.get(sp.server);
        if (data.markJoined(sp.getUUID()) && Config.JOIN_BONUS.get() > 0) {
            Bank.pay(sp.getUUID(), Config.JOIN_BONUS.get());
            sp.sendSystemMessage(Component.translatable("chunkupkeep.msg.join_bonus", Bank.format(Config.JOIN_BONUS.get())));
        }
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        if (data.claimDailyBonus(sp.getUUID(), today) && Config.DAILY_BONUS.get() > 0) {
            Bank.pay(sp.getUUID(), Config.DAILY_BONUS.get());
            sp.sendSystemMessage(Component.translatable("chunkupkeep.msg.daily_bonus", Bank.format(Config.DAILY_BONUS.get())));
        }
    }

    private Income() {}
}
