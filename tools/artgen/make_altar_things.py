"""5階「古代守護者の祭壇」の結び台・第七の門鍵・守護者シリーズ（職業ごと）の絵とモデルを作る（11月24日【補完】）。

  python3 tools/artgen/make_altar_things.py [リポジトリ] [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only seventh_gate_key

  binding_dais        結び台：白い石の円い台（高さ3ピクセル）に、金の結び目の模様。光ると結び目が翠に光る
  seventh_gate_key    第七の門鍵（お守りの絵。16×16 → 32×32）：小さな立方体の翠の結晶と、まわりを回る細い金の環
  守護者シリーズ      21章「守護者の核剣（両手剣）／守護者の外装（防具）」を、11月23日の決まりで職業ごとに。
                      11月26日から、流紋の形の塗り直しをやめ、形から作り直した（make_gear_guardian_armor.py・
                      make_gear_guardian_weapons.py。このスクリプトは、その2つを続けて呼ぶ）
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from materials import hsh  # noqa: E402

CREDIT = "AMEMARU: 5階（tools/artgen/make_altar_things.py で生成）"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


# ================================================================ 守護者シリーズ（11月26日から、形から作り直した）

def guardian_series(repo, out):
    """守護者シリーズの防具6職業と武器6を、make_gear_guardian_armor.py・make_gear_guardian_weapons.py で作る。"""
    import make_gear_guardian_armor
    import make_gear_guardian_weapons
    argv = sys.argv
    try:
        sys.argv = [argv[0], repo, out or "/tmp/gear_guardian"]
        make_gear_guardian_armor.main()
        make_gear_guardian_weapons.main()
    finally:
        sys.argv = argv


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
    # 第七の門鍵の持ち物の絵・体に表示するモデルは tools/artgen/make_gate_key.py（11月24日。ユニークの専用のモデル）
    write_json(os.path.join(base, "models/item/seventh_gate_key.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/seventh_gate_key"}})
    for name in ("watcher", "sentinel", "repair_drone", "ancient_guardian"):
        write_json(os.path.join(base, "models/item/%s_spawn_egg.json" % name), {"parent": "minecraft:item/template_spawn_egg"})
    guardian_series(repo, out)
    print("ok（守護者シリーズの持ち物の絵は16×16。続けて python3 tools/artgen/enhance_icons.py . を流す）")


if __name__ == "__main__":
    main()
