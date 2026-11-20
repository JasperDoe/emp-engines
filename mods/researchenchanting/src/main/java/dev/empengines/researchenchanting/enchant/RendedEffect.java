package dev.empengines.researchenchanting.enchant;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Rending's armour break: the armour modifier scales with amplifier + 1 (one point each). */
public class RendedEffect extends MobEffect {
    public RendedEffect(MobEffectCategory category, int colour) {
        super(category, colour);
    }
}
