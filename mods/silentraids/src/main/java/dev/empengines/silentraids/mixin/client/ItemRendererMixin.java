package dev.empengines.silentraids.mixin.client;

import dev.empengines.silentraids.client.VanillaArmorLook;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Silent Gear tools and armour of vanilla materials use the vanilla item's model (see VanillaArmorLook). */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void silentraids$vanillaModel(ItemStack stack, Level level, LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        if (stack.isEmpty()) return;
        Item vanilla = VanillaArmorLook.vanillaItem(stack);
        if (vanilla != null) cir.setReturnValue(((ItemRenderer) (Object) this).getItemModelShaper().getItemModel(vanilla));
    }
}
