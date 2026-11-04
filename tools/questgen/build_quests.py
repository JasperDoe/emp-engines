#!/usr/bin/env python3
"""
Generates the Skybound SMP quest book (FTB Quests).

The book is a guide and tutorial only: no costs and no rewards. Research is done
separately (the research bench); each age chapter opens when the player has learned
that age, through a "gamestage" task that ticks itself when Chapters grants the stage.

    python tools/questgen/build_quests.py [--check items.json]

Quest and task IDs are derived from stable keys, so re-running keeps progress.
Once the book is edited in-game with the FTB Quests editor, stop regenerating
(or port the edits back here first): this script overwrites config/ftbquests/quests.
"""
import hashlib, json, os, shutil, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
QDIR = os.path.join(ROOT, "pack", "config", "ftbquests", "quests")

# --------------------------------------------------------------------------
# SNBT writer
# --------------------------------------------------------------------------
class L(int):
    """long"""
class D(float):
    """double"""

def _key(k):
    return k if all(c.isalnum() or c in "_-+." for c in k) else json.dumps(k)

def snbt(v, ind=0):
    t = "\t"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, L):
        return f"{int(v)}L"
    if isinstance(v, float):
        return f"{float(v)}d"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, str):
        return json.dumps(v, ensure_ascii=False)
    if isinstance(v, list):
        if not v:
            return "[ ]"
        return "[\n" + "\n".join(t * (ind + 1) + snbt(x, ind + 1) for x in v) + "\n" + t * ind + "]"
    if isinstance(v, dict):
        if not v:
            return "{ }"
        inner = "\n".join(t * (ind + 1) + f"{_key(k)}: " + snbt(v[k], ind + 1) for k in sorted(v))
        return "{\n" + inner + "\n" + t * ind + "}"
    raise TypeError(type(v))

def qid(key):
    h = hashlib.sha1(("skybound:" + key).encode()).hexdigest()[:16].upper()
    return h if int(h, 16) > 1 else qid(key + "_")

# --------------------------------------------------------------------------
# Quest DSL
# --------------------------------------------------------------------------
def item(i, n=1):
    t = {"type": "item", "item": {"id": i, "count": 1}}
    if n != 1:
        t["count"] = L(n)
    return t
def check():
    return {"type": "checkmark"}
def structure(s):
    return {"type": "structure", "structure": s}
def dim(d):
    return {"type": "dimension", "dimension": d}
def stage(s):
    return {"type": "gamestage", "stage": "skybound:" + s, "team_stage": False}

class Q(dict):
    def __init__(self, key, title, x, y, tasks=(), deps=(), desc=(), sub=None, icon=None,
                 shape=None, size=None, invisible=False, hide_until_deps=False, optional=False):
        super().__init__()
        self.key, self.title, self.sub, self.desc = key, title, sub, list(desc)
        self.update(x=D(x), y=D(y))
        self.tasks = [dict(t) for t in tasks]
        self.deps = list(deps)
        if icon: self["icon"] = {"id": icon, "count": 1}
        if shape: self["shape"] = shape
        if size: self["size"] = D(size)
        if invisible: self["invisible"] = True
        if hide_until_deps: self["hide_until_deps_complete"] = True
        if optional: self["optional"] = True

def age_root(key, st, title, icon, deps, unlocks):
    """Opens an age chapter. Completes on its own once the player has researched the age."""
    return Q(key, title, 0, 0, [stage(st)], deps=deps, icon=icon, shape="gear", size=2,
             sub="Learn this age at the Research Bench",
             desc=["This chapter guides you through the age once you've researched it.",
                   "", "&eResearch unlocks:&r " + unlocks, "",
                   "&7Until then these recipes stay hidden in JEI and can't be crafted. "
                   "You can still use and trade the items if someone else makes them."])

# --------------------------------------------------------------------------
# Chapters
# --------------------------------------------------------------------------
BRIEF = [
    Q("brief_welcome", "Welcome to Skybound", 0, 0, [check()], icon="minecraft:map", shape="hexagon", size=2,
      sub="Read me first",
      desc=["The ground is yours to build on. The sky is not.",
            "",
            "Everything valuable floats above &bY 250&r, where nobody can place or break blocks. "
            "To reach it you'll need to build something that flies.",
            "",
            "Start like any vanilla world. This book is your guide: it opens new chapters as you find things "
            "and learn new ages."]),
    Q("brief_rules", "Server Rules", 0, -2.5, [check()], deps=["brief_welcome"], icon="minecraft:writable_book",
      desc=["&c-&r No building or breaking at or above &bY 250&r.",
            "&c-&r The Nether is &b1:1&r with the Overworld. It's a destination, not a shortcut.",
            "&c-&r No elytra and no teleporting.",
            "&c-&r Mob farms don't work. XP comes from exploring.",
            "&c-&r XP is &eknowledge&r: you spend it to research new recipes."]),
    Q("brief_table", "Getting Started", 2.5, 0, [item("minecraft:crafting_table")], deps=["brief_welcome"],
      desc=["Punch a tree and make a crafting table."]),
    Q("brief_furnace", "Stone Age", 4.5, 0, [item("minecraft:furnace")], deps=["brief_table"],
      desc=["Stone tools and a furnace. Your tools are made of parts. You'll learn what that means soon."]),
    Q("brief_shelter", "A Roof and a Bed", 4.5, -2, [check()], deps=["brief_table"],
      icon="minecraft:red_bed", desc=["Get through your first night."]),
    Q("brief_food", "Something to Eat", 4.5, 2, [check()], deps=["brief_table"],
      icon="minecraft:bread", desc=["Find a steady food source before you go far."]),
    Q("trig_iron", "Iron", 7, -2, [item("minecraft:iron_ingot")], deps=["brief_furnace"], shape="diamond",
      sub="Opens: Gears & Metals", desc=["Your first iron ingot. Look closer at your tools."]),
    Q("trig_andesite", "Andesite", 7, 0, [item("minecraft:andesite")], deps=["brief_furnace"], shape="diamond",
      sub="The start of every machine",
      desc=["A grey stone with a future. Andesite plus iron makes andesite alloy, "
            "the backbone of Create. You'll need to research it first."]),
    Q("trig_lapis", "The Price of Knowledge", 7, 2, [item("minecraft:lapis_lazuli")], deps=["brief_furnace"],
      shape="diamond", sub="Opens: March of Industry",
      desc=["Lapis is the key to research.",
            "",
            "Every recipe beyond vanilla has to be researched at the &eResearch Bench&r. "
            "Research costs &eXP&r plus a few sample items, and it's per player. "
            "One person can learn machines and supply the rest of the team.",
            "",
            "When you research an age, its chapter in this book opens."]),
    Q("brief_sky", "The Sky Is Off-Limits", 9.5, 0, [check()], deps=["trig_iron", "trig_andesite"],
      icon="minecraft:feather", optional=True,
      desc=["Climb a mountain and look up. Sky villages, wrecks and loot drift above &bY 250&r.",
            "You can't pillar up there. You'll have to fly."]),
]

METALS = [
    ("copper", "minecraft:copper_ingot", "Copper"),
    ("gold", "minecraft:gold_ingot", "Gold"),
    ("diamond", "minecraft:diamond", "Diamond"),
    ("bort", "silentgear:bort", "Bort"),
    ("crimson_iron", "silentgear:crimson_iron_ingot", "Crimson Iron"),
    ("blaze_gold", "silentgear:blaze_gold_ingot", "Blaze Gold"),
    ("crimson_steel", "silentgear:crimson_steel_ingot", "Crimson Steel"),
    ("netherite", "minecraft:netherite_ingot", "Netherite"),
    ("azure_silver", "silentgear:azure_silver_ingot", "Azure Silver"),
    ("azure_electrum", "silentgear:azure_electrum_ingot", "Azure Electrum"),
    ("tyrian_steel", "silentgear:tyrian_steel_ingot", "Tyrian Steel"),
]

GEARS = [
    Q("sg_reveal", "Look Closer at Your Pickaxe", 0, 0, [check()], deps=["trig_iron"], hide_until_deps=True,
      icon="silentgear:pickaxe", shape="hexagon", size=2,
      desc=["Every tool and piece of armour here is built from &eparts&r: a head, a rod, sometimes a binding or grip.",
            "Each part's material decides the stats. Hover a tool and hold Shift to see them.",
            "",
            "Better materials come from exploring. You won't know which metal is best until you find it."]),
    Q("sg_blueprint", "Blueprints", 2.5, -1.5, [item("silentgear:blueprint_paper"), item("silentgear:template_board")],
      deps=["sg_reveal"],
      desc=["Blueprints and templates shape the parts. Craft a blueprint for the part you want, "
            "then craft it with your material."]),
    Q("sg_modkit", "Swapping Parts", 2.5, 1.5, [item("silentgear:mod_kit")], deps=["sg_reveal"],
      desc=["A mod kit lets you swap parts on a tool without starting over."]),
    Q("sg_repair", "Repairs", 5, -1.5, [item("silentgear:crude_repair_kit")], deps=["sg_blueprint"],
      desc=["Load a repair kit with materials and use it on worn gear."]),
    Q("sg_paint", "A Splash of Colour", 5, 1.5, [item("silentgear:paint_mixer")], deps=["sg_modkit"], optional=True,
      desc=["Paint your gear any colour you like."]),
    Q("sg_salvage", "Salvager", 7.5, 0, [item("silentgear:salvager")], deps=["sg_repair", "sg_modkit"],
      desc=["Break old gear back down into parts and materials."]),
    Q("sg_notes", "Field Notes: Metals", 0, 3.5, [check()], deps=["sg_reveal"], icon="minecraft:paper",
      sub="Hints for hidden entries",
      desc=["Each metal gets its own entry here once you hold it. Rumours so far:",
            "&7-&r Something red runs in the walls of the Nether.",
            "&7-&r Blue veins are said to glitter in the End.",
            "&7-&r Black crystals hide deep below the stone.",
            "&7-&r Some of the best metals aren't mined at all. They're alloyed.",
            "",
            "The &eFoundry&r (Alloy Age) casts parts and doubles your ore."]),
]
for i, (k, it, t) in enumerate(METALS):
    GEARS.append(Q(f"metal_{k}", t, 2.5 + (i % 6) * 2, 3.5 + (i // 6) * 2, [item(it)], deps=["sg_notes"],
                   invisible=True, desc=["A new material for your gear. Check its stats with Shift."]))

ANDESITE = [
    age_root("and_root", "andesite_age", "Andesite Age", "create:andesite_alloy", ["trig_lapis", "trig_andesite"],
             "andesite alloy, shafts and cogs, water wheels, the millstone, press, mixer, fans, drills, saws and belts."),
    Q("and_alloy", "Andesite Alloy", 2.5, 0, [item("create:andesite_alloy", 16)], deps=["and_root"],
      desc=["The backbone of every early machine. Andesite plus iron nuggets."]),
    Q("and_kinetics", "Shafts and Cogs", 4.5, -2, [item("create:shaft", 8), item("create:cogwheel", 4)],
      deps=["and_alloy"], desc=["Rotation travels through shafts and cogs. Large cogs change speed."]),
    Q("and_wheel", "Water Wheel", 4.5, 0, [item("create:water_wheel")], deps=["and_alloy"],
      desc=["Your first power source. Put it in flowing water."]),
    Q("and_goggles", "Stress", 6.5, -2, [item("create:goggles")], deps=["and_kinetics", "and_wheel"],
      desc=["Every machine costs stress units (SU). Goggles show how much your network can handle."]),
    Q("and_windmill", "Windmill", 6.5, 2, [item("create:windmill_bearing"), item("create:white_sail", 8)],
      deps=["and_wheel"], optional=True, desc=["More power, no river needed."]),
    Q("and_millstone", "Millstone", 6.5, 0, [item("create:millstone")], deps=["and_wheel"],
      desc=["Grind ores, flowers and gravel."]),
    Q("and_press", "Mechanical Press", 8.5, -1, [item("create:mechanical_press")], deps=["and_millstone"],
      desc=["Presses ingots into sheets."]),
    Q("and_mixer", "Mixer and Basin", 8.5, 1, [item("create:mechanical_mixer"), item("create:basin")],
      deps=["and_millstone"], desc=["Mixing recipes. Some need heat underneath the basin."]),
    Q("and_belts", "Belts", 10.5, -2, [item("create:belt_connector", 8)], deps=["and_press"],
      desc=["Move items between machines."]),
    Q("and_fan", "Encased Fan", 10.5, 0, [item("create:encased_fan")], deps=["and_press", "and_mixer"],
      desc=["Blow through fire to smelt, through water to wash."]),
    Q("and_tools", "Drills and Saws", 10.5, 2, [item("create:mechanical_drill"), item("create:mechanical_saw")],
      deps=["and_mixer"], desc=["Machines that break blocks for you."]),
    Q("and_farm", "First Automated Farm", 12.5, 0, [item("create:mechanical_harvester")],
      deps=["and_belts", "and_tools"], shape="hexagon",
      desc=["Build a farm that harvests and collects on its own."]),
]

ALLOY = [
    age_root("al_root", "alloy_age", "Alloy Age", "immersiveengineering:ingot_steel", ["and_root"],
             "the IE coke oven, blast furnace and alloy kiln, copper fluid handling, brass, and the foundry."),
    Q("al_hammer", "Engineer's Hammer", 2.5, -2, [item("immersiveengineering:hammer")], deps=["al_root"],
      desc=["IE multiblocks are built by hand, then struck with the hammer to form."]),
    Q("al_coke", "Coke Oven", 4.5, -2, [item("immersiveengineering:cokebrick", 27)], deps=["al_hammer"],
      desc=["A 3x3x3 of coke bricks. Bakes coal into coke and gives creosote."]),
    Q("al_cokecoal", "Coal Coke", 6.5, -2, [item("immersiveengineering:coal_coke", 16)], deps=["al_coke"],
      desc=["Hot enough to make steel."]),
    Q("al_blast", "Blast Furnace", 8.5, -2, [item("immersiveengineering:blastbrick", 27)], deps=["al_cokecoal"],
      desc=["Iron plus coal coke makes steel."]),
    Q("al_steel", "Steel", 10.5, -2, [item("immersiveengineering:ingot_steel", 8)], deps=["al_blast"],
      shape="hexagon", desc=["The metal every later age is built on."]),
    Q("al_fluids", "Copper and Fluids", 2.5, 2, [item("create:fluid_pipe", 8), item("create:mechanical_pump"),
                                                 item("create:fluid_tank")], deps=["al_root"],
      desc=["Pipes, pumps and tanks move liquids. Copper casing opens fluid machines."]),
    Q("al_spout", "Spout and Drain", 4.5, 2, [item("create:spout"), item("create:item_drain")], deps=["al_fluids"],
      desc=["Fill and empty items with liquids."]),
    Q("al_fireclay", "Fire Bricks", 2.5, 0, [item("productivemetalworks:fire_brick", 32)], deps=["al_root"],
      desc=["Fire clay bakes into fire bricks, the walls of the foundry."]),
    Q("al_foundry", "The Foundry", 4.5, 0, [item("productivemetalworks:black_foundry_controller"),
                                             item("productivemetalworks:black_foundry_drain"),
                                             item("productivemetalworks:black_foundry_tank")],
      deps=["al_fireclay"], shape="hexagon", size=1.5,
      desc=["A multiblock that melts metal. Raw ore melts into &etwice&r the metal.",
            "Open the Productive Metalworks guide for the build."]),
    Q("al_cast", "Casting", 6.5, 0, [item("productivemetalworks:foundry_tap"), item("productivemetalworks:casting_table"),
                                     item("productivemetalworks:casting_basin")], deps=["al_foundry"],
      desc=["Pour metal into casts for ingots, blocks and Silent Gear parts."]),
    Q("al_brass", "Brass", 8.5, 0, [item("create:brass_ingot", 8)], deps=["al_cast", "al_spout"], shape="hexagon",
      desc=["Copper and zinc, alloyed in the foundry or a heated mixer. Brass opens the next age and the sky."]),
]

BRASS = [
    age_root("br_root", "brass_age", "Brass Age", "create:brass_casing", ["al_root"],
             "brass machines, mechanical crafters, deployers and arms, precision mechanisms, crushing wheels, "
             "steam engines and trains."),
    Q("br_casing", "Brass Casing", 2.5, 0, [item("create:brass_casing", 8)], deps=["br_root"],
      desc=["The shell for smarter machines."]),
    Q("br_tube", "Electron Tubes", 4.5, -2, [item("create:electron_tube", 4)], deps=["br_casing"],
      desc=["Polished rose quartz in an iron sheet."]),
    Q("br_crafter", "Mechanical Crafters", 4.5, 0, [item("create:mechanical_crafter", 9)], deps=["br_casing"],
      desc=["Big recipes, done by machines."]),
    Q("br_crush", "Crushing Wheels", 4.5, 2, [item("create:crushing_wheel", 2)], deps=["br_casing"],
      desc=["Automated ore processing. The foundry was the first upgrade. This is the next."]),
    Q("br_deployer", "Deployers", 6.5, -2, [item("create:deployer")], deps=["br_tube"],
      desc=["A hand that uses items for you."]),
    Q("br_arm", "Mechanical Arm", 6.5, 0, [item("create:mechanical_arm")], deps=["br_tube", "br_crafter"],
      desc=["Picks up and places items between machines."]),
    Q("br_precision", "Precision Mechanism", 8.5, -1, [item("create:precision_mechanism", 4)],
      deps=["br_deployer"], shape="hexagon",
      desc=["Your first sequenced assembly line: deploy, press, repeat."]),
    Q("br_speed", "Rotation Speed Controller", 8.5, 1, [item("create:rotation_speed_controller")],
      deps=["br_arm"], desc=["Set an exact RPM."]),
]

ELEC = [
    age_root("el_root", "electricity_age", "Electricity Age", "createaddition:alternator", ["br_root"],
             "Crafts & Additions (alternators, motors, rolling mill, wires) and IE's powered machines."),
    Q("el_alt", "Alternator", 2.5, 0, [item("createaddition:alternator")], deps=["el_root"], shape="hexagon",
      desc=["Electricity only comes from rotation. An alternator turns RPM into power."]),
    Q("el_mill", "Rolling Mill", 4.5, -2, [item("createaddition:rolling_mill")], deps=["el_alt"],
      desc=["Rolls ingots into rods and wire."]),
    Q("el_wire", "Wiring", 6.5, -2, [item("createaddition:copper_spool"), item("createaddition:connector", 4)],
      deps=["el_mill"], desc=["Carry power between buildings."]),
    Q("el_motor", "Electric Motor", 4.5, 0, [item("createaddition:electric_motor")], deps=["el_alt"],
      desc=["Power back into rotation, wherever you need it."]),
    Q("el_acc", "Accumulator", 4.5, 2, [item("createaddition:accumulator")], deps=["el_alt"],
      desc=["Store power for later."]),
    Q("el_ie", "Engineering Blocks", 6.5, 0, [item("immersiveengineering:light_engineering", 8),
                                              item("immersiveengineering:heavy_engineering", 8)],
      deps=["el_motor", "el_wire"], desc=["The building blocks of IE's powered machines, like the arc furnace."]),
    Q("el_nether", "The Nether Is Calling", 8.5, 0, [check()], deps=["el_ie"], icon="minecraft:obsidian",
      desc=["The next age needs blaze power. The Nether opens through a server-wide project.",
            "See &eInto the Unknown: The Nether&r."]),
]

EXOTIC = [
    age_root("ex_root", "exotic_age", "Exotic Age", "create_enchantment_industry:experience_bucket", ["el_root"],
             "Create: Enchantment Industry (liquid XP, blaze enchanting). Exotic material chains come in a later update."),
    Q("ex_xp", "Liquid Knowledge", 2.5, 0, [item("create_enchantment_industry:experience_bucket")],
      deps=["ex_root"], desc=["XP as a fluid. Bank it in tanks, trade it, or feed it into factories."]),
    Q("ex_enchanter", "Blaze Enchanter", 4.5, 0, [item("create_enchantment_industry:blaze_enchanter")],
      deps=["ex_xp"], desc=["Automated enchanting, fuelled by liquid XP."]),
    Q("ex_printer", "Printer", 4.5, 2, [item("create_enchantment_industry:printer")], deps=["ex_xp"],
      optional=True, desc=["Copy written books and enchanted books."]),
]

FLIGHT = [
    Q("fl_wreck", "A Wreck in the Hills", -2.5, 0, [structure("#skybound:wrecks")], invisible=True,
      icon="minecraft:compass", shape="hexagon", size=1.5,
      desc=["Something came down here. Someone was flying.", "Search the wreck. There may be a map."]),
    Q("fl_root", "Flight", 0, 0, [stage("flight")], deps=["fl_wreck"], icon="aeronautics:propeller_bearing",
      shape="gear", size=2, sub="Learn Flight at the Research Bench",
      desc=["Whoever flew that wreck knew something you don't. Research Flight to learn it.",
            "", "&eResearch unlocks:&r Create Aeronautics: envelopes, burners, propellers, levitite, "
            "the physics assembler and controls. Needs the Brass Age first."]),
    Q("fl_assembler", "Physics Assembler", 2.5, -2, [item("simulated:physics_assembler")], deps=["fl_root"],
      desc=["Turns a build into a moving physics object."]),
    Q("fl_balloon", "Hot Air", 2.5, 0, [item("aeronautics:white_envelope", 16), item("aeronautics:adjustable_burner")],
      deps=["fl_root"], desc=["A burner under an envelope. The simplest way up."]),
    Q("fl_prop", "Propellers", 2.5, 2, [item("aeronautics:propeller_bearing"), item("aeronautics:wooden_propeller")],
      deps=["fl_root"], desc=["Rotation into thrust."]),
    Q("fl_controls", "At the Helm", 4.5, 0, [item("simulated:steering_wheel"), item("simulated:throttle_lever")],
      deps=["fl_assembler", "fl_balloon", "fl_prop"], desc=["Steer and throttle from the deck."]),
    Q("fl_levitite", "Levitite", 4.5, -2, [item("aeronautics:levitite", 8)], deps=["fl_assembler"],
      optional=True, desc=["A block that wants to float."]),
    Q("fl_first", "First Flight", 6.5, 0, [check()], deps=["fl_controls"], icon="minecraft:feather",
      shape="hexagon", size=1.5, desc=["Take your ship above &bY 250&r and bring it back in one piece."]),
    Q("fl_nav", "Navigation Table", 8.5, 0, [item("simulated:navigation_table")], deps=["fl_first"],
      desc=["Plot a course."]),
]

SKY = [
    Q("sky_village", "Sky Village", 0, 0, [structure("#skybound:sky_villages")], deps=["fl_first"],
      hide_until_deps=True, icon="minecraft:emerald", shape="hexagon", size=1.5,
      desc=["Villages float high above the build limit. Land, trade, and see what they sell."]),
    Q("sky_highwind", "Highwind", 2.5, -1.5, [structure("create_sky_village:highwind_village")],
      deps=["sky_village"], desc=["A rarer, windier village."]),
    Q("sky_cargo", "Drifting Cargo", 2.5, 1.5, [structure("aeronauticsdiscovery:balloon_loot")],
      deps=["sky_village"], desc=["Lost cargo, still floating."]),
    Q("sky_pillager", "Pillager Airship", 4.5, 0, [structure("cr_aero_structures:pillager_airship")],
      deps=["sky_village"], desc=["They fly too. Board it."]),
]

NETHER = [
    Q("ne_root", "The Nether Key", 0, 0, [stage("nether_access")], deps=["el_root"], hide_until_deps=True,
      shape="gear", size=2, icon="minecraft:crying_obsidian", sub="Server project",
      desc=["The Nether is sealed. Opening it takes the whole server working together.",
            "When the project is finished, the Nether opens for &eeveryone&r, including players who are offline."]),
    Q("ne_enter", "Through the Portal", 2.5, 0, [dim("minecraft:the_nether")], deps=["ne_root"],
      desc=["The Nether is 1:1. Every block there is a block here."]),
    Q("ne_blaze", "Blaze Rods", 4.5, -1.5, [item("minecraft:blaze_rod", 8)], deps=["ne_enter"],
      desc=["Blaze power for the Exotic Age."]),
    Q("ne_burner", "Capture a Blaze", 4.5, 1.5, [item("create:blaze_burner")], deps=["ne_enter"],
      desc=["Catch a blaze in an empty blaze burner. Superheated mixing needs it."]),
]

LOGI = [
    age_root("lo_root", "logistics", "Logistics", "create:item_vault", ["and_root"],
             "item vaults, portable storage interfaces, packagers, frogports and stock tickers."),
    Q("lo_vault", "Item Vault", 2.5, -2, [item("create:item_vault", 4)], deps=["lo_root"],
      desc=["Bulk storage that grows with size."]),
    Q("lo_funnel", "Funnels", 2.5, 0, [item("create:andesite_funnel", 4)], deps=["lo_root"],
      desc=["Pull items in and out of anything."]),
    Q("lo_packager", "Packages", 4.5, 0, [item("create:packager"), item("create:package_frogport")],
      deps=["lo_funnel", "lo_vault"], desc=["Box up items and send them by frog."]),
    Q("lo_stock", "Stock Ticker", 6.5, 0, [item("create:stock_link"), item("create:stock_ticker")],
      deps=["lo_packager"], desc=["Order from your whole network at one counter."]),
    Q("lo_psi", "Ship Cargo", 4.5, -2, [item("create:portable_storage_interface", 2)], deps=["lo_vault"],
      desc=["Load and unload moving contraptions and ships."]),
    Q("lo_dock", "Docking", 6.5, -2, [item("simulated:docking_connector")], deps=["lo_psi", "fl_root"],
      desc=["Moor your ship at a dock."]),
    Q("lo_track", "Trains", 4.5, 2.5, [item("create:track", 32), item("create:track_station"), item("create:controls")],
      deps=["lo_root", "br_root"], icon="create:track",
      desc=["Rail lines for heavy freight. Trains are part of the Brass Age."]),
]

GROUPS = [("industry", "March of Industry"), ("unknown", "Into the Unknown")]

CHAPTERS = [  # filename, title, subtitle, group, icon, quests
    ("briefing", "Briefing", "Start here", None, "minecraft:map", BRIEF),
    ("gears_and_metals", "Gears & Metals", "Silent Gear", None, "silentgear:pickaxe", GEARS),
    ("andesite_age", "1 · Andesite Age", "Rotation and stress", "industry", "create:andesite_alloy", ANDESITE),
    ("alloy_age", "2 · Alloy Age", "Steel, brass and the foundry", "industry", "immersiveengineering:ingot_steel", ALLOY),
    ("brass_age", "3 · Brass Age", "Smarter machines", "industry", "create:brass_casing", BRASS),
    ("electricity_age", "4 · Electricity Age", "Power from rotation", "industry", "createaddition:alternator", ELEC),
    ("exotic_age", "5 · Exotic Age", "Blaze and knowledge", "industry", "create_enchantment_industry:experience_bucket", EXOTIC),
    ("first_flight", "1 · First Flight", "Leave the ground", "unknown", "aeronautics:propeller_bearing", FLIGHT),
    ("into_the_sky", "2 · Into the Sky", "Above Y 250", "unknown", "minecraft:emerald", SKY),
    ("the_nether", "2.5 · The Nether", "A server project", "unknown", "minecraft:crying_obsidian", NETHER),
    ("logistics", "Logistics & Transport", "Side chapter", None, "create:item_vault", LOGI),
]

# --------------------------------------------------------------------------
def build():
    keys = {}
    for ch in CHAPTERS:
        for q in ch[5]:
            assert q.key not in keys, q.key
            keys[q.key] = qid("quest:" + q.key)
    lang = {"file.0000000000000001.title": "Skybound SMP"}
    groups = []
    for gk, gt in GROUPS:
        gid = qid("group:" + gk)
        groups.append({"id": gid})
        lang[f"chapter_group.{gid}.title"] = gt

    if os.path.isdir(QDIR):
        shutil.rmtree(QDIR)
    for d in ("chapters", "reward_tables", "lang"):
        os.makedirs(os.path.join(QDIR, d))

    for idx, (fn, title, sub, group, icon, quests) in enumerate(CHAPTERS):
        cid = qid("chapter:" + fn)
        lang[f"chapter.{cid}.title"] = title
        if sub:
            lang[f"chapter.{cid}.chapter_subtitle"] = [sub]
        qs = []
        for q in quests:
            d = dict(q)
            d["id"] = keys[q.key]
            if q.deps:
                d["dependencies"] = [keys[k] for k in q.deps]
            d["tasks"] = []
            for n, t in enumerate(q.tasks):
                t = dict(t); t["id"] = qid(f"task:{q.key}:{n}"); d["tasks"].append(t)
            qs.append(d)
            lang[f"quest.{d['id']}.title"] = q.title
            if q.sub:
                lang[f"quest.{d['id']}.quest_subtitle"] = q.sub
            if q.desc:
                lang[f"quest.{d['id']}.quest_desc"] = q.desc
        chap = {"id": cid, "filename": fn, "group": qid("group:" + group) if group else "",
                "icon": {"id": icon, "count": 1}, "order_index": idx, "default_quest_shape": "",
                "default_hide_dependency_lines": False, "quest_links": [], "quests": qs}
        with open(os.path.join(QDIR, "chapters", fn + ".snbt"), "w", encoding="utf-8", newline="\n") as f:
            f.write(snbt(chap) + "\n")

    data = {"version": 13, "default_reward_team": False, "default_consume_items": False,
            "default_autoclaim_rewards": "disabled", "default_quest_shape": "circle",
            "default_quest_disable_jei": False, "drop_loot_crates": False, "disable_gui": False,
            "grid_scale": D(0.5), "pause_game": False, "progression_mode": "flexible",
            "lock_message": "", "emergency_items_cooldown": 300}
    with open(os.path.join(QDIR, "data.snbt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(snbt(data) + "\n")
    with open(os.path.join(QDIR, "chapter_groups.snbt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(snbt({"chapter_groups": groups}) + "\n")
    open(os.path.join(QDIR, "reward_tables", ".gitkeep"), "w").close()
    with open(os.path.join(QDIR, "lang", "en_us.snbt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(snbt(lang) + "\n")
    print(f"Wrote {len(CHAPTERS)} chapters, {sum(len(c[5]) for c in CHAPTERS)} quests")

def verify_ids(path):
    known = set()
    for v in json.load(open(path)).values():
        known.update(i for i, _ in v)
    nss = {i.split(":")[0] for i in known}
    want = set()
    for ch in CHAPTERS:
        want.add(ch[4])
        for q in ch[5]:
            want.update(t["item"]["id"] for t in q.tasks if t["type"] == "item")
            if "icon" in q:
                want.add(q["icon"]["id"])
    bad = sorted(i for i in want if i.split(":")[0] in nss and i not in known)
    print("Unknown item ids:", bad or "none")
    return not bad

if __name__ == "__main__":
    build()
    if "--check" in sys.argv:
        sys.exit(0 if verify_ids(sys.argv[sys.argv.index("--check") + 1]) else 1)
