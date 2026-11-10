package dev.skybound.createskybound;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.stress.BlockStressValues;
import dev.skybound.createskybound.sky.SkyRuns;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreateSkybound.MODID)
public final class CreateSkybound {
    public static final String MODID = "createskybound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateSkybound(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModRegistry.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(ModRegistry.LAUNCHER_INPUT.get(), () -> Config.INPUT_STRESS.get())));
        NeoForge.EVENT_BUS.register(SkyRuns.Events.class);
        NeoForge.EVENT_BUS.register(dev.skybound.createskybound.launch.LauncherWrench.class);
        NeoForge.EVENT_BUS.register(dev.skybound.createskybound.raid.RaidManager.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
