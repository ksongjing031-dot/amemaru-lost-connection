"""霜銀シリーズ（第2章・6階からの職業ごとの装備）の見た目を作る。

  python3 tools/artgen/make_frost_silver_gear.py [リポジトリ] [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py .        # 持ち物の絵（16×16）を32×32に

11月25日に作ったときは、流紋シリーズの形を霜銀の色に塗り直していた。11月26日のご指示「階層が変わったときの装備が色しか
変わっていないのはよくないので見た目をしっかりと変える」で、形から作り直した【補完】。作り方は次の2つで、このスクリプトは
それを続けて呼ぶ（前の塗り直しの作り方は、もう使わない）：
  make_gear_frost_armor.py    防具6職業（体のモデル・体の絵・持ち物の絵・docs/gear/frost_silver_<職業>_sheet.png）
  make_gear_frost_weapons.py  武器7と盾（手に持つ3Dのモデルと絵・持ち物の絵・docs/gear/frost_silver_sheet.png）
色と塗り方は frost_common.py、持ち物の絵は frost_icons.py。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def main():
    import make_gear_frost_armor
    import make_gear_frost_weapons
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_frost"
    argv = sys.argv
    try:
        sys.argv = [argv[0], repo, out]
        make_gear_frost_armor.main()
        make_gear_frost_weapons.main()
    finally:
        sys.argv = argv


if __name__ == "__main__":
    main()
