"""AMEMARU: LOST CONNECTION の Phase 3 の仮モデル（GeckoLib）を作る。

使い方: python3 tools/artgen/make_phase3_models.py <assets/amemaru のフォルダ>

作るもの（ファイルの名前・ボーンの名前・アニメーションの名前は、本番のモデルでも同じにする。docs/phase3.md の表）:
  geo/entity/<敵>.geo.json, animations/entity/<敵>.animation.json, textures/entity/<敵>.png（光る部分は _glowmask.png）
      tsunomofu（ツノモフ）・ishikure_soldier（石くれ兵）・crystal_bee（結晶蜂）・kakekiba_wolf（欠け牙の狼）
  geo/item/bee_crystal_staff.geo.json ほか（蜂晶の杖：先の結晶が浮いて回る）
  geo/armor/ancient_stone_plate・ancient_stone_robe（防具。ボーンは armorHead など8つ）
  geo/accessory/grassland_necklace・ishikure_bracelet・waystone_charm（体に表示するアクセサリー）
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geo import Model, save_anim  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"


def loop(length, bones):
    return {"loop": True, "length": length, "bones": bones}


def once(length, bones, hold=False):
    out = {"length": length, "bones": bones}
    if hold:
        out["loop"] = "hold_on_last_frame"
    return out


# ================================================================ ツノモフ（角のある毛玉）
def tsunomofu():
    cream, beige, brown, dark = "#F3EBDD", "#E2CDB0", "#B58A5C", "#2B2A33"
    m = Model("tsunomofu", 64, 64, bounds=(1.6, 1.6, 0.5), seed=11)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    m.cube("body", [-6, 2, -6], [12, 11, 12], cream, pattern="fluff")
    m.cube("body", [-3, 13, -3], [6, 2, 6], cream, pattern="fluff")
    m.bone("head", [0, 7, -6], "body")
    m.cube("head", [-3, 3, -8], [6, 4, 2], beige)
    m.cube("head", [-3.5, 7.5, -6.6], [2, 2, 1], dark)
    m.cube("head", [1.5, 7.5, -6.6], [2, 2, 1], dark)
    m.bone("horn_left", [3.5, 12, -3], "body")
    m.cube("horn_left", [2.5, 12, -4], [2, 4, 2], brown)
    m.cube("horn_left", [3, 16, -4.5], [1, 2, 1], beige)
    m.bone("horn_right", [-3.5, 12, -3], "body")
    m.cube("horn_right", [-4.5, 12, -4], [2, 4, 2], brown)
    m.cube("horn_right", [-4, 16, -4.5], [1, 2, 1], beige)
    m.bone("tail", [0, 6, 6], "body")
    m.cube("tail", [-1.5, 5, 6], [3, 3, 2], cream, pattern="fluff")
    for name, x, z in [("leg_front_left", 3, -3.5), ("leg_front_right", -3, -3.5), ("leg_back_left", 3, 3.5),
                       ("leg_back_right", -3, 3.5)]:
        m.bone(name, [x, 2, z], "root")
        m.cube(name, [x - 1.5, 0, z - 1.5], [3, 2, 3], brown)
    m.save(ROOT, "entity")
    legs = ["leg_front_left", "leg_back_right", "leg_front_right", "leg_back_left"]
    walk = {"body": {"position": {0: [0, 0, 0], 0.15: [0, 0.6, 0], 0.3: [0, 0, 0], 0.45: [0, 0.6, 0], 0.6: [0, 0, 0]}}}
    for i, leg in enumerate(legs):
        sign = 1 if i < 2 else -1
        walk[leg] = {"rotation": {0: [25 * sign, 0, 0], 0.3: [-25 * sign, 0, 0], 0.6: [25 * sign, 0, 0]}}
    save_anim(ROOT, "entity", "tsunomofu", {
        "idle": loop(2.0, {
            "body": {"position": {0: [0, 0, 0], 1.0: [0, 0.4, 0], 2.0: [0, 0, 0]},
                     "scale": {0: [1, 1, 1], 1.0: [1.03, 0.97, 1.03], 2.0: [1, 1, 1]}},
            "tail": {"rotation": {0: [0, -10, 0], 1.0: [0, 10, 0], 2.0: [0, -10, 0]}}}),
        "walk": loop(0.6, walk),
        "attack": once(0.9, {
            "body": {"rotation": {0: [0, 0, 0], 0.4: [-18, 0, 0], 0.55: [22, 0, 0], 0.9: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.4: [0, 0, 2], 0.55: [0, 0, -3], 0.9: [0, 0, 0]}}}),
        "skill_alert": once(0.5, {
            "body": {"position": {0: [0, 0, 0], 0.2: [0, 3, 0], 0.4: [0, 0, 0]}},
            "horn_left": {"rotation": {0: [0, 0, 0], 0.2: [0, 0, 15], 0.4: [0, 0, 0]}},
            "horn_right": {"rotation": {0: [0, 0, 0], 0.2: [0, 0, -15], 0.4: [0, 0, 0]}}}),
        "death": once(1.5, {
            "root": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 90]}, "position": {0: [0, 0, 0], 0.6: [0, 2, 0], 1.5: [0, -2, 0]},
                     "scale": {0: [1, 1, 1], 1.0: [1, 1, 1], 1.5: [0.6, 0.6, 0.6]}}}, hold=True),
    })


# ================================================================ 石くれ兵（古代の見張り）
def ishikure_soldier():
    stone, dark, moss, core = "#8C8A7E", "#6B695F", "#5E8A4A", "#6FE3D0"
    m = Model("ishikure_soldier", 128, 64, bounds=(2.0, 3.0, 1.3), seed=21)
    m.bone("root", [0, 0, 0])
    m.bone("leg_left", [2.5, 12, 0], "root")
    m.cube("leg_left", [0.5, 0, -2], [4, 12, 4], stone, pattern="stone")
    m.bone("leg_right", [-2.5, 12, 0], "root")
    m.cube("leg_right", [-4.5, 0, -2], [4, 12, 4], stone, pattern="stone")
    m.bone("body", [0, 12, 0], "root")
    m.cube("body", [-6, 12, -3.5], [12, 13, 7], stone, pattern="stone")
    m.cube("body", [-5, 15, -4.5], [10, 8, 1], dark, pattern="stone")
    m.cube("body", [-1.5, 18, -5], [3, 3, 1], core, glow=core, pattern="crystal")
    m.bone("head", [0, 25, 0], "body")
    m.cube("head", [-4, 25, -4], [8, 8, 8], stone, pattern="stone")
    m.cube("head", [-3, 28, -4.6], [6, 1, 1], core, glow=core)
    m.cube("head", [-4, 33, -1], [8, 1, 5], moss)
    m.bone("arm_left", [7.5, 23, 0], "body")
    m.cube("arm_left", [6, 22, -3], [4, 4, 6], dark, pattern="stone")
    m.cube("arm_left", [6.5, 11, -2.5], [5, 11, 5], stone, pattern="stone")
    m.cube("arm_left", [6, 7, -3], [6, 4, 6], dark, pattern="stone")
    m.cube("arm_left", [6, 26, -3], [4, 1, 6], moss)
    m.bone("arm_right", [-7.5, 23, 0], "body")
    m.cube("arm_right", [-10, 22, -3], [4, 4, 6], dark, pattern="stone")
    m.cube("arm_right", [-11.5, 11, -2.5], [5, 11, 5], stone, pattern="stone")
    m.cube("arm_right", [-12, 7, -3], [6, 4, 6], dark, pattern="stone")
    m.cube("arm_right", [-10, 26, -3], [4, 1, 6], moss)
    m.save(ROOT, "entity")
    save_anim(ROOT, "entity", "ishikure_soldier", {
        "idle": loop(4.0, {
            "head": {"rotation": {0: [0, -8, 0], 2.0: [0, 8, 0], 4.0: [0, -8, 0]}},
            "body": {"position": {0: [0, 0, 0], 2.0: [0, 0.3, 0], 4.0: [0, 0, 0]}}}),
        "walk": loop(1.0, {
            "leg_left": {"rotation": {0: [20, 0, 0], 0.5: [-20, 0, 0], 1.0: [20, 0, 0]}},
            "leg_right": {"rotation": {0: [-20, 0, 0], 0.5: [20, 0, 0], 1.0: [-20, 0, 0]}},
            "arm_left": {"rotation": {0: [-15, 0, 0], 0.5: [15, 0, 0], 1.0: [-15, 0, 0]}},
            "arm_right": {"rotation": {0: [15, 0, 0], 0.5: [-15, 0, 0], 1.0: [15, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.25: [0, 0.5, 0], 0.5: [0, 0, 0], 0.75: [0, 0.5, 0], 1.0: [0, 0, 0]}}}),
        "attack": once(1.4, {
            "arm_left": {"rotation": {0: [0, 0, 0], 0.7: [-160, 0, -10], 0.9: [10, 0, 0], 1.4: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 0.7: [-160, 0, 10], 0.9: [10, 0, 0], 1.4: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.7: [-10, 0, 0], 0.9: [18, 0, 0], 1.4: [0, 0, 0]}}}),
        "skill_call": once(1.2, {
            "head": {"rotation": {0: [0, 0, 0], 0.3: [-25, 0, 0], 0.9: [-25, 0, 0], 1.2: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 0.3: [-120, 0, 15], 0.9: [-120, 0, 15], 1.2: [0, 0, 0]}}}),
        "death": once(1.5, {
            "root": {"rotation": {0: [0, 0, 0], 0.8: [80, 0, 0]}, "position": {0: [0, 0, 0], 0.8: [0, 1, 0], 1.5: [0, -3, 0]}},
            "head": {"position": {0: [0, 0, 0], 0.8: [0, 0, 0], 1.2: [0, -2, -3]}}}, hold=True),
    })


# ================================================================ 結晶蜂（小型の古代機械）
def crystal_bee():
    bronze, dark, crystal, amber, wing = "#B08D57", "#7A6038", "#8FD3FF", "#FFD46A", "#E6F4FF"
    m = Model("crystal_bee", 64, 32, bounds=(1.4, 1.2, 0.4), seed=31)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    m.cube("body", [-3, 4, -3], [6, 5, 6], bronze, pattern="metal")
    m.bone("head", [0, 6, -3], "body")
    m.cube("head", [-2.5, 4.5, -6], [5, 4, 3], dark, pattern="metal")
    m.cube("head", [-2, 6, -6.6], [1, 1, 1], amber, glow=amber)
    m.cube("head", [1, 6, -6.6], [1, 1, 1], amber, glow=amber)
    m.bone("abdomen", [0, 6, 3], "body")
    m.cube("abdomen", [-2.5, 3, 3], [5, 5, 6], crystal, glow=crystal, pattern="crystal")
    m.cube("abdomen", [-0.5, 4.5, 9], [1, 1, 2], crystal, glow=crystal)
    m.bone("wing_left", [3, 9, 0], "body")
    m.cube("wing_left", [3, 9, -2], [7, 1, 4], wing)
    m.bone("wing_right", [-3, 9, 0], "body")
    m.cube("wing_right", [-10, 9, -2], [7, 1, 4], wing)
    m.save(ROOT, "entity")
    flap = {"wing_left": {"rotation": {0: [0, 0, 25], 0.1: [0, 0, -25], 0.2: [0, 0, 25]}},
            "wing_right": {"rotation": {0: [0, 0, -25], 0.1: [0, 0, 25], 0.2: [0, 0, -25]}}}
    idle = dict(flap)
    idle["body"] = {"position": {0: [0, 0, 0], 0.5: [0, 0.6, 0], 1.0: [0, 0, 0]}}
    save_anim(ROOT, "entity", "crystal_bee", {
        "idle": loop(1.0, {**idle}),
        "walk": loop(1.0, {**idle, "body": {"rotation": {0: [8, 0, 0], 1.0: [8, 0, 0]},
                                            "position": {0: [0, 0, 0], 0.5: [0, 0.6, 0], 1.0: [0, 0, 0]}}}),
        "attack": once(0.6, {"body": {"rotation": {0: [0, 0, 0], 0.3: [20, 0, 0], 0.6: [0, 0, 0]}}}),
        "skill_needle": once(1.0, {
            "abdomen": {"rotation": {0: [0, 0, 0], 0.6: [55, 0, 0], 0.8: [40, 0, 0], 1.0: [0, 0, 0]},
                        "scale": {0: [1, 1, 1], 0.6: [1.15, 1.15, 1.15], 0.8: [1, 1, 1]}}}),
        "death": once(1.2, {
            "root": {"rotation": {0: [0, 0, 0], 0.8: [0, 0, 140]}, "position": {0: [0, 0, 0], 0.8: [0, -4, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, -60]}},
            "wing_right": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, 60]}}}, hold=True),
    })


# ================================================================ 欠け牙の狼（ロストを見せる敵）
def kakekiba_wolf():
    fur, dark, belly, lost, eye = "#5B6270", "#3F4550", "#7D8494", "#F4F7FB", "#FF5A4A"
    m = Model("kakekiba_wolf", 64, 64, bounds=(2.0, 1.6, 0.6), seed=41)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 10, 0], "root")
    m.cube("body", [-3.5, 7, -6], [7, 7, 12], fur, pattern="fluff")
    m.cube("body", [-3, 6, -5], [6, 1, 10], belly)
    # 白く欠けた部分（ロスト）
    m.cube("body", [-3.7, 10, 1], [2, 3, 3], lost, glow="#DDEBFF")
    m.cube("body", [1.8, 8, -4], [2, 2, 3], lost, glow="#DDEBFF")
    m.cube("body", [-1, 13.6, -2], [3, 1, 4], lost, glow="#DDEBFF")
    m.bone("head", [0, 12, -6], "body")
    m.cube("head", [-3, 9, -11], [6, 6, 5], fur, pattern="fluff")
    m.cube("head", [-1.5, 9, -14], [3, 3, 3], dark)
    m.cube("head", [-2.5, 15, -9], [2, 2, 1], dark)
    m.cube("head", [0.5, 15, -9], [2, 2, 1], dark)
    m.cube("head", [-2.4, 12, -11.6], [1, 1, 1], eye, glow=eye)
    m.cube("head", [1.4, 12, -11.6], [1, 1, 1], eye, glow=eye)
    m.cube("head", [0.6, 8.5, -13.5], [1, 1, 1], lost, glow="#DDEBFF")
    m.bone("tail", [0, 13, 6], "body")
    m.cube("tail", [-1, 8, 6], [2, 6, 2], fur, pattern="fluff")
    for name, x, z in [("leg_front_left", 2, -4), ("leg_front_right", -2, -4), ("leg_back_left", 2, 4),
                       ("leg_back_right", -2, 4)]:
        m.bone(name, [x, 7, z], "root")
        m.cube(name, [x - 1, 0, z - 1], [2, 7, 2], dark)
    m.save(ROOT, "entity")
    walk = {"body": {"position": {0: [0, 0, 0], 0.25: [0, 0.4, 0], 0.5: [0, 0, 0]}}}
    for name, sign in [("leg_front_left", 1), ("leg_back_right", 1), ("leg_front_right", -1), ("leg_back_left", -1)]:
        walk[name] = {"rotation": {0: [30 * sign, 0, 0], 0.25: [-30 * sign, 0, 0], 0.5: [30 * sign, 0, 0]}}
    save_anim(ROOT, "entity", "kakekiba_wolf", {
        "idle": loop(3.0, {
            "tail": {"rotation": {0: [30, -10, 0], 1.5: [30, 10, 0], 3.0: [30, -10, 0]}},
            "body": {"scale": {0: [1, 1, 1], 1.5: [1.02, 1.03, 1], 3.0: [1, 1, 1]}},
            "head": {"rotation": {0: [0, 0, 0], 1.5: [4, 0, 0], 3.0: [0, 0, 0]}}}),
        "walk": loop(0.5, walk),
        "attack": once(0.6, {
            "head": {"rotation": {0: [0, 0, 0], 0.3: [-15, 0, 0], 0.4: [15, 0, 0], 0.6: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.4: [0, 0, -2], 0.6: [0, 0, 0]}}}),
        "skill_charge": once(1.4, {
            "body": {"position": {0: [0, 0, 0], 0.3: [0, -2, 1], 1.2: [0, -2, 1], 1.4: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.3: [6, 0, 0], 1.2: [6, 0, 0], 1.4: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.3: [15, 0, 0], 1.2: [15, 0, 0], 1.4: [0, 0, 0]}},
            "tail": {"rotation": {0: [30, 0, 0], 0.3: [80, 0, 0], 1.2: [80, 0, 0], 1.4: [30, 0, 0]}}}),
        "death": once(1.5, {
            "root": {"rotation": {0: [0, 0, 0], 0.7: [0, 0, 90]}, "position": {0: [0, 0, 0], 0.7: [0, 3, 0], 1.5: [0, 1, 0]}},
            "tail": {"rotation": {0: [30, 0, 0], 0.7: [0, 0, 0]}}}, hold=True),
    })


# ================================================================ 蜂晶の杖（動く・光る武器）
def bee_crystal_staff():
    wood, bronze, crystal = "#7B5A3A", "#C49A55", "#8FD3FF"
    m = Model("bee_crystal_staff", 32, 32, bounds=(1.5, 2.5, 0.5), seed=51)
    m.bone("staff", [0, 0, 0])
    m.cube("staff", [-0.5, -8, -0.5], [1, 22, 1], wood)
    m.cube("staff", [-1, -9, -1], [2, 2, 2], bronze, pattern="metal")
    m.cube("staff", [-1.5, 12, -1.5], [3, 2, 3], bronze, pattern="metal")
    m.cube("staff", [-2.5, 14, -0.5], [1, 3, 1], bronze)
    m.cube("staff", [1.5, 14, -0.5], [1, 3, 1], bronze)
    m.bone("crystal", [0, 17, 0], "staff")
    m.cube("crystal", [-1.5, 15, -1.5], [3, 5, 3], crystal, glow=crystal, pattern="crystal")
    m.bone("shard_a", [0, 17, 0], "crystal")
    m.cube("shard_a", [2.5, 17, -0.5], [1, 1, 1], crystal, glow=crystal)
    m.bone("shard_b", [0, 17, 0], "crystal")
    m.cube("shard_b", [-3.5, 16, -0.5], [1, 1, 1], crystal, glow=crystal)
    m.save(ROOT, "item")
    save_anim(ROOT, "item", "bee_crystal_staff", {
        "idle": loop(4.0, {
            "crystal": {"rotation": {0: [0, 0, 0], 2.0: [0, 180, 0], 4.0: [0, 360, 0]},
                        "position": {0: [0, 0, 0], 1.0: [0, 0.6, 0], 2.0: [0, 0, 0], 3.0: [0, 0.6, 0], 4.0: [0, 0, 0]}},
            "shard_a": {"position": {0: [0, 0, 0], 2.0: [0, 1, 0], 4.0: [0, 0, 0]}},
            "shard_b": {"position": {0: [0, 1, 0], 2.0: [0, 0, 0], 4.0: [0, 1, 0]}}}),
    })


# ================================================================ 防具（古代石の重鎧・法衣）
def armor_bones(m):
    m.bone("armorHead", [0, 24, 0])
    m.bone("armorBody", [0, 24, 0])
    m.bone("armorRightArm", [-5, 22, 0])
    m.bone("armorLeftArm", [5, 22, 0])
    m.bone("armorRightLeg", [-1.9, 12, 0])
    m.bone("armorLeftLeg", [1.9, 12, 0])
    m.bone("armorRightBoot", [-1.9, 12, 0])
    m.bone("armorLeftBoot", [1.9, 12, 0])


def ancient_stone_plate():
    stone, dark, trim, core = "#8F8C80", "#66645A", "#C9B27A", "#6FE3D0"
    m = Model("ancient_stone_plate", 128, 64, bounds=(2.0, 3.0, 1.0), seed=61)
    armor_bones(m)
    m.cube("armorHead", [-4, 24, -4], [8, 8, 8], stone, inflate=1.0, pattern="stone")
    m.cube("armorHead", [-1, 32, -5], [2, 3, 10], trim, pattern="metal")
    m.cube("armorHead", [-3, 27, -5.2], [6, 1, 1], core, glow=core)
    m.cube("armorBody", [-4, 12, -2], [8, 12, 4], stone, inflate=1.05, pattern="stone")
    m.cube("armorBody", [-3, 16, -3.4], [6, 6, 1], dark, pattern="stone")
    m.cube("armorBody", [-1, 18, -4], [2, 2, 1], core, glow=core)
    m.cube("armorBody", [-4, 12, -2], [8, 1, 4], trim, inflate=1.3)
    m.cube("armorRightArm", [-8, 12, -2], [4, 12, 4], stone, inflate=0.9, pattern="stone")
    m.cube("armorRightArm", [-9.5, 22, -3], [5, 3, 6], dark, pattern="stone")
    m.cube("armorLeftArm", [4, 12, -2], [4, 12, 4], stone, inflate=0.9, pattern="stone")
    m.cube("armorLeftArm", [4.5, 22, -3], [5, 3, 6], dark, pattern="stone")
    m.cube("armorRightLeg", [-3.9, 3, -2], [4, 9, 4], stone, inflate=0.6, pattern="stone")
    m.cube("armorLeftLeg", [-0.1, 3, -2], [4, 9, 4], stone, inflate=0.6, pattern="stone")
    m.cube("armorRightBoot", [-3.9, 0, -2], [4, 4, 4], dark, inflate=1.0, pattern="stone")
    m.cube("armorLeftBoot", [-0.1, 0, -2], [4, 4, 4], dark, inflate=1.0, pattern="stone")
    m.save(ROOT, "armor")
    save_anim(ROOT, "armor", "ancient_stone_plate", {"idle": loop(1.0, {})})


def ancient_stone_robe():
    cloth, inner, trim, rune = "#3E5A8C", "#2A3B5E", "#C9B27A", "#8FD3FF"
    m = Model("ancient_stone_robe", 128, 64, bounds=(2.0, 3.0, 1.0), seed=71)
    armor_bones(m)
    m.cube("armorHead", [-4, 24, -4], [8, 8, 8], cloth, inflate=0.8, pattern="fluff")
    m.cube("armorHead", [-2, 32, -1], [4, 3, 4], cloth, pattern="fluff")
    m.cube("armorHead", [-4, 24, -5], [8, 1, 1], trim, inflate=0.2)
    m.cube("armorBody", [-4, 12, -2], [8, 12, 4], cloth, inflate=0.7, pattern="fluff")
    m.cube("armorBody", [-1, 13, -3.2], [2, 10, 1], trim)
    m.cube("armorBody", [-1, 19, -3.6], [2, 2, 1], rune, glow=rune)
    m.cube("armorRightArm", [-8, 12, -2], [4, 12, 4], cloth, inflate=0.6, pattern="fluff")
    m.cube("armorRightArm", [-8.5, 11, -2.5], [5, 3, 5], inner, inflate=0.3)
    m.cube("armorLeftArm", [4, 12, -2], [4, 12, 4], cloth, inflate=0.6, pattern="fluff")
    m.cube("armorLeftArm", [3.5, 11, -2.5], [5, 3, 5], inner, inflate=0.3)
    m.cube("armorRightLeg", [-3.9, 2, -2], [4, 10, 4], cloth, inflate=0.8, pattern="fluff")
    m.cube("armorLeftLeg", [-0.1, 2, -2], [4, 10, 4], cloth, inflate=0.8, pattern="fluff")
    m.cube("armorRightBoot", [-3.9, 0, -2], [4, 3, 4], inner, inflate=0.5)
    m.cube("armorLeftBoot", [-0.1, 0, -2], [4, 3, 4], inner, inflate=0.5)
    m.save(ROOT, "armor")
    save_anim(ROOT, "armor", "ancient_stone_robe", {"idle": loop(1.0, {})})


# ================================================================ 体に表示するアクセサリー
def accessories():
    # 草原の首飾り（胸元）
    m = Model("grassland_necklace", 32, 32, bounds=(1.0, 2.0, 1.0), seed=81)
    armor_bones(m)
    m.bone("pendant", [0, 22, -2.6], "armorBody")
    m.cube("armorBody", [-3, 23, -2.7], [6, 1, 1], "#7A5B3A")
    m.cube("armorBody", [-3, 21, -2.7], [1, 2, 1], "#7A5B3A")
    m.cube("armorBody", [2, 21, -2.7], [1, 2, 1], "#7A5B3A")
    m.cube("pendant", [-1.5, 18, -3.1], [3, 3, 1], "#6BE36B", glow="#9CFFB0", pattern="crystal")
    m.cube("pendant", [-0.5, 21, -3.0], [1, 1, 1], "#C9B27A")
    m.save(ROOT, "accessory")
    save_anim(ROOT, "accessory", "grassland_necklace", {
        "idle": loop(3.0, {"pendant": {"rotation": {0: [0, 0, -4], 1.5: [0, 0, 4], 3.0: [0, 0, -4]}}})})
    # 石くれの腕輪（右の手首）
    m = Model("ishikure_bracelet", 32, 32, bounds=(1.0, 2.0, 1.0), seed=82)
    armor_bones(m)
    m.cube("armorRightArm", [-8, 13, -2], [4, 2, 4], "#8C8A7E", inflate=0.45, pattern="stone")
    m.cube("armorRightArm", [-9, 13.5, -0.5], [1, 1, 1], "#6FE3D0", glow="#6FE3D0")
    m.save(ROOT, "accessory")
    save_anim(ROOT, "accessory", "ishikure_bracelet", {"idle": loop(1.0, {})})
    # 道標のお守り（腰に下げる）
    m = Model("waystone_charm", 32, 32, bounds=(1.0, 2.0, 1.0), seed=83)
    armor_bones(m)
    m.bone("charm", [3.5, 12, -2.6], "armorBody")
    m.cube("charm", [3, 10, -2.8], [1, 2, 1], "#C9B27A")
    m.cube("charm", [2.5, 6.5, -3.2], [2, 4, 1], "#9AA4B5", pattern="stone")
    m.cube("charm", [3, 8, -3.5], [1, 1, 1], "#8FD3FF", glow="#8FD3FF")
    m.cube("charm", [3, 5.5, -3.0], [1, 1, 1], "#E59BFF")
    m.save(ROOT, "accessory")
    save_anim(ROOT, "accessory", "waystone_charm", {
        "idle": loop(2.4, {"charm": {"rotation": {0: [0, 0, -8], 1.2: [0, 0, 8], 2.4: [0, 0, -8]}}})})


if __name__ == "__main__":
    tsunomofu()
    ishikure_soldier()
    crystal_bee()
    kakekiba_wolf()
    bee_crystal_staff()
    ancient_stone_plate()
    ancient_stone_robe()
    accessories()
    print("models written to", ROOT)
