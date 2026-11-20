package dev.empengines.engines.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /uitheme grey|light|dark: picks the menu and HUD look. grey is vanilla, light is Recolourful Containers,
 * dark is Recolourful Containers DARK (the pack's default). It just switches those resource packs on or off
 * (Options > Resource Packs does the same) and remembers the choice like any resource pack setting.
 */
@EventBusSubscriber(modid = "emp_engines", value = Dist.CLIENT)
public final class UiTheme {
    private UiTheme() {}

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("uitheme")
                .executes(c -> { c.getSource().sendSystemMessage(Component.literal("UI theme: " + current() + ". Use /uitheme grey, light or dark.")); return 1; })
                .then(Commands.argument("theme", StringArgumentType.word())
                        .suggests((c, b) -> { for (String s : List.of("grey", "light", "dark")) b.suggest(s); return b.buildFuture(); })
                        .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "theme")))));
    }

    private static boolean isTheme(String id) { return id.toLowerCase(Locale.ROOT).contains("recolourful"); }

    private static boolean isDark(String id) { return id.toLowerCase(Locale.ROOT).contains("dark"); }

    private static String current() {
        PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
        for (String id : repo.getSelectedIds()) if (isTheme(id)) return isDark(id) ? "dark" : "light";
        return "grey";
    }

    private static int set(CommandSourceStack source, String theme) {
        String t = theme.toLowerCase(Locale.ROOT).replace("gray", "grey");
        if (!List.of("grey", "light", "dark").contains(t)) {
            source.sendFailure(Component.literal("Unknown theme: " + theme + " (grey, light or dark)"));
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        String wanted = null;
        if (!t.equals("grey")) {
            for (String id : repo.getAvailableIds()) {
                if (isTheme(id) && isDark(id) == t.equals("dark")) { wanted = id; break; }
            }
            if (wanted == null) {
                source.sendFailure(Component.literal("The " + t + " UI pack isn't installed (resourcepacks folder)."));
                return 0;
            }
        }
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        selected.removeIf(UiTheme::isTheme);
        if (wanted != null) selected.add(wanted);   // last = highest priority
        if (selected.equals(new ArrayList<>(repo.getSelectedIds()))) {
            source.sendSystemMessage(Component.literal("UI theme is already " + t + "."));
            return 1;
        }
        repo.setSelected(selected);
        source.sendSystemMessage(Component.literal("UI theme: " + t + " (reloading resources...)").withStyle(ChatFormatting.GRAY));
        mc.execute(() -> mc.options.updateResourcePacks(repo));
        return 1;
    }
}
