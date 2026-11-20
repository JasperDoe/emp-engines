package dev.empengines.silentraids.mixin.client;

import dev.empengines.silentraids.client.BloodMoonClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blood-tinted night sky and clouds during a blood moon. Shader packs read these colours too. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void silentraids$sky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(BloodMoonClient.tintSky(cir.getReturnValue(), partialTick));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void silentraids$clouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(BloodMoonClient.tintCloud(cir.getReturnValue(), partialTick));
    }
}
