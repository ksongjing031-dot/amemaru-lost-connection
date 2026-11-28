"""状態「認証（第七の門鍵）」のアイコン（18×18。11月28日【補完】）。

  python3 -I tools/artgen/make_unique_icons.py [assets/amemaru のフォルダ]

「第七の門鍵」（5階のユニークのお守り）の固有スキル「認証」が働いている間につく状態（ModEffects.GATE_WARD）。
門鍵そのもの（小さな立方体の翠の結晶と、まわりを回る細い環）を、守りの形（盾のふち）の中に描く。
描き方は make_phase2_art.py の状態のアイコンと同じ（効果の色 #5FE0A8）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_phase2_art as art  # noqa: E402

JADE = art.hexc("5FE0A8")
JADE_LIGHT = art.hexc("BDF7DD")
JADE_DARK = art.hexc("2E8F68")
JADE_DEEP = art.hexc("1D5A43")


def e_gate_ward(c):
    # 守りの形（うすい盾のふち）
    c.poly([(9, 1), (16, 3), (15, 11), (9, 16), (3, 11), (2, 3)], None, JADE_DARK)
    # 立方体の翠の結晶（上・左・右の面）
    c.poly([(9, 4), (13, 6), (9, 8), (5, 6)], JADE_LIGHT)
    c.poly([(5, 6), (9, 8), (9, 13), (5, 11)], JADE)
    c.poly([(9, 8), (13, 6), (13, 11), (9, 13)], JADE_DARK)
    c.line([(9, 8), (9, 13)], JADE_DEEP, 1)
    # まわりを回る細い環（ななめの楕円の、手前の半分だけ明るく）
    c.arc((2, 7, 16, 12), 0, 180, art.GOLD_LIGHT, 1)
    c.arc((2, 7, 16, 12), 180, 360, art.GOLD_DARK, 1)
    # 光
    art.sparkle(c, 14, 3, art.WHITE, 1)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"
    path = os.path.join(root, "textures/mob_effect/gate_ward.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    art.effect_icon(e_gate_ward).save(path)
    print("wrote", path)


if __name__ == "__main__":
    main()
