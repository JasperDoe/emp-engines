package dev.empengines.tieredfoundry.client;

import dev.empengines.tieredfoundry.Molten;
import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.TieredFoundry;
import dev.empengines.tieredfoundry.item.CastItem;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

/** Client wiring: the screen, block entity renderers, molten fluid textures and tints, cast models. */
public final class ClientSetup {
    public static final ResourceLocation STILL = TieredFoundry.id("block/fluid/molten_still");
    public static final ResourceLocation FLOWING = TieredFoundry.id("block/fluid/molten_flowing");

    public static void init(IEventBus bus) {
        // Ponder plugins have to be added before the first resource reload
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new dev.empengines.tieredfoundry.client.ponder.FoundryPonderPlugin());
        bus.addListener(ClientSetup::setup);
        bus.addListener(ClientSetup::screens);
        bus.addListener(ClientSetup::extensions);
        bus.addListener(ClientSetup::itemColours);
    }

    private static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            BlockEntityRenderers.register(Registry.CASTING_BE.get(), CastingRenderer::new);
            BlockEntityRenderers.register(Registry.TAP_BE.get(), TapRenderer::new);
            BlockEntityRenderers.register(Registry.CONTROLLER_BE.get(), ControllerRenderer::new);
            ResourceLocation pattern = TieredFoundry.id("pattern");
            for (Item cast : new Item[]{Registry.GOLD_CAST.get(), Registry.SAND_CAST.get()}) {
                ItemProperties.register(cast, pattern, (stack, level, entity, seed) -> CastItem.modelIndex(stack));
            }
        });
    }

    private static void screens(RegisterMenuScreensEvent e) {
        e.register(Registry.CONTROLLER_MENU.get(), ControllerScreen::new);
    }

    private static void extensions(RegisterClientExtensionsEvent e) {
        for (Molten m : Molten.ALL) {
            int colour = m.def.colour();
            e.registerFluidType(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return STILL; }
                @Override public ResourceLocation getFlowingTexture() { return FLOWING; }
                @Override public int getTintColor() { return colour; }
            }, m.type.get());
        }
    }

    private static void itemColours(RegisterColorHandlersEvent.Item e) {
        e.register(new DynamicFluidContainerModel.Colors(), Molten.ALL.stream().map(m -> m.bucket.get()).toArray(Item[]::new));
        e.register((stack, layer) -> layer == 0 ? 0xFFE8A878 : -1, Registry.FIRE_CLAY.get());
    }

    private ClientSetup() {}
}
