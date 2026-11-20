package dev.empengines.silentraids.gear;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.empengines.silentraids.SilentRaids;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.crafting.recipe.ConversionRecipe;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.util.GearData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla tools, weapons and armour turn into their Silent Gear counterparts as soon as they're in a player's
 * inventory (picked up, looted, traded, taken from a chest). The conversions are Silent Gear's own
 * ("silentgear:conversion" recipes), kept in data/silentraids/gear_conversion/ instead of as crafting
 * recipes, so the crafting grid and recipe viewers don't list them. Enchantments, name and lore come along;
 * wear carries over as the same fraction of durability.
 */
public final class VanillaToSilent {
    private static volatile Map<Item, Conversion> conversions = Map.of();

    private record Conversion(GearItem gear, Collection<PartInstance> parts) {}

    private VanillaToSilent() {}

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent e) {
        e.addListener(new Loader(e.getRegistryAccess()));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0 || p.isCreative() || p.isSpectator()) return;
        Map<Item, Conversion> map = conversions;
        if (map.isEmpty()) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Conversion c = map.get(stack.getItem());
            if (c == null) continue;
            ItemStack out = convert(stack, c, p);
            if (!out.isEmpty()) inv.setItem(i, out);
        }
    }

    private static ItemStack convert(ItemStack from, Conversion c, ServerPlayer p) {
        try {
            ItemStack out = c.gear().construct(c.parts());
            GearData.recalculateGearData(out, p);
            copy(from, out, DataComponents.ENCHANTMENTS);
            copy(from, out, DataComponents.CUSTOM_NAME);
            copy(from, out, DataComponents.LORE);
            copy(from, out, DataComponents.REPAIR_COST);
            if (from.isDamaged() && from.getMaxDamage() > 0 && out.getMaxDamage() > 0) {
                float worn = from.getDamageValue() / (float) from.getMaxDamage();
                out.setDamageValue(Mth.clamp(Math.round(worn * out.getMaxDamage()), 0, out.getMaxDamage() - 1));
            }
            return out;
        } catch (Throwable t) {
            SilentRaids.LOGGER.warn("Couldn't convert {} to Silent Gear", from, t);
            return ItemStack.EMPTY;
        }
    }

    private static <T> void copy(ItemStack from, ItemStack to, net.minecraft.core.component.DataComponentType<T> type) {
        T value = from.get(type);
        if (value != null) to.set(type, value);
    }

    /** Reads data/<ns>/gear_conversion/*.json: Silent Gear conversion recipes. */
    private static final class Loader extends SimpleJsonResourceReloadListener {
        private final HolderLookup.Provider registries;

        Loader(HolderLookup.Provider registries) {
            super(new Gson(), "gear_conversion");
            this.registries = registries;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<Item, Conversion> map = new HashMap<>();
            Method getParts;
            Field item;
            try {
                getParts = ConversionRecipe.class.getDeclaredMethod("getParts");
                getParts.setAccessible(true);
                item = ConversionRecipe.class.getDeclaredField("item");
                item.setAccessible(true);
            } catch (ReflectiveOperationException ex) {
                SilentRaids.LOGGER.error("Silent Gear's ConversionRecipe has changed; vanilla gear won't be converted", ex);
                conversions = Map.of();
                return;
            }
            for (Map.Entry<ResourceLocation, JsonElement> f : files.entrySet()) {
                try {
                    if (!f.getValue().isJsonObject()) continue;
                    RecipeHolder<?> holder = Parser.parse(f.getKey(), f.getValue().getAsJsonObject(), registries);
                    if (holder == null || !(holder.value() instanceof ConversionRecipe recipe)) continue;
                    @SuppressWarnings("unchecked")
                    Collection<PartInstance> parts = (Collection<PartInstance>) getParts.invoke(recipe);
                    GearItem gear = (GearItem) item.get(recipe);
                    Conversion c = new Conversion(gear, parts);
                    for (Ingredient ing : ((Recipe<?>) recipe).getIngredients()) {
                        for (ItemStack s : ing.getItems()) map.put(s.getItem(), c);
                    }
                } catch (Throwable t) {
                    SilentRaids.LOGGER.warn("Bad gear conversion {}", f.getKey(), t);
                }
            }
            conversions = Map.copyOf(map);
            SilentRaids.LOGGER.info("{} vanilla items convert to Silent Gear", map.size());
        }
    }

    /** Never instantiated: only here to reach RecipeManager's protected recipe parser. */
    private static final class Parser extends RecipeManager {
        private Parser() { super(null); }

        static RecipeHolder<?> parse(ResourceLocation id, com.google.gson.JsonObject json, HolderLookup.Provider registries) {
            return fromJson(id, json, registries);
        }
    }
}
