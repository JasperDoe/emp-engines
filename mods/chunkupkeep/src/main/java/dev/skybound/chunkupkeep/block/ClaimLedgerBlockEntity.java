package dev.skybound.chunkupkeep.block;

import dev.skybound.chunkupkeep.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Remembers which FTB team placed the ledger. */
public class ClaimLedgerBlockEntity extends BlockEntity {
    private UUID team;

    public ClaimLedgerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CLAIM_LEDGER_BE.get(), pos, state);
    }

    public UUID team() { return team; }

    public void setTeam(UUID team) {
        this.team = team;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        if (team != null) tag.putUUID("team", team);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        team = tag.hasUUID("team") ? tag.getUUID("team") : null;
    }
}
