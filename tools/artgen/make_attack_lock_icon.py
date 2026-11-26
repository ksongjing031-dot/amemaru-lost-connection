"""状態「硬直」のアイコン（18×18。11月26日）。

  python3 tools/artgen/make_attack_lock_icon.py [assets/amemaru のフォルダ]

遠距離職が攻撃したあとの「足が止まる」状態（ModEffects.ATTACK_LOCK）。地面に踏みしめた両足と、その上の「止まれ」の
しるし（金の2本の線と、足もとの琥珀の地面）。描き方は make_phase2_art.py の状態のアイコンと同じ（効果の色 #C8A86E）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_phase2_art as art  # noqa: E402

AMBER = art.hexc("C8A86E")
AMBER_DARK = art.hexc("8A6A3A")


def e_attack_lock(c):
    # 足もとの地面（琥珀）
    c.rect(1, 14, 16, 16, AMBER_DARK)
    c.line([(1, 14), (16, 14)], AMBER, 1)
    # 踏みしめた両足（左右のくつ。つま先が前へ出る）
    for x in (3, 10):
        c.rect(x, 6, x + 4, 13, art.STEEL_DARK)
        c.rect(x, 11, x + 5, 13, art.STEEL_DARK)
        c.line([(x, 6), (x + 4, 6)], art.STEEL, 1)
        c.line([(x, 13), (x + 5, 13)], art.OUTLINE, 1)
    # 「止まれ」の金の2本の線（上から押さえつける）
    for x in (5, 12):
        c.line([(x, 1), (x, 3)], art.GOLD, 1)
    c.line([(3, 4), (7, 4)], art.GOLD_LIGHT, 1)
    c.line([(10, 4), (14, 4)], art.GOLD_LIGHT, 1)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"
    path = os.path.join(root, "textures/mob_effect/attack_lock.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    art.effect_icon(e_attack_lock).save(path)
    print("wrote", path)


if __name__ == "__main__":
    main()
