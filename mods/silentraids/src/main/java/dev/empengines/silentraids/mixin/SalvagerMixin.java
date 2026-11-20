package dev.empengines.silentraids.mixin;

import dev.empengines.silentraids.ApothBridge;
import dev.empengines.silentraids.gear.GearLevels;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.block.salvager.SalvagerBlockEntity;
import net.silentchaos512.gear.crafting.recipe.salvage.SalvagingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Silent Gear Salvager won't take apart gear that has gems socketed or has gained levels (both would be lost).
 * Pull the gems first with a Sigil of Withdrawal, or keep the item.
 */
@Mixin(value = SalvagerBlockEntity.class, remap = false)
public abstract class SalvagerMixin {
    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true)
    private void silentraids$refuseValuable(ItemStack input, CallbackInfoReturnable<SalvagingRecipe> cir) {
        if (input.isEmpty()) return;
        if (GearLevels.get(input).level() > 0 || ApothBridge.hasGems(input)) cir.setReturnValue(null);
    }
}
