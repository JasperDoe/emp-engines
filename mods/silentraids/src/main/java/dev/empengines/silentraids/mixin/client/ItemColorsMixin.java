package dev.empengines.silentraids.mixin.client;

import dev.empengines.silentraids.client.VanillaArmorLook;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When a Silent Gear item is drawn with a vanilla item's model (see VanillaArmorLook), Silent Gear's material tint
 * must not be applied on top: vanilla's layer 0 is tintable, so an iron sword came out greyed by the iron colour.
 * Vanilla colours instead: none, except leather armour, which gets vanilla's undyed leather brown.
 */
@Mixin(ItemColors.class)
public abstract class ItemColorsMixin {
    private static final int UNDYED_LEATHER = 0xFFA06540;

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void silentraids$vanillaColours(ItemStack stack, int tintIndex, CallbackInfoReturnable<Integer> cir) {
        if (stack.isEmpty()) return;
        Item vanilla = VanillaArmorLook.vanillaItem(stack);
        if (vanilla == null) return;
        boolean leather = vanilla == Items.LEATHER_HELMET || vanilla == Items.LEATHER_CHESTPLATE
                || vanilla == Items.LEATHER_LEGGINGS || vanilla == Items.LEATHER_BOOTS;
        cir.setReturnValue(leather && tintIndex == 0 ? UNDYED_LEATHER : -1);
    }
}
