package dev.empengines.engines.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CloudWorks API (Diet's recipe reader) writes a debug file for every recipe in the game on each server start:
 * about 7000 files and 20 minutes of a background thread, and Diet's own calculation waits in the same queue.
 * Nothing uses the files, so the dump is skipped.
 */
@Pseudo
@Mixin(targets = "com.cloudworks.api.recipeparser.DebugOutputWriter", remap = false)
public abstract class CloudWorksDebugDumpMixin {
    @Inject(method = "writeDebugOutputAsync", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void emp_engines$skipDump(CallbackInfo ci) {
        ci.cancel();
    }
}
