"""スキルに重ねて鳴らす音を書き出す（11月24日のご指示「スキルのサウンドがすごく寂しいのでもっとつけてください」）。

使い方: python3 tools/datagen/build_skill_sounds.py [--check 音の名前の一覧.txt]

書き出すもの：data/amemaru/amemaru/skill_sounds/<職業>.json（SkillSounds.java が読む）

スキルの定義の sound（放ったとき）・cast_sound（詠唱・溜めの始まり）・impact_sound（当たったとき）は1つずつしか鳴らないため、
その上に重ねる音を、技の種類ごとの「音の型」（MOTIFS）から組み立てる【補完】。
  - cast：詠唱・溜めを始めたとき / release：放ったとき / impact：当たったとき（1回の届きにつき1度）
  - スキルの定義の音と同じ音は、同じときには重ねない（同じ音が2つ鳴ってうるさくならないように。遅れて鳴らす連打の音は重ねる）
  - 1つのときに重ねるのは6つまで（SkillSounds.MAX_LAYERS）
音はバニラ（Minecraft 1.20.1）の音の名前（minecraft: を省いたもの）。--check に音の名前の一覧（1行に1つ）を渡すと、ない音を探す。
"""
import glob
import json
import os
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/amemaru/amemaru")
SOUNDS_JSON = os.path.join(ROOT, "src/main/resources/assets/amemaru/sounds.json")
OUT = os.path.join(DATA, "skill_sounds")
MAX_LAYERS = 6

# 音の型：（音, 大きさ, 高さ, 高さのゆらぎ, 遅れ tick）
MOTIFS = {
    # ---- 剣と盾（ナイト）
    "slash": [("entity.player.attack.sweep", 0.9, 1.0, 0.08, 0), ("item.trident.throw", 0.45, 1.5, 0.1, 0),
              ("block.chain.hit", 0.35, 1.7, 0.1, 2)],
    "slash_hit": [("entity.player.attack.strong", 0.7, 1.1, 0.1, 0), ("block.chain.hit", 0.4, 1.4, 0.1, 1)],
    "heavy": [("entity.player.attack.strong", 1.0, 0.8, 0.05, 0), ("item.trident.throw", 0.5, 0.9, 0.05, 0),
              ("entity.iron_golem.attack", 0.5, 0.9, 0.05, 2)],
    "heavy_hit": [("entity.zombie.attack_iron_door", 0.3, 0.8, 0.05, 0), ("entity.player.attack.knockback", 0.7, 0.8, 0.05, 0),
                  ("block.anvil.land", 0.15, 1.6, 0.05, 1)],
    "shield": [("item.shield.block", 0.9, 0.9, 0.08, 0), ("item.armor.equip_iron", 0.6, 0.8, 0.05, 1),
               ("block.anvil.land", 0.15, 1.7, 0.05, 2)],
    "shield_hit": [("entity.player.attack.knockback", 0.8, 0.8, 0.05, 0), ("item.shield.block", 0.5, 0.7, 0.05, 1)],
    "guard": [("item.shield.block", 0.7, 0.8, 0.05, 0), ("item.armor.equip_netherite", 0.7, 1.0, 0.05, 2),
              ("block.beacon.power_select", 0.35, 1.4, 0.05, 4)],
    "chain": [("block.chain.place", 0.9, 0.9, 0.08, 0), ("block.chain.break", 0.6, 1.2, 0.08, 2), ("item.armor.equip_chain", 0.6, 1.1, 0.05, 4)],
    "chain_hit": [("block.chain.hit", 0.8, 0.9, 0.1, 0), ("entity.player.attack.knockback", 0.5, 1.0, 0.05, 1)],
    "warcry": [("entity.ravager.roar", 0.45, 1.25, 0.05, 0), ("block.bell.use", 0.5, 0.8, 0.05, 2), ("item.armor.equip_iron", 0.5, 0.9, 0.05, 3),
               ("block.beacon.power_select", 0.3, 1.5, 0.05, 6)],
    "call": [("item.shield.block", 0.5, 1.2, 0.05, 0), ("entity.ravager.roar", 0.2, 1.6, 0.05, 1), ("block.bell.resonate", 0.45, 0.9, 0.05, 2)],
    "oath": [("block.beacon.power_select", 0.7, 1.1, 0.05, 0), ("item.armor.equip_netherite", 0.5, 1.0, 0.05, 2),
             ("block.bell.resonate", 0.4, 1.2, 0.05, 3), ("block.amethyst_block.chime", 0.6, 1.3, 0.1, 5)],
    "castle": [("block.beacon.activate", 0.8, 0.9, 0.0, 0), ("item.armor.equip_netherite", 0.7, 0.8, 0.0, 2), ("block.bell.use", 0.6, 0.7, 0.0, 3),
               ("block.anvil.land", 0.2, 1.2, 0.0, 4), ("block.bell.resonate", 0.5, 0.9, 0.0, 6)],
    "cleave_cast": [("block.beacon.ambient", 0.7, 1.4, 0.0, 0), ("item.trident.riptide_2", 0.5, 1.1, 0.0, 4)],
    "cleave": [("entity.lightning_bolt.impact", 0.8, 1.2, 0.0, 0), ("entity.player.attack.sweep", 1.0, 0.7, 0.0, 0),
               ("item.trident.riptide_3", 0.6, 1.0, 0.0, 1), ("entity.warden.sonic_boom", 0.25, 1.6, 0.0, 2)],
    "cleave_hit": [("entity.generic.explode", 0.5, 1.4, 0.05, 0), ("block.anvil.land", 0.2, 1.4, 0.05, 1)],
    "earth": [("entity.ravager.attack", 0.8, 0.9, 0.05, 0), ("block.basalt.break", 0.8, 0.7, 0.05, 1), ("entity.generic.explode", 0.35, 1.3, 0.05, 2)],
    "quake_hit": [("block.deepslate.break", 0.8, 0.8, 0.05, 0), ("entity.player.big_fall", 0.6, 0.8, 0.05, 1),
                  ("entity.generic.explode", 0.3, 1.5, 0.05, 1)],
    "leap": [("entity.phantom.swoop", 0.5, 1.4, 0.05, 0), ("item.trident.throw", 0.5, 0.8, 0.05, 2)],
    "landing": [("entity.generic.explode", 0.6, 1.0, 0.05, 0), ("entity.goat.ram_impact", 0.7, 0.9, 0.05, 0),
                ("block.deepslate.break", 0.7, 0.8, 0.05, 1)],
    "dash": [("entity.phantom.swoop", 0.6, 1.5, 0.08, 0), ("entity.player.attack.sweep", 0.4, 1.4, 0.05, 2)],
    "spin": [("entity.player.attack.sweep", 0.9, 0.85, 0.05, 0), ("item.trident.riptide_1", 0.4, 1.3, 0.05, 1),
             ("entity.player.attack.sweep", 0.7, 1.15, 0.05, 3)],
    "wheel": [("item.trident.riptide_2", 0.6, 1.1, 0.0, 0), ("entity.player.attack.sweep", 0.8, 0.9, 0.0, 2),
              ("entity.player.attack.sweep", 0.6, 1.2, 0.0, 6)],
    "charge_cast": [("block.respawn_anchor.charge", 0.5, 1.0, 0.0, 0), ("block.beacon.ambient", 0.4, 1.4, 0.0, 4)],
    # ---- 魔法（ウィザード）
    "fire": [("entity.blaze.shoot", 0.6, 1.1, 0.08, 0), ("entity.ghast.shoot", 0.25, 1.5, 0.05, 0), ("block.fire.ambient", 0.7, 1.3, 0.1, 1)],
    "fire_hit": [("entity.generic.burn", 0.5, 1.2, 0.1, 0), ("entity.firework_rocket.blast", 0.35, 1.3, 0.1, 0),
                 ("block.fire.extinguish", 0.35, 1.5, 0.1, 1)],
    "ice": [("block.powder_snow.break", 0.8, 0.9, 0.08, 0), ("block.amethyst_cluster.break", 0.6, 1.5, 0.1, 1),
            ("entity.player.hurt_freeze", 0.4, 1.3, 0.05, 2)],
    "ice_hit": [("block.glass.break", 0.6, 1.3, 0.1, 0), ("block.powder_snow.step", 0.6, 1.0, 0.05, 0), ("block.amethyst_block.break", 0.5, 1.6, 0.1, 1)],
    "frost_field": [("block.powder_snow.place", 0.8, 0.8, 0.05, 0), ("item.elytra.flying", 0.2, 1.6, 0.0, 0),
                    ("block.amethyst_block.resonate", 0.6, 1.4, 0.05, 2)],
    "ice_cast": [("block.amethyst_block.resonate", 0.7, 1.1, 0.0, 0), ("block.beacon.ambient", 0.4, 1.6, 0.0, 2),
                 ("block.powder_snow.step", 0.5, 0.8, 0.0, 6)],
    "lightning": [("block.respawn_anchor.charge", 0.6, 1.6, 0.05, 0), ("entity.firework_rocket.blast", 0.5, 1.6, 0.05, 1),
                  ("entity.lightning_bolt.impact", 0.4, 1.5, 0.05, 2)],
    "lightning_hit": [("entity.lightning_bolt.impact", 0.7, 1.3, 0.1, 0), ("entity.firework_rocket.blast_far", 0.5, 1.2, 0.1, 1),
                      ("entity.lightning_bolt.thunder", 0.25, 1.5, 0.1, 2)],
    "star": [("entity.firework_rocket.twinkle", 0.6, 1.2, 0.1, 0), ("entity.illusioner.cast_spell", 0.35, 1.3, 0.05, 0),
             ("block.amethyst_block.resonate", 0.6, 1.4, 0.1, 1)],
    "star_hit": [("entity.firework_rocket.large_blast", 0.5, 1.2, 0.1, 0), ("block.amethyst_block.break", 0.6, 1.3, 0.1, 1),
                 ("entity.firework_rocket.twinkle_far", 0.5, 1.1, 0.1, 4)],
    "star_cast": [("block.beacon.ambient", 0.5, 1.5, 0.0, 0), ("block.amethyst_block.resonate", 0.5, 1.3, 0.0, 3)],
    "meteor_cast": [("block.beacon.ambient", 0.7, 1.0, 0.0, 0), ("entity.illusioner.prepare_blindness", 0.3, 1.2, 0.0, 6),
                    ("block.respawn_anchor.charge", 0.5, 0.8, 0.0, 12)],
    "meteor_hit": [("entity.dragon_fireball.explode", 0.5, 0.9, 0.1, 0), ("entity.firework_rocket.large_blast", 0.5, 0.8, 0.1, 1),
                   ("block.deepslate.break", 0.6, 0.7, 0.1, 2)],
    "light": [("block.beacon.activate", 0.5, 1.7, 0.05, 0), ("item.trident.riptide_1", 0.5, 1.5, 0.05, 1), ("block.amethyst_block.chime", 0.7, 1.6, 0.1, 2)],
    "light_hit": [("block.amethyst_block.break", 0.6, 1.5, 0.1, 0), ("entity.firework_rocket.blast", 0.4, 1.7, 0.1, 0)],
    "arcane_buff": [("block.enchantment_table.use", 0.8, 1.1, 0.05, 0), ("block.amethyst_block.resonate", 0.6, 1.3, 0.05, 2),
                    ("block.respawn_anchor.charge", 0.4, 1.5, 0.05, 4)],
    "barrier": [("block.amethyst_cluster.place", 0.8, 1.0, 0.05, 0), ("block.respawn_anchor.charge", 0.45, 1.5, 0.05, 2),
                ("block.beacon.power_select", 0.4, 1.6, 0.05, 4)],
    "barrier_hit": [("block.amethyst_cluster.place", 0.5, 1.4, 0.05, 0), ("block.note_block.chime", 0.4, 1.8, 0.05, 2)],
    "blink": [("entity.illusioner.mirror_move", 0.6, 1.3, 0.05, 0), ("block.amethyst_block.chime", 0.6, 1.5, 0.1, 1),
              ("entity.ender_eye.death", 0.4, 1.4, 0.05, 2)],
    "thread": [("block.amethyst_block.chime", 0.6, 1.4, 0.1, 0), ("block.enchantment_table.use", 0.5, 1.4, 0.05, 2)],
    "explosion_hit": [("entity.generic.explode", 0.6, 1.1, 0.1, 0), ("entity.firework_rocket.large_blast", 0.4, 0.9, 0.1, 1)],
    # ---- 癒しと鈴（ヒーラー）
    "bell": [("block.note_block.bell", 0.7, 1.0, 0.05, 0), ("block.amethyst_block.chime", 0.6, 1.3, 0.1, 2), ("block.bell.resonate", 0.25, 1.5, 0.05, 4)],
    "bell_hit": [("block.bell.use", 0.5, 1.4, 0.05, 0), ("block.amethyst_block.break", 0.4, 1.5, 0.1, 1)],
    "heal": [("block.amethyst_block.chime", 0.7, 1.2, 0.1, 0), ("block.note_block.chime", 0.5, 1.5, 0.05, 2),
             ("entity.experience_orb.pickup", 0.3, 0.8, 0.1, 3)],
    "heal_hit": [("block.note_block.chime", 0.5, 1.8, 0.05, 0), ("entity.experience_orb.pickup", 0.35, 1.1, 0.1, 1),
                 ("block.amethyst_block.resonate", 0.4, 1.6, 0.05, 3)],
    "big_heal": [("block.bell.resonate", 0.6, 1.2, 0.0, 0), ("block.amethyst_block.chime", 0.8, 1.0, 0.1, 2), ("block.note_block.chime", 0.6, 1.4, 0.05, 4),
                 ("block.note_block.chime", 0.5, 1.8, 0.05, 7)],
    "revive": [("item.totem.use", 0.35, 1.3, 0.0, 0), ("block.bell.resonate", 0.6, 1.0, 0.0, 2), ("block.beacon.activate", 0.5, 1.4, 0.0, 4)],
    "holy_cast": [("block.beacon.ambient", 0.7, 1.2, 0.0, 0), ("block.bell.resonate", 0.4, 1.4, 0.0, 4), ("block.amethyst_block.resonate", 0.5, 1.2, 0.0, 10)],
    "blessing": [("block.beacon.power_select", 0.6, 1.3, 0.05, 0), ("block.note_block.pling", 0.5, 1.4, 0.05, 2), ("block.note_block.pling", 0.45, 1.8, 0.05, 4)],
    "sanctuary": [("block.beacon.activate", 0.6, 1.2, 0.0, 0), ("block.bell.resonate", 0.5, 1.3, 0.0, 3), ("block.amethyst_block.resonate", 0.6, 1.0, 0.0, 6)],
    "rain": [("weather.rain.above", 0.4, 1.2, 0.0, 0), ("entity.player.splash", 0.5, 1.3, 0.05, 0), ("block.amethyst_block.chime", 0.6, 1.4, 0.1, 2)],
    "judgment": [("block.bell.use", 0.8, 0.7, 0.0, 0), ("block.anvil.land", 0.2, 1.3, 0.0, 1), ("entity.lightning_bolt.impact", 0.35, 1.6, 0.0, 2)],
    # ---- 弓（アーチャー）
    "arrow": [("item.crossbow.shoot", 0.5, 1.3, 0.1, 0), ("item.trident.throw", 0.3, 1.8, 0.1, 1)],
    "arrow_hit": [("entity.arrow.hit", 0.8, 1.0, 0.1, 0), ("item.trident.hit", 0.35, 1.5, 0.1, 0)],
    "multi_arrow": [("item.crossbow.shoot", 0.4, 1.4, 0.1, 0), ("entity.arrow.shoot", 0.7, 1.15, 0.05, 2), ("entity.arrow.shoot", 0.6, 1.3, 0.05, 4)],
    "heavy_arrow": [("item.crossbow.shoot", 0.9, 0.8, 0.05, 0), ("item.trident.throw", 0.6, 0.9, 0.05, 0), ("item.trident.riptide_1", 0.5, 1.3, 0.05, 1)],
    "heavy_arrow_hit": [("item.trident.hit", 0.8, 0.9, 0.1, 0), ("entity.firework_rocket.blast", 0.35, 1.2, 0.1, 1)],
    "flash": [("entity.firework_rocket.twinkle", 0.6, 1.5, 0.1, 0), ("block.amethyst_block.chime", 0.6, 1.7, 0.1, 1)],
    "wind": [("entity.phantom.flap", 0.7, 1.3, 0.1, 0), ("item.elytra.flying", 0.25, 1.6, 0.0, 0), ("entity.player.attack.sweep", 0.35, 1.6, 0.1, 2)],
    "focus": [("item.spyglass.use", 0.8, 1.0, 0.05, 0), ("block.respawn_anchor.charge", 0.4, 1.6, 0.05, 2), ("block.note_block.pling", 0.4, 1.9, 0.05, 4)],
    "draw_cast": [("item.crossbow.loading_start", 0.8, 0.9, 0.0, 0), ("item.crossbow.loading_middle", 0.6, 0.9, 0.0, 8)],
    "star_arrow": [("entity.firework_rocket.launch", 0.6, 1.2, 0.0, 0), ("block.beacon.activate", 0.4, 1.8, 0.0, 1),
                   ("entity.firework_rocket.twinkle", 0.6, 1.0, 0.0, 6)],
    # ---- 刃と毒と影（ローグ）
    "blade": [("entity.player.attack.sweep", 0.7, 1.4, 0.1, 0), ("block.chain.hit", 0.35, 1.9, 0.1, 1), ("entity.player.attack.sweep", 0.5, 1.6, 0.1, 3)],
    "blade_hit": [("entity.player.attack.crit", 0.6, 1.3, 0.1, 0), ("item.trident.hit", 0.3, 1.8, 0.1, 1)],
    "flurry": [("entity.player.attack.sweep", 0.6, 1.5, 0.1, 2), ("entity.player.attack.sweep", 0.6, 1.3, 0.1, 4), ("entity.player.attack.sweep", 0.6, 1.7, 0.1, 6)],
    "poison": [("entity.splash_potion.throw", 0.5, 1.2, 0.1, 0), ("block.brewing_stand.brew", 0.5, 1.4, 0.05, 1), ("entity.slime.squish", 0.4, 1.3, 0.1, 2)],
    "poison_hit": [("entity.slime.squish", 0.6, 1.0, 0.1, 0), ("entity.puffer_fish.sting", 0.4, 1.3, 0.1, 0), ("block.honey_block.slide", 0.5, 0.8, 0.05, 1)],
    "mist": [("entity.splash_potion.break", 0.6, 0.8, 0.05, 0), ("block.fire.extinguish", 0.5, 0.7, 0.05, 1), ("entity.puffer_fish.blow_up", 0.4, 0.8, 0.05, 2)],
    "smoke": [("block.fire.extinguish", 0.7, 0.9, 0.05, 0), ("entity.puffer_fish.blow_up", 0.5, 0.7, 0.05, 1), ("entity.phantom.flap", 0.4, 1.4, 0.05, 2)],
    "shadow": [("entity.illusioner.mirror_move", 0.5, 1.2, 0.1, 0), ("entity.vex.charge", 0.4, 1.0, 0.1, 0), ("entity.phantom.swoop", 0.5, 1.3, 0.1, 1)],
    "shadow_hit": [("entity.player.attack.crit", 0.7, 1.0, 0.1, 0), ("entity.evoker_fangs.attack", 0.4, 1.4, 0.1, 1)],
    "web": [("block.wool.break", 0.6, 1.2, 0.1, 0), ("block.wool.hit", 0.6, 1.0, 0.1, 1), ("entity.spider.ambient", 0.25, 1.6, 0.1, 2)],
    "web_hit": [("block.wool.place", 0.6, 1.0, 0.1, 0), ("block.wool.break", 0.4, 1.4, 0.1, 1)],
    "throw": [("entity.player.attack.sweep", 0.4, 1.8, 0.1, 0), ("item.trident.throw", 0.5, 1.7, 0.1, 0)],
    "thousand": [("entity.illusioner.mirror_move", 0.4, 1.2, 0.0, 0), ("entity.player.attack.sweep", 0.7, 1.2, 0.1, 0),
                 ("entity.player.attack.sweep", 0.7, 1.5, 0.1, 3), ("entity.player.attack.sweep", 0.7, 1.0, 0.1, 6),
                 ("entity.player.attack.sweep", 0.7, 1.7, 0.1, 9)],
    "venom_buff": [("item.bottle.fill", 0.6, 1.1, 0.05, 2), ("block.brewing_stand.brew", 0.6, 1.2, 0.05, 4)],
    # ---- 拳と気（モンク）
    "fist": [("entity.player.attack.knockback", 0.6, 1.2, 0.1, 0), ("entity.blaze.shoot", 0.2, 1.8, 0.1, 0)],
    "fist_hit": [("entity.player.attack.strong", 0.8, 0.9, 0.1, 0), ("entity.iron_golem.attack", 0.35, 1.4, 0.1, 1)],
    "jab": [("entity.player.attack.strong", 0.6, 1.3, 0.1, 2), ("entity.player.attack.strong", 0.6, 1.1, 0.1, 4),
            ("entity.player.attack.knockback", 0.5, 1.4, 0.1, 6)],
    "kick": [("entity.player.attack.sweep", 0.6, 1.1, 0.1, 0), ("entity.phantom.flap", 0.5, 1.3, 0.1, 1)],
    "kick_hit": [("entity.player.attack.knockback", 0.8, 0.9, 0.1, 0), ("entity.goat.ram_impact", 0.4, 1.3, 0.1, 1)],
    "ki": [("block.respawn_anchor.charge", 0.4, 1.5, 0.05, 0), ("entity.blaze.shoot", 0.35, 1.5, 0.1, 0), ("block.conduit.attack.target", 0.5, 1.3, 0.05, 1)],
    "ki_hit": [("entity.warden.attack_impact", 0.5, 1.4, 0.1, 0), ("entity.player.attack.knockback", 0.6, 1.0, 0.1, 0)],
    "ki_buff": [("block.respawn_anchor.charge", 0.6, 1.2, 0.05, 0), ("entity.blaze.shoot", 0.5, 0.8, 0.05, 1), ("block.fire.ambient", 0.6, 1.1, 0.05, 2),
                ("block.beacon.power_select", 0.5, 1.4, 0.05, 3)],
    "calm": [("ambient.underwater.exit", 0.5, 1.2, 0.05, 0), ("block.bubble_column.upwards_inside", 0.4, 1.3, 0.05, 1),
             ("block.amethyst_block.resonate", 0.4, 1.2, 0.05, 3)],
    "thunder_fist": [("entity.iron_golem.attack", 0.7, 0.8, 0.0, 0), ("entity.lightning_bolt.impact", 0.35, 1.5, 0.0, 1),
                     ("entity.generic.explode", 0.3, 1.6, 0.0, 1)],
    "dragon": [("entity.blaze.shoot", 0.6, 0.9, 0.05, 0), ("entity.phantom.flap", 0.6, 1.0, 0.05, 1), ("entity.ender_dragon.flap", 0.5, 1.3, 0.05, 2)],
    "throw_body": [("entity.player.attack.sweep", 0.6, 0.9, 0.05, 0), ("entity.player.big_fall", 0.8, 0.9, 0.05, 6)],
    "vortex": [("entity.evoker.cast_spell", 0.5, 1.3, 0.05, 0), ("item.trident.riptide_2", 0.5, 1.4, 0.05, 1), ("block.conduit.attack.target", 0.5, 1.1, 0.05, 3)],
    "hundred": [("entity.player.attack.strong", 0.6, 1.3, 0.1, 2), ("entity.player.attack.strong", 0.6, 1.0, 0.1, 4),
                ("entity.player.attack.strong", 0.6, 1.5, 0.1, 6), ("entity.player.attack.knockback", 0.6, 1.2, 0.1, 8)],
    "heal_ki": [("block.respawn_anchor.charge", 0.4, 1.4, 0.05, 0), ("block.amethyst_block.chime", 0.6, 1.2, 0.1, 2), ("block.note_block.chime", 0.4, 1.6, 0.05, 4)],
}

# スキル → 音の型（release・impact・cast。左から順に重ね、6つまで）
SKILLS = {
    # ナイト
    "knight/binding_chain": {"release": ["chain"], "impact": ["chain_hit"]},
    "knight/call": {"release": ["call"]},
    "knight/charge_slash": {"release": ["dash", "slash"], "impact": ["slash_hit"]},
    "knight/crushing_blow": {"release": ["heavy"], "impact": ["heavy_hit"]},
    "knight/earth_splitter": {"release": ["earth"], "impact": ["quake_hit"]},
    "knight/great_wheel": {"release": ["wheel"]},
    "knight/heaven_cleave": {"cast": ["cleave_cast"], "release": ["cleave"], "impact": ["cleave_hit"]},
    "knight/heavy_slash": {"cast": ["charge_cast"], "release": ["heavy"], "impact": ["heavy_hit"]},
    "knight/iron_pledge": {"release": ["chain", "guard"]},
    "knight/iron_resolve": {"release": ["guard"]},
    "knight/leap_slash": {"release": ["leap"], "impact": ["landing"]},
    "knight/oath_circle": {"release": ["oath"]},
    "knight/oath_release": {"release": ["heavy"], "impact": ["heavy_hit", "cleave_hit"]},
    "knight/parry": {"release": ["guard"]},
    "knight/rampart": {"release": ["guard"]},
    "knight/shield_bash": {"release": ["shield"], "impact": ["shield_hit"]},
    "knight/shield_bond": {"release": ["chain"]},
    "knight/shield_rush": {"release": ["dash", "shield"], "impact": ["shield_hit"]},
    "knight/sword_pressure": {"release": ["slash"], "impact": ["slash_hit"]},
    "knight/unfallen_castle": {"release": ["castle"]},
    "knight/unyielding_vow": {"release": ["oath"]},
    "knight/war_cry": {"release": ["warcry"]},
    "knight/whirlwind": {"release": ["spin"], "impact": ["slash_hit"]},
    # ウィザード
    "wizard/absolute_zero": {"release": ["ice", "frost_field"], "impact": ["ice_hit"]},
    "wizard/blink": {"release": ["blink"]},
    "wizard/blizzard": {"release": ["frost_field"]},
    "wizard/chain_lightning": {"release": ["lightning"], "impact": ["lightning_hit"]},
    "wizard/comet": {"cast": ["star_cast"], "release": ["star"], "impact": ["star_hit", "explosion_hit"]},
    "wizard/frost_circle": {"release": ["ice"], "impact": ["ice_hit"]},
    "wizard/frost_mist": {"release": ["frost_field"]},
    "wizard/frost_wave": {"release": ["ice"], "impact": ["ice_hit"]},
    "wizard/ice_garden": {"cast": ["ice_cast"], "release": ["ice"], "impact": ["ice_hit"]},
    "wizard/ice_shard": {"release": ["ice"], "impact": ["ice_hit"]},
    "wizard/icicle": {"release": ["ice"], "impact": ["ice_hit"]},
    "wizard/lamp_barrage": {"cast": ["charge_cast"], "release": ["fire"], "impact": ["fire_hit"]},
    "wizard/lamp_bolt": {"release": ["fire"], "impact": ["fire_hit"]},
    "wizard/light_lance": {"release": ["light"], "impact": ["light_hit"]},
    "wizard/light_veil": {"release": ["barrier"], "impact": ["barrier_hit"]},
    "wizard/mana_focus": {"release": ["arcane_buff"]},
    "wizard/mana_thread": {"release": ["thread"]},
    "wizard/meteor_shower": {"cast": ["meteor_cast"], "release": ["star"], "impact": ["meteor_hit"]},
    "wizard/star_burst": {"release": ["star"], "impact": ["star_hit"]},
    "wizard/star_ring": {"release": ["arcane_buff", "star"]},
    "wizard/star_shield": {"release": ["barrier"]},
    "wizard/starfall": {"cast": ["meteor_cast"], "release": ["star"], "impact": ["meteor_hit"]},
    "wizard/thunderbolt": {"release": ["lightning"], "impact": ["lightning_hit"]},
    # ヒーラー
    "healer/admonishing_bell": {"release": ["bell"], "impact": ["bell_hit"]},
    "healer/bell_heal": {"release": ["heal"], "impact": ["heal_hit"]},
    "healer/bell_strike": {"release": ["bell"], "impact": ["bell_hit"]},
    "healer/blessed_shield": {"release": ["barrier"], "impact": ["barrier_hit"]},
    "healer/blessing_light": {"release": ["blessing"]},
    "healer/bond_mend": {"release": ["heal"], "impact": ["heal_hit"]},
    "healer/bond_sanctuary": {"release": ["sanctuary"]},
    "healer/call_back": {"cast": ["holy_cast"], "release": ["revive"]},
    "healer/calming_circle": {"release": ["sanctuary"]},
    "healer/chorus_bell": {"release": ["blessing", "bell"]},
    "healer/great_call": {"cast": ["holy_cast"], "release": ["revive", "big_heal"], "impact": ["heal_hit"]},
    "healer/guardian_bell": {"release": ["blessing"], "impact": ["barrier_hit"]},
    "healer/healing_rain": {"release": ["rain"]},
    "healer/heavens_bell": {"release": ["bell"], "impact": ["judgment", "heal_hit"]},
    "healer/judgment_bell": {"release": ["bell"], "impact": ["judgment"]},
    "healer/life_thread": {"release": ["thread"], "impact": ["heal_hit"]},
    "healer/mending_light": {"release": ["heal"], "impact": ["heal_hit"]},
    "healer/prayer_circle": {"release": ["big_heal"]},
    "healer/purifying_chime": {"release": ["bell"], "impact": ["bell_hit"]},
    "healer/resonant_healing": {"release": ["big_heal"]},
    "healer/silence_sweep": {"release": ["blessing"]},
    "healer/swelling_bell": {"cast": ["holy_cast"], "release": ["big_heal"], "impact": ["heal_hit"]},
    "healer/swift_prayer": {"release": ["blessing"]},
    # アーチャー
    "archer/arrow_fan": {"release": ["multi_arrow"], "impact": ["arrow_hit"]},
    "archer/arrow_rain": {"release": ["multi_arrow"], "impact": ["arrow_hit"]},
    "archer/arrow_storm": {"release": ["multi_arrow", "wind"]},
    "archer/back_leap": {"release": ["dash", "arrow"], "impact": ["arrow_hit"]},
    "archer/burst_arrow": {"release": ["arrow"], "impact": ["explosion_hit"]},
    "archer/drill_arrow": {"release": ["heavy_arrow"], "impact": ["heavy_arrow_hit"]},
    "archer/flash_arrow": {"release": ["arrow", "flash"], "impact": ["light_hit"]},
    "archer/full_draw": {"cast": ["draw_cast"], "release": ["heavy_arrow"], "impact": ["heavy_arrow_hit"]},
    "archer/gale_dance": {"release": ["wind"]},
    "archer/hawks_gaze": {"release": ["focus"]},
    "archer/hunters_focus": {"release": ["focus"]},
    "archer/marking_arrow": {"release": ["arrow"], "impact": ["arrow_hit"]},
    "archer/piercing_shot": {"release": ["heavy_arrow"], "impact": ["heavy_arrow_hit"]},
    "archer/pinning_arrow": {"release": ["arrow"], "impact": ["arrow_hit", "chain_hit"]},
    "archer/quick_draw": {"release": ["arrow"], "impact": ["arrow_hit"]},
    "archer/ricochet": {"release": ["arrow"], "impact": ["arrow_hit"]},
    "archer/shooting_star": {"cast": ["draw_cast"], "release": ["star_arrow"], "impact": ["star_hit"]},
    "archer/sky_piercer": {"cast": ["draw_cast"], "release": ["heavy_arrow"], "impact": ["lightning_hit"]},
    "archer/sniper_stance": {"release": ["focus"]},
    "archer/spread_arrows": {"release": ["multi_arrow"], "impact": ["arrow_hit"]},
    "archer/triple_shot": {"release": ["multi_arrow"], "impact": ["arrow_hit"]},
    "archer/whirl_arrows": {"release": ["wind", "multi_arrow"], "impact": ["arrow_hit"]},
    "archer/wind_cloak": {"release": ["wind"]},
    # ローグ
    "rogue/binding_threads": {"release": ["web"], "impact": ["web_hit"]},
    "rogue/corroding_edge": {"release": ["blade"], "impact": ["poison_hit"]},
    "rogue/envenom": {"release": ["venom_buff"]},
    "rogue/flurry": {"release": ["flurry"], "impact": ["blade_hit"]},
    "rogue/hamstring": {"release": ["blade"], "impact": ["blade_hit"]},
    "rogue/numbing_needle": {"release": ["throw"], "impact": ["poison_hit"]},
    "rogue/phantom_blades": {"release": ["shadow", "blade"], "impact": ["blade_hit"]},
    "rogue/poison_bloom": {"release": ["poison", "mist"], "impact": ["poison_hit"]},
    "rogue/poison_mist": {"release": ["mist"]},
    "rogue/shadow_burial": {"release": ["shadow"], "impact": ["shadow_hit"]},
    "rogue/shadow_dance": {"release": ["shadow"]},
    "rogue/shadow_double": {"release": ["shadow"]},
    "rogue/shadow_run": {"release": ["shadow", "dash"]},
    "rogue/shadow_stitch": {"release": ["shadow"], "impact": ["shadow_hit"]},
    "rogue/shadow_strike": {"release": ["shadow"], "impact": ["shadow_hit"]},
    "rogue/smoke_bomb": {"release": ["smoke"]},
    "rogue/thousand_blades": {"release": ["thousand"], "impact": ["blade_hit"]},
    "rogue/throwing_knife": {"release": ["throw"], "impact": ["blade_hit"]},
    "rogue/toxic_burst": {"release": ["poison"], "impact": ["poison_hit", "explosion_hit"]},
    "rogue/twin_fangs": {"release": ["blade"], "impact": ["blade_hit"]},
    "rogue/venom_blade": {"release": ["blade"], "impact": ["poison_hit"]},
    "rogue/venom_veil": {"release": ["mist"]},
    "rogue/vital_strike": {"release": ["blade"], "impact": ["shadow_hit"]},
    # モンク
    "monk/air_palm": {"release": ["ki", "wind"], "impact": ["ki_hit"]},
    "monk/back_strike": {"release": ["dash", "fist"], "impact": ["fist_hit"]},
    "monk/chain_kicks": {"release": ["kick"], "impact": ["kick_hit"]},
    "monk/collapsing_fist": {"release": ["dash", "fist"], "impact": ["fist_hit"]},
    "monk/flowing_stance": {"release": ["calm"]},
    "monk/hakkei": {"release": ["ki"], "impact": ["ki_hit"]},
    "monk/heaven_earth": {"release": ["ki_buff", "calm"]},
    "monk/hundred_bond": {"release": ["hundred"], "impact": ["fist_hit"]},
    "monk/hundred_fists": {"release": ["hundred"]},
    "monk/inch_punch": {"release": ["fist"], "impact": ["ki_hit"]},
    "monk/jab_combo": {"release": ["jab"], "impact": ["fist_hit"]},
    "monk/kaisei_fist": {"release": ["fist", "heal_ki"], "impact": ["fist_hit"]},
    "monk/ki_bullet": {"release": ["ki"], "impact": ["ki_hit"]},
    "monk/ki_share": {"release": ["heal_ki"], "impact": ["heal_hit"]},
    "monk/ki_vortex": {"release": ["vortex"], "impact": ["ki_hit"]},
    "monk/kien": {"release": ["ki_buff"]},
    "monk/mountain_breaker": {"release": ["leap"], "impact": ["landing"]},
    "monk/palm_strike": {"release": ["fist"], "impact": ["ki_hit"]},
    "monk/rising_dragon": {"release": ["dragon"], "impact": ["kick_hit"]},
    "monk/shrink_step": {"release": ["dash"], "impact": ["fist_hit"]},
    "monk/spin_kick": {"release": ["kick"], "impact": ["kick_hit"]},
    "monk/thunder_fist": {"cast": ["charge_cast"], "release": ["thunder_fist"], "impact": ["ki_hit"]},
    "monk/turning_throw": {"release": ["throw_body"], "impact": ["fist_hit"]},
}


def vanilla_of(sound_id, mod_sounds):
    """スキルの定義の音（amemaru:skill.xxx）が鳴らす、バニラの音の名前（なければ空）。"""
    if not sound_id:
        return ""
    key = sound_id.split(":", 1)[1]
    entry = mod_sounds.get(key)
    if not entry:
        return ""
    names = [s["name"] if isinstance(s, dict) else s for s in entry.get("sounds", [])]
    return names[0].replace("minecraft:", "") if names else ""


def build_layers(motifs, avoid):
    out = []
    for motif in motifs:
        for sound, volume, pitch, spread, delay in MOTIFS[motif]:
            # スキルの定義の音と同じ音を、同じときに重ねない（遅れて鳴らす連打の音は重ねる）
            if (sound in avoid and delay == 0) or any(l["sound"] == "minecraft:" + sound and l["delay"] == delay for l in out):
                continue
            layer = OrderedDict([("sound", "minecraft:" + sound), ("volume", volume), ("pitch", pitch)])
            if spread:
                layer["spread"] = spread
            layer["delay"] = delay
            out.append(layer)
    out.sort(key=lambda l: l["delay"])
    return out[:MAX_LAYERS]


def main():
    check = None
    if "--check" in sys.argv:
        names = open(sys.argv[sys.argv.index("--check") + 1], encoding="utf-8").read().split()
        check = set(names)
    mod_sounds = json.load(open(SOUNDS_JSON, encoding="utf-8"))
    actives = {}
    for path in sorted(glob.glob(os.path.join(DATA, "skills", "*", "*.json"))):
        d = json.load(open(path, encoding="utf-8"))
        if d.get("type", "active") != "active":
            continue
        actives[os.path.relpath(path, os.path.join(DATA, "skills"))[:-5].replace(os.sep, "/")] = d
    missing = sorted(set(actives) - set(SKILLS))
    extra = sorted(set(SKILLS) - set(actives))
    if missing or extra:
        sys.exit("音の型が決まっていないアクティブスキル：%s／ないスキル：%s" % (missing, extra))
    bad = set()
    for motif in MOTIFS.values():
        for layer in motif:
            if check is not None and layer[0] not in check:
                bad.add(layer[0])
    if bad:
        sys.exit("1.20.1 にない音：%s" % sorted(bad))

    os.makedirs(OUT, exist_ok=True)
    by_job = OrderedDict()
    total = 0
    for skill, plan in SKILLS.items():
        d = actives[skill]
        job = skill.split("/")[0]
        avoid_release = {vanilla_of(d.get("sound"), mod_sounds)}
        avoid_cast = {vanilla_of(d.get("cast_sound"), mod_sounds)}
        avoid_impact = {vanilla_of(d.get("impact_sound"), mod_sounds)}
        entry = OrderedDict()
        for phase, avoid in (("cast", avoid_cast), ("release", avoid_release), ("impact", avoid_impact)):
            if plan.get(phase):
                entry[phase] = build_layers(plan[phase], avoid)
                if not entry[phase]:
                    sys.exit("%s の %s に重ねる音が残りません（スキルの定義の音と同じ音ばかり）" % (skill, phase))
                total += len(entry[phase])
        by_job.setdefault(job, OrderedDict())[skill] = entry
    for job, skills in by_job.items():
        out = OrderedDict([("_comment", "スキルに重ねて鳴らす音（11月24日。tools/datagen/build_skill_sounds.py が書き出す。手で直さない）。"
                                         "cast：詠唱・溜めの始まり、release：放ったとき、impact：当たったとき。"),
                           ("skills", skills)])
        with open(os.path.join(OUT, job + ".json"), "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("スキル %d・重ねる音 %d（%s）" % (len(SKILLS), total, "・".join("%s %d" % (j, len(s)) for j, s in by_job.items())))


if __name__ == "__main__":
    main()
