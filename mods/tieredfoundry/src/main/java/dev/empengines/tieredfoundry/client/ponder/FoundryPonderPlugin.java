package dev.empengines.tieredfoundry.client.ponder;

import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.TieredFoundry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Hold W over the controller, a heater or a drain (how to build a foundry) or an air intake (what fans do). */
public class FoundryPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() { return TieredFoundry.MODID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(Registry.CONTROLLER.getId(), Registry.SOLID_HEATER.getId(), Registry.LAVA_HEATER.getId(),
                        Registry.DRAIN.getId(), Registry.FIRE_BRICKS.getId())
                .addStoryBoard("foundry", FoundryScenes::build);
        helper.forComponents(Registry.AIR_DUCT.getId())
                .addStoryBoard("foundry", FoundryScenes::airIntake);
    }
}
