package dev.empengines.tieredfoundry.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.empengines.tieredfoundry.block.FoundryProps;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Schematic: assets/tieredfoundry/ponder/foundry.nbt (7 x 5 x 7, made by tools/art/ponder_foundry.py).
 * Inside: air from (2,2,2) to (4,3,4). Floor heaters at y 1 under it; brick ring around them at y 1 with an air
 * intake at (5,1,3) and an encased fan at (6,1,3) blowing west into it. Brick walls at y 2..3, controller at (3,2,1)
 * facing north, drain at (3,2,5), tap at (3,2,6) facing south over a casting table at (3,1,6).
 */
public final class FoundryScenes {
    private static final BlockPos CONTROLLER = new BlockPos(3, 2, 1), DRAIN = new BlockPos(3, 2, 5),
            TAP = new BlockPos(3, 2, 6), TABLE = new BlockPos(3, 1, 6), INTAKE = new BlockPos(5, 1, 3), FAN = new BlockPos(6, 1, 3);

    public static void build(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("foundry", "Building a Foundry");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.idle(10);

        Selection floor = util.select().fromTo(2, 1, 2, 4, 1, 4);
        Selection ring = util.select().fromTo(1, 1, 1, 5, 1, 5).substract(floor);
        Selection corners = corners(util, 1, 3);
        Selection walls = util.select().fromTo(1, 2, 1, 5, 3, 5).substract(util.select().fromTo(2, 2, 2, 4, 3, 4))
                .substract(util.select().position(CONTROLLER)).substract(util.select().position(DRAIN));

        // 1. heaters
        scene.world().showSection(floor, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).text("A foundry stands on a floor of heaters: one under every block of the inside, up to 7 by 7")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90).text("Solid fuel heaters burn the fuel put in the controller. Lava heaters burn piped-in lava, in a fire brick foundry only")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget();
        scene.idle(100);

        // 2. the ring
        scene.world().showSection(ring, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).text("Ring the heaters with bricks, with a Brick Wall at each corner. Air intakes go in this ring, along a side, never in the floor")
                .pointAt(util.vector().blockSurface(INTAKE, Direction.UP)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        // 3. walls with brick corners
        scene.world().showSection(walls, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "corners", corners, 90);
        scene.overlay().showText(90).text("Build the walls on the ring, up to 8 tall. Every corner, top to bottom, is a wall of the foundry's bricks: Brick Walls here, Fire Brick Walls in a fire brick foundry")
                .pointAt(util.vector().centerOf(1, 2, 1)).placeNearTarget().colored(PonderPalette.GREEN).attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90).text("Use one kind of brick throughout, corner walls included: bricks make a tier 1 foundry, fire bricks a tier 2 one that runs hotter and holds its heat longer")
                .pointAt(util.vector().centerOf(5, 3, 5)).placeNearTarget();
        scene.idle(100);

        // 4. controller
        scene.world().showSection(util.select().position(CONTROLLER), Direction.SOUTH);
        scene.idle(15);
        scene.overlay().showText(90).text("Put the Foundry Controller in a wall, facing out. Its screen takes the items to melt and the fuel")
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        // 5. drain, tap, table
        scene.world().showSection(util.select().position(DRAIN), Direction.NORTH);
        scene.idle(10);
        scene.world().showSection(util.select().position(TABLE).add(util.select().position(TAP)), Direction.NORTH);
        scene.idle(15);
        scene.overlay().showText(90).text("Drains in the walls let the molten metal out. A tap on a drain pours into the casting table or basin below it")
                .pointAt(util.vector().blockSurface(TAP, Direction.UP)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        // 6. light it
        scene.overlay().showControls(util.vector().blockSurface(CONTROLLER, Direction.NORTH), Pointing.DOWN, 40)
                .rightClick().withItem(new ItemStack(Items.COAL));
        scene.idle(20);
        scene.world().modifyBlocks(floor, FoundryScenes::lit, false);
        scene.world().modifyBlock(CONTROLLER, FoundryScenes::lit, false);
        scene.world().modifyBlock(DRAIN, FoundryScenes::lit, false);
        scene.effects().emitParticles(util.vector().of(3.5, 4, 3.5),
                scene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1, 60);
        scene.overlay().showText(90).text("With fuel and something to melt, the heaters light and the foundry heats up. Hover the heat bar to see how hot, and how fast it cools")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void airIntake(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("air_intake", "Blowing Air into a Foundry");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.9f);
        scene.showBasePlate();
        scene.idle(10);

        Selection foundry = util.select().fromTo(1, 1, 1, 5, 3, 5).add(util.select().position(TAP)).add(util.select().position(TABLE));
        scene.world().showSection(foundry, Direction.DOWN);
        scene.world().modifyBlocks(util.select().fromTo(2, 1, 2, 4, 1, 4), FoundryScenes::lit, false);
        scene.world().modifyBlock(CONTROLLER, FoundryScenes::lit, false);
        scene.idle(20);

        // 1. where it goes
        scene.overlay().showOutline(PonderPalette.WHITE, "intake", util.select().position(INTAKE), 80);
        scene.overlay().showText(80).text("Air Intakes take the place of a brick in the ring around the heaters, along a side, not at a corner")
                .pointAt(util.vector().blockSurface(INTAKE, Direction.EAST)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        // 2. the fan
        scene.world().showSection(util.select().position(FAN), Direction.WEST);
        scene.idle(10);
        scene.world().setKineticSpeed(util.select().position(FAN), -64);   // facing west: negative speed blows west
        scene.world().modifyBlock(INTAKE, FoundryScenes::lit, false);
        scene.overlay().showText(90).text("Blow an encased fan into it. Forced air makes the foundry hotter (faster fans, more heat), lets it alloy, and gets more metal out of ores")
                .pointAt(util.vector().blockSurface(FAN, Direction.UP)).placeNearTarget().colored(PonderPalette.BLUE).attachKeyFrame();
        scene.idle(100);

        // 3. smoke and heat loss
        scene.effects().emitParticles(util.vector().of(3.5, 4, 3.5),
                scene.effects().particleEmitterWithinBlockSpace(ParticleTypes.LARGE_SMOKE, new Vec3(0, 0.12, 0)), 2, 80);
        scene.overlay().showText(90).text("Smoke pours out of the open top. More fans in more intakes: more smoke, and the foundry loses its heat more slowly between loads")
                .pointAt(util.vector().centerOf(3, 3, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80).text("Better bricks cut heat loss too: fire bricks lose heat 40% slower than plain bricks")
                .pointAt(util.vector().centerOf(1, 3, 5)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    private static Selection corners(SceneBuildingUtil util, int y1, int y2) {
        return util.select().fromTo(1, y1, 1, 1, y2, 1).add(util.select().fromTo(5, y1, 1, 5, y2, 1))
                .add(util.select().fromTo(1, y1, 5, 1, y2, 5)).add(util.select().fromTo(5, y1, 5, 5, y2, 5));
    }

    private static BlockState lit(BlockState s) {
        return s.hasProperty(FoundryProps.LIT) ? s.setValue(FoundryProps.LIT, true) : s;
    }

    private FoundryScenes() {}
}
