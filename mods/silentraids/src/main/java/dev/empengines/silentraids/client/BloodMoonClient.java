package dev.empengines.silentraids.client;

import dev.empengines.silentraids.net.BloodMoonSync;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client only: the red moon, a blood-tinted night sky, clouds and fog. Fades in and out over about 5 s. */
public final class BloodMoonClient {
    /** 0 = normal night, 1 = full blood moon. */
    private static float strength, last;

    private BloodMoonClient() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = BloodMoonSync.clientActive && mc.level != null && mc.level.dimension() == Level.OVERWORLD;
        if (mc.level == null) BloodMoonSync.clientActive = false;
        last = strength;
        strength = Mth.clamp(strength + (on ? 0.01f : -0.02f), 0f, 1f);
    }

    public static float strength(float partialTick) {
        return Mth.lerp(partialTick, last, strength);
    }

    /** How dark it is: the tint only shows at night, so dawn and dusk blend in. */
    private static float night(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0f;
        float sun = mc.level.getSunAngle(partialTick);
        float c = Mth.cos(sun);   // 1 at noon, -1 at midnight
        return Mth.clamp(0.5f - c * 1.5f, 0f, 1f);
    }

    public static float amount(float partialTick) {
        return strength(partialTick) * night(partialTick);
    }

    public static Vec3 tintSky(Vec3 c, float partialTick) {
        float k = amount(partialTick);
        if (k <= 0) return c;
        return new Vec3(Mth.lerp(k * 0.55, c.x, 0.32), Mth.lerp(k * 0.55, c.y, 0.02), Mth.lerp(k * 0.55, c.z, 0.03));
    }

    public static Vec3 tintCloud(Vec3 c, float partialTick) {
        float k = amount(partialTick);
        if (k <= 0) return c;
        return new Vec3(Mth.lerp(k * 0.5, c.x, 0.45), Mth.lerp(k * 0.5, c.y, 0.08), Mth.lerp(k * 0.5, c.z, 0.08));
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor e) {
        float k = amount((float) e.getPartialTick()) * 0.5f;
        if (k <= 0) return;
        e.setRed(Mth.lerp(k, e.getRed(), 0.30f));
        e.setGreen(Mth.lerp(k, e.getGreen(), 0.02f));
        e.setBlue(Mth.lerp(k, e.getBlue(), 0.03f));
    }
}
