package dev.empengines.researchenchanting.mixin.client;

import dev.empengines.researchenchanting.client.Glyphs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The table's client tick (vanilla turns its book here) also sends the glyph streams. */
@Mixin(EnchantingTableBlockEntity.class)
public abstract class EnchantingTableTickMixin {
    @Inject(method = "bookAnimationTick", at = @At("TAIL"))
    private static void researchenchanting$glyphs(Level level, BlockPos pos, BlockState state, EnchantingTableBlockEntity be, CallbackInfo ci) {
        Glyphs.tick(level, pos, state, be);
    }
}
