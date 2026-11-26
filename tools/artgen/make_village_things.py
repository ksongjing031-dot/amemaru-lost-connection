"""6階「凍結した村」の村人の見た目と、熱の結晶・記憶の結晶の絵（11月25日【補完】）。

  python3 tools/artgen/make_village_things.py <リポジトリ> [確認画像の出力先]

作るもの
  textures/entity/village/<名前>.png         村人の見た目（プレイヤーと同じ形の 64×64）。記憶の場面の役者（npcs/echo_*.json）に使う
  textures/entity/village/<名前>_statue.png  同じ人の氷像の見た目：色を白と淡い青に寄せ、顔を白く抜く（ロストの侵食。Phase 0-A 9章の6階
                                             「氷像の顔が空白」）。tower の VillageStatueRenderer が氷の色で半分すけて描く
  textures/item/heat_crystal.png・memory_crystal.png（16×16。tools/artgen/enhance_icons.py で 32×32 にする）
  村長の毛皮（6階のボスのレア。頭・胴・脚・足の一式）：11月26日から、古代石の法衣の形の塗り直しをやめ、形から作り直した
  （tools/artgen/make_gear_rare_reshape.py。このスクリプトは、その村長の毛皮の分を呼ぶ）
  6階の素材（霜銀・氷晶・凍て毛皮・霜狼の牙・凍て鳥の羽根）の models/item（絵は tools/artgen/make_floor_accessory_art.py）
村人：merchant（値切る商人）・buyer（客）・mother（母）・child_girl（女の子。日記の子）・child_boy・ringer（鐘を鳴らす若者）・
      ordo（村長オルド）・man・woman・old_man・guard（門番）
"""
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


class Skin:
    def __init__(self, slim, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.slim = slim
        self.rng = random.Random(seed)

    def px(self, x, y, c):
        if c is None:
            return
        self.img.putpixel((x, y), hexc(c) if isinstance(c, str) else c)

    def box(self, u, v, w, h, d, fn):
        faces = {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
        }
        for name, (fx, fy, fw, fh) in faces.items():
            for y in range(fh):
                for x in range(fw):
                    self.px(fx + x, fy + y, fn(name, x, y, fw, fh))

    def head(self, fn):
        self.box(0, 0, 8, 8, 8, fn)

    def hat(self, fn):
        self.box(32, 0, 8, 8, 8, fn)

    def body(self, fn, layer=False):
        self.box(16, 32 if layer else 16, 8, 12, 4, fn)

    def arm(self, side, fn, layer=False):
        w = 3 if self.slim else 4
        if side == "right":
            self.box(40, 32 if layer else 16, w, 12, 4, fn)
        else:
            self.box(48 if layer else 32, 48, w, 12, 4, fn)

    def leg(self, side, fn, layer=False):
        if side == "right":
            self.box(0, 32 if layer else 16, 4, 12, 4, fn)
        else:
            self.box(0 if layer else 16, 48, 4, 12, 4, fn)


# 村人の決まり（髪・肌・上着・ズボン・靴・ひげ・帽子・前かけ・肩かけ・毛皮のえり）
PEOPLE = {
    "merchant": dict(slim=False, hair="#6B4528", skin="#E8C2A0", top="#7A5A3A", pants="#4A4038", shoes="#3A2A1E", beard="#6B4528",
                     hat="knit", hat_c="#B0402E", apron="#3E7A4A"),
    "buyer": dict(slim=True, hair="#3A2A22", skin="#F0D2B8", top="#7C3448", pants="#5A3040", shoes="#2E2420", shawl="#A24B5E",
                  long_hair=True),
    "mother": dict(slim=True, hair="#8A5530", skin="#F2D6BE", top="#3E5E92", pants="#344E7C", shoes="#3A2A20", apron="#EEE8DC",
                   long_hair=True),
    "child_girl": dict(slim=True, hair="#D8B45A", skin="#F6DCC8", top="#B23A32", pants="#7A2A26", shoes="#4A2A1A", scarf="#F2F0EA",
                       braids=True),
    "child_boy": dict(slim=False, hair="#7A5230", skin="#F2D4BA", top="#3F6E3E", pants="#3C4A5A", shoes="#3A2A1E", hat="knit",
                      hat_c="#3F5FA0"),
    "ringer": dict(slim=False, hair="#262024", skin="#E9C8A8", top="#8A8A90", pants="#4E4A44", shoes="#2E2420", vest="#6B4A2C"),
    "ordo": dict(slim=False, hair="#D8D8D8", skin="#E4C0A0", top="#2E3E6A", pants="#2A2E40", shoes="#2A2018", beard="#EDEDED",
                 long_beard=True, fur="#C8C2B4", trim="#C9A03A", hat="fur", hat_c="#B8B2A4"),
    "man": dict(slim=False, hair="#5A3A22", skin="#E8C4A2", top="#8A4A32", pants="#45403A", shoes="#30241A"),
    "woman": dict(slim=True, hair="#D2A85A", skin="#F2D6BE", top="#4E7A52", pants="#3E6244", shoes="#3A2A20", shawl="#D8C8A8",
                  long_hair=True),
    "old_man": dict(slim=False, hair="#9A9A9A", skin="#E2BE9E", top="#6A5038", pants="#403A34", shoes="#2E241A", beard="#B8B8B8",
                    hat="cap", hat_c="#55483C"),
    "guard": dict(slim=False, hair="#4A3A2A", skin="#E6C2A0", top="#8E949C", pants="#3E4450", shoes="#2A2A2E", tabard="#2E5A9A",
                  hat="helm", hat_c="#A8AEB6"),
    # 11月25日（8階）：湖底の祠の、最後の歌い手（長い黒髪・白い衣・青い肩掛け・水色の縁取り）。絵は make_lake_things.py が書く【補完】
    "singer": dict(slim=True, hair="#232A3A", skin="#F0D6C2", top="#E6ECF2", pants="#C4D0DE", shoes="#6E7A8C", shawl="#4E74A8",
                   trim="#8EC4DA", long_hair=True),
}


def person(name, spec, statue):
    s = Skin(spec["slim"], sum(ord(c) * (i + 1) for i, c in enumerate(name)))
    r = s.rng

    def n(c, amount=0.05):
        return shade(hexc(c), 1.0 + r.uniform(-amount, amount))

    hair = spec["hair"]
    skin = spec["skin"]

    def head(face, x, y, w, h):
        if face == "top":
            return n(hair)
        if face == "bottom":
            return shade(hexc(skin), 0.85)
        if face == "back":
            return n(hair, 0.06)
        if face == "front":
            if statue:
                # ロストの侵食：顔が白く抜けている（ほんの少しだけ、輪郭が残る）
                return (246, 250, 253, 255) if (1 <= x <= 6 and 2 <= y <= 7) else n(hair if y <= 1 else skin, 0.03)
            if y <= 1:
                return n(hair)
            if y == 2 and x in (1, 2, 5, 6):
                return shade(hexc(hair), 0.8)          # 眉
            if y == 3 and x in (1, 2, 5, 6):
                return "#F4F4F4" if x in (1, 6) else "#2A3A5A"   # 目
            if y == 5 and 3 <= x <= 4:
                return shade(hexc(skin), 0.75)          # 鼻
            if spec.get("beard") and y >= 5:
                if spec.get("long_beard") or y >= 6:
                    return n(spec["beard"], 0.04)
            if y == 6 and 2 <= x <= 5:
                return "#B06A5A"                        # 口
            return n(skin, 0.03)
        # 横：上は髪、下は肌（長い髪は肩まで）
        if y <= 2 or (spec.get("long_hair") and ((face == "right" and x <= 2) or (face == "left" and x >= 5))):
            return n(hair)
        if spec.get("beard") and y >= 6:
            return n(spec["beard"])
        return n(skin, 0.03)
    s.head(head)

    def hat(face, x, y, w, h):
        kind = spec.get("hat")
        c = spec.get("hat_c", "#555555")
        if kind == "knit":
            if face == "top" or (face != "bottom" and y <= 2):
                return n(c, 0.08) if (x + y) % 2 else shade(hexc(c), 0.85)
        elif kind == "cap":
            if face == "top" or (face != "bottom" and y <= 1):
                return n(c)
            if face == "front" and y == 2:
                return shade(hexc(c), 0.7)
        elif kind == "fur":
            if face == "top" or (face != "bottom" and y <= 2):
                return n(c, 0.1)
        elif kind == "helm":
            if face == "top" or (face != "bottom" and y <= 2) or (face in ("right", "left") and y <= 6):
                return n(c, 0.06)
            if face == "front" and y <= 4 and x in (3, 4):
                return shade(hexc(c), 0.8)
        if spec.get("braids") and face in ("right", "left") and y >= 4:
            return n(hair) if x in (2, 3, 4) else None
        if spec.get("long_hair") and face == "back" and y >= 4:
            return n(hair, 0.07)
        return None
    s.hat(hat)

    top = spec["top"]

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return n(top)
        if spec.get("fur") and y <= 2:
            return n(spec["fur"], 0.1)
        if spec.get("scarf") and y <= 2:
            return n(spec["scarf"], 0.05)
        if spec.get("trim") and face == "front" and x in (3, 4):
            return n(spec["trim"], 0.05)
        if spec.get("tabard") and face in ("front", "back") and 2 <= x <= 5 and y >= 2:
            return n(spec["tabard"], 0.05)
        if spec.get("vest") and face == "front" and (x <= 1 or x >= 6) and y >= 1:
            return n(spec["vest"], 0.05)
        if spec.get("apron") and face == "front" and 1 <= x <= 6 and y >= 5:
            return n(spec["apron"], 0.04)
        if spec.get("shawl") and y <= 4:
            return n(spec["shawl"], 0.06)
        if y == 7:
            return shade(hexc(top), 0.75)     # 帯
        return n(top)
    s.body(body)

    def arm(face, x, y, w, h):
        if face == "bottom" or y >= 10:
            return n(skin, 0.03)
        if spec.get("fur") and y >= 8 and face != "top":
            return n(spec["fur"], 0.1)
        if spec.get("shawl") and y <= 3 and face != "bottom":
            return n(spec["shawl"], 0.06)
        return n(top)
    s.arm("right", arm)
    s.arm("left", arm)
    pants = spec["pants"]

    def leg(face, x, y, w, h):
        if face == "bottom" or y >= 9:
            return n(spec["shoes"], 0.05)
        if spec.get("apron") and spec["slim"] and face == "front" and y <= 4:
            return n(spec["apron"], 0.04)
        return n(pants)
    s.leg("right", leg)
    s.leg("left", leg)
    img = s.img
    if statue:
        # 氷の色に寄せる（白と淡い青。顔はもう白い）
        px = img.load()
        for yy in range(64):
            for xx in range(64):
                c = px[xx, yy]
                if c[3] == 0:
                    continue
                lum = (c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11) / 255.0
                t = 0.62
                base = (214, 232, 246)
                v = 0.78 + lum * 0.3
                px[xx, yy] = (int(min(255, (c[0] * (1 - t) + base[0] * t) * v)), int(min(255, (c[1] * (1 - t) + base[1] * t) * v)),
                              int(min(255, (c[2] * (1 - t) + base[2] * t) * v * 1.04)), 255)
    return img


def crystal_icon(memory):
    """16×16 の結晶（熱：橙と赤の炎の芯。記憶：淡い青に、中に小さな人影の光）"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    if memory:
        pal = ["#1E3E6A", "#2E5E9A", "#5C9AD8", "#9CCAF2", "#D8EEFF", "#FFFFFF"]
    else:
        pal = ["#6A1E10", "#A8361A", "#E0662A", "#F6A23E", "#FFD878", "#FFF6D8"]
    shape = [
        "......44........",
        ".....4334.......",
        "....433224......",
        "...4332221......",
        "..433222110.....",
        ".4332221110.4...",
        ".43322211100434.",
        ".433222111004332",
        "..4322211100432.",
        "..43222111043...",
        "...4322110.4....",
        "....43221100....",
        ".....432110.....",
        "......4210......",
        ".......40.......",
        "................",
    ]
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            px[x, y] = hexc(pal[{"4": 0, "0": 1, "1": 2, "2": 3, "3": 4}[ch]])
    # 芯の光
    core = hexc(pal[5])
    for (x, y) in ((5, 6), (6, 6), (5, 7), (6, 7), (6, 5)):
        px[x, y] = core
    if memory:
        for (x, y) in ((6, 8), (6, 9), (5, 9), (7, 9)):
            px[x, y] = hexc("#FFFFFF")
    return img


# ================================================================ 村長の毛皮（6階のボスのレア。11月26日に形から作り直した）

def fur_series(repo, out=None):
    """村長の毛皮の体のモデル・絵・持ち物の絵・docs/gear/mayor_fur_sheet.png を、make_gear_rare_reshape.py で作る。"""
    import make_gear_rare_reshape
    make_gear_rare_reshape.export_mayor(repo, out or "/tmp/gear_rare")


MATERIAL_MODELS = ["frost_silver", "ice_crystal", "frozen_fur", "frost_fang", "ice_feather"]


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    if "--fur-sheet" in sys.argv:
        fur_series(repo)
        return
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    vdir = os.path.join(base, "textures/entity/village")
    os.makedirs(vdir, exist_ok=True)
    made = []
    for name, spec in PEOPLE.items():
        person(name, spec, False).save(os.path.join(vdir, name + ".png"))
        person(name, spec, True).save(os.path.join(vdir, name + "_statue.png"))
        made.append(name)
    for memory, name in ((False, "heat_crystal"), (True, "memory_crystal")):
        crystal_icon(memory).save(os.path.join(base, "textures/item", name + ".png"))
    fur_series(repo, out)
    import json
    for name in MATERIAL_MODELS:
        with open(os.path.join(base, "models/item", name + ".json"), "w", encoding="utf-8") as fp:
            fp.write(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, indent=2) + "\n")
    if out:
        os.makedirs(out, exist_ok=True)
        sheet = Image.new("RGBA", (len(made) * 140, 300), (40, 44, 48, 255))
        d = ImageDraw.Draw(sheet)
        for k, name in enumerate(made):
            a = Image.open(os.path.join(vdir, name + ".png")).resize((128, 128), Image.NEAREST)
            b = Image.open(os.path.join(vdir, name + "_statue.png")).resize((128, 128), Image.NEAREST)
            sheet.alpha_composite(a, (k * 140 + 6, 20))
            sheet.alpha_composite(b, (k * 140 + 6, 160))
            d.text((k * 140 + 6, 4), name, fill=(235, 235, 220))
        sheet.save(os.path.join(out, "village_skins_check.png"))
    print("ok", len(made))


if __name__ == "__main__":
    main()
