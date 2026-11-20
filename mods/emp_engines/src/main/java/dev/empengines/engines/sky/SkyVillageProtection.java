package dev.empengines.engines.sky;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Sky villages (structure tag emp_engines:sky_villages) can't be broken or built on: no placing or breaking blocks
 * inside any of their pieces, and explosions leave them standing. Chests, doors, levers and villagers still work.
 * Creative and spectator players are exempt. Everywhere else, including high mountains, building is free.
 * (Replaces the old pack-wide "nothing at or above Y 250" rule.)
 */
public final class SkyVillageProtection {
    private SkyVillageProtection() {}

    public static final TagKey<Structure> SKY_VILLAGES =
            TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("emp_engines", "sky_villages"));

    /** Sky villages sit at Y 250 and up; anything lower is skipped without a structure lookup. */
    private static final int LOWEST = 240;

    static boolean protectedAt(LevelAccessor level, BlockPos pos) {
        if (pos.getY() < LOWEST || !(level instanceof ServerLevel server)) return false;
        return server.structureManager().getStructureWithPieceAt(pos, SKY_VILLAGES).isValid();
    }

    private static boolean exempt(Entity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    private static void tell(Entity e) {
        if (e instanceof Player p) {
            p.displayClientMessage(Component.translatable("message.emp_engines.sky_village_protected")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (exempt(e.getPlayer()) || !protectedAt(e.getLevel(), e.getPos())) return;
        e.setCanceled(true);
        tell(e.getPlayer());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() == null || exempt(e.getEntity()) || !protectedAt(e.getLevel(), e.getPos())) return;
        e.setCanceled(true);
        tell(e.getEntity());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        e.getAffectedBlocks().removeIf(pos -> protectedAt(e.getLevel(), pos));
    }
}
