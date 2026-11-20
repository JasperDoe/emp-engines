#!/usr/bin/env python3
"""Named Apotheosis Elites for Night Raid mobs. Apotheosis 8.9 only ships four Elites (skeleton types from Frontier
up, a Nether archer and a joke goat), so most raiders had no Elite and got our plain "Elite <mob>" stand-in.

    python tools/raids/elites.py   writes pack/kubejs/data/silentraids/apothic_elites/raids/*.json

success_chance 0: they never appear on natural spawns, only when a Night Raid makes a raider an Elite (the raid code
asks Apotheosis for any Elite matching the mob and the player's world tier). No affix gear (the pack has none). The names here are replaced in raids by a random Apotheosis name
(ApothBridge.randomName); they show in Jade/EMI-style places only.
"""
import json
import os
import shutil

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'pack', 'kubejs', 'data', 'silentraids', 'apothic_elites', 'raids')
TIERS = ['haven', 'frontier', 'ascent', 'summit', 'pinnacle']


def mod(attr, lo, hi=None, op='add_value'):
    return {'attribute': attr, 'operation': op, 'value': lo if hi is None else {'min': lo, 'max': hi}}


def elite(name, entities, gear, stats, tiers=TIERS, nbt=None, effects=None):
    basic = {'constraints': {'dimensions': ['minecraft:overworld']}, 'name': name,
             'valid_gear_sets': {t: [f'#{t}_{gear}'] for t in tiers},
             'weights': {t: {'quality': 0.1, 'weight': 100} for t in tiers}}
    if nbt:
        basic['nbt'] = nbt
    s = {'attribute_modifiers': stats, 'enchant_chance': 0.25, 'enchantment_levels': {'primary': 15, 'secondary': 8}}
    if effects:
        s['effects'] = effects
    return {'type': 'apotheosis:elite', 'basic_data': basic, 'entities': entities, 'stats': s, 'success_chance': 0.0}


HP, DMG, KB, SPD, SCALE = 'minecraft:generic.max_health', 'minecraft:generic.attack_damage', \
    'minecraft:generic.knockback_resistance', 'minecraft:generic.movement_speed', 'minecraft:generic.scale'
ARROW = 'apothic_attributes:arrow_damage'

ELITES = {
    'grave_warden': elite('Grave Warden', ['minecraft:zombie', 'minecraft:husk', 'minecraft:zombie_villager'], 'melee', [
        mod(HP, 10, 25), mod(DMG, 0.3, 0.6, 'add_multiplied_total'), mod(KB, 0.4), mod(SCALE, 0.15)]),
    'bone_sentry': elite('Bone Sentry', ['minecraft:skeleton', 'minecraft:stray', 'minecraft:bogged'], 'ranged', [
        mod(HP, 6, 14), mod(ARROW, 0.2, 0.35, 'add_multiplied_total'), mod(KB, 0.3)], tiers=['haven']),
    'broodmother': elite('Broodmother', ['minecraft:spider', 'minecraft:cave_spider'], 'melee', [
        mod(HP, 12, 24), mod(DMG, 0.4, 0.7, 'add_multiplied_total'), mod(SPD, 0.1, None, 'add_multiplied_base'), mod(SCALE, 0.35)]),
    'volatile_creeper': elite('Volatile Creeper', 'minecraft:creeper', 'melee', [mod(HP, 10, 20), mod(KB, 0.5)],
                              nbt={'ExplosionRadius': 4}),
    'hedge_witch': elite('Hedge Witch', 'minecraft:witch', 'melee', [mod(HP, 10, 20), mod(KB, 0.3)],
                         effects=[{'effect': 'minecraft:speed', 'amplifier': 0}]),
    'illager_warlord': elite('Illager Warlord', ['minecraft:vindicator', 'minecraft:evoker'], 'melee', [
        mod(HP, 15, 30), mod(DMG, 0.4, 0.8, 'add_multiplied_total'), mod(KB, 0.5)]),
    'ashen_knight': elite('Ashen Knight', 'minecraft:wither_skeleton', 'melee', [
        mod(HP, 15, 30), mod(DMG, 0.3, 0.6, 'add_multiplied_total'), mod(KB, 0.6)]),
    'ravager_alpha': elite('Ravager Alpha', 'minecraft:ravager', 'melee', [
        mod(HP, 30, 60), mod(DMG, 0.3, 0.5, 'add_multiplied_total'), mod(SCALE, 0.25)]),
}

if __name__ == '__main__':
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    for k, v in ELITES.items():
        with open(os.path.join(OUT, k + '.json'), 'w', newline='\n') as f:
            json.dump(v, f, indent=2)
            f.write('\n')
    print(f'wrote {len(ELITES)} raid Elites')
