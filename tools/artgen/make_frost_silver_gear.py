"""霜銀シリーズ（第2章・6階からの職業ごとの装備）の見た目を作る（11月25日【補完】）。

  python3 tools/artgen/make_frost_silver_gear.py [リポジトリ] [確認画像の出力先]

形は、流紋シリーズ（3〜4階。tools/artgen/make_gear_ryumon_*.py）の職業ごとの形をそのまま使い、色を霜銀の色に塗り直す
（守護者シリーズ〈tools/artgen/make_altar_things.py〉と同じやり方）：
  青銅・金（茶）      → 霜銀（白く冷たい銀。少し明るく）
  明るい水色・青緑    → 氷晶の淡い氷青
  暗い青・藍          → 夜の雪の藍（深いまま）
  灰・白・生成り      → 雪の白（青みの白）
  そのほかの色（緑の外套・赤など） → 色をうすくし、少し冷たく
コモン（工房で作る品）なので、光る部分はない（20章：コモンは素朴な形）。

書き出すもの：
  武器7（frost_silver_<種類>）：持ち物の絵・手に持つ3Dの絵とモデル
  盾（frost_silver_shield）：3Dのモデル（構えたときの形も）
  防具6シリーズ（frost_silver_<職業>）：体のモデル（geo/armor。流紋の geo の識別子を変えたもの）・体の絵
  防具の頭・胴・脚・足24：持ち物の絵と models/item
"""
import colorsys
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

CREDIT = "AMEMARU: 霜銀シリーズ（tools/artgen/make_frost_silver_gear.py で生成）：流紋の形を、霜銀の色に塗り替えた"
WEAPONS = ["sword", "greatsword", "staff", "bell_staff", "bow", "daggers", "gauntlets"]
PIECES = {
    "knight": ["helm", "cuirass", "greaves", "sabatons"],
    "wizard": ["hat", "robe", "legwear", "shoes"],
    "healer": ["hood", "robe", "legwear", "shoes"],
    "archer": ["cap", "jerkin", "leggings", "boots"],
    "rogue": ["mask", "garb", "leggings", "tabi"],
    "monk": ["headband", "gi", "hakama", "sandals"],
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def recolor(img):
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
                # 灰・白・生成り → 雪の白（青み）
                nr, ng, nb = colorsys.hsv_to_rgb(212 / 360.0, 0.07, min(1.0, 0.28 + vv * 0.76))
            elif 150 <= deg <= 250 and vv >= 0.5:
                # 明るい水色・青緑 → 氷晶の氷青
                nr, ng, nb = colorsys.hsv_to_rgb(198 / 360.0, min(1.0, ss * 0.75 + 0.05), min(1.0, vv * 1.08 + 0.08))
            elif 150 <= deg <= 250:
                # 暗い青・藍 → 夜の雪の藍（深いまま）
                nr, ng, nb = colorsys.hsv_to_rgb(226 / 360.0, min(1.0, ss * 0.9), vv * 0.95)
            elif 15 <= deg <= 60:
                # 青銅・金 → 霜銀
                nr, ng, nb = colorsys.hsv_to_rgb(214 / 360.0, 0.12, min(1.0, vv * 1.02 + 0.14))
            else:
                nr, ng, nb = colorsys.hsv_to_rgb((hh + (200 / 360.0 - hh) * 0.25) % 1.0, ss * 0.45, min(1.0, vv * 1.05))
            out[y, x, :3] = (nr, ng, nb)
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def swap(obj, src, dst):
    return json.loads(json.dumps(obj).replace(src, dst))


def weapons(base):
    item = os.path.join(base, "models/item")
    tex = os.path.join(base, "textures/item")
    made = []
    for kind in WEAPONS:
        src, gid = "ryumon_" + kind, "frost_silver_" + kind
        recolor(Image.open(os.path.join(tex, src + ".png"))).save(os.path.join(tex, gid + ".png"))
        recolor(Image.open(os.path.join(tex, src + "_model.png"))).save(os.path.join(tex, gid + "_model.png"))
        with open(os.path.join(item, src + "_3d.json"), encoding="utf-8") as fp:
            model = json.load(fp)
        model["credit"] = CREDIT
        model["textures"] = {k: "amemaru:item/" + gid + "_model" for k in model["textures"]}
        for e in model.get("elements", []):
            e.pop("forge_data", None)
        write_json(os.path.join(item, gid + "_3d.json"), model)
        with open(os.path.join(item, src + ".json"), encoding="utf-8") as fp:
            outer = json.load(fp)
        write_json(os.path.join(item, gid + ".json"), swap(outer, src, gid))
        made.append((src, gid))
    # 盾：3Dのモデル（絵は64×64）と、構えたときの形
    src, gid = "ryumon_shield", "frost_silver_shield"
    recolor(Image.open(os.path.join(tex, src + "_model.png"))).save(os.path.join(tex, gid + "_model.png"))
    for suffix in ("_model", "", "_blocking"):
        with open(os.path.join(item, src + suffix + ".json"), encoding="utf-8") as fp:
            model = json.load(fp)
        model = swap(model, src, gid)
        if "credit" in model:
            model["credit"] = CREDIT
        write_json(os.path.join(item, gid + suffix + ".json"), model)
    made.append((src + "_model", gid + "_model"))
    return made


def armor(base):
    item = os.path.join(base, "models/item")
    tex = os.path.join(base, "textures/item")
    geo_dir = os.path.join(base, "geo/armor")
    tex_dir = os.path.join(base, "textures/armor")
    made = []
    for job, pieces in PIECES.items():
        src_series, series = "ryumon_" + job, "frost_silver_" + job
        with open(os.path.join(geo_dir, src_series + ".geo.json"), encoding="utf-8") as fp:
            geo = json.load(fp)
        for g in geo.get("minecraft:geometry", []):
            g["description"]["identifier"] = "geometry." + series
        write_json(os.path.join(geo_dir, series + ".geo.json"), geo)
        recolor(Image.open(os.path.join(tex_dir, src_series + ".png"))).save(os.path.join(tex_dir, series + ".png"))
        for piece in pieces:
            src, gid = src_series + "_" + piece, series + "_" + piece
            recolor(Image.open(os.path.join(tex, src + ".png"))).save(os.path.join(tex, gid + ".png"))
            with open(os.path.join(item, src + ".json"), encoding="utf-8") as fp:
                outer = json.load(fp)
            write_json(os.path.join(item, gid + ".json"), swap(outer, src, gid))
            made.append((src, gid))
        made.append(("@" + src_series, "@" + series))
    return made


def check(base, made, out):
    from make_forest_models import sheet_font
    os.makedirs(out, exist_ok=True)
    tiles = []
    for src, gid in made:
        if src.startswith("@"):
            continue
        a = Image.open(os.path.join(base, "textures/item", src + ".png")).convert("RGBA")
        b = Image.open(os.path.join(base, "textures/item", gid + ".png")).convert("RGBA")
        tiles.append((a, b))
    per_row = 12
    rows = (len(tiles) + per_row - 1) // per_row
    s = Image.new("RGBA", (per_row * 110, 34 + rows * 230), (40, 44, 48, 255))
    for k, (a, b) in enumerate(tiles):
        x, y = (k % per_row) * 110, 34 + (k // per_row) * 230
        s.alpha_composite(a.resize((100, 100), Image.NEAREST), (x + 5, y))
        s.alpha_composite(b.resize((100, 100), Image.NEAREST), (x + 5, y + 108))
    d = ImageDraw.Draw(s)
    d.text((6, 6), "霜銀シリーズの持ち物の絵（上：もとにした流紋シリーズ・下：霜銀の色）", fill=(235, 235, 220), font=sheet_font(18))
    s.save(os.path.join(out, "frost_silver_check.png"))
    # 体の絵（防具6シリーズ）
    sheets = [Image.open(os.path.join(base, "textures/armor", gid[1:] + ".png")).convert("RGBA") for src, gid in made if gid.startswith("@")]
    w = sum(img.width * 3 + 10 for img in sheets)
    hh = max(img.height * 3 for img in sheets) + 10
    t = Image.new("RGBA", (w, hh), (40, 44, 48, 255))
    x = 0
    for img in sheets:
        t.alpha_composite(img.resize((img.width * 3, img.height * 3), Image.NEAREST), (x, 5))
        x += img.width * 3 + 10
    t.save(os.path.join(out, "frost_silver_armor_textures.png"))


def sheets(repo):
    """docs/gear/frost_silver_<職業>_sheet.png：流紋の確認画像（make_gear_ryumon_armor.py）と同じ並びで、霜銀の絵を着せて撮る。"""
    import make_gear_ryumon_armor as RA
    from make_floor_accessory_art import disk_icon
    from setsheet import make_set_sheet
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    man = RA.mannequin()
    mtex = man.paint(RA.paint_mannequin, frames=1)[0]
    for key, cfg in RA.SETS.items():
        g = cfg["build"]()
        RA.paint(g)
        tex = np.asarray(Image.open(os.path.join(base, "textures/armor", "frost_silver_%s.png" % key)).convert("RGBA"))
        full = [(man, mtex), (g, tex)]
        pieces = [(label, slot, item.replace("ryumon_", "frost_silver_", 1)) for label, slot, item in cfg["pieces"]]
        icons = [(label, disk_icon(repo, item)) for label, _, item in pieces]
        piece_views = [(label, -145, 14, [(man, mtex), (RA.subset(g, RA.PIECE_BONES[slot]), tex)]) for label, slot, _ in pieces]
        job = cfg["title"].split("（")[1].rstrip("）")
        items = [
            {"label": "フルセット", "rarity": "コモン・Lv24・" + job + "専用", "note": NOTES[key], "pairs": full,
             "views": [(lab, y, p) for y, p, lab in RA.VIEWS], "icons": icons},
            {"label": "部位ごと", "rarity": "着た場所だけ表示", "note": "", "pairs": full, "views": piece_views,
             "icons": icons, "icon_cell": 40},
        ]
        make_set_sheet(os.path.join(repo, "docs/gear", "frost_silver_%s_sheet.png" % key), cfg["title"].replace("流紋", "霜銀"),
                       "霜銀シリーズ（第2章の職業ごとの装備）／ 流紋の形を霜銀の色に塗り直したもの【補完】／ 128×64の絵", items, row_h=420)


NOTES = {
    "knight": "流紋の重鎧の形を、霜銀の白い金属に。\n胸の円盤と結晶は氷晶の淡い青。",
    "wizard": "流紋の長衣の形を、夜の雪の藍に。\n銀の縫い取りと、氷晶の灯り。",
    "healer": "流紋の白法衣の形を、雪の白に。\n金の飾りは霜銀に、鈴は氷晶の青。",
    "archer": "流紋の狩り装束の形。\n革は霜銀の灰白に、緑の外套は色をうすく。",
    "rogue": "流紋の黒装束の形。\n黒い布はそのままの深い色、金具は霜銀。",
    "monk": "流紋の道着の形を、雪の白に。\n藍の帯と袴は、明るい所が氷青に。",
}


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    if "--sheets" in sys.argv:
        sheets(repo)
        return
    made = weapons(base) + armor(base)
    if out:
        check(base, [m for m in made if not m[0].endswith("_model")], out)
    print("ok", len([m for m in made if not m[0].startswith("@")]))


if __name__ == "__main__":
    main()
