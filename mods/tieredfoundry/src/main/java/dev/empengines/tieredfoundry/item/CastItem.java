package dev.empengines.tieredfoundry.item;

import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.recipe.PartCastingRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * A casting table mould. The shape is the {@code tieredfoundry:pattern} component: "ingot", "nugget", "plate", "gem",
 * or "item:&lt;id&gt;" for a Silent Gear part. Gold casts are made by pouring molten gold over such an item on a
 * casting table and last forever; sand casts are made by clicking sand onto a table holding the item (the item is
 * given back) and break after one pour.
 */
public final class CastItem extends Item {
    public static final List<String> PATTERNS = List.of("ingot", "nugget", "plate", "gem");
    private static final List<TagKey<Item>> TAGS = List.of(tag("ingots"), tag("nuggets"), tag("plates"), tag("gems"));
    /** Silent Gear parts with a cast, in the order of the item model overrides (tools/foundry/gen.py, SG_PARTS). */
    public static final List<String> PARTS = List.of(
            "arrow_heads", "axe_head", "boot_plates", "bow_limbs", "bracelet_band", "chestplate_plates", "crossbow_limbs",
            "dagger_blade", "excavator_head", "fishing_reel_and_hook", "hammer_head", "helmet_plates", "hoe_head",
            "katana_blade", "knife_blade", "legging_plates", "machete_blade", "mattock_head", "necklace_chain", "paxel_head",
            "pickaxe_head", "prospector_hammer_head", "ring_shank", "saw_blade", "shear_blades", "shield_plate",
            "shovel_head", "sickle_blade", "slingshot_limbs", "spear_tip", "sword_blade", "tip", "rod", "trident_prongs");
    public static final int GOLD_COST = 180;

    public final boolean sand;

    public CastItem(boolean sand, Properties p) {
        super(p);
        this.sand = sand;
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    public static ItemStack of(Item cast, String pattern) {
        ItemStack s = new ItemStack(cast);
        s.set(Registry.PATTERN.get(), pattern);
        return s;
    }

    public static String partPattern(String part) {
        return "item:silentgear:" + part;
    }

    /** Every pattern a cast can have here, for the creative tab and EMI. */
    public static List<String> creativePatterns() {
        List<String> out = new ArrayList<>(PATTERNS);
        if (ModList.get().isLoaded("silentgear")) for (String p : PARTS) out.add(partPattern(p));
        return out;
    }

    /** The tag-based shape an item stands for (an ingot makes an ingot cast), or null. */
    public static String patternFor(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof CastItem) return null;
        for (int i = 0; i < TAGS.size(); i++) if (stack.is(TAGS.get(i))) return PATTERNS.get(i);
        return null;
    }

    /** As above, also knowing Silent Gear parts that have a part casting recipe. */
    public static String patternFor(Level level, ItemStack stack) {
        String p = patternFor(stack);
        if (p != null || stack.isEmpty() || stack.getItem() instanceof CastItem || level == null) return p;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (RecipeHolder<PartCastingRecipe> h : level.getRecipeManager().getAllRecipesFor(Registry.PART_CASTING.get())) {
            if (h.value().part().equals(id)) return h.value().pattern();
        }
        return null;
    }

    /** The shape of a cast stack, or null if the stack is not a cast. */
    public static String patternOf(ItemStack stack) {
        if (!(stack.getItem() instanceof CastItem)) return null;
        return stack.getOrDefault(Registry.PATTERN.get(), "ingot");
    }

    /** 0, 1, 2 ... for the item model overrides: the base shapes, then the Silent Gear parts. */
    public static float modelIndex(ItemStack stack) {
        String p = stack.getOrDefault(Registry.PATTERN.get(), "ingot");
        int i = PATTERNS.indexOf(p);
        if (i >= 0) return i;
        if (p.startsWith("item:silentgear:")) {
            int j = PARTS.indexOf(p.substring("item:silentgear:".length()));
            if (j >= 0) return PATTERNS.size() + j;
        }
        return 0;
    }

    @Override
    public Component getName(ItemStack stack) {
        String p = stack.getOrDefault(Registry.PATTERN.get(), "ingot");
        if (p.startsWith("item:")) {
            Item part = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(p.substring(5)));
            Component partName = part == Items.AIR ? Component.literal(p.substring(5)) : Component.translatable(part.getDescriptionId());
            return Component.translatable(getDescriptionId() + ".part", partName);
        }
        return Component.translatable(getDescriptionId() + "." + p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(sand ? "tooltip.tieredfoundry.sand_cast" : "tooltip.tieredfoundry.gold_cast").withStyle(ChatFormatting.GRAY));
    }
}
