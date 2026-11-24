"""5階「古代守護者の祭壇」の結び台・第七の門鍵・守護者シリーズ（職業ごと）の絵とモデルを作る（11月24日【補完】）。

  python3 tools/artgen/make_altar_things.py [リポジトリ] [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only seventh_gate_key

  binding_dais        結び台：白い石の円い台（高さ3ピクセル）に、金の結び目の模様。光ると結び目が翠に光る
  seventh_gate_key    第七の門鍵（お守りの絵。16×16 → 32×32）：小さな立方体の翠の結晶と、まわりを回る細い金の環
  守護者シリーズ      21章「守護者の核剣（両手剣）／守護者の外装（防具）」を、11月23日の決まりで職業ごとに。形は流紋シリーズ
                      （職業ごとの武器と防具）の形をもとに、色を古代守護者の白い石・青銅・翠の核に塗り替える【補完】。
                      武器6（guardian_<種類>）と、胴の外装6（防具のシリーズ guardian_<職業>。流紋の体のモデルを使う）
"""
import colorsys
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_forest_things import paint_rows  # noqa: E402
from materials import hsh  # noqa: E402

CREDIT = "AMEMARU: 5階（tools/artgen/make_altar_things.py で生成）"
WEAPONS = {"greatsword": "guardian_greatsword", "staff": "guardian_staff", "bell_staff": "guardian_bell_staff", "bow": "guardian_bow",
           "daggers": "guardian_daggers", "gauntlets": "guardian_gauntlets"}
CHESTS = {"knight": "ryumon_knight_cuirass", "wizard": "ryumon_wizard_robe", "healer": "ryumon_healer_robe",
          "archer": "ryumon_archer_jerkin", "rogue": "ryumon_rogue_garb", "monk": "ryumon_monk_gi"}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


# ================================================================ 塗り替え（流紋 → 守護者）

def recolor(img):
    """流紋シリーズの色を、守護者の色へ：青緑・青 → 翠（明るく）、灰・白 → あたたかい白い石、茶・金 → 青銅（少し落ち着かせる）"""
    a = np.asarray(img.convert("RGBA")).astype(float) / 255.0
    out = a.copy()
    h, w, _ = a.shape
    for y in range(h):
        for x in range(w):
            r, g, b, al = a[y, x]
            if al == 0:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r, g, b)
            deg = hh * 360
            if ss < 0.18:
                # 灰・白 → あたたかい白い石（少し明るく）
                v2 = min(1.0, 0.25 + vv * 0.85)
                nr, ng, nb = colorsys.hsv_to_rgb(40 / 360.0, 0.08, v2)
            elif 150 <= deg <= 250:
                # 青緑・青 → 翠
                nr, ng, nb = colorsys.hsv_to_rgb(150 / 360.0, min(1.0, ss * 1.1 + 0.1), min(1.0, vv * 1.15 + 0.05))
            elif 15 <= deg <= 60:
                # 茶・金 → 青銅
                nr, ng, nb = colorsys.hsv_to_rgb(34 / 360.0, min(1.0, ss * 0.85), vv * 0.95)
            else:
                nr, ng, nb = colorsys.hsv_to_rgb(hh, ss * 0.6, vv)
            out[y, x, :3] = (nr, ng, nb)
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def guardian_series(base):
    item = os.path.join(base, "models/item")
    tex = os.path.join(base, "textures/item")
    made = []
    for kind, gid in WEAPONS.items():
        src = "ryumon_" + kind
        recolor(Image.open(os.path.join(tex, src + ".png"))).save(os.path.join(tex, gid + ".png"))
        recolor(Image.open(os.path.join(tex, src + "_model.png"))).save(os.path.join(tex, gid + "_model.png"))
        with open(os.path.join(item, src + "_3d.json"), encoding="utf-8") as fp:
            model = json.load(fp)
        model["credit"] = CREDIT + "：流紋の形を、守護者の色に塗り替えた"
        model["textures"] = {k: "amemaru:item/" + gid + "_model" for k in model["textures"]}
        write_json(os.path.join(item, gid + "_3d.json"), model)
        with open(os.path.join(item, src + ".json"), encoding="utf-8") as fp:
            outer = json.load(fp)
        text = json.dumps(outer).replace(src + "_3d", gid + "_3d").replace("amemaru:item/" + src + "\"", "amemaru:item/" + gid + "\"")
        write_json(os.path.join(item, gid + ".json"), json.loads(text))
        made.append(gid)
    armor_geo = os.path.join(base, "geo/armor")
    armor_tex = os.path.join(base, "textures/armor")
    for job, chest in CHESTS.items():
        series = "guardian_" + job
        shutil.copyfile(os.path.join(armor_geo, "ryumon_%s.geo.json" % job), os.path.join(armor_geo, series + ".geo.json"))
        with open(os.path.join(armor_geo, series + ".geo.json"), encoding="utf-8") as fp:
            geo = json.load(fp)
        for g in geo.get("minecraft:geometry", []):
            g["description"]["identifier"] = "geometry." + series
        write_json(os.path.join(armor_geo, series + ".geo.json"), geo)
        recolor(Image.open(os.path.join(armor_tex, "ryumon_%s.png" % job))).save(os.path.join(armor_tex, series + ".png"))
        shell = "guardian_%s_shell" % job
        recolor(Image.open(os.path.join(tex, chest + ".png"))).save(os.path.join(tex, shell + ".png"))
        with open(os.path.join(item, chest + ".json"), encoding="utf-8") as fp:
            outer = json.load(fp)
        write_json(os.path.join(item, shell + ".json"), json.loads(json.dumps(outer).replace(chest, shell)))
        made.append(shell)
    return made


# ================================================================ 結び台

def dais_top(lit):
    img = Image.new("RGB", (16, 16))
    p = img.load()
    for x in range(16):
        for y in range(16):
            v = int((hsh(x, y, 901) - 0.5) * 14)
            base = (226, 222, 212)
            d = math.hypot(x - 7.5, y - 7.5)
            if x in (0, 15) or y in (0, 15):
                base = (180, 172, 156)
            # 結び目（金の輪2つと、交わる線）
            ring = abs(math.hypot(x - 5.5, y - 7.5) - 3.2) < 0.6 or abs(math.hypot(x - 9.5, y - 7.5) - 3.2) < 0.6
            if ring:
                base = (90, 230, 160) if lit else (200, 160, 64)
            if d < 1.0:
                base = (240, 255, 245) if lit else (170, 140, 60)
            p[x, y] = tuple(max(0, min(255, c + v)) for c in base)
    return img


def dais_side():
    img = Image.new("RGB", (16, 16))
    p = img.load()
    for x in range(16):
        for y in range(16):
            v = int((hsh(x, y, 902) - 0.5) * 14)
            base = (196, 188, 172) if y < 14 else (150, 120, 60)
            p[x, y] = tuple(max(0, min(255, c + v)) for c in base)
    return img


def build_dais(base):
    tdir = os.path.join(base, "textures/block/binding_dais")
    os.makedirs(tdir, exist_ok=True)
    dais_side().save(os.path.join(tdir, "side.png"))
    variants = {}
    for lit in (False, True):
        name = "lit" if lit else "unlit"
        dais_top(lit).save(os.path.join(tdir, name + ".png"))
        top = "amemaru:block/binding_dais/" + name
        side = "amemaru:block/binding_dais/side"
        faces = {d: {"texture": "#side", "uv": [0, 13, 16, 16]} for d in ("north", "east", "south", "west")}
        faces["up"] = {"texture": "#top", "uv": [0, 0, 16, 16]}
        faces["down"] = {"texture": "#side", "uv": [0, 0, 16, 16], "cullface": "down"}
        write_json(os.path.join(base, "models/block/binding_dais", name + ".json"),
                   {"credit": CREDIT, "parent": "minecraft:block/block", "textures": {"particle": top, "side": side, "top": top},
                    "elements": [{"from": [0, 0, 0], "to": [16, 3, 16], "faces": faces}]})
        variants["lit=%s" % ("true" if lit else "false")] = {"model": "amemaru:block/binding_dais/" + name}
    write_json(os.path.join(base, "blockstates/binding_dais.json"), {"variants": variants})
    write_json(os.path.join(base, "models/item/binding_dais.json"), {"parent": "amemaru:block/binding_dais/lit"})


# ================================================================ 第七の門鍵（お守り）

GATE_KEY = [
    "................",
    "......oooo......",
    "....oo....oo....",
    "...o..gGGg..o...",
    "..o..gJJJJg..o..",
    "..o.gJWJJJJg.o..",
    ".o..gJJJJJJg..o.",
    ".o..gJJJjJJg..o.",
    ".o..gJJJJjJg..o.",
    "..o.gJJJJJJg.o..",
    "..o..gJJJJg..o..",
    "...o..gGGg..o...",
    "....oo....oo....",
    "......oooo......",
    "................",
    "................",
]
GATE_KEY_PAL = {"o": "#C9A03A", "g": "#1E7A52", "G": "#2A9A68", "J": "#3FBC84", "j": "#1E7A52", "W": "#CCF8E2"}


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    build_dais(base)
    paint_rows(GATE_KEY, GATE_KEY_PAL).save(os.path.join(base, "textures/item/seventh_gate_key.png"))
    write_json(os.path.join(base, "models/item/seventh_gate_key.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/seventh_gate_key"}})
    for name in ("watcher", "sentinel", "repair_drone", "ancient_guardian"):
        write_json(os.path.join(base, "models/item/%s_spawn_egg.json" % name), {"parent": "minecraft:item/template_spawn_egg"})
    made = guardian_series(base)
    if out:
        check(base, made, out)
    print("ok", len(made))


def check(base, made, out):
    from make_forest_models import sheet_font
    os.makedirs(out, exist_ok=True)
    tiles = []
    for name in made:
        src = name.replace("guardian_", "ryumon_")
        if name.endswith("_shell"):
            job = name.split("_")[1]
            src = CHESTS[job]
        pair = [Image.open(os.path.join(base, "textures/item", src + ".png")).convert("RGBA"),
                Image.open(os.path.join(base, "textures/item", name + ".png")).convert("RGBA")]
        tiles.append(pair)
    s = Image.new("RGBA", (len(tiles) * 140, 330), (40, 44, 48, 255))
    for k, (a, b) in enumerate(tiles):
        s.alpha_composite(a.resize((128, 128), Image.NEAREST), (k * 140 + 6, 30))
        s.alpha_composite(b.resize((128, 128), Image.NEAREST), (k * 140 + 6, 170))
    d = ImageDraw.Draw(s)
    d.text((6, 4), "守護者シリーズの持ち物の絵（上：流紋のもと・下：守護者の色）", fill=(235, 235, 220), font=sheet_font(16))
    s.save(os.path.join(out, "guardian_series_check.png"))


if __name__ == "__main__":
    main()
