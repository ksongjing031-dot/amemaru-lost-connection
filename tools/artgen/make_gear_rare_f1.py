"""1階のレア装備（強い敵のまれな落とし物）：見張りの石刃（片手剣）・欠け牙の大剣（両手剣）・蜂晶の杖（杖・GeckoLib）。

  python3 tools/artgen/make_gear_rare_f1.py <リポジトリ> [確認画像の出力先]

レアの決まり：光る部分がある（動かない）。どれも落とした敵の体の一部と同じ色で光る。
  見張りの石刃 … 石くれ兵の継ぎ目と同じ青緑（#6FE3D0）。石を積んだ刃の継ぎ目が光る
  欠け牙の大剣 … 欠け牙の狼の牙と同じ白（ロストの白）。牙の欠けた所が光る
  蜂晶の杖     … 結晶蜂の青い結晶。蜂の巣の六角の枠にはめた核が光る。
                 ふだんは動かない。攻撃・スキルのとき（use）だけ、羽が開いて核がふくらむ
仕様書：docs/gear/rare_f1.md
"""
import json
import os
import sys
from types import SimpleNamespace

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from javamodel import JavaModel, rot_matrix  # noqa: E402
from materials import Painter, RAMPS, pick, hsh  # noqa: E402
import weapons  # noqa: E402

CREDIT = "AMEMARU: 1階のレア装備（tools/artgen/make_gear_rare_f1.py で生成。docs/gear/rare_f1.md）"
MOSS_COL = (75, 97, 64)


# ---------------------------------------------------------------- 見張りの石刃（片手剣）

def build_sentinel_blade():
    m = JavaModel("sentinel_blade", 64, 64)
    # 柄頭：小さな見張り石（積んだ石2段）。横一本の目が青緑に光る
    m.box("pommel", (6.75, -2.5, 6.75), (9.25, -0.75, 9.25), "stone")
    m.box("pommel_cap", (7.25, -3.25, 7.25), (8.75, -2.5, 8.75), "stone")
    m.box("pommel_eye", (7.25, -1.95, 6.6), (8.75, -1.45, 9.4), "glow_teal", glow=True)
    # 握り：苔鉄の芯に黒い革を巻く。上下に苔鉄の輪
    m.box("grip", (7.3, -0.75, 7.3), (8.7, 4.5, 8.7), "leather_dark", tags={"wrap": True})
    m.box("grip_ring", (7.1, 1.6, 7.1), (8.9, 2.1, 8.9), "iron")
    # 鍔：刃の側へ長くのびた石（片刃なので、刃の側を守る）。まん中に見張りの「目」
    m.box("guard", (6.0, 4.5, 7.0), (11.0, 6.25, 9.0), "stone", tags={"grooves": [5.25]})
    m.box("guard_jaw", (10.5, 3.5, 7.15), (11.75, 4.75, 8.85), "stone")
    m.box("guard_back", (5.25, 4.75, 7.15), (6.0, 6.0, 8.85), "iron")
    m.box("guard_eye", (7.5, 4.9, 6.75), (8.5, 5.85, 9.25), "glow_teal", glow=True)
    # 刃：苔鉄の背（左）に、石の塊を5つ積んだ片刃。継ぎ目（少し奥）が青緑に光る
    m.box("spine", (6.25, 6.25, 7.6), (7.0, 23.25, 8.4), "iron",
          tags={"rivets": [(6.625, 8.5), (6.625, 12.0), (6.625, 15.5), (6.625, 19.0)]})
    segs = ((6.25, 9.75, 10.25), (10.25, 13.25, 10.5), (13.75, 16.75, 10.75), (17.25, 20.25, 10.5),
            (20.75, 22.25, 10.0))
    for k, (y0, y1, x1) in enumerate(segs):
        tags = {"sharp": True}
        if k in (1, 2, 3):
            tags["grooves"] = [y0 + 1.25]
        m.box(f"seg_{k}", (7.0, y0, 7.5), (x1, y1, 8.5), "stone", tags=tags)
        if k < len(segs) - 1:
            nx1 = min(x1, segs[k + 1][2]) - 0.35
            m.box(f"seam_{k}", (7.0, y1, 7.65), (nx1, segs[k + 1][0], 8.35), "glow_teal", glow=True)
    # 先：背の側がとがる段々の先（道標の段）
    m.box("tip_1", (7.0, 22.25, 7.55), (9.25, 23.25, 8.45), "stone", tags={"sharp": True})
    m.box("tip_2", (6.25, 23.25, 7.6), (8.25, 24.25, 8.4), "stone", tags={"sharp": True})
    m.box("tip_3", (6.25, 24.25, 7.65), (7.5, 25.0, 8.35), "stone", tags={"sharp": True})
    return m


# ---------------------------------------------------------------- 欠け牙の大剣（両手剣）

def build_chipped_fang():
    m = JavaModel("chipped_fang_greatsword", 64, 64)
    # 柄頭：狼の爪（骨）。先が片側へ曲がる
    m.box("pommel", (7.25, -5.0, 7.25), (8.75, -3.0, 8.75), "bone")
    m.box("claw", (7.5, -6.75, 7.5), (8.5, -4.75, 8.5), "bone", rot=((8, -5, 8), "z", 22.5), tags={"claw": True})
    # 握り：狼の毛を巻いた長い柄。まん中に苔鉄の輪
    m.box("grip", (7.2, -3.0, 7.2), (8.8, 4.5, 8.8), "fur_wolf", tags={"wrap": True})
    m.box("grip_ring", (7.0, 0.5, 7.0), (9.0, 1.25, 9.0), "iron")
    # 鍔：苔鉄の棒、両端に骨。背の側に狼の毛の房
    m.box("guard", (4.5, 4.5, 7.25), (11.5, 6.0, 8.75), "iron", tags={"rivets": [(6.0, 5.25), (10.0, 5.25)]})
    m.box("guard_bone_l", (3.75, 4.25, 7.0), (4.75, 6.25, 9.0), "bone")
    m.box("guard_bone_r", (11.25, 4.25, 7.0), (12.25, 6.25, 9.0), "bone")
    m.box("tuft", (4.75, 2.25, 7.4), (6.0, 4.5, 8.6), "fur_wolf", tags={"tuft": True})
    # 刃の背：苔鉄の枠（左）に、背骨のような骨の板（3つ）
    m.box("frame", (5.5, 6.0, 7.6), (7.0, 24.5, 8.4), "iron")
    for k, y in enumerate((9.0, 15.0, 21.0)):
        m.box(f"vertebra_{k}", (5.0, y, 7.25), (7.25, y + 2.0, 8.75), "bone", tags={"vertebra": True})
    m.box("socket", (6.5, 6.0, 7.25), (11.5, 7.25, 8.75), "iron", tags={"rivets": [(9.0, 6.6)]})
    # 牙：根元が広く、まん中がふくらみ、先は背の側へ曲がる。背の側は厚く、刃の側は薄い
    #     （根元は象牙色、先へ行くほど白いエナメル）。上の方の刃が欠け、欠けた所が白く光る
    body = ((7.25, 10.0, 11.75), (10.0, 12.5, 11.5), (12.5, 15.0, 11.25), (15.0, 17.0, 11.0), (17.0, 18.0, 10.75),
            (18.0, 21.0, 9.25), (21.0, 22.75, 10.0), (22.75, 24.5, 9.25))
    for k, (y0, y1, x1) in enumerate(body):
        thick_x = min(9.0, x1 - 0.75)
        m.box(f"fang_core_{k}", (7.0, y0, 7.3), (thick_x, y1, 8.7), "fang", tags={"core": True})
        tags = {"sharp": True} if k != 5 else {"chip_side": True}
        m.box(f"fang_edge_{k}", (thick_x, y0, 7.55), (x1, y1, 8.45), "fang", tags=tags)
    m.box("tip_1", (6.5, 24.5, 7.4), (9.0, 25.75, 8.6), "fang", tags={"sharp": True})
    m.box("tip_2", (6.0, 25.75, 7.5), (8.0, 26.75, 8.5), "fang", tags={"sharp": True})
    m.box("tip_3", (5.5, 26.5, 7.6), (7.25, 28.0, 8.4), "fang", rot=((6.5, 26.5, 8), "z", 22.5), tags={"sharp": True})
    m.box("frame_cap", (5.0, 24.25, 7.45), (7.0, 25.25, 8.55), "bone")
    # 欠けた所：刃が大きく欠けた跡（割れた面）と、まわりのひび。白く光る
    m.box("chip", (9.25, 18.0, 7.6), (9.6, 21.0, 8.4), "glow_lost", glow=True)
    # ひび：欠けた所から、刃の根元の方へ稲妻のように走る（表と裏。牙の面より少し浮かせる）
    for k, (x0, y0, x1, y1) in enumerate(((8.25, 19.75, 9.25, 20.1), (8.0, 18.5, 8.35, 20.1), (7.4, 18.5, 8.0, 18.85),
                                          (9.0, 21.0, 9.35, 22.0), (7.4, 16.75, 7.75, 18.5), (7.75, 16.75, 8.6, 17.1),
                                          (8.6, 15.5, 8.95, 17.1))):
        m.box(f"crack_{k}", (x0, y0, 7.2), (x1, y1, 8.8), "glow_lost", glow=True)
    return m


# ---------------------------------------------------------------- 蜂晶の杖（GeckoLib）

def gcube(g, bone, name, origin, size, mat, rot=None, pivot=None, **kw):
    """ゲームの中の向き（x は反転済み。表が +z）で書いた箱を、Bedrock の値にして足す。"""
    x0, y0, z0 = origin
    w = size[0]
    b_origin = (-(x0 + w), y0, z0)
    b_rot = None
    b_pivot = None
    if rot:
        b_rot = (-rot[0], -rot[1], rot[2])
        b_pivot = (-pivot[0], pivot[1], pivot[2])
    return g.cube(bone, name, b_origin, size, mat, rot=b_rot, pivot=b_pivot, **kw)


def build_bee_staff():
    """原点（0,0,0）が、アイテムの絵の中心（8, 8.16, 8）に来る。握りは y −8〜−2（Java の 0〜6 と同じ）。"""
    g = GeoModel("bee_crystal_staff", 64, 64)
    g.bone("staff", (0, 0, 0))
    g.bone("core", (0, 13, 0), parent="staff")
    g.bone("wing_left", (4.4, 15.0, 0), parent="staff")
    g.bone("wing_right", (-4.4, 15.0, 0), parent="staff")
    S = "staff"
    # 石突き：結晶蜂の針（細い結晶）と、青銅の口金
    gcube(g, S, "needle_tip", (-0.5, -17.5, -0.5), (1, 1, 1), "needle", inflate=-0.25)
    gcube(g, S, "needle", (-0.5, -16.5, -0.5), (1, 2, 1), "needle", inflate=-0.1)
    gcube(g, S, "ferrule", (-1, -14.5, -1), (2, 1, 2), "bronze")
    # 柄：くるみの木。握りは黒い革、上下に青銅の輪
    gcube(g, S, "shaft", (-0.5, -13.5, -0.5), (1, 21, 1), "walnut", inflate=0.15)
    gcube(g, S, "grip", (-1, -8, -1), (2, 6, 2), "leather_dark", tags={"wrap": True})
    gcube(g, S, "band_low", (-1, -9, -1), (2, 1, 2), "bronze", inflate=0.1)
    gcube(g, S, "band_high", (-1, -2, -1), (2, 1, 2), "bronze", inflate=0.1)
    gcube(g, S, "collar", (-1.5, 6.5, -1.5), (3, 2, 3), "bronze", tags={"cells": True})
    # 先：蜂の巣の六角の枠（青銅）。とがった頂点が上と下
    cx, cy, r = 0.0, 13.0, 4.5
    hx = r * np.cos(np.radians(30))
    gcube(g, S, "hex_r", (hx - 0.5, cy - 2.5, -1), (1, 5, 2), "bronze")
    gcube(g, S, "hex_l", (-hx - 0.5, cy - 2.5, -1), (1, 5, 2), "bronze")
    for name, sx, sy, ang in (("hex_tr", 1, 1, -30), ("hex_tl", -1, 1, 30), ("hex_br", 1, -1, 30), ("hex_bl", -1, -1, -30)):
        mx, my = sx * hx / 2, cy + sy * (r * 0.75)
        gcube(g, S, name, (mx - 2.5, my - 0.5, -1), (5, 1, 2), "bronze", rot=(0, 0, ang), pivot=(mx, my, 0))
    for k, (vx, vy) in enumerate(((0, cy + r), (hx, cy + r / 2), (hx, cy - r / 2), (0, cy - r), (-hx, cy - r / 2),
                                  (-hx, cy + r / 2))):
        gcube(g, S, f"hex_v{k}", (vx - 0.5, vy - 0.5, -1), (1, 1, 2), "bronze", inflate=0.2, tags={"vertex": True})
    # 核をとめる青銅のピンと、てっぺんの小さな結晶の針
    gcube(g, S, "pin_top", (-0.5, cy + 3, -0.5), (1, 1, 1), "bronze")
    gcube(g, S, "pin_low", (-0.5, cy - 4, -0.5), (1, 1, 1), "bronze")
    gcube(g, S, "crown", (-1, cy + r, -1), (2, 1, 2), "bronze", inflate=-0.2)
    gcube(g, S, "crown_needle", (-0.5, cy + r + 0.6, -0.5), (1, 2, 1), "needle", inflate=-0.2)
    # 核：結晶蜂の青い結晶。琥珀色の縞（蜂の印）。光る
    gcube(g, "core", "core", (-1.5, cy - 2, -1), (3, 4, 2), "glow_bee", glow=True)
    gcube(g, "core", "core_top", (-0.5, cy + 2, -0.5), (1, 1, 1), "glow_bee", glow=True, inflate=-0.1)
    gcube(g, "core", "core_low", (-0.5, cy - 3, -0.5), (1, 1, 1), "glow_bee", glow=True, inflate=-0.1)
    # 羽：結晶の薄い羽（前と後ろの2枚ずつ）。ふだんはたたんで、後ろへのびる
    # Bedrock の left は +x（ゲームの中では −x）。wing_left はゲームの −x 側
    for side, bone, sx in (("l", "wing_left", -1), ("r", "wing_right", 1)):
        x = sx * (hx + 0.55)
        gcube(g, bone, f"wing_fore_{side}", (x, cy + 0.5, -5), (0, 6, 5), "wing", rot=(-22.5, 0, 0),
              pivot=(x, cy + 2, 0), tags={"wing": "fore"})
        gcube(g, bone, f"wing_hind_{side}", (x, cy - 1.5, -4), (0, 4, 4), "wing", rot=(-10, 0, 0),
              pivot=(x, cy, 0), tags={"wing": "hind"})
    return g


BEE_ANIMATIONS = {
    "animation.bee_crystal_staff.idle": {"loop": True, "animation_length": 1.0, "bones": {}},
    "animation.bee_crystal_staff.use": {
        "animation_length": 0.7,
        "bones": {
            "core": {"scale": {"0.0": [1, 1, 1], "0.12": [1.4, 1.4, 1.4], "0.45": [1.15, 1.15, 1.15], "0.7": [1, 1, 1]}},
            "wing_left": {"rotation": {"0.0": [0, 0, 0], "0.12": [0, -80, 0], "0.5": [0, -80, 0], "0.7": [0, 0, 0]}},
            "wing_right": {"rotation": {"0.0": [0, 0, 0], "0.12": [0, 80, 0], "0.5": [0, 80, 0], "0.7": [0, 0, 0]}},
        },
    },
}


def posed(g, bone_rot):
    """use のアニメーションの途中（羽を開き、核をふくらませた形）を、確認画像のために作る。
    bone_rot：{ボーン: (Bedrockの回転 (rx, ry, rz), 大きさ)}。GeckoLib と同じく、ボーンの回転は x と y を反転して使う。"""
    pivots = {name: np.array([-p[0], p[1], p[2]], dtype=float) for name, p, _ in g.bones}
    out = []
    for c in g.elements:
        if c.bone not in bone_rot:
            out.append(c)
            continue
        (rx, ry, rz), s = bone_rot[c.bone]
        R = rot_matrix("z", rz) @ rot_matrix("y", -ry) @ rot_matrix("x", -rx)
        out.append(_Posed(c, R, pivots[c.bone], s))
    return SimpleNamespace(elements=out)


class _Posed:
    def __init__(self, c, R, pivot, scale):
        self._c, self._R, self._pv, self._s = c, R, pivot, scale
        self.faces = {k: _PosedFace(f, self) for k, f in c.faces.items()}

    def __getattr__(self, k):
        return getattr(self._c, k)

    def move(self, q):
        return self._R @ ((q - self._pv) * self._s) + self._pv


class _PosedFace:
    def __init__(self, f, owner):
        self._f, self._o = f, owner

    def __getattr__(self, k):
        return getattr(self._f, k)

    def corners_world(self):
        return [self._o.move(q) for q in self._f.corners_world()]

    def normal_world(self):
        return self._o._R @ self._f.normal_world()


# ---------------------------------------------------------------- 塗り

class RarePainter(Painter):
    def bevel(self, c, up=1.2, down=1.0):
        if c.el.mat == "fang" and self.facing(c):
            # 牙の表と裏：上下の継ぎ目は見せない（1本の牙に見えるように）。左右のふちだけ
            d = c.edge_dist()
            if d["left"] == 0:
                return up if c.du[0] > 0 else -down
            if d["right"] == 0:
                return -down if c.du[0] > 0 else up
            return 0.0
        return super().bevel(c, up, down)

    def paint_stone(self, c):
        tone = self.std(c, "stone", base=3.1, noise=0.18, blob=0.4, up=1.3, down=1.1, specks=0.03)
        tone += self.grooves(c)
        if c.el.tags.get("sharp"):
            # 研いだ刃（右のふち）：表と裏のいちばん右の列と、右の面が明るい
            if self.facing(c) and c.el.to[0] - c.p[0] < 0.75:
                tone += 1.6
            if c.n[0] > 0.6:
                tone += 1.2
        if abs(c.n[0]) > 0.6:
            tone += 0.35
        col = pick(RAMPS["stone"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 300 + self.eid(c)) < 0.12:
            col = MOSS_COL
        return col

    def paint_iron(self, c):
        tone = self.std(c, "iron", base=3.0, noise=0.2, blob=0.35, up=1.6, down=0.9)
        tone += self.rivet(c)
        col = pick(RAMPS["iron"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 500 + self.eid(c)) < 0.18:
            col = MOSS_COL
        return col

    def paint_leather_dark(self, c):
        tone = self.std(c, "leather_dark", base=3.1, noise=0.25, blob=0.2, up=0.8, down=0.6) + self.wrap(c)
        return pick(RAMPS["leather_dark"], tone)

    def paint_glow_teal(self, c):
        return self.paint_glow(c, "glow_teal", base=4.0)

    def paint_glow_lost(self, c):
        return self.paint_glow(c, "glow_lost", base=4.6)

    def paint_fang(self, c):
        rgb = self._fang(c)
        # 根元（y 7〜15）は象牙色、先（y 19〜）は白いエナメル
        t = min(1.0, max(0.0, (c.p[1] - 13.0) / 7.0))
        warm = pick(RAMPS["bone"], self._fang_tone)
        return tuple(int(round(warm[i] + (rgb[i] - warm[i]) * t)) for i in range(3))

    def _fang(self, c):
        tone = self.std(c, "fang", base=4.3, noise=0.06, blob=0.12, up=0.9, down=1.0)
        # 牙のエナメルの縦すじ（うすく）
        col_k = int(np.floor(c.p[0] * 2 + 0.01))
        if self.facing(c) and c.el.tags.get("core") and hsh(col_k, 77) < 0.3:
            tone -= 0.25
        if c.p[1] < 9.0:
            tone -= 0.6 * (9.0 - c.p[1]) / 1.75  # 根元（ソケットの近く）は少し暗い
        if c.el.tags.get("sharp"):
            if self.facing(c) and c.el.to[0] - c.p[0] < 0.75:
                tone += 1.3
            if c.n[0] > 0.6:
                tone += 1.0
        if c.el.tags.get("chip_side") and c.n[0] > 0.6:
            tone -= 1.6  # 欠けた所の奥（影）
        if self.facing(c) and 15.0 < c.p[1] < 22.0 and abs(c.p[0] - 8.4) < 1.6:
            tone -= 0.9  # 欠けのまわりのくすんだエナメル（光るひびが見えるように）
        if c.el.tags.get("core") and self.facing(c) and c.p[0] - c.el.frm[0] < 0.6:
            tone -= 0.4  # 背の側の根元の影
        self._fang_tone = tone + 0.3
        return pick(RAMPS["fang"], tone)

    def paint_bone(self, c):
        tone = self.std(c, "bone", base=3.6, noise=0.2, blob=0.3, up=1.2, down=1.0)
        if c.el.tags.get("claw") and c.j == c.H - 1:
            tone -= 1.0
        if c.el.tags.get("vertebra") and self.facing(c) and abs(c.p[1] - (c.el.frm[1] + c.el.to[1]) / 2) < 0.5:
            tone -= 1.1  # 背骨の節の溝
        return pick(RAMPS["bone"], tone)

    def paint_fur_wolf(self, c):
        # 毛の束：縦に流れるすじ。巻いた所は斜め
        strand = hsh(int(np.floor(c.p[0] * 2 + c.p[2] * 2)), int(np.floor(c.p[1] / 1.5)), 31)
        tone = 3.0 + (strand - 0.5) * 1.2 + self.bevel(c, up=0.8, down=0.8) + self.wrap(c) * 0.6
        if c.el.tags.get("tuft") and c.j == c.H - 1 and c.i % 2 == 0:
            return None  # 房の先はぎざぎざ
        return pick(RAMPS["fur_wolf"], tone)

    def paint_bronze(self, c):
        tone = self.std(c, "bronze", base=3.4, noise=0.2, blob=0.3, up=1.5, down=1.0)
        if c.el.tags.get("cells") and self.facing(c) and (c.i + (c.j % 2)) % 2 == 0:
            tone -= 0.8  # 蜂の巣の小さな穴
        if c.el.tags.get("vertex"):
            tone += 0.6
        return pick(RAMPS["bronze"], tone)

    def paint_walnut(self, c):
        tone = self.std(c, "walnut", base=3.2, noise=0.25, blob=0.2, up=0.9, down=0.8)
        if hsh(int(np.floor(c.p[1] / 2)), int(np.floor(c.p[0] + c.p[2])), 51) < 0.2:
            tone -= 0.6
        return pick(RAMPS["walnut"], tone)

    def paint_needle(self, c):
        tone = 4.0 + (self.tex(c, 91) - 0.5) * 0.6 + self.bevel(c, up=1.4, down=1.0)
        return pick(RAMPS["crystal_dim"], tone)

    def paint_glow_bee(self, c):
        tone = 4.0 + (self.tex(c, 81) - 0.5) * 0.5
        if c.el.tags.get("stripes") and c.face.d not in ("up", "down"):
            # 琥珀色の縞（結晶蜂の体の色）
            if c.j in (1, 3) if c.H >= 4 else c.j == 1:
                return pick(RAMPS["glow_amber"], 3.6 + (self.tex(c, 82) - 0.5) * 0.6)
        d = c.edge_dist()
        if min(d.values()) >= 1:
            tone += 1.4  # まん中ほど明るい
        return pick(RAMPS["glow_bee"], tone)

    def paint_wing(self, c):
        # 結晶の薄い羽：位置で塗る（表と裏が同じ色になるように）。ふちを丸く抜く
        tags = c.el.tags
        # 位置（ゲームの中の y と z）で決める：表と裏が同じ色になる
        y0, y1 = c.el.frm[1], c.el.to[1]
        z0, z1 = c.el.frm[2], c.el.to[2]
        u = (c.p[2] - z0) / (z1 - z0)
        v = (c.p[1] - y0) / (y1 - y0)
        r2 = (u - 0.5) ** 2 / 0.25 + (v - 0.5) ** 2 / 0.25
        if r2 > 1.05:
            return None
        if r2 > 0.62:
            return (96, 142, 168, 255)  # ふちの筋
        if abs((1 - u) - v) < 0.12:
            return (120, 170, 196, 255)  # まん中の筋（付け根から先へ）
        base = (206, 238, 250) if tags.get("wing") == "fore" else (186, 226, 244)
        return base + (255,)


# ---------------------------------------------------------------- 持ち物の絵（16×16）

ICON = {
    "S": "#D8E1E3", "s": "#B7C3C8", "t": "#7C8A92", "T": "#606D75", "u": "#454F56",
    "i": "#757E79", "I": "#9AA39E", "k": "#323836", "x": "#232827",
    "g": "#38B9A6", "G": "#6FE3D0", "H": "#B0F5EA",
    "L": "#654430", "l": "#3A261A",
    "f": "#D6DBE0", "F": "#FAFBFC", "e": "#9DA5B0", "E": "#BCC3CB",
    "b": "#CBC3A8", "B": "#E1DAC2", "n": "#8E846D",
    "w": "#5B6270", "W": "#8D95A2", "v": "#444B57",
    "o": "#AD8247", "O": "#E2C385", "p": "#6A4C28",
    "c": "#4E9EDD", "C": "#C6EAFF", "a": "#E8AA35",
    "r": "#875D3E", "R": "#523723",
    "q": "#BFDDE0", "Q": "#72A4AE",
}


def ascii_icon(rows, outline):
    from pixelart import Pixel
    from materials import hexc
    rows = [r for r in rows.strip("\n").splitlines()]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    p = Pixel()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                p.put(x, y, hexc(ICON[ch]))
    p.outline(outline)
    return p.icon()


def _pixel():
    from pixelart import Pixel
    return Pixel()


def _c(k):
    from materials import hexc
    return hexc(ICON[k])


def icon_sentinel():
    p = _pixel()
    # 刃：苔鉄の背（左）・石（段ごとに色を変える）・研いだふち（右）。継ぎ目は青緑に光る
    def row(t):
        if t in (9, 12):
            return [_c("k"), _c("g"), _c("G"), _c("H")]
        shade = _c("s") if t in (8, 13, 14) else _c("t")
        extra = [_c("T")] if t in (10, 11) else []
        return [_c("k"), shade, _c("s"), _c("S")] + extra
    p.diag_band(8, 13, row, dx0=-2)
    x, y = p.axis(14)
    p.put(x - 2, y, _c("k"))
    p.put(x - 1, y, _c("s"))
    p.put(x, y, _c("S"))
    x, y = p.axis(15)
    p.put(x - 2, y, _c("t"))
    # 鍔：刃の側（右下）へ長い石。まん中に見張りの目
    x, y = p.axis(7)
    for u in range(-1, 4):
        p.put(x + u, y + u, _c("s") if u < 3 else _c("t"))
        p.put(x + u + 1, y + u, _c("T"))
    p.put(x, y, _c("G"))
    p.put(x + 1, y, _c("g"))
    # 握り（黒い革）と柄頭（積んだ石と青緑の目）
    for t in (4, 5, 6):
        x, y = p.axis(t)
        p.put(x, y, _c("L") if t % 2 == 0 else _c("l"))
        p.put(x + 1, y, _c("l") if t % 2 == 0 else _c("L"))
    x, y = p.axis(3)
    p.put(x, y, _c("s"))
    p.put(x + 1, y, _c("G"))
    p.put(x, y + 1, _c("t"))
    p.put(x + 1, y + 1, _c("T"))
    return p


def icon_fang():
    p = _pixel()
    # 刃：苔鉄の枠（左）と骨の節・牙（根元は象牙、先は白）。先は背の側へ曲がる。上の方の刃に光る欠け
    def row(t):
        frame = _c("B") if t in (7, 10) else _c("k")
        body = [_c("b"), _c("B")] if t <= 8 else ([_c("E"), _c("f")] if t <= 10 else [_c("f"), _c("F")])
        width_extra = [_c("e")] if t <= 9 else []
        return [frame] + body + [_c("F")] + width_extra
    p.diag_band(6, 12, row, dx0=-2)
    # 欠け：刃のふちを2つ欠き、その奥が白く光る
    for t in (10, 11):
        x, y = p.axis(t)
        p.erase(x + 1, y)
        p.put(x, y, _c("C"))
    # 先：細くなって、背の側（左上）へ曲がる
    x, y = p.axis(13)
    p.put(x - 2, y, _c("k"))
    p.put(x - 1, y, _c("f"))
    p.put(x, y, _c("F"))
    x, y = p.axis(14)
    p.put(x - 3, y, _c("f"))
    p.put(x - 2, y, _c("F"))
    # 鍔：苔鉄の棒と、両端の骨
    x, y = p.axis(5)
    for u in range(-2, 3):
        p.put(x + u, y + u, _c("b") if abs(u) == 2 else _c("i"))
        p.put(x + u + 1, y + u, _c("n") if abs(u) == 2 else _c("k"))
    # 握り（狼の毛）と柄頭（骨の爪）
    for t in (2, 3, 4):
        x, y = p.axis(t)
        p.put(x, y, _c("W") if t % 2 == 0 else _c("w"))
        p.put(x + 1, y, _c("v"))
    x, y = p.axis(1)
    p.put(x, y, _c("b"))
    p.put(x + 1, y, _c("n"))
    return p


BEE_HEAD = (
    "....qq.oOo......",
    "...qQq.pCcoQq...",
    "....qqopcCCo.qQ.",
    "......pccCco.q..",
    ".......pcco.....",
    "........pp......",
)


def icon_bee():
    p = _pixel()
    # 先：蜂の巣の六角の枠（青銅）に青い核。両側にたたんだ結晶の羽
    for dy, row in enumerate(BEE_HEAD):
        for x, ch in enumerate(row):
            if ch != ".":
                p.put(x, 1 + dy, _c(ch))
    # 柄：くるみの木、握りは黒い革、青銅の輪。石突きは結晶の針
    for t in range(2, 9):
        x, y = p.axis(t)
        if t in (4, 5):
            p.put(x, y, _c("L"))
            p.put(x + 1, y, _c("l"))
        elif t in (3, 6):
            p.put(x, y, _c("O"))
            p.put(x + 1, y, _c("o"))
        else:
            p.put(x, y, _c("r"))
            p.put(x + 1, y, _c("R"))
    x, y = p.axis(1)
    p.put(x + 1, y, _c("q"))
    p.put(x, y + 1, _c("Q"))
    return p


def icons():
    out = {}
    for name, fn, outline in (("sentinel_blade", icon_sentinel, "#101415"),
                              ("chipped_fang_greatsword", icon_fang, "#14161C"),
                              ("bee_crystal_staff", icon_bee, "#1A140C")):
        p = fn()
        p.outline(outline)
        out[name] = p.icon()
    return out


# ---------------------------------------------------------------- 確認画像（作業用）

def draft(out):
    from render import render_many
    from PIL import Image
    tiles = []
    for build in (build_sentinel_blade, build_chipped_fang):
        m = build()
        m.cull_hidden_faces()
        m.pack()
        tex = m.paint(RarePainter(m), frames=1)[0]
        for yaw, pitch in ((0, 0), (-90, 0), (-35, 16)):
            tiles.append(render_many([(m, tex)], yaw=yaw, pitch=pitch, scale=11, size=(260, 420), center=(8, 11, 8)))
        tiles.append(render_many([(m, tex)], yaw=-35, pitch=16, scale=11, size=(260, 420), center=(8, 11, 8), darkness=0.25))
    g = build_bee_staff()
    g.pack()
    tex = g.paint(RarePainter(g), frames=1)[0]
    for yaw, pitch in ((0, 0), (-90, 0), (-35, 16)):
        tiles.append(render_many([(g, tex)], yaw=yaw, pitch=pitch, scale=11, size=(260, 420), center=(0, 1, 0)))
    opened = posed(g, {"wing_left": ((0, -80, 0), 1.0), "wing_right": ((0, 80, 0), 1.0), "core": ((0, 0, 0), 1.4)})
    tiles.append(render_many([(opened, tex)], yaw=-20, pitch=10, scale=11, size=(260, 420), center=(0, 1, 0)))
    s = Image.new("RGBA", (260 * 4, 420 * 3), (40, 44, 52, 255))
    for k, im in enumerate(tiles):
        s.alpha_composite(im, (260 * (k % 4), 420 * (k // 4)))
    s.save(os.path.join(out, "rare_draft.png"))
    ic = icons()
    s2 = Image.new("RGBA", (3 * 138 + 10, 148), (139, 139, 139, 255))
    for k, n in enumerate(("sentinel_blade", "chipped_fang_greatsword", "bee_crystal_staff")):
        s2.alpha_composite(ic[n].big(8), (10 + k * 138, 10))
    s2.save(os.path.join(out, "rare_icons.png"))


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def extent_center(model):
    pts = np.array([q for el in model.elements for f in el.faces.values() for q in f.corners_world()])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    return (lo + hi) / 2, lo, hi


def export_bee_staff(repo, g, frames, icon, display):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    g.save(repo, "item", frames, animations=BEE_ANIMATIONS)
    icon.save(os.path.join(base, "textures/item/bee_crystal_staff_icon.png"))
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/bee_crystal_staff_icon"}}
    write_json(os.path.join(base, "models/item/bee_crystal_staff.json"), {
        "loader": "forge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": "amemaru:item/bee_crystal_staff_3d"},
        "perspectives": {"gui": flat, "fixed": flat, "ground": flat},
    })
    write_json(os.path.join(base, "models/item/bee_crystal_staff_3d.json"), {
        "credit": CREDIT,
        "parent": "builtin/entity",
        "gui_light": "front",
        "textures": {"particle": "amemaru:item/bee_crystal_staff_icon"},
        "display": display,
    })


def build_all():
    ic = icons()
    items = []
    for build, kind, label, rarity_note, note in (
            (build_sentinel_blade, "sword", "見張りの石刃", "レア（石くれ兵のまれな落とし物）",
             "石くれ兵の体と同じ、積んだ石の片刃\n継ぎ目（見張りの古い命令の文字）が\n青緑に光る。鍔と柄頭に見張りの目"),
            (build_chipped_fang, "greatsword", "欠け牙の大剣", "レア（欠け牙の狼のまれな落とし物）",
             "狼の白い牙を、苔鉄と骨の枠にはめた\n片刃。根元は象牙色、先は白く背へ曲がる\n大きく欠けた所のひびが白く光る")):
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(RarePainter(m), frames=1)
        items.append({"name": m.name, "kind": kind, "model": m, "frames": frames, "tex": frames[0],
                      "icon": ic[m.name], "label": label, "rarity_note": rarity_note, "note": note})
    g = build_bee_staff()
    g.pack()
    frames = g.paint(RarePainter(g), frames=1)
    items.append({"name": "bee_crystal_staff", "kind": "staff", "model": g, "frames": frames, "tex": frames[0],
                  "icon": ic["bee_crystal_staff"], "label": "蜂晶の杖", "rarity_note": "レア（結晶蜂のまれな落とし物）",
                  "note": "蜂の巣の六角の青銅の枠に、結晶蜂の\n青い核（光る）。両側にたたんだ結晶の羽\n攻撃・スキルのときだけ羽が開き核がふくらむ"})
    return items


# 片刃の武器（持ったときに刃が前を向くようにする。weapons.hold_display）
SINGLE_EDGE = ("sentinel_blade", "chipped_fang_greatsword")


def base_display(name, kind):
    """持ち方のもと（weapons.hold_display で大きさと片刃の向きを掛ける前）。古代石の武器と同じ持ち方から始める。"""
    from make_gear_ancient_stone import weapon_display
    display = weapon_display(kind)
    # 一人称の大きさ：石刃は刃が太く、杖は先の六角が大きいので、古代石の武器より少し小さくする（CIの写真で決めた）
    fp = {"sentinel_blade": (0.5, 0.0), "bee_crystal_staff": (0.42, -0.75)}.get(name)
    if fp:
        for hand in ("firstperson_righthand", "firstperson_lefthand"):
            t = list(display[hand]["translation"])
            t[1] = round(t[1] + fp[1], 2)
            display[hand] = dict(display[hand], scale=[fp[0]] * 3, translation=t)
    return display


def main():
    from setsheet import make_set_sheet
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_rare"
    os.makedirs(out, exist_ok=True)
    items = build_all()
    for it in items:
        display = base_display(it["name"], it["kind"])
        if it["name"] == "bee_crystal_staff":
            export_bee_staff(repo, it["model"], it["frames"], it["icon"], display)
        else:
            # 石刃と大剣は片刃（刃の側がモデルの +x）。持ったときに刃が前を向くように、手の中で刃の軸のまわりに回す
            weapons.export_weapon(repo, it["name"], it["model"], it["frames"], it["icon"], it["kind"], CREDIT,
                                  display=display, edge_forward=it["name"] in SINGLE_EDGE)
        c, lo, hi = extent_center(it["model"])
        if it["name"] == "bee_crystal_staff":
            c = c + np.array([8.0, 8.16, 8.0])
        print(it["name"], "中心（アイテムのピクセル）", np.round(c, 2), "大きさ", np.round(hi - lo, 2))
    rows = [{"label": it["label"], "rarity": it["rarity_note"], "rarity_color": (96, 168, 255), "note": it["note"],
             "model": it["model"], "tex": it["tex"], "icon": it["icon"]} for it in items]
    sb, fg, bs = items
    opened = posed(bs["model"], {"wing_left": ((0, -80, 0), 1.0), "wing_right": ((0, 80, 0), 1.0), "core": ((0, 0, 0), 1.4)})
    details = [
        {"label": "石刃：暗い所の継ぎ目と目", "model": sb["model"], "tex": sb["tex"], "center": (8.4, 13, 8), "yaw": -30,
         "pitch": 10, "scale": 15, "darkness": 0.22},
        {"label": "大剣：欠けた所の白いひび", "model": fg["model"], "tex": fg["tex"], "center": (8.6, 19.5, 8), "yaw": -25,
         "pitch": 8, "scale": 26},
        {"label": "杖：六角の枠と青い核（ふだん）", "model": bs["model"], "tex": bs["tex"], "center": (0, 13, -1),
         "yaw": -40, "pitch": 12, "scale": 22},
        {"label": "杖：攻撃・スキルのとき（羽が開く）", "pairs": [(opened, bs["tex"])], "center": (0, 13.5, -1), "yaw": -15,
         "pitch": 8, "scale": 19},
    ]
    R = RAMPS
    palette = [
        ("見張りの石刃：石・苔鉄・青緑", [R["stone"][i] for i in (2, 4, 6)] + [R["iron"][i] for i in (2, 4)] +
         [R["glow_teal"][i] for i in (3, 4, 5)]),
        ("欠け牙の大剣：象牙・白・狼の毛", [R["bone"][i] for i in (3, 5)] + [R["fang"][i] for i in (3, 5, 6)] +
         [R["fur_wolf"][i] for i in (2, 4)] + [R["glow_lost"][5]]),
        ("蜂晶の杖：青銅・くるみ・青い核・羽", [R["bronze"][i] for i in (2, 4, 6)] + [R["walnut"][i] for i in (2, 4)] +
         [R["glow_bee"][i] for i in (3, 5)] + [(206, 238, 250)]),
    ]
    sheet_path = os.path.join(repo, "docs", "gear", "rare_f1_sheet.png")
    make_set_sheet(sheet_path, "⑤ 1階のレア装備（強い敵のまれな落とし物）",
                   "レア：光る部分がある（動かない）｜光る色は、落とした敵の体と同じ（石くれ兵の青緑・欠け牙の狼の白・結晶蜂の青）",
                   rows, details=details, palette=palette)
    import shutil
    shutil.copy(sheet_path, os.path.join(out, "rare_f1_sheet.png"))


if __name__ == "__main__":
    main()
