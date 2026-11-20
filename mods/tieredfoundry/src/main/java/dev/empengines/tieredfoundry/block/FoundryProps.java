package dev.empengines.tieredfoundry.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Shared block state properties. TIER picks the texture set (1 = vanilla bricks, 2 = fire bricks), GregTech style. */
public final class FoundryProps {
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 2);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    private FoundryProps() {}
}
