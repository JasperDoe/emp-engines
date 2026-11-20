package dev.empengines.researchenchanting.enchant;

import dev.empengines.researchenchanting.Registry;
import dev.empengines.researchenchanting.loot.NotesLootModifier;
import dev.empengines.researchenchanting.mixin.MobAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

import java.util.List;

/**
 * The code half of the new and reworked enchantments, plus the cleanup that keeps Unbreaking, Mending and
 * enchanted books out of the game:
 * <ul>
 * <li>Rending: each melee hit strips 2 / 3 / 4 armour points for 6 s, stacking to 3 hits.</li>
 * <li>Smite (reworked): 5 / 10 / 15% a hit calls lightning on the target, 8 s cooldown; the bolt never lights blocks.</li>
 * <li>Boon of Arthropods (reworked Bane): kills summon 1 / 2 / 3 silverfish that hunt monsters for 20 s, then crumble.
 *     They never burrow into blocks or wake other silverfish.</li>
 * <li>Hookshot: sneak + use fires a grapple to the block aimed at, 10 / 16 / 22 blocks, 4 s cooldown, no fall damage after.</li>
 * </ul>
 */
public final class EnchantEvents {
    private static final String SMITE_CD = "researchenchanting:smite_ready";
    private static final String BOON_UNTIL = "researchenchanting:boon_until";
    private static final String HOOK_GRACE = "researchenchanting:hook_grace";

    // ---- on hit ----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void hit(LivingDamageEvent.Post e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker) return;
        ItemStack weapon = src.getWeaponItem() != null ? src.getWeaponItem() : attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        int rend = Enchants.level(attacker, weapon, Enchants.RENDING);
        if (rend > 0) {
            int per = rend + 1;
            MobEffectInstance had = target.getEffect(Registry.RENDED);
            int points = Math.min(per * 3, (had == null ? 0 : had.getAmplifier() + 1) + per);
            target.addEffect(new MobEffectInstance(Registry.RENDED, 120, points - 1, false, true, true), attacker);
        }

        int smite = Enchants.level(attacker, weapon, Enchants.SMITE);
        if (smite > 0 && target.level() instanceof ServerLevel level) {
            CompoundTag pd = attacker.getPersistentData();
            long now = level.getGameTime();
            if (now >= pd.getLong(SMITE_CD) && level.random.nextFloat() < 0.05f * smite) {
                pd.putLong(SMITE_CD, now + 160);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(target.getX(), target.getY(), target.getZ());
                    bolt.setVisualOnly(true);   // no fire on wooden decks, no charged creepers
                    level.addFreshEntity(bolt);
                }
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().lightningBolt(), 5.0f);
            }
        }
    }

    // ---- on kill ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void kill(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || isSummon(victim)) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof LivingEntity killer) || src.getDirectEntity() != killer) return;
        ItemStack weapon = src.getWeaponItem() != null ? src.getWeaponItem() : killer.getMainHandItem();
        int boon = Enchants.level(killer, weapon, Enchants.BOON);
        for (int n = 0; n < boon; n++) {
            Silverfish fish = EntityType.SILVERFISH.create(level);
            if (fish == null) continue;
            fish.moveTo(victim.getX() + (level.random.nextDouble() - 0.5), victim.getY(), victim.getZ() + (level.random.nextDouble() - 0.5),
                    level.random.nextFloat() * 360f, 0f);
            fish.getPersistentData().putLong(BOON_UNTIL, level.getGameTime() + 400);
            tame(fish);
            level.addFreshEntity(fish);
            level.sendParticles(ParticleTypes.POOF, fish.getX(), fish.getY() + 0.2, fish.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
        }
        if (boon > 0) level.playSound(null, victim.blockPosition(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static boolean isSummon(Entity e) {
        return e instanceof Silverfish && e.getPersistentData().contains(BOON_UNTIL);
    }

    /** Monsters only, never burrowing, never waking infested blocks. */
    private static void tame(Silverfish fish) {
        MobAccessor m = (MobAccessor) fish;
        m.researchenchanting$goals().removeAllGoals(g -> true);
        m.researchenchanting$targets().removeAllGoals(g -> true);
        m.researchenchanting$goals().addGoal(1, new FloatGoal(fish));
        m.researchenchanting$goals().addGoal(2, new MeleeAttackGoal(fish, 1.1, true));
        m.researchenchanting$targets().addGoal(1, new NearestAttackableTargetGoal<>(fish, Monster.class, 5, false, false,
                t -> !isSummon(t) && !(t instanceof Creeper)));
    }

    @SubscribeEvent
    public static void summonTick(EntityTickEvent.Pre e) {
        Entity ent = e.getEntity();
        if (!isSummon(ent) || !(ent.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() < ent.getPersistentData().getLong(BOON_UNTIL)) return;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBBLESTONE)), ent.getX(), ent.getY() + 0.2, ent.getZ(), 8, 0.15, 0.1, 0.15, 0.05);
        level.playSound(null, ent.blockPosition(), SoundEvents.SILVERFISH_DEATH, SoundSource.NEUTRAL, 0.6f, 1.3f);
        ent.discard();
    }

    /** Summons don't survive a reload (their goals would be vanilla again). */
    @SubscribeEvent
    public static void summonLoad(EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() && isSummon(e.getEntity())) e.setCanceled(true);
    }

    // ---- Hookshot ----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void hookshot(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        ItemStack stack = e.getItemStack();
        if (!p.isShiftKeyDown()) return;
        int lvl = Enchants.level(p, stack, Enchants.HOOKSHOT);
        if (lvl <= 0) return;
        e.setCanceled(true);
        if (p.getCooldowns().isOnCooldown(stack.getItem())) { e.setCancellationResult(InteractionResult.FAIL); return; }
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!(p.level() instanceof ServerLevel level)) return;
        double range = 4 + 6 * lvl;
        HitResult hit = p.pick(range, 1.0f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            level.playSound(null, p.blockPosition(), SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 0.6f, 1.4f);
            p.getCooldowns().addCooldown(stack.getItem(), 20);
            return;
        }
        Vec3 target = bh.getLocation();
        Vec3 eye = p.getEyePosition();
        Vec3 delta = target.subtract(p.position());
        double dist = delta.length();
        Vec3 v = delta.normalize().scale(Math.min(3.0, 0.6 + dist * 0.12)).add(0, 0.2 + Math.min(0.4, dist * 0.02), 0);
        p.setDeltaMovement(v);
        p.hurtMarked = true;
        p.resetFallDistance();
        p.getPersistentData().putLong(HOOK_GRACE, level.getGameTime() + 80);
        Vec3 step = target.subtract(eye);
        int points = (int) Math.ceil(step.length() * 2);
        ItemParticleOption chain = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.CHAIN));
        for (int i = 1; i <= points; i++) {
            Vec3 at = eye.add(step.scale(i / (double) points));
            level.sendParticles(chain, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, bh.getBlockPos(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.8f, 0.8f);
        p.getCooldowns().addCooldown(stack.getItem(), 80);
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        LivingEntity ent = e.getEntity();
        if (ent.getPersistentData().getLong(HOOK_GRACE) > ent.level().getGameTime()) e.setCanceled(true);
    }

    // ---- Unbreaking and Mending stripped ------------------------------------------------------------------------

    @SubscribeEvent
    public static void strip(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (p.level().isClientSide || p.tickCount % 100 != 0) return;
        var reg = p.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> unb = reg.getHolder(Enchantments.UNBREAKING).orElse(null);
        Holder<Enchantment> mend = reg.getHolder(Enchantments.MENDING).orElse(null);
        if (unb == null || mend == null) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (EnchantmentHelper.getItemEnchantmentLevel(unb, s) > 0 || EnchantmentHelper.getItemEnchantmentLevel(mend, s) > 0) {
                EnchantmentHelper.updateEnchantments(s, m -> m.removeIf(Enchants::isRemoved));
            }
        }
    }

    // ---- trades: enchanted books become Research Notes ---------------------------------------------------------

    @SubscribeEvent
    public static void trades(VillagerTradesEvent e) {
        e.getTrades().values().forEach(EnchantEvents::wrapAll);
    }

    @SubscribeEvent
    public static void wanderer(WandererTradesEvent e) {
        wrapAll(e.getGenericTrades());
        wrapAll(e.getRareTrades());
    }

    private static void wrapAll(List<VillagerTrades.ItemListing> list) {
        list.replaceAll(in -> (trader, random) -> {
            MerchantOffer o = in.getOffer(trader, random);
            if (o == null || !o.getResult().is(Items.ENCHANTED_BOOK)) return o;
            return new MerchantOffer(o.getItemCostA(), o.getItemCostB(), NotesLootModifier.convert(o.getResult()),
                    o.getMaxUses(), o.getXp(), o.getPriceMultiplier());
        });
    }

    private EnchantEvents() {}
}
