package dev.empengines.engines.mixin;

import dev.empengines.engines.raid.RaidManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player walking along a boarding rope has no blocks under them, which the server would take for
 * flying (and kick them after a few seconds). A boarding rope counts as something to stand on.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public abstract class ServerGamePacketListenerMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true, remap = false)
    private void emp_engines$ropeIsGround(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (RaidManager.onBoardingRope(entity)) cir.setReturnValue(false);
    }
}
