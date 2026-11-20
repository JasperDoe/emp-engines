package dev.empengines.silentraids.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.empengines.silentraids.client.BloodMoonClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paints the moon red during a blood moon. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    // Right after the moon texture is bound (the sun is bound first)
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V", ordinal = 1, shift = At.Shift.AFTER), require = 0)
    private void silentraids$redMoon(Matrix4f frustum, Matrix4f projection, float partialTick, Camera camera, boolean foggy, Runnable fog, CallbackInfo ci) {
        float k = BloodMoonClient.strength(partialTick);
        if (k <= 0) return;
        float[] c = RenderSystem.getShaderColor();
        RenderSystem.setShaderColor(c[0], c[1] * (1 - 0.8f * k), c[2] * (1 - 0.85f * k), c[3]);
    }

    // Moon drawn: put the colour back before the stars
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F"), require = 0)
    private void silentraids$moonDone(Matrix4f frustum, Matrix4f projection, float partialTick, Camera camera, boolean foggy, Runnable fog, CallbackInfo ci) {
        if (BloodMoonClient.strength(partialTick) <= 0) return;
        float[] c = RenderSystem.getShaderColor();
        RenderSystem.setShaderColor(1f, 1f, 1f, c[3]);
    }
}
