package dev.empengines.engines.raid;

import dev.empengines.engines.launch.SableBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The raider ship's witch. She stays on her post, and once a boarding rope is tied, every 7 seconds
 * throws a splash potion of Mining Fatigue I (20 s) at whoever is breaking an anchor (or failing that,
 * anyone standing within 4 blocks of one), up to 16 blocks away. It splashes whoever is beside them too.
 * Milk clears it. She still drinks her own healing potions; her usual harming potions are taken away.
 */
public class WitchGoal extends Goal {
    static final int GAP = 140, RANGE = 16, BESIDE = 4, DURATION = 20 * 20, SEEN_BREAKING = 20;

    private final Raid.RaiderMob raider;
    private final Witch witch;
    private int cooldown = 60, repath;

    private WitchGoal(Witch witch, Raid.RaiderMob raider) {
        this.witch = witch;
        this.raider = raider;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Swaps a witch's own attack and targeting for this. */
    static void install(Witch witch, Raid.RaiderMob raider) {
        witch.goalSelector.removeAllGoals(g -> g instanceof RangedAttackGoal || g instanceof WaterAvoidingRandomStrollGoal);
        witch.targetSelector.removeAllGoals(g -> true);
        witch.goalSelector.addGoal(0, new WitchGoal(witch, raider));
    }

    @Override public boolean canUse() { return raider.raid().phase != Raid.Phase.DONE; }
    @Override public boolean canContinueToUse() { return canUse(); }

    @Override
    public void tick() {
        Raid raid = raider.raid();
        Vec3 post = SableBridge.toWorld(raid.raider.ship, Vec3.atBottomCenterOf(raider.post));
        if (witch.position().distanceTo(post) > 1.2) {
            if (--repath <= 0 || witch.getNavigation().isDone()) {
                repath = 20;
                Vec3 p = Vec3.atBottomCenterOf(raider.post);
                witch.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
            }
        } else witch.getNavigation().stop();

        ServerPlayer target = pick(raid);
        if (target != null) witch.getLookControl().setLookAt(target, 30, 30);
        if (--cooldown > 0 || target == null || witch.isDrinkingPotion() || !raid.anyRopeUp()) return;
        cooldown = GAP;
        throwAt(target);
    }

    /** Whoever is breaking an anchor; failing that, the nearest player next to one. */
    private ServerPlayer pick(Raid raid) {
        ServerLevel level = (ServerLevel) witch.level();
        long now = level.getGameTime();
        ServerPlayer best = null;
        double bestD = RANGE * RANGE;
        for (Map.Entry<UUID, Long> e : raid.breaking.entrySet()) {
            if (now - e.getValue() > SEEN_BREAKING) continue;
            if (level.getPlayerByUUID(e.getKey()) instanceof ServerPlayer p && usable(p)) {
                double d = p.distanceToSqr(witch);
                if (d < bestD) { bestD = d; best = p; }
            }
        }
        if (best != null) return best;
        List<Vec3> anchors = raid.anchorPositions();
        for (ServerPlayer p : level.players()) {
            if (!usable(p)) continue;
            double d = p.distanceToSqr(witch);
            if (d >= bestD) continue;
            for (Vec3 a : anchors) if (p.position().distanceTo(a) <= BESIDE) { bestD = d; best = p; break; }
        }
        return best;
    }

    private static boolean usable(ServerPlayer p) { return p.isAlive() && !p.isSpectator() && !p.isCreative(); }

    private void throwAt(ServerPlayer target) {
        ItemStack potion = new ItemStack(Items.SPLASH_POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(0x4A4217),
                List.of(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, DURATION, 0))));
        ThrownPotion thrown = new ThrownPotion(witch.level(), witch);
        thrown.setItem(potion);
        double dx = target.getX() - witch.getX(), dz = target.getZ() - witch.getZ();
        double dy = target.getEyeY() - 1.1 - witch.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        thrown.setXRot(thrown.getXRot() + 20f);
        thrown.shoot(dx, dy + flat * 0.2, dz, 0.75f, 2f);
        // Thrown from a moving ship: it keeps the ship's speed.
        Vec3 carry = RaiderFlight.pointVelocity(raider.raid().raider.ship, witch.position()).scale(1 / 20.0);
        thrown.setDeltaMovement(thrown.getDeltaMovement().add(carry));
        witch.swing(InteractionHand.MAIN_HAND);
        witch.level().playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1f, 0.8f + witch.getRandom().nextFloat() * 0.4f);
        witch.level().addFreshEntity(thrown);
    }

    @Override public void stop() { witch.getNavigation().stop(); }
}
