package dev.skybound.createskybound.block;

import net.minecraft.world.level.block.Block;

/** A launcher corner. The lowest pylon tier on a platform sets how long the sky run lasts. */
public class PylonBlock extends Block {
    private final int tier;

    public PylonBlock(int tier, Properties props) {
        super(props);
        this.tier = tier;
    }

    public int tier() { return tier; }
}
