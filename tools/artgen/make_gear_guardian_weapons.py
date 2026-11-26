"""守護者シリーズの武器（5階「古代守護者の祭壇」の職業ごとの装備。エピック）。11月26日に形から作り直した。

  python3 tools/artgen/make_gear_guardian_weapons.py <リポジトリ> [確認画像の出力先]

11月26日のご指示「階層が変わったときの装備が色しか変わっていないのはよくないので見た目をしっかりと変える」【補完】。
前は流紋シリーズの武器の形を守護者の色に塗り直していた。古代守護者の白い石と青銅・翠の記録核から作り直した。
翠の部品は光る（エピックの光る部分。forge_data の明るさ15）：
  核剣  ＝白い石の一枚岩の刃（格子の溝・青銅の背・まん中の翠のすじ・斜めに断った先）・刃の根元の四角の青銅の輪の中の翠の核
  核杖  ＝積んだ石の柄の先に、四角の青銅の輪と、その中に浮かぶ翠の立方体（輪の角に石）
  核鈴  ＝石の柄の先の青銅の横木から下がる、段の石の鐘と翠の舌（左右に翠の房）
  核弓  ＝角ばった石の腕を青銅の継ぎ目でつなぐ（継ぎ目と弭に翠）・石の握りの翠の核
  双核刃＝三角の白い石の刃と、まん中の翠のすじ・四角の青銅の鍔・翠の柄頭
  核拳  ＝格子の石の筒と、四角い石のこぶし（青銅の歯3つ）・甲の翠の核
持ち方と大きさは流紋シリーズと同じ。色と塗り方は guardian_common.py、持ち物の絵は guardian_icons.py。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from materials import Painter  # noqa: E402
import weapons  # noqa: E402
from ryumon_common import RyumonPaint  # noqa: E402
from guardian_common import GuardianPaint  # noqa: E402
from make_gear_ancient_stone import weapon_display  # noqa: E402
from make_gear_phase6 import display as phase6_display, BOW_STRING_X  # noqa: E402

CREDIT = "AMEMARU: 守護者シリーズ（11月26日に形から作り直した。tools/artgen/make_gear_guardian_weapons.py で生成。翠の部品は光る）"


class GuardianWeaponPainter(GuardianPaint, RyumonPaint, Painter):
    pass


def jade(m, name, frm, to, rot=None):
    m.box(name, frm, to, "jade", rot=rot, glow=True)


# ================================================================ 核剣（ナイトの両手剣）

def build_greatsword():
    m = JavaModel("guardian_greatsword", 64, 64)
    m.box("pommel", (6.75, -5.0, 6.75), (9.25, -2.5, 9.25), "gstone", tags={"edge_bright": "top"})
    jade(m, "pommel_core", (7.5, -5.6, 7.5), (8.5, -5.0, 8.5))
    m.box("grip", (7.4, -2.5, 7.4), (8.6, 7.75, 8.6), "gstone_dark", tags={"steps": 2})
    for k, y in enumerate((0.5, 4.5)):
        m.box(f"band_{k}", (7.1, y, 7.1), (8.9, y + 0.75, 8.9), "gbronze")
    # 刃の根元の四角の青銅の輪と、その中の翠の核
    m.box("ring_bottom", (4.0, 7.75, 6.9), (12.0, 8.75, 9.1), "gbronze")
    m.box("ring_l", (4.0, 8.75, 6.9), (5.0, 12.25, 9.1), "gbronze")
    m.box("ring_r", (11.0, 8.75, 6.9), (12.0, 12.25, 9.1), "gbronze")
    m.box("ring_top", (4.0, 12.25, 6.9), (12.0, 13.25, 9.1), "gbronze")
    jade(m, "core", (6.75, 9.25, 6.6), (9.25, 11.75, 9.4))
    # 刃：白い石の一枚岩（格子の溝）・左に青銅の背・まん中の翠のすじ・斜めに断った先
    m.box("blade", (5.6, 13.25, 7.3), (10.4, 28.0, 8.7), "gstone", tags={"lattice": 3, "edge": "right"})
    m.box("spine", (5.0, 13.25, 7.5), (5.6, 28.0, 8.5), "gbronze")
    jade(m, "inlay", (7.75, 14.5, 7.15), (8.25, 26.0, 8.85))
    m.box("tip", (5.6, 27.0, 7.4), (10.4, 29.5, 8.6), "gstone", rot=((8.0, 28.25, 8.0), "z", 22.5), tags={"edge_bright": "top"})
    return m


# ================================================================ 核杖（ウィザード）

def build_staff():
    m = JavaModel("guardian_staff", 64, 64)
    m.box("foot", (7.4, -8.5, 7.4), (8.6, -6.5, 8.6), "gstone")
    m.box("shaft", (7.6, -6.5, 7.6), (8.4, 15.0, 8.4), "gstone_dark", tags={"steps": 3})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "ivory", tags={"wrap": True})
    for k, y in enumerate((-5.0, 7.0, 12.0)):
        m.box(f"ring_{k}", (7.3, y, 7.3), (8.7, y + 0.75, 8.7), "gbronze")
    m.box("collar", (6.9, 15.0, 6.9), (9.1, 16.0, 9.1), "gbronze")
    # 先：四角の青銅の輪（前を向く）と、角の石。輪の中に浮かぶ翠の立方体
    m.box("frame_bottom", (4.5, 16.0, 7.5), (11.5, 17.0, 8.5), "gbronze")
    m.box("frame_top", (4.5, 23.0, 7.5), (11.5, 24.0, 8.5), "gbronze")
    m.box("frame_l", (4.5, 17.0, 7.5), (5.5, 23.0, 8.5), "gbronze")
    m.box("frame_r", (10.5, 17.0, 7.5), (11.5, 23.0, 8.5), "gbronze")
    for k, (x, y) in enumerate(((4.25, 15.75), (10.25, 15.75), (4.25, 22.75), (10.25, 22.75))):
        m.box(f"corner_{k}", (x, y, 7.25), (x + 1.5, y + 1.5, 8.75), "gstone", tags={"edge_bright": "top"})
    jade(m, "core", (6.75, 18.75, 6.75), (9.25, 21.25, 9.25))
    m.box("finial", (7.5, 24.0, 7.5), (8.5, 25.0, 8.5), "gstone")
    return m


# ================================================================ 核鈴（ヒーラー）

def build_bell_staff():
    m = JavaModel("guardian_bell_staff", 64, 64)
    m.box("foot", (7.4, -8.0, 7.4), (8.6, -6.0, 8.6), "gstone")
    m.box("shaft", (7.6, -6.0, 7.6), (8.4, 17.0, 8.4), "gstone_dark", tags={"steps": 3})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "ivory", tags={"wrap": True})
    for k, y in enumerate((-4.0, 7.0, 12.0)):
        m.box(f"ring_{k}", (7.3, y, 7.3), (8.7, y + 0.75, 8.7), "gbronze")
    # 先：青銅の横木（両はしに石）・下がる段の石の鐘と、翠の舌・左右に下がる翠の房
    m.box("yoke", (4.5, 21.0, 7.4), (11.5, 22.0, 8.6), "gbronze")
    m.box("yoke_l", (4.0, 20.5, 7.25), (5.0, 22.5, 8.75), "gstone")
    m.box("yoke_r", (11.0, 20.5, 7.25), (12.0, 22.5, 8.75), "gstone")
    m.box("hanger", (7.6, 20.0, 7.6), (8.4, 21.0, 8.4), "gbronze")
    m.box("bell_top", (7.0, 19.0, 7.0), (9.0, 20.0, 9.0), "gstone")
    m.box("bell_mid", (6.5, 17.5, 6.5), (9.5, 19.0, 9.5), "gstone", tags={"steps": 2})
    m.box("bell_lip", (6.0, 16.75, 6.0), (10.0, 17.5, 10.0), "gstone", tags={"edge_bright": "bottom"})
    jade(m, "clapper", (7.5, 15.75, 7.5), (8.5, 16.75, 8.5))
    for side, x in (("l", 4.25), ("r", 11.25)):
        m.box(f"tassel_cord_{side}", (x - 0.0, 18.5, 7.85), (x + 0.5, 20.5, 8.15), "gbronze")
        jade(m, f"tassel_{side}", (x - 0.25, 17.5, 7.6), (x + 0.75, 18.5, 8.4))
    return m


# ================================================================ 核弓（アーチャー）

def build_bow():
    """腕と弦の形は流紋の弓と同じ（持ち方の決まり。弦は +x の側）。腕は角ばった石の塊を、青銅の継ぎ目でつなぐ。"""
    m = JavaModel("guardian_bow", 64, 64)
    m.box("grip", (6.9, 0, 7.1), (8.9, 5, 8.9), "gstone", tags={"steps": 2})
    m.box("grip_band", (6.7, 2.0, 6.9), (9.1, 3.0, 9.1), "gbronze")
    jade(m, "grip_core", (9.0, 1.75, 7.5), (9.75, 3.25, 8.5))
    segs = ((5, 9, 8.0), (9, 13, 8.6), (13, 16.5, 9.4), (16.5, 19, 10.3))
    for k, (y0, y1, x) in enumerate(segs):
        m.box(f"upper_{k}", (x - 0.75, y0 + 0.2, 7.3), (x + 0.75, y1 - 0.2, 8.7), "gstone", tags={"edge_bright": "top"})
        m.box(f"lower_{k}", (x - 0.75, -y1 + 5.2, 7.3), (x + 0.75, -y0 + 4.8, 8.7), "gstone", tags={"edge_bright": "bottom"})
        if k:
            m.box(f"joint_up_{k}", (x - 0.85, y0 - 0.3, 7.25), (x + 0.85, y0 + 0.3, 8.75), "gbronze")
            m.box(f"joint_dn_{k}", (x - 0.85, -y0 + 4.7, 7.25), (x + 0.85, -y0 + 5.3, 8.75), "gbronze")
    jade(m, "node_up", (8.7, 12.6, 7.6), (9.5, 13.4, 8.4))
    jade(m, "node_dn", (8.7, -8.4, 7.6), (9.5, -7.6, 8.4))
    m.box("tip_top", (9.9, 19, 7.5), (11.3, 20.4, 8.5), "gbronze")
    jade(m, "tip_top_core", (10.2, 20.4, 7.7), (11.0, 21.2, 8.3))
    m.box("tip_bottom", (9.9, -15.4, 7.5), (11.3, -14, 8.5), "gbronze")
    jade(m, "tip_bottom_core", (10.2, -16.0, 7.7), (11.0, -15.4, 8.3))
    m.box("string", (BOW_STRING_X[0], -14.9, 7.88), (BOW_STRING_X[1], 19.9, 8.12), "ivory")
    return m


# ================================================================ 双核刃（ローグ）

def build_daggers():
    """大きさは流紋の双短剣と同じ。三角の白い石の刃（根元が広い）と、まん中の翠のすじ。"""
    m = JavaModel("guardian_daggers", 64, 64)
    jade(m, "pommel", (7.4, -2.8, 7.4), (8.6, -1.6, 8.6))
    m.box("pommel_cap", (7.2, -1.6, 7.2), (8.8, -1.2, 8.8), "gbronze")
    m.box("grip", (7.35, -1.2, 7.35), (8.65, 4, 8.65), "gstone_dark", tags={"steps": 2})
    m.box("guard", (5.25, 4.0, 7.1), (10.75, 5.0, 8.9), "gbronze")
    m.box("guard_l", (5.25, 5.0, 7.1), (6.0, 6.0, 8.9), "gbronze")
    m.box("guard_r", (10.0, 5.0, 7.1), (10.75, 6.0, 8.9), "gbronze")
    for k, (x0, y0, x1, y1) in enumerate(((6.0, 5.0, 10.0, 8.0), (6.6, 8.0, 9.4, 11.0), (7.1, 11.0, 8.9, 14.0),
                                         (7.5, 14.0, 8.5, 16.5), (7.8, 16.5, 8.2, 18.0))):
        m.box(f"blade_{k}", (x0, y0, 7.65), (x1, y1, 8.35), "gstone", tags={"edge": "both"})
    jade(m, "inlay", (7.8, 5.5, 7.55), (8.2, 13.5, 8.45))
    return m


# ================================================================ 核拳（モンク）

def build_gauntlets():
    """腕の座標で作る（流紋の籠手と同じ。両手にはめる）。格子の石の筒と四角い石のこぶし・青銅の歯・甲の翠の核。"""
    m = JavaModel("guardian_gauntlets", 64, 64)
    m.box("cuff", (5.4, 4.2, 5.4), (10.6, 7.6, 10.6), "gstone", tags={"lattice": 3})
    m.box("cuff_ring", (5.2, 4.0, 5.2), (10.8, 4.8, 10.8), "gbronze")
    m.box("wrist_ring", (5.3, 7.4, 5.3), (10.7, 8.0, 10.7), "gbronze")
    m.box("fist", (5.5, 8.0, 5.5), (10.5, 11.0, 10.5), "gstone", tags={"steps": 2, "edge_bright": "top"})
    for k, x in enumerate((5.8, 7.45, 9.1)):
        m.box(f"tooth_{k}", (x, 9.6, 4.9), (x + 1.1, 11.2, 5.5), "gbronze")
    jade(m, "back_core", (7.25, 8.6, 10.5), (8.75, 10.1, 11.1))
    jade(m, "front_core", (7.5, 8.4, 5.0), (8.5, 9.3, 5.5))
    return m


WEAPONS = (
    (build_greatsword, "greatsword", lambda: weapon_display("greatsword"), "守護者の核剣（ナイト）",
     "白い石の一枚岩の刃（格子の溝・青銅の背）\nまん中の翠のすじ・根元の四角の輪の中の翠の核"),
    (build_staff, "staff", lambda: weapon_display("staff"), "守護者の核杖（ウィザード）",
     "積んだ石の柄の先に、四角の青銅の輪と\nその中に浮かぶ翠の立方体（輪の角に石）"),
    (build_bell_staff, "bell_staff", lambda: phase6_display("bell_staff"), "守護者の核鈴（ヒーラー）",
     "青銅の横木から下がる段の石の鐘と、翠の舌\n左右に下がる翠の房"),
    (build_bow, "bow", lambda: phase6_display("bow"), "守護者の核弓（アーチャー）",
     "角ばった石の腕を、青銅の継ぎ目でつなぐ\n継ぎ目と弭に翠・握りに翠の核"),
    (build_daggers, "daggers", lambda: phase6_display("daggers"), "守護者の双核刃（ローグ）",
     "三角の白い石の刃と、まん中の翠のすじ\n四角の青銅の鍔・翠の柄頭"),
    (build_gauntlets, "gauntlets", lambda: phase6_display("gauntlets"), "守護者の核拳（モンク）",
     "格子の石の筒と、四角い石のこぶし\n青銅の歯3つ・甲の翠の核（両手にはめる）"),
)


def main():
    import guardian_icons
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_guardian"
    os.makedirs(out, exist_ok=True)
    rows = []
    for build, kind, disp, label, note in WEAPONS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(GuardianWeaponPainter(m), frames=1)
        icon = guardian_icons.icon(m.name)
        weapons.export_weapon(repo, m.name, m, frames, icon, kind, CREDIT, display=disp())
        rows.append({"label": label, "rarity": "エピック（古代守護者のレア）", "note": note, "model": m, "tex": frames[0],
                     "icon": icon})
        print("wrote", m.name, "部品", len(m.elements), "光る部品", sum(1 for e in m.elements if e.glow))
    from setsheet import make_set_sheet
    from materials import RAMPS
    palette = [
        ("白い石", [RAMPS["gstone"][i] for i in (1, 2, 3, 4, 5, 6)]),
        ("翠の記録核（光る）", [RAMPS["jade"][i] for i in (2, 3, 4, 5, 6)]),
        ("青銅・暗い石・白い布", [RAMPS["gbronze"][3], RAMPS["gbronze"][5], RAMPS["gstone_dark"][3], RAMPS["ivory"][4]]),
    ]
    path = os.path.join(repo, "docs", "gear", "guardian_sheet.png")
    make_set_sheet(path, "守護者シリーズの武器（5階・古代守護者のレア）",
                   "エピック・翠の部品は光る｜11月26日に形から作り直した：白い石・青銅・翠の記録核・四角の輪", rows, palette=palette)
    import shutil
    shutil.copy(path, os.path.join(out, "guardian_weapons_sheet.png"))


if __name__ == "__main__":
    main()
