#!/usr/bin/env python3
"""Generates Tiered Foundry's JSON: blockstates, models, lang, loot, tags and recipes.

Run from anywhere:  python tools/foundry/gen.py
Edit the METALS table below to change what melts, at what temperature, and what it casts into; everything else
follows from it. Textures (placeholders from Tinkers' Construct, MIT) are not touched by this script.
"""
import json
import os
import re
import shutil

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'mods', 'tieredfoundry', 'src', 'main', 'resources')
NS = 'tieredfoundry'
ASSETS = os.path.join(ROOT, 'assets', NS)
DATA = os.path.join(ROOT, 'data', NS)
SG = 'silentgear'

ING, NUG, BLK = 90, 10, 810

# name: temperature, display name, then what melts and what casts.
#   melt: list of (ingredient, amount, ore?)   ingredient is "#tag" or "item"
#   ingot / nugget / plate / block: item made by casting (table with that cast, or basin for block)
#   mod: only load these recipes when this mod is present
METALS = {
    'iron': dict(temp=950, name='Molten Iron', melt=[
        ('#c:ingots/iron', ING, False), ('#c:nuggets/iron', NUG, False), ('#c:storage_blocks/iron', BLK, False),
        ('#c:plates/iron', ING, False), ('#c:raw_materials/iron', ING, True), ('#c:storage_blocks/raw_iron', BLK, True),
        ('#c:ores/iron', ING, True), ('create:crushed_raw_iron', ING, True)],
        ingot='minecraft:iron_ingot', nugget='minecraft:iron_nugget', plate='create:iron_sheet', block='minecraft:iron_block'),
    'gold': dict(temp=850, name='Molten Gold', melt=[
        ('#c:ingots/gold', ING, False), ('#c:nuggets/gold', NUG, False), ('#c:storage_blocks/gold', BLK, False),
        ('#c:plates/gold', ING, False), ('#c:raw_materials/gold', ING, True), ('#c:storage_blocks/raw_gold', BLK, True),
        ('#c:ores/gold', ING, True), ('create:crushed_raw_gold', ING, True), (f'{NS}:gold_cast', 180, False)],
        ingot='minecraft:gold_ingot', nugget='minecraft:gold_nugget', plate='create:golden_sheet', block='minecraft:gold_block'),
    'copper': dict(temp=800, name='Molten Copper', melt=[
        ('#c:ingots/copper', ING, False), ('#c:nuggets/copper', NUG, False), ('#c:storage_blocks/copper', BLK, False),
        ('#c:plates/copper', ING, False), ('#c:raw_materials/copper', ING, True), ('#c:storage_blocks/raw_copper', BLK, True),
        ('#c:ores/copper', ING, True), ('create:crushed_raw_copper', ING, True)],
        ingot='minecraft:copper_ingot', nugget='create:copper_nugget', plate='create:copper_sheet', block='minecraft:copper_block'),
    'zinc': dict(temp=420, name='Molten Zinc', melt=[
        ('#c:ingots/zinc', ING, False), ('#c:nuggets/zinc', NUG, False), ('#c:storage_blocks/zinc', BLK, False),
        ('#c:raw_materials/zinc', ING, True), ('#c:storage_blocks/raw_zinc', BLK, True), ('#c:ores/zinc', ING, True),
        ('create:crushed_raw_zinc', ING, True)],
        ingot='create:zinc_ingot', nugget='create:zinc_nugget', block='create:zinc_block'),
    'brass': dict(temp=900, name='Molten Brass', melt=[
        ('#c:ingots/brass', ING, False), ('#c:nuggets/brass', NUG, False), ('#c:storage_blocks/brass', BLK, False),
        ('#c:plates/brass', ING, False)],
        ingot='create:brass_ingot', nugget='create:brass_nugget', plate='create:brass_sheet', block='create:brass_block'),
    'netherite': dict(temp=1500, name='Molten Netherite', melt=[
        ('#c:ingots/netherite', ING, False), ('#c:storage_blocks/netherite', BLK, False)],
        ingot='minecraft:netherite_ingot', block='minecraft:netherite_block'),
    'ancient_debris': dict(temp=1500, name='Molten Ancient Debris', melt=[
        ('minecraft:ancient_debris', 100, True), ('minecraft:netherite_scrap', 100, False)],
        ingot=('minecraft:netherite_scrap', 100)),
    'crimson_iron': dict(temp=1100, name='Molten Crimson Iron', mod=SG, melt=[
        (f'{SG}:crimson_iron_ingot', ING, False), (f'{SG}:crimson_iron_nugget', NUG, False), (f'{SG}:crimson_iron_block', BLK, False),
        (f'{SG}:raw_crimson_iron', ING, True), (f'{SG}:raw_crimson_iron_block', BLK, True), (f'{SG}:crimson_iron_ore', ING, True)],
        ingot=f'{SG}:crimson_iron_ingot', nugget=f'{SG}:crimson_iron_nugget', block=f'{SG}:crimson_iron_block'),
    'azure_silver': dict(temp=1200, name='Molten Azure Silver', mod=SG, melt=[
        (f'{SG}:azure_silver_ingot', ING, False), (f'{SG}:azure_silver_nugget', NUG, False), (f'{SG}:azure_silver_block', BLK, False),
        (f'{SG}:raw_azure_silver', ING, True), (f'{SG}:raw_azure_silver_block', BLK, True), (f'{SG}:azure_silver_ore', ING, True)],
        ingot=f'{SG}:azure_silver_ingot', nugget=f'{SG}:azure_silver_nugget', block=f'{SG}:azure_silver_block'),
    'blaze_gold': dict(temp=1300, name='Molten Blaze Gold', mod=SG, melt=[
        (f'{SG}:blaze_gold_ingot', ING, False), (f'{SG}:blaze_gold_nugget', NUG, False), (f'{SG}:blaze_gold_block', BLK, False)],
        ingot=f'{SG}:blaze_gold_ingot', nugget=f'{SG}:blaze_gold_nugget', block=f'{SG}:blaze_gold_block'),
    'crimson_steel': dict(temp=1600, name='Molten Crimson Steel', mod=SG, melt=[
        (f'{SG}:crimson_steel_ingot', ING, False), (f'{SG}:crimson_steel_nugget', NUG, False), (f'{SG}:crimson_steel_block', BLK, False)],
        ingot=f'{SG}:crimson_steel_ingot', nugget=f'{SG}:crimson_steel_nugget', block=f'{SG}:crimson_steel_block'),
    'azure_electrum': dict(temp=1500, name='Molten Azure Electrum', mod=SG, melt=[
        (f'{SG}:azure_electrum_ingot', ING, False), (f'{SG}:azure_electrum_nugget', NUG, False), (f'{SG}:azure_electrum_block', BLK, False)],
        ingot=f'{SG}:azure_electrum_ingot', nugget=f'{SG}:azure_electrum_nugget', block=f'{SG}:azure_electrum_block'),
    'tyrian_steel': dict(temp=1800, name='Molten Tyrian Steel', mod=SG, melt=[
        (f'{SG}:tyrian_steel_ingot', ING, False), (f'{SG}:tyrian_steel_nugget', NUG, False), (f'{SG}:tyrian_steel_block', BLK, False)],
        ingot=f'{SG}:tyrian_steel_ingot', nugget=f'{SG}:tyrian_steel_nugget', block=f'{SG}:tyrian_steel_block'),
    'diamond': dict(temp=1450, name='Molten Diamond', melt=[
        ('#c:gems/diamond', ING, False), ('#c:storage_blocks/diamond', BLK, False), ('#c:ores/diamond', ING, True)],
        gem='minecraft:diamond', block='minecraft:diamond_block'),
    'shulker': dict(temp=1200, name='Molten Shulker', melt=[('minecraft:shulker_shell', 100, False)]),
    'blaze': dict(temp=1000, name='Molten Blaze', melt=[('minecraft:blaze_rod', 400, False), ('minecraft:blaze_powder', 100, False)]),
    'magma_cream': dict(temp=900, name='Molten Magma Cream', melt=[('minecraft:magma_cream', 100, False), ('minecraft:magma_block', 400, False)]),
    'ender': dict(temp=1000, name='Molten Ender', melt=[('minecraft:ender_pearl', 100, False)]),
    # sand melts at 1000° (coal, or charcoal with a fan): only a foundry makes molten glass, so fire bricks need tier 1
    'glass': dict(temp=1000, name='Molten Glass', melt=[
        ('#c:sands', 1000, False), ('#c:glass_blocks', 1000, False), ('#c:glass_panes', 250, False)],
        block=('minecraft:glass', 1000)),
}

# Mixed inside a foundry with a running fan: ([(metal, mB)...], (metal, mB), temperature)
ALLOYS = {
    'brass': ([('copper', 30), ('zinc', 10)], ('brass', 40), 900),
    'netherite': ([('ancient_debris', 40), ('gold', 36)], ('netherite', 9), 1500),
    'blaze_gold': ([('gold', 90), ('blaze', 200)], ('blaze_gold', 90), 1300),
    'crimson_steel': ([('crimson_iron', 810), ('blaze', 200), ('magma_cream', 100)], ('crimson_steel', 270), 1600),
    'azure_electrum': ([('azure_silver', 360), ('gold', 180), ('ender', 100)], ('azure_electrum', 90), 1500),
    # meant for the pressure lid (tier 2.5); fire bricks until that exists
    'tyrian_steel': ([('crimson_steel', 90), ('azure_electrum', 90), ('shulker', 100), ('ancient_debris', 100)], ('tyrian_steel', 360), 1800),
}
ALLOY_MOD = {'blaze_gold': SG, 'crimson_steel': SG, 'azure_electrum': SG, 'tyrian_steel': SG}

# Silent Gear part casting (was Silent Gear Metalworks): part, part type, gear type (Silent Gear's own, as in its
# part recipes), ingots' worth (Silent Gear Metalworks' counts), cast texture.
# Order matters: it is the cast item's model override order (CastItem.PARTS in Java).
SG_PARTS = [
    ('arrow_heads', 'main', 'arrow', 1, 'arrow'), ('axe_head', 'main', 'axe', 3, 'small_axe_head'),
    ('boot_plates', 'main', 'boots', 4, 'boots_plating'), ('bow_limbs', 'main', 'bow', 3, 'bow_limb'),
    ('bracelet_band', 'main', 'bracelet', 3, 'coin'), ('chestplate_plates', 'main', 'chestplate', 8, 'chestplate_plating'),
    ('crossbow_limbs', 'main', 'crossbow', 3, 'bow_limb'), ('dagger_blade', 'main', 'dagger', 1, 'small_blade'),
    ('excavator_head', 'main', 'excavator', 5, 'large_plate'), ('fishing_reel_and_hook', 'main', 'fishing_rod', 2, 'wire'),
    ('hammer_head', 'main', 'hammer', 6, 'hammer_head'), ('helmet_plates', 'main', 'helmet', 5, 'helmet_plating'),
    ('hoe_head', 'main', 'hoe', 2, 'adze_head'), ('katana_blade', 'main', 'katana', 3, 'broad_blade'),
    ('knife_blade', 'main', 'knife', 1, 'small_blade'), ('legging_plates', 'main', 'leggings', 7, 'leggings_plating'),
    ('machete_blade', 'main', 'machete', 3, 'broad_blade'), ('mattock_head', 'main', 'mattock', 4, 'adze_head'),
    ('necklace_chain', 'main', 'necklace', 3, 'maille'), ('paxel_head', 'main', 'paxel', 5, 'broad_axe_head'),
    ('pickaxe_head', 'main', 'pickaxe', 3, 'pick_head'), ('prospector_hammer_head', 'main', 'prospector_hammer', 2, 'hammer_head'),
    ('ring_shank', 'main', 'ring', 2, 'coin'), ('saw_blade', 'main', 'saw', 5, 'gear'),
    ('shear_blades', 'main', 'shears', 2, 'small_blade'), ('shield_plate', 'main', 'shield', 2, 'large_plate'),
    ('shovel_head', 'main', 'shovel', 1, 'adze_head'), ('sickle_blade', 'main', 'sickle', 3, 'small_blade'),
    ('slingshot_limbs', 'main', 'slingshot', 2, 'bow_limb'), ('spear_tip', 'main', 'spear', 1, 'arrow'),
    ('sword_blade', 'main', 'sword', 2, 'small_blade'), ('tip', 'tip', 'tool', 2, 'tool_binding'),
    ('rod', 'rod', 'tool', 2, 'tool_handle'), ('trident_prongs', 'main', 'trident', 3, 'broad_blade'),
]
BASE_PATTERNS = ['ingot', 'nugget', 'plate', 'gem']

BLOCKS = {
    'fire_bricks': 'Fire Bricks', 'fire_brick_wall': 'Fire Brick Wall', 'foundry_controller': 'Foundry Controller', 'solid_fuel_heater': 'Solid Fuel Heater',
    'lava_heater': 'Lava Heater', 'air_duct': 'Air Intake', 'foundry_drain': 'Foundry Drain', 'foundry_tap': 'Foundry Tap',
    'casting_table': 'Casting Table', 'casting_basin': 'Casting Basin',
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def ingredient(s):
    return {'tag': s[1:]} if s.startswith('#') else {'item': s}


def conditions(mod):
    return {'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': mod}]} if mod else {}


def molten(m):
    return f'{NS}:molten_{m}'


def tex(p):
    return f'{NS}:block/{p}'


def bricks(tier):
    return 'minecraft:block/bricks' if tier == 1 else tex('fire_bricks')


# ---------------------------------------------------------------------------------------------------------------- assets

def assets():
    bs, bm, im = os.path.join(ASSETS, 'blockstates'), os.path.join(ASSETS, 'models', 'block'), os.path.join(ASSETS, 'models', 'item')
    for d in (bs, bm, im):
        if os.path.isdir(d):
            for f in os.listdir(d):
                if f.endswith('.json'):
                    os.remove(os.path.join(d, f))

    write(f'{bm}/fire_bricks.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex('fire_bricks')}})
    write(f'{bs}/fire_bricks.json', {'variants': {'': {'model': f'{NS}:block/fire_bricks'}}})
    # Fire Brick Wall, laid out like vanilla's brick wall (a tier 2 foundry's corners)
    for m, parent in (('post', 'template_wall_post'), ('side', 'template_wall_side'), ('side_tall', 'template_wall_side_tall')):
        write(f'{bm}/fire_brick_wall_{m}.json', {'parent': f'minecraft:block/{parent}', 'textures': {'wall': tex('fire_bricks')}})
    write(f'{bm}/fire_brick_wall_inventory.json', {'parent': 'minecraft:block/wall_inventory', 'textures': {'wall': tex('fire_bricks')}})
    parts = [{'when': {'up': 'true'}, 'apply': {'model': f'{NS}:block/fire_brick_wall_post'}}]
    for side, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for h, m in (('low', 'side'), ('tall', 'side_tall')):
            a = {'model': f'{NS}:block/fire_brick_wall_{m}', 'uvlock': True}
            if y:
                a['y'] = y
            parts.append({'when': {side: h}, 'apply': a})
    write(f'{bs}/fire_brick_wall.json', {'multipart': parts})

    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    variants = {}
    for t in (1, 2):
        for lit in (False, True):
            name = f'controller_t{t}_{"on" if lit else "off"}'
            write(f'{bm}/{name}.json', {'parent': 'minecraft:block/orientable', 'textures': {
                'front': tex(f'tier{t}/controller_{"on" if lit else "off"}'), 'side': bricks(t), 'top': bricks(t)}})
            for f, y in rot.items():
                v = {'model': f'{NS}:block/{name}'}
                if y:
                    v['y'] = y
                variants[f'facing={f},lit={str(lit).lower()},tier={t}'] = v
    write(f'{bs}/foundry_controller.json', {'variants': variants})

    def part(block, model_for):
        variants = {}
        for t in (1, 2):
            for lit in (False, True):
                name, model = model_for(t, lit)
                write(f'{bm}/{name}.json', model)
                variants[f'lit={str(lit).lower()},tier={t}'] = {'model': f'{NS}:block/{name}'}
        write(f'{bs}/{block}.json', {'variants': variants})

    on = lambda lit: 'on' if lit else 'off'
    part('solid_fuel_heater', lambda t, lit: (f'solid_fuel_heater_{on(lit)}', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': tex(f'heater_{on(lit)}'), 'top': bricks(t), 'bottom': bricks(t)}}))
    part('lava_heater', lambda t, lit: (f'lava_heater_t{t}_{on(lit)}', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': tex(f'lava_heater_{on(lit)}'), 'top': bricks(t), 'bottom': bricks(t)}}))
    part('air_duct', lambda t, lit: (f'air_duct_t{t}_{on(lit)}', {'parent': 'minecraft:block/cube_all', 'textures': {
        'all': tex(f'tier{t}/duct_{on(lit)}')}}))
    part('foundry_drain', lambda t, lit: (f'drain_t{t}_{on(lit)}', {'parent': 'minecraft:block/cube_column', 'textures': {
        'side': tex(f'tier{t}/drain_{on(lit)}'), 'end': bricks(t)}}))

    variants = {}
    for t in (1, 2):
        write(f'{bm}/tap_t{t}.json', {'parent': f'{NS}:block/template/faucet', 'textures': {'tex': tex(f'tier{t}/tap')}})
        for f, y in rot.items():
            v = {'model': f'{NS}:block/tap_t{t}'}
            if y:
                v['y'] = y
            variants[f'facing={f},tier={t}'] = v
    write(f'{bs}/foundry_tap.json', {'variants': variants})

    for b in ('table', 'basin'):
        write(f'{bm}/casting_{b}.json', {'parent': f'{NS}:block/template/{b}', 'textures': {
            'top': tex(f'casting/{b}_top'), 'side': tex(f'casting/{b}_side'), 'bottom': tex(f'casting/{b}_bottom')}})
        write(f'{bs}/casting_{b}.json', {'variants': {'': {'model': f'{NS}:block/casting_{b}'}}})

    item_parent = {'fire_bricks': 'fire_bricks', 'fire_brick_wall': 'fire_brick_wall_inventory', 'foundry_controller': 'controller_t1_off', 'solid_fuel_heater': 'solid_fuel_heater_off',
                   'lava_heater': 'lava_heater_t2_off', 'air_duct': 'air_duct_t1_off', 'foundry_drain': 'drain_t1_off',
                   'foundry_tap': 'tap_t1', 'casting_table': 'casting_table', 'casting_basin': 'casting_basin'}
    for item, model in item_parent.items():
        write(f'{im}/{item}.json', {'parent': f'{NS}:block/{model}'})

    write(f'{im}/fire_clay.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/clay_ball'}})
    write(f'{im}/fire_brick.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/fire_brick'}})
    for kind in ('gold_cast', 'sand_cast'):
        overrides = []
        shapes = [(p, p) for p in BASE_PATTERNS] + [('part_' + part, texture) for part, _, _, _, texture in SG_PARTS]
        for i, (name, texture) in enumerate(shapes):
            if i == 0:
                continue
            write(f'{im}/{kind}_{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{kind}_{texture}'}})
            overrides.append({'predicate': {f'{NS}:pattern': i}, 'model': f'{NS}:item/{kind}_{name}'})
        write(f'{im}/{kind}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{kind}_ingot'},
                                    'overrides': overrides})

    for m in METALS:
        write(f'{bm}/molten_{m}.json', {'textures': {'particle': tex('fluid/molten_still')}})
        write(f'{bs}/molten_{m}.json', {'variants': {'': {'model': f'{NS}:block/molten_{m}'}}})
        write(f'{im}/molten_{m}_bucket.json', {'parent': 'neoforge:item/bucket', 'loader': 'neoforge:fluid_container',
                                                 'fluid': molten(m)})

    lang = {'itemGroup.tieredfoundry': 'Tiered Foundry'}
    for b, n in BLOCKS.items():
        lang[f'block.{NS}.{b}'] = n
    lang[f'item.{NS}.fire_clay'] = 'Fire Clay'
    lang[f'item.{NS}.fire_brick'] = 'Fire Brick'
    for kind, n in (('gold_cast', 'Gold'), ('sand_cast', 'Sand')):
        lang[f'item.{NS}.{kind}'] = f'{n} Cast'
        for p in BASE_PATTERNS:
            lang[f'item.{NS}.{kind}.{p}'] = f'{n} {p.title()} Cast'
        lang[f'item.{NS}.{kind}.part'] = f'{n} %s Cast'
    for m, d in METALS.items():
        lang[f'fluid.{NS}.molten_{m}'] = d['name']
        lang[f'fluid_type.{NS}.molten_{m}'] = d['name']
        lang[f'block.{NS}.molten_{m}'] = d['name']
        lang[f'item.{NS}.molten_{m}_bucket'] = f'{d["name"]} Bucket'
    lang.update({
        'tooltip.tieredfoundry.gold_cast': 'Reusable. Place on a casting table and pour.',
        'tooltip.tieredfoundry.sand_cast': 'Breaks after one pour.',
        'message.tieredfoundry.not_linked': 'Not part of a formed foundry',
        'message.tieredfoundry.filter_set': 'Only accepts %s',
        'message.tieredfoundry.filter_cleared': 'Filter cleared',
        'screen.tieredfoundry.title': 'Foundry · Tier %s · Ore ×%s',
        'screen.tieredfoundry.title_unformed': 'Foundry · not formed',
        'screen.tieredfoundry.help': 'Build behind it: an air interior up to 7×7×8 over a floor of heaters, ringed by bricks (air intakes in the ring), walls of bricks and drains, brick walls up every corner, all one kind of brick.',
        'screen.tieredfoundry.mb': '%s mB',
        'screen.tieredfoundry.total': 'Total %s / %s mB',
        'screen.tieredfoundry.pours_first': 'Drains pour this first',
        'screen.tieredfoundry.click_bottom': 'Click to pour this first',
        'screen.tieredfoundry.heat': 'Heat %s° (heading for %s°)',
        'screen.tieredfoundry.max_heat': 'These bricks take up to %s°',
        'screen.tieredfoundry.heat_loss': 'Loses %s° a second when the heaters stop (better bricks and more fans: slower)',
        'screen.tieredfoundry.fan': 'Fan blowing at %s RPM: hotter, ores ×1.4 in brick, alloying on',
        'screen.tieredfoundry.no_fan': 'No fan: blow a Create fan into an air intake to alloy',
        'screen.tieredfoundry.lava': 'Lava %s / %s mB',
        'screen.tieredfoundry.melts_at': 'Melts at %s°',
        'screen.tieredfoundry.melts_to': 'Into %s mB of %s',
        'screen.tieredfoundry.too_cold': 'Too cold to melt yet',
        'status.tieredfoundry.unchecked': 'Checking…',
        'status.tieredfoundry.ok': 'Running',
        'status.tieredfoundry.no_interior': 'The block behind the controller must be air (the inside of the foundry)',
        'status.tieredfoundry.too_wide': 'The inside is wider than 7 blocks',
        'status.tieredfoundry.blocked_interior': 'Something is in the way inside the foundry',
        'status.tieredfoundry.bad_floor': 'Floor block at %s must be a heater: the whole floor under the inside is heaters',
        'status.tieredfoundry.bad_ring': 'Block at %s, in the ring around the heaters, must be bricks or an air intake',
        'status.tieredfoundry.bad_corner': 'Corner at %s must be a wall of the foundry\'s bricks (Brick Wall, or Fire Brick Wall for fire bricks)',
        'status.tieredfoundry.mixed_heaters': 'Mixed heaters at %s: the floor is all solid fuel heaters or all lava heaters',
        'status.tieredfoundry.bad_wall': 'Wall block at %s must be bricks or a drain',
        'status.tieredfoundry.two_controllers': 'A second controller is in the wall at %s',
        'status.tieredfoundry.mixed_bricks': 'Mixed bricks at %s: use one kind throughout',
        'status.tieredfoundry.shared_part': 'The block at %s belongs to another foundry',
        'status.tieredfoundry.no_heaters': 'No heaters in the floor',
        'status.tieredfoundry.lava_needs_fire_bricks': 'Lava heaters only work in a fire brick foundry',
        'status.tieredfoundry.no_fuel': 'Out of fuel: put fuel in a solid fuel heater',
        'status.tieredfoundry.no_lava': 'Out of lava: pipe lava into a lava heater',
        'status.tieredfoundry.no_fuel_or_lava': 'Out of fuel and lava',
        'status.tieredfoundry.full': 'Full: drain some metal out',
        'status.tieredfoundry.too_hot_for_tier': '%s needs hotter bricks than these',
        'status.tieredfoundry.needs_more_heat': '%s needs more heat (better fuel, a fan or lava)',
        'emi.category.tieredfoundry.melting': 'Foundry Melting',
        'emi.category.tieredfoundry.alloying': 'Foundry Alloying',
        'emi.category.tieredfoundry.casting_table': 'Table Casting',
        'emi.category.tieredfoundry.casting_basin': 'Basin Casting',
        'emi.tieredfoundry.temp': 'Temp: %s°C',
        'emi.tieredfoundry.temp_fan': '%s°C + fan',
        'emi.tieredfoundry.ore': 'Ore ×%s–%s',
        'emi.tieredfoundry.alloy_temp': '%s°, needs a fan',
        'emi.info.tieredfoundry.sand_cast': 'Put an ingot, nugget, plate, gem or Silent Gear part on a casting table, then click it with sand: you get the item back and a sand cast that breaks after one pour.',
        'emi.info.tieredfoundry.structure1': 'Foundry: put the controller in a wall facing out. Behind it, an inside of air up to 7×7 and 8 tall.',
        'emi.info.tieredfoundry.structure2': 'Floor under the inside: all heaters (solid fuel or lava). Around it, a ring of bricks; air intakes go in this ring. Walls all round: bricks or drains. Every corner, top to bottom, is a wall block: Brick Wall (Fire Brick Wall in a fire brick foundry).',
        'emi.info.tieredfoundry.structure3': 'One kind of brick throughout. Vanilla bricks: tier 1, up to 1300°, ores ×1.2. A Create fan blowing into an air intake: tier 1.5, alloying, ores ×1.4, slower heat loss.',
        'emi.info.tieredfoundry.structure4': 'Fire bricks (Fire Clay on a casting table glazed with 250 mB of molten glass, 4 to a block): tier 2, up to 1900°, lava heaters, ores ×1.6, and they hold heat longer. Fuel use grows by one heater\'s worth per 9 heaters.',
        'emi.info.tieredfoundry.casting1': 'A tap on a drain pours into the table or basin below: click it or pulse it with redstone. Create spouts and pipes can fill them too.',
        'emi.info.tieredfoundry.casting2': 'Sneak-click a table or basin with a filled bucket or tank to only accept that fluid; sneak-click with an empty hand to clear.',
    })
    lang.update(ponder_lang())
    write(os.path.join(ASSETS, 'lang', 'en_us.json'), lang)


def ponder_lang():
    """Ponder looks texts up as <modid>.ponder.<scene>.text_<n>, numbered in the order each scene shows them."""
    src = open(os.path.join(ROOT, '..', 'java', 'dev', 'empengines', NS, 'client', 'ponder',
                            'FoundryScenes.java'), encoding='utf-8').read()
    out = {}
    for body in re.split(r'public static void \w+\(SceneBuilder', src)[1:]:
        m = re.search(r'title\("(\w+)", "([^"]+)"\)', body)
        if not m:
            continue
        out[f'{NS}.ponder.{m.group(1)}.header'] = m.group(2)
        for i, t in enumerate(re.findall(r'\.text\("([^"]+)"\)', body), 1):
            out[f'{NS}.ponder.{m.group(1)}.text_{i}'] = t
    return out


# ------------------------------------------------------------------------------------------------------------------ data

def data():
    for sub in ('recipe', 'loot_table'):
        d = os.path.join(DATA, sub)
        if os.path.isdir(d):
            shutil.rmtree(d)
    rec = os.path.join(DATA, 'recipe')

    for b in BLOCKS:
        write(os.path.join(DATA, 'loot_table', 'blocks', f'{b}.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{b}'}],
                                                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'),
          {'values': [f'{NS}:{b}' for b in BLOCKS]})
    for kind in ('block', 'item'):   # so it connects like any wall, and counts as one for recipes
        write(os.path.join(ROOT, 'data', 'minecraft', 'tags', kind, 'walls.json'), {'values': [f'{NS}:fire_brick_wall']})

    for m in METALS:
        write(os.path.join(ROOT, 'data', 'c', 'tags', 'fluid', f'molten_{m}.json'),
              {'values': [molten(m), molten(m) + '_flowing']})

    for m, d in METALS.items():
        mod = d.get('mod')
        for ing, amount, ore in d['melt']:
            name = ing.replace('#', '').replace(':', '_').replace('/', '_')
            r = {'type': f'{NS}:melting', 'ingredient': ingredient(ing), 'result': {'id': molten(m), 'amount': amount},
                 'temperature': d['temp']}
            if ore:
                r['ore'] = True
            item_mod = None if ing.startswith('#') else ing.split(':')[0]
            need = mod or (item_mod if item_mod not in ('minecraft', NS) else None)
            write(f'{rec}/melting/{m}/{name}.json', {**conditions(need), **r})
        for kind, pattern, amount in (('ingot', 'ingot', ING), ('nugget', 'nugget', NUG), ('plate', 'plate', ING), ('gem', 'gem', ING), ('block', None, BLK)):
            out = d.get(kind)
            if not out:
                continue
            if isinstance(out, tuple):
                out, amount = out
            r = {'type': f'{NS}:casting', 'block': 'basin' if pattern is None else 'table'}
            if pattern:
                r['pattern'] = pattern
            r['fluid'] = {'tag': f'c:molten_{m}', 'amount': amount}
            r['result'] = {'id': out, 'count': 1}
            out_mod = out.split(':')[0]
            need = mod or (out_mod if out_mod not in ('minecraft', NS) else None)
            write(f'{rec}/casting/{m}/{kind}.json', {**conditions(need), **r})

    # Fire Clay on a casting table, glazed with our own molten glass (not the tag: no other mod's glass skips tier 1)
    write(f'{rec}/casting/glass/fire_brick.json', {'type': f'{NS}:casting', 'block': 'table', 'cast': {'item': f'{NS}:fire_clay'},
                                                   'fluid': {'fluid': molten('glass'), 'amount': 250},
                                                   'result': {'id': f'{NS}:fire_brick', 'count': 1}})

    for name, (ins, out, temp) in ALLOYS.items():
        write(f'{rec}/alloying/{name}.json', {**conditions(ALLOY_MOD.get(name)), 'type': f'{NS}:alloying',
                                              'inputs': [{'tag': f'c:molten_{m}', 'amount': a} for m, a in ins],
                                              'result': {'id': molten(out[0]), 'amount': out[1]}, 'temperature': temp})

    for part, part_type, gear_type, count, _ in SG_PARTS:
        write(f'{rec}/part_casting/{part}.json', {**conditions(SG), 'type': f'{NS}:part_casting', 'part': f'{SG}:{part}',
                                                  'part_type': f'{SG}:{part_type}', 'gear_type': f'{SG}:{gear_type}',
                                                  'material_count': count})

    def shaped(name, pattern, key, result, count=1):
        write(f'{rec}/crafting/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                                              'key': {k: ingredient(v) for k, v in key.items()},
                                              'result': {'id': result, 'count': count}})

    shaped('foundry_controller', ['BCB', 'CGC', 'BCB'], {'B': 'minecraft:bricks', 'C': '#c:ingots/copper', 'G': 'minecraft:glass_pane'}, f'{NS}:foundry_controller')
    shaped('solid_fuel_heater', ['BBB', 'BFB', 'BBB'], {'B': 'minecraft:brick', 'F': 'minecraft:furnace'}, f'{NS}:solid_fuel_heater')
    shaped('fire_bricks', ['FF', 'FF'], {'F': f'{NS}:fire_brick'}, f'{NS}:fire_bricks')
    shaped('fire_brick_wall', ['FFF', 'FFF'], {'F': f'{NS}:fire_bricks'}, f'{NS}:fire_brick_wall', 6)
    write(f'{rec}/stonecutting/fire_brick_wall.json', {'type': 'minecraft:stonecutting', 'ingredient': {'item': f'{NS}:fire_bricks'},
                                                        'result': {'id': f'{NS}:fire_brick_wall', 'count': 1}})
    shaped('lava_heater', ['FFF', 'PSP', 'FFF'], {'F': f'{NS}:fire_bricks', 'P': 'create:fluid_pipe', 'S': f'{NS}:solid_fuel_heater'}, f'{NS}:lava_heater')
    shaped('air_duct', ['BIB', 'I I', 'BIB'], {'B': 'minecraft:brick', 'I': 'minecraft:iron_bars'}, f'{NS}:air_duct')
    shaped('foundry_drain', ['B B', 'C C', 'B B'], {'B': 'minecraft:brick', 'C': '#c:ingots/copper'}, f'{NS}:foundry_drain')
    shaped('foundry_tap', ['BCB', ' B '], {'B': 'minecraft:brick', 'C': '#c:ingots/copper'}, f'{NS}:foundry_tap')
    shaped('casting_table', ['BBB', 'B B', 'B B'], {'B': 'minecraft:brick'}, f'{NS}:casting_table')
    shaped('casting_basin', ['B B', 'B B', 'BBB'], {'B': 'minecraft:brick'}, f'{NS}:casting_basin')
    write(f'{rec}/crafting/fire_clay.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                             'ingredients': [{'item': 'minecraft:clay_ball'}, {'tag': 'minecraft:sand'}, {'item': 'minecraft:gravel'}],
                                             'result': {'id': f'{NS}:fire_clay', 'count': 1}})
    write(f'{rec}/mixing/fire_clay.json', {'type': 'create:mixing',
                                           'ingredients': [{'item': 'minecraft:clay_ball'}, {'tag': 'minecraft:sand'}, {'item': 'minecraft:gravel'}],
                                           'results': [{'id': f'{NS}:fire_clay', 'count': 2}]})


if __name__ == '__main__':
    assets()
    data()
    n = sum(len(f) for _, _, f in os.walk(ROOT))
    print(f'wrote Tiered Foundry JSON ({n} resource files in total)')
