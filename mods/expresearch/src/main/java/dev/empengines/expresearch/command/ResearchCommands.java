package dev.empengines.expresearch.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.empengines.expresearch.research.ResearchRegistry;
import dev.empengines.expresearch.research.ResearchService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** /expresearch grant|revoke|grantall|list <player> [research] */
public final class ResearchCommands {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("expresearch").requires(s -> s.hasPermission(2))
                .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("research", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(ResearchRegistry.nodes().keySet(), b))
                                .executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    ResourceLocation id = ResourceLocationArgument.getId(c, "research");
                                    boolean ok = ResearchService.unlock(p, id);
                                    c.getSource().sendSuccess(() -> Component.literal(ok ? "Unlocked " + id : "Nothing changed (unknown or already unlocked)"), true);
                                    return ok ? 1 : 0;
                                }))))
                .then(Commands.literal("revoke").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("research", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(ResearchRegistry.nodes().keySet(), b))
                                .executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    ResourceLocation id = ResourceLocationArgument.getId(c, "research");
                                    boolean ok = ResearchService.revoke(p, id);
                                    c.getSource().sendSuccess(() -> Component.literal(ok ? "Revoked " + id : "Nothing changed"), true);
                                    return ok ? 1 : 0;
                                }))))
                .then(Commands.literal("grantall").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            int n = 0;
                            for (ResourceLocation id : ResearchRegistry.nodes().keySet()) if (ResearchService.unlock(p, id)) n++;
                            int count = n;
                            c.getSource().sendSuccess(() -> Component.literal("Unlocked " + count + " research for " + p.getName().getString() + "'s team"), true);
                            return count;
                        })))
                .then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            c.getSource().sendSuccess(() -> Component.literal("Team research: " + ResearchService.unlocked(p)), false);
                            return 1;
                        }))));
    }

    private ResearchCommands() {}
}
