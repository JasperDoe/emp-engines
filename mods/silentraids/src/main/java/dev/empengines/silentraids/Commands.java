package dev.empengines.silentraids;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.empengines.silentraids.gear.GearLevels;
import dev.empengines.silentraids.siege.BloodMoon;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /silentraids status [player]          when the next night raid is, your tier, threat and gear level cap
 * /silentraids nightraid start|stop     (ops) start a night raid now, or end the current one
 * /silentraids gearlevel &lt;level&gt;        (ops) set the level of the Silent Gear item in your hand
 */
public final class Commands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("silentraids")
                .then(net.minecraft.commands.Commands.literal("status")
                        .executes(c -> status(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(net.minecraft.commands.Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(c -> status(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(net.minecraft.commands.Commands.literal("nightraid").requires(s -> s.hasPermission(2))
                        .then(net.minecraft.commands.Commands.literal("start").executes(c -> {
                            BloodMoon.force(c.getSource().getServer().overworld(), true);
                            c.getSource().sendSuccess(() -> Component.literal("Night raid started (ends after 10 minutes, at dawn, or with /silentraids nightraid stop)."), true);
                            return 1;
                        }))
                        .then(net.minecraft.commands.Commands.literal("stop").executes(c -> {
                            BloodMoon.force(c.getSource().getServer().overworld(), false);
                            c.getSource().sendSuccess(() -> Component.literal("Forced night raid stopped (a natural one keeps going until dawn)."), true);
                            return 1;
                        })))
                .then(net.minecraft.commands.Commands.literal("gearlevel").requires(s -> s.hasPermission(2))
                        .then(net.minecraft.commands.Commands.argument("level", IntegerArgumentType.integer(0, 100)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack held = p.getMainHandItem();
                            if (!GearLevels.isLevelledGear(held)) {
                                c.getSource().sendFailure(Component.literal("Hold a Silent Gear tool, weapon or armour piece."));
                                return 0;
                            }
                            GearLevels.setLevel(held, IntegerArgumentType.getInteger(c, "level"), p);
                            c.getSource().sendSuccess(() -> Component.literal("Level set."), false);
                            return 1;
                        }))));
    }

    private static int status(CommandSourceStack src, ServerPlayer p) {
        src.sendSuccess(() -> BloodMoon.status(p), false);
        return 1;
    }

    private Commands() {}
}
