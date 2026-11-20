package dev.empengines.silentraids.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.silentchaos512.gear.api.material.IMaterialCategory;
import net.silentchaos512.gear.core.component.GearConstructionData;
import net.silentchaos512.gear.gear.material.MaterialCategories;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.GearHelper;

import java.util.List;

/**
 * Silent Gear armour of iron tier and below is drawn with the vanilla armour textures instead of Silent Gear's
 * one full-cover plate texture: iron and gold look exactly like vanilla iron and gold armour, cloth (leather, silk,
 * membrane) like leather armour in the material's colour, turtle like the turtle shell, and every other iron-tier
 * or lower material (copper, bronze, steel, stone, wood...) like iron armour tinted to the material's colour.
 * Armour whose look comes from a material above iron (cast-only, or a cast coating) keeps Silent Gear's look.
 *
 * Item models too (inventory, hand, item frames): a Silent Gear pickaxe, axe, shovel, hoe, sword, helmet,
 * chestplate, leggings or boots made of wood, stone, iron or gold (armour: leather, iron, gold; turtle helmet)
 * uses the matching vanilla item's model. Other materials keep Silent Gear's tinted model, which is the only way
 * to tell them apart.
 */
public final class VanillaArmorLook {
    private static final List<IMaterialCategory> CAST_ONLY = List.of(MaterialCategories.get("emp_cast_only"));
    private static final List<IMaterialCategory> CLOTH = List.of(MaterialCategories.CLOTH);

    private enum Look { IRON, IRON_TINTED, GOLD, LEATHER, TURTLE }

    private VanillaArmorLook() {}

    private static Look look(ItemStack stack) {
        try {
            if (GearHelper.isBroken(stack)) return null;
            GearConstructionData data = GearData.getConstruction(stack);
            if (data == null) return null;
            PartInstance part = data.getCoatingOrMainPart();
            if (part == null) return null;
            MaterialInstance mat = part.getPrimaryMaterial();
            if (mat == null || mat.get() == null || mat.hasAnyCategory(CAST_ONLY)) return null;
            ResourceLocation id = mat.getId();
            if (id == null) return null;
            String path = id.getPath();
            if (path.equals("iron")) return Look.IRON;
            if (path.equals("gold")) return Look.GOLD;
            if (path.equals("turtle")) return Look.TURTLE;
            if (mat.hasAnyCategory(CLOTH)) return Look.LEATHER;
            return Look.IRON_TINTED;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final java.util.Set<String> TOOLS = java.util.Set.of("pickaxe", "axe", "shovel", "hoe", "sword");
    private static final java.util.Set<String> ARMOUR = java.util.Set.of("helmet", "chestplate", "leggings", "boots");

    /** The vanilla item whose model this Silent Gear item should use, or null to keep Silent Gear's. */
    public static Item vanillaItem(ItemStack stack) {
        try {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!"silentgear".equals(itemId.getNamespace())) return null;
            String kind = itemId.getPath();
            boolean tool = TOOLS.contains(kind), armour = ARMOUR.contains(kind);
            if (!tool && !armour) return null;
            MaterialInstance mat = material(stack);
            if (mat == null) return null;
            String m = mat.getId().getPath();
            String prefix;
            if (m.equals("iron")) prefix = "iron";
            else if (m.equals("gold")) prefix = "golden";
            else if (tool && (m.equals("wood") || m.startsWith("wood/"))) prefix = "wooden";
            else if (tool && (m.equals("stone") || m.startsWith("stone/"))) prefix = "stone";
            else if (armour && m.equals("leather")) prefix = "leather";
            else if (kind.equals("helmet") && m.equals("turtle")) return Items.TURTLE_HELMET;
            else return null;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(prefix + "_" + kind));
            return item == Items.AIR ? null : item;
        } catch (Throwable t) {
            return null;
        }
    }

    private static MaterialInstance material(ItemStack stack) {
        if (GearHelper.isBroken(stack)) return null;
        GearConstructionData data = GearData.getConstruction(stack);
        if (data == null) return null;
        PartInstance part = data.getCoatingOrMainPart();
        if (part == null) return null;
        MaterialInstance mat = part.getPrimaryMaterial();
        if (mat == null || mat.get() == null || mat.getId() == null || mat.hasAnyCategory(CAST_ONLY)) return null;
        return mat;
    }

    /** The vanilla texture to draw, or null to keep Silent Gear's. */
    public static ResourceLocation texture(ItemStack stack, boolean inner) {
        Look look = look(stack);
        if (look == null) return null;
        String name = switch (look) {
            case IRON, IRON_TINTED -> "iron";
            case GOLD -> "gold";
            case LEATHER -> "leather";
            case TURTLE -> inner ? "leather" : "turtle";
        };
        return ResourceLocation.withDefaultNamespace("textures/models/armor/" + name + "_layer_" + (inner ? 2 : 1) + ".png");
    }

    /** True when the vanilla texture should be drawn untinted (it already has its colours). */
    public static boolean untinted(ItemStack stack) {
        Look look = look(stack);
        return look == Look.IRON || look == Look.GOLD || look == Look.TURTLE;
    }
}
