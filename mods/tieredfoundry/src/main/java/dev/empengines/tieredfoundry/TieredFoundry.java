package dev.empengines.tieredfoundry;

import com.mojang.logging.LogUtils;
import dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Tiered Foundry: a brick foundry that grows in tiers instead of giving its best yield from the first build.
 *
 * <ul>
 * <li>Tier 1: vanilla bricks, solid fuel heaters in the floor. Melts basic metals, casts; 1.2x ore.</li>
 * <li>Tier 1.5: a Create encased fan blowing into an air duct in the floor. Adds heat by fan speed and allows
 * alloying; 1.4x ore.</li>
 * <li>Tier 2: every brick swapped for fire bricks (fire clay glazed with molten glass from a tier 1 foundry), lava
 * heaters fed by Create pipes; 1.6x ore.</li>
 * </ul>
 * Items go in through the controller; molten metal comes out through drains and taps into casting tables and
 * basins. Raw and Create-crushed ores get the tier's multiplier, so a crushing line stacks on top.
 */
@Mod(TieredFoundry.MODID)
public final class TieredFoundry {
    public static final String MODID = "tieredfoundry";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TieredFoundry(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        Registry.register(modBus);
        modBus.addListener(Registry::capabilities);
        NeoForge.EVENT_BUS.register(ControllerBlockEntity.Watch.class);
        if (FMLEnvironment.dist.isClient()) dev.empengines.tieredfoundry.client.ClientSetup.init(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
