package dev.empengines.engines;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.stress.BlockStressValues;
import dev.empengines.engines.sky.SkyRuns;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(EmpyreanEngines.MODID)
public final class EmpyreanEngines {
    public static final String MODID = "emp_engines";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EmpyreanEngines(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModRegistry.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(ModRegistry.LAUNCHER_INPUT.get(), () -> Config.INPUT_STRESS.get())));
        modBus.addListener(dev.empengines.engines.raid.BoardingSync::register);
        NeoForge.EVENT_BUS.register(SkyRuns.Events.class);
        NeoForge.EVENT_BUS.register(dev.empengines.engines.launch.LauncherWrench.class);
        NeoForge.EVENT_BUS.register(dev.empengines.engines.raid.RaidManager.class);
        NeoForge.EVENT_BUS.register(dev.empengines.engines.raid.NaturalRaids.class);
        NeoForge.EVENT_BUS.register(dev.empengines.engines.portal.NetherKeys.class);
        NeoForge.EVENT_BUS.register(dev.empengines.engines.compat.DietKickstart.class);
        NeoForge.EVENT_BUS.register(dev.empengines.engines.sky.SkyVillageProtection.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
