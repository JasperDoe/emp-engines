package dev.empengines.silentraids.mixin.client;

import dev.empengines.silentraids.client.VanillaArmorLook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.item.gear.GearArmorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla armour textures for Silent Gear armour of iron tier and below (see VanillaArmorLook). */
@Mixin(value = GearArmorItem.class, remap = false)
public abstract class GearArmorItemMixin {
    @Inject(method = "getArmorTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private void silentraids$vanillaTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner,
                                            CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation tex = VanillaArmorLook.texture(stack, inner);
        if (tex != null) cir.setReturnValue(tex);
    }

    @Inject(method = "getArmorColor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void silentraids$noTint(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (VanillaArmorLook.untinted(stack)) cir.setReturnValue(0xFFFFFFFF);   // opaque white, as Silent Gear itself uses for untinted layers
    }
}
