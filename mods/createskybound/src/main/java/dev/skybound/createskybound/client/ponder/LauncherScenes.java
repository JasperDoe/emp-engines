package dev.skybound.createskybound.client.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.skybound.createskybound.ModRegistry;
import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity;
import dev.skybound.createskybound.launch.Platform;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Schematic: assets/createskybound/ponder/launcher.nbt (7 x 6 x 7, made by tools/art/ponder_launcher.py).
 * Platform at y 1 from (1,1,1) to (5,1,5), computer on the south edge at (3,2,5),
 * the east edge casing at (5,1,3) becomes the shaft input, fed by a shaft at (6,1,3).
 */
public final class LauncherScenes {
    private static final Platform PLATFORM = new Platform(1, 1, 5, 5, 1, 1);

    public static void launcher(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("launcher", "Building a Skyward Launcher");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(3, 2, 5);
        BlockPos input = util.grid().at(5, 1, 3);
        BlockPos shaft = util.grid().at(6, 1, 3);
        BlockPos lever = util.grid().at(2, 2, 5);
        Selection quartz = util.select().fromTo(2, 1, 2, 4, 1, 4);
        Selection corners = util.select().position(1, 1, 1).add(util.select().position(5, 1, 1))
                .add(util.select().position(1, 1, 5)).add(util.select().position(5, 1, 5));
        Selection ring = util.select().fromTo(1, 1, 1, 5, 1, 5).substract(quartz).substract(corners);
        Selection ship = util.select().fromTo(2, 3, 2, 4, 5, 4);

        // 1. The quartz centre
        scene.world().showSection(quartz, Direction.DOWN);
        sound(scene, SoundEvents.STONE_PLACE, 1f, 0.5f);
        scene.idle(15);
        scene.overlay().showText(70).text("Fill a square with blocks of quartz. The ship will rest over it.")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        // 2. The brass ring
        scene.world().showSection(ring, Direction.DOWN);
        sound(scene, SoundEvents.WOOD_PLACE, 0.9f, 0.6f);
        scene.idle(15);
        scene.overlay().showText(60).text("Surround it with a ring of brass casing")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(70);

        // 3. The pylons
        scene.world().showSection(corners, Direction.DOWN);
        sound(scene, SoundEvents.AMETHYST_BLOCK_PLACE, 1f, 0.7f);
        scene.idle(15);
        scene.overlay().showText(80).text("Each corner takes a Resonance Pylon. The weakest pylon decides how long the run in the Chaotic Skies lasts.")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget().colored(PonderPalette.BLUE).attachKeyFrame();
        scene.idle(90);

        // 4. The computer on top of the ring
        scene.world().showSection(util.select().position(computer), Direction.DOWN);
        sound(scene, SoundEvents.NETHERITE_BLOCK_PLACE, 1f, 0.6f);
        scene.idle(15);
        scene.overlay().showText(80).text("Place the Vector Planar Computer on top of the ring. Right-click it to check the platform and charge.")
                .pointAt(util.vector().topOf(computer)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        // 5. Wrench a casing into a shaft input
        scene.overlay().showControls(util.vector().blockSurface(input, Direction.EAST), Pointing.DOWN, 40)
                .rightClick().withItem(new ItemStack(AllItems.WRENCH.get()));
        scene.idle(12);
        scene.world().setBlock(input, ModRegistry.LAUNCHER_INPUT.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X), false);
        sound(scene, SoundEvents.CHAIN_PLACE, 1.2f, 0.8f);
        scene.idle(15);
        scene.world().showSection(util.select().position(shaft), Direction.WEST);
        scene.idle(10);
        scene.world().setKineticSpeed(util.select().position(input).add(util.select().position(shaft)), 32);
        scene.overlay().showText(80).text("Wrench a face of the ring to open that casing into a shaft input, then feed it rotation")
                .pointAt(util.vector().blockSurface(input, Direction.EAST)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        // 6. Charging
        scene.overlay().showText(90).text("Rotation charges the launcher. Heavier ships need more charge, and every input on the ring adds to the rate.")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        for (int i = 1; i <= 5; i++) {
            float f = i / 5f;
            scene.world().modifyBlockEntity(computer, VectorPlanarComputerBlockEntity.class, be -> be.ponderPreview(PLATFORM, f));
            if (i == 1) sound(scene, SoundEvents.PORTAL_AMBIENT, 0.15f, 0.55f);
            scene.idle(20);
        }
        sound(scene, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.6f);
        scene.idle(10);

        // 7. A ship, and the redstone signal
        ElementLink<WorldSectionElement> boat = scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(60).text("Bring a ship over the platform")
                .pointAt(util.vector().centerOf(3, 4, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(70);
        scene.world().showSection(util.select().position(lever), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(lever), Pointing.DOWN, 30).rightClick();
        scene.idle(8);
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.effects().indicateRedstone(lever);
        sound(scene, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 0.7f);
        scene.overlay().showText(70).text("When it is fully charged, a redstone signal starts the countdown")
                .pointAt(util.vector().topOf(computer)).placeNearTarget().attachKeyFrame();
        scene.idle(30);

        // 8. Lift, then the throw
        scene.world().moveSection(boat, new Vec3(0, 1, 0), 40);
        sound(scene, SoundEvents.BEACON_ACTIVATE, 0.8f, 0.6f);
        scene.idle(55);
        sound(scene, SoundEvents.ENDER_EYE_LAUNCH, 1f, 0.5f);
        scene.world().moveSection(boat, new Vec3(0, 14, 0), 12);
        scene.idle(10);
        scene.world().hideIndependentSection(boat, Direction.UP);
        scene.world().modifyBlockEntity(computer, VectorPlanarComputerBlockEntity.class, be -> be.ponderPreview(PLATFORM, 0));
        scene.idle(15);
        scene.overlay().showText(80).text("The ship is thrown up through the clouds and on into the Chaotic Skies. The charge is spent, ready for the next launch.")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    private static void sound(SceneBuilder scene, SoundEvent sound, float volume, float pitch) {
        scene.addInstruction(ps -> Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume)));
    }

    private LauncherScenes() {}
}
