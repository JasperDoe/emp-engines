package dev.empengines.silentraids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Everything that touches Apotheosis, by reflection: the mod is optional, and this way a change in one of
 * its method signatures only switches the matching feature off (logged once) instead of crashing the game.
 */
public final class ApothBridge {
    public static final ResourceLocation SOCKETS = ResourceLocation.fromNamespaceAndPath("apotheosis", "sockets");
    public static final ResourceLocation AFFIXES = ResourceLocation.fromNamespaceAndPath("apotheosis", "affixes");
    public static final ResourceLocation RARITY = ResourceLocation.fromNamespaceAndPath("apotheosis", "rarity");
    public static final ResourceLocation SOCKETED_GEMS = ResourceLocation.fromNamespaceAndPath("apotheosis", "socketed_gems");
    public static final ResourceLocation GEM = ResourceLocation.fromNamespaceAndPath("apotheosis", "gem");
    public static final ResourceLocation SIGIL = ResourceLocation.fromNamespaceAndPath("apotheosis", "sigil_of_socketing");
    /** Persistent-data keys Apotheosis puts on its bosses. */
    public static final String BOSS_KEY = "apoth.boss", MINIBOSS_KEY = "apoth.miniboss";

    private static Boolean loaded;
    private static boolean failed;
    private static Class<?> worldTier, genContext;
    private static Method setTier, forPlayerAtPos, randomGem, invaderRandom, createBoss, eliteRandom, transformElite, entityName;
    private static Object invaders, elites;

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("apotheosis");
        return loaded;
    }

    private static boolean init() {
        if (!loaded() || failed) return false;
        if (worldTier != null) return true;
        try {
            worldTier = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
            genContext = Class.forName("dev.shadowsoffire.apotheosis.tiers.GenContext");
            setTier = worldTier.getMethod("setTier", Player.class, worldTier);
            forPlayerAtPos = genContext.getMethod("forPlayerAtPos", RandomSource.class, Player.class, BlockPos.class);
            randomGem = Class.forName("dev.shadowsoffire.apotheosis.socket.gem.GemRegistry").getMethod("createRandomGemStack", genContext);

            Class<?> invReg = Class.forName("dev.shadowsoffire.apotheosis.mobs.registries.InvaderRegistry");
            invaders = invReg.getField("INSTANCE").get(null);
            invaderRandom = invReg.getMethod("getRandomItem", genContext);
            createBoss = Class.forName("dev.shadowsoffire.apotheosis.mobs.types.Invader").getMethod("createBoss", ServerLevelAccessor.class, BlockPos.class, genContext);

            Class<?> eliteReg = Class.forName("dev.shadowsoffire.apotheosis.mobs.registries.EliteRegistry");
            elites = eliteReg.getField("INSTANCE").get(null);
            eliteRandom = eliteReg.getMethod("getRandomItem", genContext, Entity.class);
            transformElite = Class.forName("dev.shadowsoffire.apotheosis.mobs.types.Elite").getMethod("transformMiniboss", ServerLevelAccessor.class, Mob.class, genContext);
            try {
                entityName = Class.forName("dev.shadowsoffire.apotheosis.util.NameHelper").getMethod("setEntityName", RandomSource.class, Mob.class);
            } catch (Throwable t) {
                SilentRaids.LOGGER.warn("Silent Apotheosis Raids: no Apotheosis name generator, Elites keep plain names", t);
            }
            return true;
        } catch (Throwable t) {
            failed = true;
            SilentRaids.LOGGER.error("Silent Apotheosis Raids: couldn't hook into Apotheosis (tiers, gems and Invaders are off)", t);
            return false;
        }
    }

    /** Apotheosis world tier names, lowest first. */
    public static final String[] TIER_NAMES = {"haven", "frontier", "ascent", "summit", "pinnacle"};

    public static void setTier(Player player, int tier) {
        if (!init()) return;
        try {
            Object value = null;
            for (Object c : worldTier.getEnumConstants()) if (((Enum<?>) c).ordinal() == tier) value = c;
            if (value != null) setTier.invoke(null, player, value);
        } catch (Throwable t) {
            SilentRaids.LOGGER.warn("Couldn't set the Apotheosis world tier of {}", player.getName().getString(), t);
        }
    }

    private static Object ctx(Player player, BlockPos pos) throws Exception {
        return forPlayerAtPos.invoke(null, player.getRandom(), player, pos);
    }

    /** A random gem for this player's tier, or empty. */
    public static ItemStack randomGem(Player player) {
        if (!init()) return ItemStack.EMPTY;
        try {
            Object stack = randomGem.invoke(null, ctx(player, player.blockPosition()));
            return stack instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            SilentRaids.LOGGER.warn("Couldn't roll an Apotheosis gem", t);
            return ItemStack.EMPTY;
        }
    }

    /** A random Apotheosis Invader for the player's tier, created at pos but not added to the level. */
    public static Mob createInvader(ServerLevel level, BlockPos pos, Player player) {
        if (!init()) return null;
        try {
            Object c = ctx(player, pos);
            Object inv = invaderRandom.invoke(invaders, c);
            if (inv == null) return null;
            return (Mob) createBoss.invoke(inv, level, pos, c);
        } catch (Throwable t) {
            SilentRaids.LOGGER.warn("Couldn't create an Apotheosis Invader", t);
            return null;
        }
    }

    /** Turns the mob into a matching Apotheosis Elite. False if none fits this mob type. */
    public static boolean makeElite(ServerLevel level, Mob mob, Player player) {
        if (!init()) return false;
        try {
            Object c = ctx(player, mob.blockPosition());
            Object elite = eliteRandom.invoke(elites, c, mob);
            if (elite == null) return false;
            transformElite.invoke(elite, level, mob, c);
            randomName(mob);
            return true;
        } catch (Throwable t) {
            SilentRaids.LOGGER.warn("Couldn't create an Apotheosis Elite", t);
            return false;
        }
    }

    /**
     * A random Apotheosis name (the kind Invaders get, e.g. "Lord Grimbold the Unyielding") in the colour of the
     * mob's current name (gold if none). False if the generator isn't there.
     */
    public static boolean randomName(Mob mob) {
        if (!init() || entityName == null) return false;
        try {
            net.minecraft.network.chat.Style style = mob.getCustomName() != null ? mob.getCustomName().getStyle()
                    : net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.GOLD);
            Object name = entityName.invoke(null, mob.getRandom(), mob);
            if (!(name instanceof String n) || n.isBlank()) return false;
            mob.setCustomName(net.minecraft.network.chat.Component.literal(n).withStyle(style));
            mob.setCustomNameVisible(true);
            return true;
        } catch (Throwable t) {
            SilentRaids.LOGGER.warn("Couldn't name an Elite", t);
            return false;
        }
    }

    public static ItemStack sigil(int count) {
        Item item = BuiltInRegistries.ITEM.get(SIGIL);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<Integer> socketsComponent() {
        return (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(SOCKETS);
    }

    public static int sockets(ItemStack stack) {
        DataComponentType<Integer> type = socketsComponent();
        return type == null ? 0 : stack.getOrDefault(type, 0);
    }

    public static void setSockets(ItemStack stack, int sockets) {
        DataComponentType<Integer> type = socketsComponent();
        if (type != null) stack.set(type, Math.max(0, Math.min(16, sockets)));
    }

    /** True if the item has at least one gem socketed. */
    public static boolean hasGems(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SOCKETED_GEMS);
        return type != null && stack.get(type) instanceof ItemContainerContents gems && gems.nonEmptyItems().iterator().hasNext();
    }

    /** True for Apotheosis affix gear (anything with a rarity or affixes that isn't a gem). */
    public static boolean isAffixItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        DataComponentType<?> gem = BuiltInRegistries.DATA_COMPONENT_TYPE.get(GEM);
        if (gem != null && stack.has(gem)) return false;
        DataComponentType<?> affixes = BuiltInRegistries.DATA_COMPONENT_TYPE.get(AFFIXES);
        DataComponentType<?> rarity = BuiltInRegistries.DATA_COMPONENT_TYPE.get(RARITY);
        return (affixes != null && stack.has(affixes)) || (rarity != null && stack.has(rarity));
    }

    private ApothBridge() {}
}
