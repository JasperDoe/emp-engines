package dev.empengines.engines.client;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.empengines.engines.EmpyreanEngines;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import dev.empengines.engines.ModRegistry;
import dev.empengines.engines.block.VectorPlanarComputerBlockEntity;
import dev.empengines.engines.block.VectorPlanarComputerBlockEntity.Phase;
import dev.empengines.engines.launch.LaunchPath;
import dev.empengines.engines.launch.LaunchTracker;
import dev.empengines.engines.sky.SkyRuns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Renderers (the pylons' shards; the launch light is drawn at a render stage), the shaft visual, Ponder, camera shake near a launch, the charging hum and the white-out. */
public final class ClientEvents {

    @EventBusSubscriber(modid = EmpyreanEngines.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(ModRegistry.PYLON_BE.get(), PylonRenderer::new);
            e.registerBlockEntityRenderer(ModRegistry.LAUNCHER_INPUT_BE.get(), ShaftRenderer::new);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                // The input's spinning shaft, drawn by Flywheel like Create's own encased shafts
                SimpleBlockEntityVisualizer.builder(ModRegistry.LAUNCHER_INPUT_BE.get())
                        .factory(SingleAxisRotatingVisual::shaft)
                        .skipVanillaRender(be -> true)
                        .apply();
            });
        }
    }

    @EventBusSubscriber(modid = EmpyreanEngines.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        private static float shake;              // 0..1, eased each tick
        private static float whiteout;           // 0..1 screen white
        private static ResourceKey<Level> lastDim;
        private static boolean launchingAboard;

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            float targetShake = 0, targetWhite = 0;
            launchingAboard = false;
            for (VectorPlanarComputerBlockEntity be : LaunchTracker.active()) {
                if (be.getLevel() != player.level()) continue;
                Vec3 ship = be.shipPosition(0);
                double dist = player.position().distanceTo(ship);
                float near = (float) Math.max(0, 1 - dist / 96.0);
                float t = be.phaseTicks();
                float strength = switch (be.phase()) {
                    case COUNTDOWN -> 0.15f * t / LaunchPath.COUNTDOWN;
                    case BEAMS -> 0.25f;
                    case SPIN -> 0.25f + 0.55f * t / LaunchPath.SPIN;
                    case LAUNCH -> Math.max(0, 0.7f - t / 25f);   // one hard shove as it's thrown, then calm
                    default -> 0;
                };
                targetShake = Math.max(targetShake, strength * near);
                boolean aboard = dist < 24;
                if (be.phase() == Phase.LAUNCH && aboard) {
                    launchingAboard = true;
                    targetWhite = Math.max(targetWhite, Math.max(0, (t - (LaunchPath.LAUNCH - LaunchPath.WHITEOUT)) / (float) LaunchPath.WHITEOUT));
                }
            }
            // Arriving in the Etheric Skies: start white and fade in.
            ResourceKey<Level> dim = player.level().dimension();
            if (lastDim != null && dim != lastDim && dim.equals(SkyRuns.ETHERIC_SKIES)) whiteout = 1f;
            lastDim = dim;

            ChargeHum.update();
            shake += (targetShake - shake) * 0.3f;
            if (targetWhite > whiteout) whiteout += (targetWhite - whiteout) * 0.5f;
            else whiteout = Math.max(targetWhite, whiteout - (launchingAboard ? 0 : 0.025f));
        }

        /** The launch light, drawn after the clouds and weather. */
        @SubscribeEvent
        public static void stage(RenderLevelStageEvent e) {
            LaunchRenderer.renderAll(e);
            BoardingRopeClient.render(e);
        }

        /** Walking on boarding ropes (the client moves its own player). */
        @SubscribeEvent
        public static void playerTick(PlayerTickEvent.Post e) {
            if (e.getEntity() instanceof LocalPlayer p) BoardingRopeClient.walk(p);
        }

        @SubscribeEvent
        public static void camera(ViewportEvent.ComputeCameraAngles e) {
            if (shake <= 0.01f) return;
            double time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + e.getPartialTick();
            float s = shake * 1.6f;
            e.setYaw(e.getYaw() + (float) (Math.sin(time * 1.7) * Math.sin(time * 0.53)) * s);
            e.setPitch(e.getPitch() + (float) (Math.sin(time * 2.3 + 1.1) * Math.cos(time * 0.71)) * s);
            e.setRoll(e.getRoll() + (float) Math.sin(time * 1.3 + 2.0) * s * 0.6f);
        }

        @SubscribeEvent
        public static void overlay(RenderGuiEvent.Post e) {
            if (whiteout <= 0.005f) return;
            int a = Math.round(Math.min(1f, whiteout) * 255);
            var g = e.getGuiGraphics();
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xFFFFFF);
        }

        private GameBus() {}
    }

    private ClientEvents() {}
}
