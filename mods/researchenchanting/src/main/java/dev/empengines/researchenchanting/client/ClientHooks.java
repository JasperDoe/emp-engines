package dev.empengines.researchenchanting.client;

import dev.empengines.researchenchanting.Registry;
import dev.empengines.researchenchanting.ResearchEnchanting;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only setup. Only touched on the physical client. */
public final class ClientHooks {
    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(Registry.TABLE_MENU.get(), ResearchTableScreen::new));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() ->
                ItemProperties.register(Registry.RESEARCH_FOCUS.get(), ResearchEnchanting.id("look"),
                        (stack, level, entity, seed) -> ResearchFocusItem.look(stack))));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) ->
                e.registerSpriteSet(Registry.GLYPH.get(), WormGlyph.Provider::new));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Flights::render);
        // Hint for the start of the system: a plain book is where a research focus comes from.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) -> {
            if (e.getItemStack().is(net.minecraft.world.item.Items.BOOK)) {
                e.getToolTip().add(net.minecraft.network.chat.Component.translatable("item.researchenchanting.book_hint")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        });
    }

    public static RegistryAccess registries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    /** Set by the EMI plugin when it ran before the server's foci arrived, so its research pages are missing. */
    public static volatile boolean emiMissedFoci;

    /** New foci from the server: rebuild EMI only if it was built without them (it usually loads after). */
    public static void fociChanged() {
        if (!emiMissedFoci || !ModList.get().isLoaded("emi")) return;
        emiMissedFoci = false;
        try {
            Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("reload").invoke(null);
        } catch (ReflectiveOperationException ex) {
            ResearchEnchanting.LOGGER.debug("Could not reload EMI after the research foci changed", ex);
        }
    }

    private ClientHooks() {}
}
