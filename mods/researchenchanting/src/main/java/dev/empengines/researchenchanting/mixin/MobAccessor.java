package dev.empengines.researchenchanting.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Boon of Arthropods rewires its silverfish: no burrowing, no waking friends, hunt monsters only. */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("goalSelector")
    GoalSelector researchenchanting$goals();

    @Accessor("targetSelector")
    GoalSelector researchenchanting$targets();
}
