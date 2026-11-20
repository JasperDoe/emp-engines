package dev.empengines.engines.client.ponder;

import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.ModRegistry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Hold W over the computer or a pylon to watch how a launcher goes together. */
public class EmpyreanPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() { return EmpyreanEngines.MODID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModRegistry.VECTOR_PLANAR_COMPUTER.getId(), ModRegistry.PYLON_T1.getId(),
                        ModRegistry.PYLON_T2.getId(), ModRegistry.PYLON_T3.getId())
                .addStoryBoard("launcher", LauncherScenes::launcher);
    }
}
