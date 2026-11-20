package dev.empengines.silentraids.siege;

import dev.empengines.silentraids.ApothBridge;
import dev.empengines.silentraids.SilentRaids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Picking, spawning and equipping the raiders of one wave. */
final class RaidMobs {
    static final String RAIDER = "silentraids.raider";   // siege id
    static final String KIND = "silentraids.kind";       // normal / elite / invader
    static final String THREAT = "silentraids.threat";

    private record Entry(EntityType<? extends Mob> type, int weight, double minThreat) {}

    private static final List<Entry> POOL = List.of(
            new Entry(EntityType.ZOMBIE, 10, 0), new Entry(EntityType.SKELETON, 8, 0), new Entry(EntityType.SPIDER, 5, 0),
            new Entry(EntityType.CREEPER, 3, 0.5),
            new Entry(EntityType.HUSK, 3, 1), new Entry(EntityType.STRAY, 3, 1), new Entry(EntityType.ZOMBIE_VILLAGER, 2, 1),
            new Entry(EntityType.WITCH, 2, 1.5),
            new Entry(EntityType.VINDICATOR, 4, 2), new Entry(EntityType.PILLAGER, 4, 2), new Entry(EntityType.CAVE_SPIDER, 2, 2),
            new Entry(EntityType.WITHER_SKELETON, 3, 3), new Entry(EntityType.EVOKER, 1, 3.5),
            new Entry(EntityType.RAVAGER, 1, 4));

    private static final Item[][] ARMOUR = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}};
    private static final Item[] SWORDS = {Items.STONE_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static int wavesFor(double threat) {
        return Mth.clamp(1 + (int) Math.round(threat * 0.9), 1, 5);
    }

    static int waveSize(double threat, int wave, int players) {
        double n = (3 + threat * 2 + wave) * (1 + 0.4 * Math.max(0, players - 1));
        return Mth.clamp((int) Math.round(n), 2, 24);
    }

    private static EntityType<? extends Mob> pick(RandomSource r, double threat) {
        int total = 0;
        for (Entry e : POOL) if (threat >= e.minThreat) total += e.weight;
        int roll = r.nextInt(Math.max(1, total));
        for (Entry e : POOL) {
            if (threat < e.minThreat) continue;
            roll -= e.weight;
            if (roll < 0) return e.type;
        }
        return EntityType.ZOMBIE;
    }

    /** A free spot 30-40 blocks from the centre, near its height: solid ground, two blocks of air. */
    static BlockPos findSpawn(ServerLevel level, BlockPos centre, int rMin, int rMax, RandomSource r) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = rMin + r.nextDouble() * Math.max(0, rMax - rMin);
            int x = centre.getX() + (int) Math.round(Math.cos(a) * d);
            int z = centre.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, centre.getY(), z))) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(surface - centre.getY()) <= 12 && standable(level, new BlockPos(x, surface, z))) return new BlockPos(x, surface, z);
            for (int dy = 8; dy >= -8; dy--) {
                BlockPos p = new BlockPos(x, centre.getY() + dy, z);
                if (standable(level, p)) return p;
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        if (!below.isFaceSturdy(level, feet.below(), Direction.UP)) return false;
        for (BlockPos p : new BlockPos[]{feet, feet.above()}) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet) < 8 || level.random.nextInt(3) == 0;
    }

    /** Spawns one wave. Returns the UUIDs of everything spawned. */
    static List<Entity> spawnWave(ServerLevel level, Siege siege, ServerPlayer anchor, int wave, int count, boolean lastWave) {
        RandomSource r = level.random;
        double threat = siege.threat;
        List<Entity> out = new ArrayList<>();
        int rMin = dev.empengines.silentraids.Config.MIN_RADIUS.get(), rMax = Math.max(rMin, dev.empengines.silentraids.Config.MAX_RADIUS.get());

        // Invaders: one in the last wave from Ascent, two at Pinnacle
        int invaders = threat >= 4 ? 2 : threat >= 2 ? 1 : 0;
        if (lastWave && ApothBridge.loaded()) {
            for (int i = 0; i < invaders; i++) {
                BlockPos pos = findSpawn(level, anchor.blockPosition(), rMin, rMax, r);
                if (pos == null) continue;
                Mob boss = ApothBridge.createInvader(level, pos, anchor);
                if (boss == null) continue;
                boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360, 0);
                List<Entity> all = new ArrayList<>();
                boss.getSelfAndPassengers().forEach(all::add);
                for (Entity e : all) if (e instanceof Mob m) prepare(m, siege, "invader", threat, true);
                level.addFreshEntityWithPassengers(boss);
                out.addAll(all);
            }
        }

        double eliteChance = threat < 1 ? 0 : Math.min(0.2, 0.04 + 0.03 * (threat - 1));
        for (int i = 0; i < count; i++) {
            BlockPos pos = findSpawn(level, anchor.blockPosition(), rMin, rMax, r);
            if (pos == null) continue;
            EntityType<? extends Mob> type = pick(r, threat);
            if (type == EntityType.RAVAGER && !lastWave) type = EntityType.VINDICATOR;
            Mob mob = type.create(level);
            if (mob == null) continue;
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360, 0);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            equip(mob, threat, r, level);
            buff(mob, threat);
            String kind = "normal";
            // Every blood moon brings at least one Elite: the first raider of the final wave if none came before
            boolean forceElite = lastWave && siege.elites == 0;
            if (forceElite || r.nextDouble() < eliteChance) {
                siege.elites++;
                kind = "elite";
                if (!ApothBridge.makeElite(level, mob, anchor)) ownElite(mob);
            }
            prepare(mob, siege, kind, threat, true);
            level.addFreshEntityWithPassengers(mob);
            out.add(mob);
        }
        return out;
    }

    private static void prepare(Mob mob, Siege siege, String kind, double threat, boolean noDrops) {
        mob.getPersistentData().putString(RAIDER, siege.id.toString());
        mob.getPersistentData().putString(KIND, kind);
        mob.getPersistentData().putDouble(THREAT, threat);
        mob.setPersistenceRequired();
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null && follow.getBaseValue() < 64) follow.setBaseValue(64);
        if (noDrops) for (EquipmentSlot s : EquipmentSlot.values()) mob.setDropChance(s, 0f);
        if (mob instanceof PathfinderMob) mob.goalSelector.addGoal(1, new BreachGoal(mob));
    }

    /** Armour (visible on zombies and skeletons) gets more likely and better with threat; enchanted from Summit. */
    private static void equip(Mob mob, double threat, RandomSource r, ServerLevel level) {
        if (!(mob instanceof Zombie) && !(mob instanceof AbstractSkeleton)) return;
        double pieceChance = Math.min(1, 0.25 + 0.15 * threat);
        for (int i = 0; i < 4; i++) {
            if (r.nextDouble() >= pieceChance) continue;
            int material = Mth.clamp((int) Math.floor(threat + (r.nextDouble() - 0.5)), 0, 4);
            ItemStack piece = new ItemStack(ARMOUR[material][i]);
            if (threat >= 3) piece = EnchantmentHelper.enchantItem(r, piece, (int) (5 + threat * 5), level.registryAccess(), Optional.empty());
            mob.setItemSlot(ARMOUR_SLOTS[i], piece);
        }
        if (mob instanceof Zombie && r.nextDouble() < 0.2 + 0.1 * threat) {
            ItemStack sword = new ItemStack(SWORDS[Mth.clamp((int) Math.floor(threat), 0, 4)]);
            if (threat >= 3) sword = EnchantmentHelper.enchantItem(r, sword, (int) (5 + threat * 5), level.registryAccess(), Optional.empty());
            mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
        }
        if (mob instanceof AbstractSkeleton && threat >= 3 && mob.getMainHandItem().is(Items.BOW)) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(r, new ItemStack(Items.BOW), (int) (5 + threat * 5), level.registryAccess(), Optional.empty()));
        }
    }

    private static void buff(Mob mob, double threat) {
        add(mob, Attributes.MAX_HEALTH, "threat_health", 0.15 * threat);
        add(mob, Attributes.ATTACK_DAMAGE, "threat_damage", 0.10 * threat);
        mob.setHealth(mob.getMaxHealth());
    }

    /** Our own Elite, for mob types Apotheosis has no Elite for. */
    private static void ownElite(Mob mob) {
        add(mob, Attributes.MAX_HEALTH, "elite_health", 1.5);
        add(mob, Attributes.ATTACK_DAMAGE, "elite_damage", 0.5);
        add(mob, Attributes.MOVEMENT_SPEED, "elite_speed", 0.15);
        add(mob, Attributes.KNOCKBACK_RESISTANCE, "elite_knockback", 0.5);
        mob.setHealth(mob.getMaxHealth());
        if (!ApothBridge.randomName(mob)) {
            mob.setCustomName(Component.literal("Elite ").append(mob.getType().getDescription()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            mob.setCustomNameVisible(true);
        }
    }

    private static void add(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String name, double amount) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null || amount <= 0) return;
        inst.addOrReplacePermanentModifier(new AttributeModifier(SilentRaids.id(name), amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    static boolean isRaider(Entity e) {
        return e.getPersistentData().contains(RAIDER);
    }

    static String kind(Entity e) {
        return e.getPersistentData().getString(KIND);
    }

    /** True if the player is near enough to the raider for it to chase them. */
    static boolean inReach(Player p, Mob m) {
        return p.level() == m.level() && p.distanceToSqr(m) < 96 * 96;
    }

    static AABB around(Player p, double r) {
        return p.getBoundingBox().inflate(r);
    }

    private RaidMobs() {}
}
