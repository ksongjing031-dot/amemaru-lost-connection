"""2〜5階のアクセサリーと、その素材の絵とモデル（11月24日「階層ごとにアクセサリーも追加しましょう」）。

  python3 tools/artgen/make_floor_accessory_art.py <リポジトリ> [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only <下の ICON_NAMES>

体に表示するもの（首飾り・腕輪・お守り）は GeckoLib のモデル（1階の make_gear_accessories.py と同じ置き方とボーン）。
アンコモンの決まり：光らない・動かない。小さな飾り（玉・輪・房）で、階ごとの色と形を出す。
指輪とレアのお守りは、持ち物の絵だけ（体には出さない）。

階ごとの色と形【補完】：
  2階 忘れられた森    ：葉影の尾の黒緑の紐・古樹の樹皮・光茸（淡い青緑のかさ）・迷い灯（灯籠の中の琥珀の芯）
  3階 古代の水路      ：ナマズのひげの紐・緑青の水路の青銅・歯車・流水の結晶（青い雫）
  4階 崩壊した神殿    ：聖獣の金のたてがみの紐・白い祈りの石・歌声の結晶（淡い藤色の鈴）・手をつなぐ人の壁画
  5階 古代守護者の祭壇：真鍮の鎖と枠・翠の核・監視眼のレンズ・防衛兵の盾の縁
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import mannequin, paint_mannequin  # noqa: E402
from make_gear_accessories import AccessoryPainter, armored, gcube, new_accessory  # noqa: E402
from materials import RAMPS, hexc, hsh, pick, ramp  # noqa: E402

LOCAL = {
    "fur_shade": ramp("#0E140F", "#172119", "#212E23", "#2C3D2E", "#3A4F3B", "#4C644B", "#62795E"),
    "glowcap": ramp("#1D5A52", "#2A7A6E", "#3E9A8A", "#5DBFAC", "#86DCCB", "#B5EEE3", "#E2FBF5"),
    "stem": ramp("#6B6450", "#8A8268", "#A8A084", "#C4BDA0", "#DAD4BA", "#ECE7D2", "#F8F5EA"),
    "bark": ramp("#24170F", "#3A2618", "#523723", "#6C4930", "#875D3E", "#A57750", "#C29467"),
    "root": ramp("#1A110B", "#2A1C12", "#3C281A", "#503624", "#66452F", "#7E573C", "#98704E"),
    "rust": ramp("#3A1A10", "#5A2A16", "#7A3C1E", "#9A5028", "#B86A38", "#D08A52", "#E6AE7A"),
    "wick": ramp("#5A3008", "#7A4A0C", "#A36714", "#C98A1F", "#E8AA35", "#FFD46A", "#FFE7A8"),
    "whisker": ramp("#191A12", "#2A2B1E", "#40402D", "#58583F", "#7A7558", "#9C9676", "#BEB898"),
    "bronze_teal": ramp("#13231F", "#1E3530", "#2A4A42", "#386057", "#4A786C", "#6A9C8C", "#7EB0A0"),
    "flow": ramp("#0E2A4A", "#16477A", "#2E7EC2", "#4F9ED8", "#7CC4F0", "#B6E4FA", "#E6F6FF"),
    "mane": ramp("#5A4214", "#7A5A20", "#9C7830", "#C09A44", "#DCBC64", "#F0D890", "#FBF0C4"),
    "stone_white": ramp("#5E5A50", "#7E786C", "#A09A8C", "#BFB9A8", "#D8D3C4", "#EAE6DA", "#F8F6EE"),
    "song": ramp("#4A4478", "#665E9E", "#8478C0", "#A49ADB", "#C5BCEF", "#E2DCFA", "#F6F3FF"),
    "gold": ramp("#5A3A08", "#7A520E", "#A36F16", "#C98F24", "#E8B23C", "#FFD46A", "#FFF0B0"),
    "brass": ramp("#3A2A0E", "#5A4416", "#7E6222", "#A28232", "#C4A248", "#DEC06A", "#F2DC98"),
    "jade": ramp("#0B3A24", "#11563A", "#1A7A52", "#26A06C", "#48C488", "#86E2AE", "#C8F6DC"),
    "lens": ramp("#1E3A52", "#2C5676", "#3E7698", "#5E9CBC", "#8CC2DA", "#BFE2F0", "#EEF9FF"),
    "leather_dark": RAMPS["leather_dark"],
}


class FloorAccessoryPainter(AccessoryPainter):
    def _std(self, c, name, base=3.2, noise=0.25, blob=0.35, up=1.3, down=1.0, specks=0.0):
        tone = base + (self.tex(c, 11) - 0.5) * noise + (self.blob(c, 2.0, 12 + self.eid(c)) - 0.5) * blob
        if specks:
            r = self.tex(c, 13)
            if r < specks:
                tone -= 0.9
            elif r > 1 - specks * 0.6:
                tone += 0.7
        tone += self.bevel(c, up=up, down=down)
        return pick(LOCAL[name], tone)

    # ---- 2階 ----
    def paint_fur_shade(self, c):
        # 毛の筋：縦に明るい筋と暗い筋
        streak = (0.5, -0.4, 0.1)[(c.i * 2 + c.j) % 3]
        tone = 3.2 + streak + (self.tex(c, 21) - 0.5) * 0.4 + self.bevel(c, up=0.8, down=0.8)
        if c.el.tags.get("tassel") and c.j == c.H - 1 and c.i % 2 == 1:
            return None
        return pick(LOCAL["fur_shade"], tone)

    def paint_glowcap(self, c):
        tone = 3.4 + self.bevel(c, up=1.4, down=1.0) + (self.tex(c, 22) - 0.5) * 0.4
        if c.n[1] > 0.6 and self.tex(c, 23) > 0.72:
            return pick(LOCAL["glowcap"], 6)  # かさの白い斑
        return pick(LOCAL["glowcap"], tone)

    def paint_stem(self, c):
        return self._std(c, "stem", base=4.0, noise=0.2, blob=0.2, up=0.8, down=0.8)

    def paint_bark(self, c):
        tone = 3.2 + (self.tex(c, 24) - 0.5) * 0.5 + self.bevel(c, up=1.1, down=1.0)
        # 縦の溝（樹皮の割れ目）
        if self.facing(c) or abs(c.n[0]) > 0.6:
            col = int(np.floor(c.p[0] * 2 + c.p[2] * 2))
            if hsh(col, 31) < 0.3:
                tone -= 1.2
        if self.blob(c, 1.0, 32 + self.eid(c)) < 0.15:
            return pick(LOCAL["rust"], 3.0 + (self.tex(c, 33) - 0.5))  # 管の錆の染み
        return pick(LOCAL["bark"], tone)

    def paint_root(self, c):
        return self._std(c, "root", base=3.4, noise=0.4, blob=0.3)

    def paint_rust(self, c):
        return self._std(c, "rust", base=3.0, noise=0.5, blob=0.5, specks=0.08)

    def paint_wick(self, c):
        return self._std(c, "wick", base=4.4, noise=0.3, blob=0.2, up=0.8, down=0.6)

    # ---- 3階 ----
    def paint_whisker(self, c):
        return self._std(c, "whisker", base=3.6, noise=0.3, blob=0.2, up=1.0, down=0.8)

    def paint_bronze_teal(self, c):
        tone = 3.6 + (self.tex(c, 41) - 0.5) * 0.4 + self.bevel(c, up=1.4, down=1.0)
        tone += self.rivet(c)
        # 歯車の歯（tags teeth）の先は、すれて地の青銅が出る
        if c.el.tags.get("teeth") or self.blob(c, 1.0, 42 + self.eid(c)) < 0.22:
            return pick(RAMPS["bronze"], tone + 0.2)
        return pick(LOCAL["bronze_teal"], tone)

    def paint_flow(self, c):
        tone = 3.8 + self.bevel(c, up=1.6, down=0.8)
        # 中の水の流れ（ななめの明るい筋）
        if (c.i + c.j * 2) % 4 == 0:
            tone += 1.0
        return pick(LOCAL["flow"], tone)

    # ---- 4階 ----
    def paint_mane(self, c):
        k = (c.i + c.j) % 3
        tone = (4.4, 3.6, 3.0)[k] + self.bevel(c, up=0.6, down=0.8)
        if c.el.tags.get("tassel") and c.j == c.H - 1 and c.i % 2 == 1:
            return None
        if self.tex(c, 51) < 0.08:
            return pick(LOCAL["stone_white"], 5)  # 白く色の抜けた毛（ロスト）
        return pick(LOCAL["mane"], tone)

    def paint_stone_white(self, c):
        tone = 4.0 + (self.tex(c, 52) - 0.5) * 0.3 + self.bevel(c, up=1.0, down=1.1)
        tone += self.grooves(c)
        # 壁画（tags mural）：手をつなぐ小さな人の列（2ピクセルごとに人と手）
        if c.el.tags.get("mural") and self.facing(c) or (c.el.tags.get("mural") and abs(c.n[0]) > 0.6):
            row = c.j % 4
            if row in (1, 2) and c.i % 2 == 0:
                tone -= 1.8
            elif row == 2:
                tone -= 1.0
        return pick(LOCAL["stone_white"], tone)

    def paint_song(self, c):
        tone = 4.0 + self.bevel(c, up=1.6, down=0.9)
        if c.n[1] < -0.6:
            tone -= 1.2  # 鈴の内側
        return pick(LOCAL["song"], tone)

    def paint_gold(self, c):
        return self._std(c, "gold", base=3.8, noise=0.2, blob=0.2, up=1.6, down=1.0)

    # ---- 5階 ----
    def paint_brass(self, c):
        tone = 3.6 + (self.tex(c, 61) - 0.5) * 0.3 + self.bevel(c, up=1.6, down=1.0) + self.rivet(c)
        if c.el.tags.get("link") and (c.i + c.j) % 2 == 1:
            tone -= 0.6
        return pick(LOCAL["brass"], tone)

    def paint_jade(self, c):
        tone = 3.8 + self.bevel(c, up=1.6, down=0.8)
        # 中の光の糸の結び目（十字）
        if c.W >= 2 and c.H >= 2 and (c.i == c.W // 2 or c.j == c.H // 2):
            tone += 1.0
        return pick(LOCAL["jade"], tone)

    def paint_lens(self, c):
        tone = 4.0 + self.bevel(c, up=1.8, down=0.6)
        if c.i == 0 and c.j == 0:
            tone += 1.5
        return pick(LOCAL["lens"], tone)

    def paint_leather_dark(self, c):
        return pick(LOCAL["leather_dark"], 3.4 + (self.tex(c, 71) - 0.5) * 0.4 + self.bevel(c, up=0.8, down=0.8))


# ---------------------------------------------------------------- 共通の形

def neck_cord(g, mat, inflate=-0.2):
    """首の後ろを回り、胸の前でV字に下りる紐（1階の草原の首飾りと同じ通り道）。"""
    B = "armorBody"
    gcube(g, B, "cord_back", (-3, 23.4, 1.9), (6, 1, 1), mat, inflate=inflate)
    for side, sx in (("r", 1), ("l", -1)):
        gcube(g, B, f"cord_side_{side}", (sx * 3.25 - 0.5, 23.4, -2.2), (1, 1, 4), mat, inflate=inflate)
        cx, cy = sx * 1.6, 23.0
        gcube(g, B, f"cord_v_{side}", (cx - 1.75, cy - 0.5, -2.45), (4, 1, 1), mat, inflate=inflate,
              rot=(0, 0, sx * 30), pivot=(cx, cy, -2.0))


def wrist_band(g, mat, y0=14.5, inflate=0.15, tags=None):
    """右の手首の少し上を一回りする4つの部品（1階の石くれの腕輪と同じ置き方）。"""
    A = "armorRightArm"
    t = tags or {}
    gcube(g, A, "seg_front", (4.5, y0, -2.75), (3, 2, 1), mat, inflate=inflate, tags=dict(t))
    gcube(g, A, "seg_back", (4.5, y0, 1.75), (3, 2, 1), mat, inflate=inflate, tags=dict(t))
    gcube(g, A, "seg_out", (7.75, y0, -1.5), (1, 2, 3), mat, inflate=inflate, tags=dict(t))
    gcube(g, A, "seg_in", (3.25, y0, -1.5), (1, 2, 3), mat, inflate=inflate, tags=dict(t))
    return A


def hip_cord(g, mat, ring_mat, x=-5.0):
    """左の腰から下がる紐と、小さな輪（1階の道標のお守りと同じ置き方）。"""
    B = "armorBody"
    gcube(g, B, "cord", (x + 0.5, 10.6, -0.5), (1, 2, 1), mat, inflate=-0.25)
    gcube(g, B, "ring", (x + 0.5, 10.0, -0.5), (1, 1, 1), ring_mat, inflate=-0.05)
    return B


# ---------------------------------------------------------------- 2階

def build_glowcap_necklace():
    g = new_accessory("glowcap_necklace")
    B = "armorBody"
    neck_cord(g, "fur_shade")
    # V字の下に、光茸を3つ（まん中が大きい）。かさは淡い青緑、白い斑
    gcube(g, B, "stem_mid", (-0.5, 20.4, -2.8), (1, 1, 1), "stem", inflate=-0.15)
    gcube(g, B, "cap_mid", (-1, 21.1, -3.2), (2, 1, 2), "glowcap", inflate=0.05)
    gcube(g, B, "cap_mid_top", (-0.5, 21.8, -2.7), (1, 1, 1), "glowcap", inflate=-0.1)
    for side, sx in (("r", 1), ("l", -1)):
        gcube(g, B, f"stem_{side}", (sx * 1.7 - 0.5, 21.0, -2.6), (1, 1, 1), "stem", inflate=-0.3)
        gcube(g, B, f"cap_{side}", (sx * 1.7 - 0.5, 21.6, -2.75), (1, 1, 1), "glowcap", inflate=0.05)
    return g


def build_rootknot_bracelet():
    g = new_accessory("rootknot_bracelet")
    A = wrist_band(g, "bark")
    y0 = 14.5
    # 角の結び目（根のこぶ）と、外の錆びた古代の管のかけら、苔
    for k, (x, z) in enumerate(((7.75, -2.5), (7.75, 1.5), (3.25, -2.5), (3.25, 1.5))):
        gcube(g, A, f"knot_{k}", (x, y0 + 0.5, z), (1, 1, 1), "root", inflate=0.18)
    gcube(g, A, "pipe", (8.55, y0 + 0.25, -0.5), (1, 1, 1), "rust", inflate=0.1)
    gcube(g, A, "pipe_ring", (8.75, y0 + 1.15, -0.5), (1, 1, 1), "iron", inflate=-0.2)
    return g


def build_wisp_charm():
    g = new_accessory("wisp_charm")
    B = hip_cord(g, "fur_shade", "iron")
    x = -5.0
    # 小さな灯籠：笠・4本の柱・中の琥珀の芯・台
    gcube(g, B, "cap", (x, 8.6, -1), (2, 1, 2), "iron", inflate=-0.1)
    gcube(g, B, "cap_top", (x + 0.5, 9.3, -0.5), (1, 1, 1), "iron", inflate=-0.2)
    for k, (dx, dz) in enumerate(((0, 0), (1.5, 0), (0, 1.5), (1.5, 1.5))):
        gcube(g, B, f"post_{k}", (x + dx, 5.8, -1 + dz), (1, 3, 1), "iron", inflate=-0.32)
    gcube(g, B, "wick", (x + 0.5, 6.3, -0.5), (1, 2, 1), "wick", inflate=-0.05)
    gcube(g, B, "base", (x, 5.0, -1), (2, 1, 2), "iron", inflate=0.05)
    # 葉影の尾の房
    gcube(g, B, "tassel_knot", (x + 0.5, 4.2, -0.5), (1, 1, 1), "fur_shade", inflate=-0.1)
    gcube(g, B, "tassel", (x + 0.5, 2.2, -0.5), (1, 2, 1), "fur_shade", inflate=0.15, tags={"tassel": True})
    return g


# ---------------------------------------------------------------- 3階

def build_clearwater_necklace():
    g = new_accessory("clearwater_necklace")
    B = "armorBody"
    neck_cord(g, "whisker", inflate=-0.28)
    gcube(g, B, "bail", (-0.5, 21.7, -2.8), (1, 1, 1), "bronze_teal", inflate=-0.15)
    # 流水の結晶の雫（上が細く、下がふくらみ、先がとがる）
    gcube(g, B, "drop_top", (-0.5, 20.9, -2.9), (1, 1, 1), "flow", inflate=-0.12)
    gcube(g, B, "drop_mid", (-1, 19.3, -3.2), (2, 2, 1), "flow", inflate=-0.05)
    gcube(g, B, "drop_tip", (-0.5, 18.6, -3.0), (1, 1, 1), "flow", inflate=-0.2)
    return g


def build_cogwheel_bracelet():
    g = new_accessory("cogwheel_bracelet")
    A = wrist_band(g, "bronze_teal")
    y0 = 14.5
    # 歯車の歯：外向きに小さく出る（前・後ろ・外に2つずつ）
    teeth = [((5.0, y0 + 0.5, -3.35), (1, 1, 1)), ((6.5, y0 + 0.5, -3.35), (1, 1, 1)),
             ((5.0, y0 + 0.5, 2.35), (1, 1, 1)), ((6.5, y0 + 0.5, 2.35), (1, 1, 1)),
             ((8.35, y0 + 0.5, -1.25), (1, 1, 1)), ((8.35, y0 + 0.5, 0.25), (1, 1, 1))]
    for k, (o, s) in enumerate(teeth):
        gcube(g, A, f"tooth_{k}", o, s, "bronze_teal", inflate=-0.28, tags={"teeth": True})
    gcube(g, A, "hub", (8.6, y0 + 0.5, -0.5), (1, 1, 1), "bronze_teal", inflate=-0.1, tags={"teeth": True})
    return g


def build_whisker_charm():
    g = new_accessory("whisker_charm")
    B = hip_cord(g, "whisker", "bronze_teal")
    x = -5.0
    # 青銅の小さな水門の札（表に小さなハンドル）
    gcube(g, B, "plate", (x, 6.2, -0.5), (2, 3, 1), "bronze_teal", inflate=0.05,
          tags={"rivets": [(x + 0.3, 8.8), (x + 1.7, 8.8)]})
    gcube(g, B, "wheel", (x + 0.5, 7.0, -0.9), (1, 1, 1), "bronze_teal", inflate=-0.1, tags={"teeth": True})
    # ナマズのひげ：札の下から、ゆるく曲がって2本
    for side, sx in (("r", 1), ("l", -1)):
        gcube(g, B, f"whisker_{side}", (x + 0.5 + sx * 0.4, 3.6, -0.5), (1, 3, 1), "whisker", inflate=-0.35,
              rot=(0, 0, sx * 18), pivot=(x + 1.0, 6.2, 0.0))
    return g


# ---------------------------------------------------------------- 4階

def build_chime_necklace():
    g = new_accessory("chime_necklace")
    B = "armorBody"
    neck_cord(g, "mane")
    for side, sx in (("r", 1), ("l", -1)):
        gcube(g, B, f"bead_{side}", (sx * 2.1 - 0.5, 23.05, -2.75), (1, 1, 1), "stone_white", inflate=-0.12)
    # 歌声の結晶の鈴（つまみ・肩・すそ・舌）
    gcube(g, B, "bell_knob", (-0.5, 21.6, -2.8), (1, 1, 1), "song", inflate=-0.22)
    gcube(g, B, "bell_shoulder", (-1, 20.6, -3.3), (2, 1, 2), "song", inflate=-0.12)
    gcube(g, B, "bell_skirt", (-1, 19.6, -3.3), (2, 1, 2), "song", inflate=0.22)
    gcube(g, B, "bell_clapper", (-0.5, 18.9, -2.8), (1, 1, 1), "gold", inflate=-0.3)
    return g


def build_mural_bracelet():
    g = new_accessory("mural_bracelet")
    A = wrist_band(g, "stone_white", tags={"mural": True})
    y0 = 14.5
    # 角を、聖獣の金のたてがみで巻いて留める
    for k, (x, z) in enumerate(((7.75, -2.5), (7.75, 1.5), (3.25, -2.5), (3.25, 1.5))):
        gcube(g, A, f"bind_{k}", (x, y0 + 0.5, z), (1, 1, 1), "mane", inflate=0.12)
    return g


def build_mane_charm():
    g = new_accessory("mane_charm")
    B = hip_cord(g, "mane", "gold")
    x = -5.5
    # 白い石の輪（四角い輪。まん中が空く）に、金のたてがみを巻く
    gcube(g, B, "ring_top", (x, 8.4, -0.5), (3, 1, 1), "stone_white", inflate=0.05)
    gcube(g, B, "ring_bottom", (x, 6.0, -0.5), (3, 1, 1), "stone_white", inflate=0.05)
    gcube(g, B, "ring_left", (x, 7.0, -0.5), (1, 1, 1), "stone_white", inflate=0.05)
    gcube(g, B, "ring_right", (x + 2, 7.0, -0.5), (1, 1, 1), "stone_white", inflate=0.05)
    gcube(g, B, "wrap", (x + 1, 8.4, -0.5), (1, 1, 1), "mane", inflate=0.2)
    gcube(g, B, "tassel_knot", (x + 1, 5.2, -0.5), (1, 1, 1), "gold", inflate=-0.15)
    gcube(g, B, "tassel", (x + 1, 3.2, -0.5), (1, 2, 1), "mane", inflate=0.15, tags={"tassel": True})
    return g


# ---------------------------------------------------------------- 5階

def build_jade_necklace():
    g = new_accessory("jade_necklace")
    B = "armorBody"
    neck_cord(g, "brass", inflate=-0.3)
    # 真鍮の枠に包んだ翠の核（ひし形に見えるよう、枠を上下に少し出す）
    gcube(g, B, "bail", (-0.5, 21.7, -2.8), (1, 1, 1), "brass", inflate=-0.2, tags={"link": True})
    gcube(g, B, "frame", (-1, 19.0, -3.0), (2, 3, 1), "brass", inflate=0.0)
    gcube(g, B, "frame_tip", (-0.5, 18.4, -3.0), (1, 1, 1), "brass", inflate=-0.1)
    gcube(g, B, "core", (-0.5, 19.5, -3.35), (1, 2, 1), "jade", inflate=0.05)
    return g


def build_sentinel_bracelet():
    g = new_accessory("sentinel_bracelet")
    A = wrist_band(g, "brass", inflate=0.1)
    y0 = 14.5
    # 外に、防衛兵の盾の縁を写した板（リベットと、まん中に翠の粒）
    gcube(g, A, "shield", (8.45, y0 - 0.5, -1.5), (1, 3, 3), "brass", inflate=0.0,
          tags={"rivets": [(8.9, y0 + 2.0), (8.9, y0 - 0.2)]})
    gcube(g, A, "jade", (9.15, y0 + 0.5, -0.5), (1, 1, 1), "jade", inflate=-0.15)
    return g


def build_lens_charm():
    g = new_accessory("lens_charm")
    B = hip_cord(g, "leather_dark", "brass")
    x = -5.5
    # 真鍮の目玉（十字に組んで丸く見せる）と、まん中のレンズ
    gcube(g, B, "disc_h", (x, 7.0, -0.5), (3, 1, 1), "brass", inflate=0.0)
    gcube(g, B, "disc_v", (x + 1, 6.0, -0.5), (1, 3, 1), "brass", inflate=0.0)
    gcube(g, B, "disc_c", (x + 0.5, 6.5, -0.4), (2, 2, 1), "brass", inflate=-0.1)
    gcube(g, B, "lens", (x + 1, 7.0, -0.95), (1, 1, 1), "lens", inflate=0.1)
    gcube(g, B, "tassel", (x + 1, 4.2, -0.5), (1, 2, 1), "leather_dark", inflate=-0.1)
    return g


BUILDERS = [build_glowcap_necklace, build_rootknot_bracelet, build_wisp_charm,
            build_clearwater_necklace, build_cogwheel_bracelet, build_whisker_charm,
            build_chime_necklace, build_mural_bracelet, build_mane_charm,
            build_jade_necklace, build_sentinel_bracelet, build_lens_charm]


# ---------------------------------------------------------------- 持ち物の絵（16×16。enhance_icons.py で 32×32 にする）

def ring_rows(top):
    """指輪：上から少し見下ろした輪と、上の飾り（top の3行）。"""
    rows = [
        "................",
        "................",
        top[0],
        top[1],
        top[2],
        "....bBSSSSBb....",
        "...bB......Bb...",
        "..bB........Bd..",
        "..bB........Bd..",
        "..bB........Bd..",
        "...bB......Bd...",
        "....bBBBBBBd....",
        ".....dddddd.....",
        "................",
        "................",
        "................",
    ]
    return rows


RING_GEM = ["......SGGS......", ".....SGgGgS.....", ".....SGGGgS....."]
RING_KNUCKLE = ["......SkkS......", ".....SkSkSkS....", ".....SSSSSS....."]
RING_FLAT = ["................", ".....SGGGGS.....", ".....SgGGgS....."]

ICONS = {}


def ring(name, top, pal):
    ICONS[name] = (ring_rows(top), pal)


# 2階：樹皮の輪（苔鉄の台）
ring("bark_battle_ring", RING_GEM, {"B": "#A57750", "b": "#6C4930", "d": "#3A2618", "S": "#59615D", "G": "#86DCCB", "g": "#3E9A8A"})
ring("bark_wisdom_ring", RING_GEM, {"B": "#A57750", "b": "#6C4930", "d": "#3A2618", "S": "#59615D", "G": "#FFD46A", "g": "#C98A1F"})
# 3階：緑青の青銅の輪（闘環は歯車の刻み、智環は流水の結晶）
ring("bronze_battle_ring", RING_KNUCKLE, {"B": "#7EB0A0", "b": "#4A786C", "d": "#2A4A42", "S": "#AD8247", "k": "#E2C385"})
ring("bronze_wisdom_ring", RING_GEM, {"B": "#7EB0A0", "b": "#4A786C", "d": "#2A4A42", "S": "#AD8247", "G": "#7CC4F0", "g": "#2E7EC2"})
# 4階：白い石の輪（金の台に祈り石／歌声の結晶）
ring("prayer_battle_ring", RING_GEM, {"B": "#EAE6DA", "b": "#BFB9A8", "d": "#7E786C", "S": "#E8B23C", "G": "#FFF6E0", "g": "#D8C29A"})
ring("prayer_wisdom_ring", RING_GEM, {"B": "#EAE6DA", "b": "#BFB9A8", "d": "#7E786C", "S": "#E8B23C", "G": "#E2DCFA", "g": "#8478C0"})
# 5階：真鍮の輪（翠の核／監視眼のレンズ）
ring("brass_battle_ring", RING_FLAT, {"B": "#DEC06A", "b": "#A28232", "d": "#5A4416", "S": "#7E6222", "G": "#48C488", "g": "#1A7A52"})
ring("brass_wisdom_ring", RING_GEM, {"B": "#DEC06A", "b": "#A28232", "d": "#5A4416", "S": "#7E6222", "G": "#BFE2F0", "g": "#3E7698"})

ICONS["glowcap_necklace"] = ([
    "................",
    "....fFfFfFfF....",
    "...F........f...",
    "..f..........F..",
    "..F..........f..",
    "..f..........F..",
    "...F........f...",
    "....fF....Ff....",
    "......FffF......",
    "....CC.CC.CC....",
    "...CcCCcCCcC....",
    "....sS.sS.sS....",
    ".....s..s..s....",
    "................",
    "................",
    "................",
], {"F": "#4C644B", "f": "#212E23", "C": "#86DCCB", "c": "#E2FBF5", "S": "#DAD4BA", "s": "#A8A084"})

ICONS["rootknot_bracelet"] = ([
    "................",
    "................",
    "................",
    ".....rbbbbbr....",
    "...rb.......br..",
    "..kB.........Bk.",
    "..bB.........Bb.",
    "..bB.........Bp.",
    "..kb.........bP.",
    "...rBBBBBBBBBr..",
    "...bbdbbbdbbb...",
    "...rdddrdddddr..",
    "....rrrrrrrrr...",
    "................",
    "................",
    "................",
], {"B": "#A57750", "b": "#6C4930", "d": "#3A2618", "r": "#2A1C12", "k": "#59615D", "p": "#B86A38", "P": "#7A3C1E"})

ICONS["wisp_charm"] = ([
    "................",
    "......fFfF......",
    ".....F....f.....",
    ".....f....F.....",
    "......FffF......",
    ".......kk.......",
    "......kKKk......",
    ".....kKKKKk.....",
    ".....k.ww.k.....",
    ".....k.WW.k.....",
    ".....k.ww.k.....",
    ".....kKKKKk.....",
    ".......FF.......",
    "......FffF......",
    "......f..f......",
    "................",
], {"F": "#4C644B", "f": "#212E23", "k": "#434A47", "K": "#757E79", "W": "#FFE7A8", "w": "#E8AA35"})

ICONS["shade_tail_charm"] = ([
    "................",
    ".......kk.......",
    "......k..k......",
    ".......kk.......",
    "......FFFF......",
    ".....FfFFfF.....",
    ".....FFffFF.....",
    "....FfFFFFfF....",
    "....FFfFFfFF....",
    "....fFFffFFf....",
    ".....fFFFFf.....",
    ".....FfFFfF.....",
    "......fFFf......",
    "......F..F......",
    ".......ff.......",
    "................",
], {"F": "#3A4F3B", "f": "#172119", "k": "#AD8247"})

ICONS["clearwater_necklace"] = ([
    "................",
    "....wWwWwWwW....",
    "...W........w...",
    "..w..........W..",
    "..W..........w..",
    "..w..........W..",
    "...W........w...",
    "....wW....Ww....",
    "......WwwW......",
    ".......nN.......",
    ".......LB.......",
    "......LBBB......",
    "......BwBB......",
    ".......BB.......",
    "........B.......",
    "................",
], {"W": "#9C9676", "w": "#58583F", "n": "#4A786C", "N": "#7EB0A0", "L": "#B6E4FA", "B": "#4F9ED8"})

ICONS["cogwheel_bracelet"] = ([
    "................",
    "................",
    "......t.t.......",
    ".....mMMMMm.....",
    "...tm.......mt..",
    "..mM.........Mm.",
    "..tM.........Mt.",
    "..mM.........Mm.",
    "..tm.........mt.",
    "...mMMMMMMMMMm..",
    "...tmmTmmTmmmt..",
    "....mmmmmmmmm...",
    "................",
    "................",
    "................",
    "................",
], {"M": "#7EB0A0", "m": "#386057", "t": "#C9A15F", "T": "#E2C385"})

ICONS["whisker_charm"] = ([
    "................",
    "......wWwW......",
    ".....W....w.....",
    ".....w....W.....",
    "......WwwW......",
    ".......nn.......",
    "......MMMM......",
    "......MtTM......",
    "......MTtM......",
    "......MMMM......",
    "......mmmm......",
    ".....w....w.....",
    "....w......w....",
    "....W......W....",
    ".....W....W.....",
    "................",
], {"W": "#9C9676", "w": "#58583F", "n": "#AD8247", "M": "#4A786C", "m": "#2A4A42", "t": "#C9A15F", "T": "#E2C385"})

ICONS["water_mirror_charm"] = ([
    "................",
    ".......nn.......",
    "......n..n......",
    ".......nn.......",
    ".....MMMMMM.....",
    "....MLLLLLLM....",
    "....MLWLLBLM....",
    "....MLLWLBLM....",
    "....MLBLLLLM....",
    "....MLLBLWLM....",
    "....MLLLLLLM....",
    ".....MMMMMM.....",
    "......m..m......",
    "................",
    "................",
    "................",
], {"n": "#AD8247", "M": "#4A786C", "m": "#2A4A42", "L": "#7CC4F0", "B": "#2E7EC2", "W": "#E6F6FF"})

ICONS["chime_necklace"] = ([
    "................",
    "....mMmMmMmM....",
    "...M........m...",
    "..m..........M..",
    "..M..........m..",
    "..m..........M..",
    "...M.s....s.m...",
    "....mM....Mm....",
    "......MmmM......",
    ".......vv.......",
    "......vVVv......",
    ".....vVPVVv.....",
    ".....vVVVVv.....",
    "....vvvvvvvv....",
    ".......gg.......",
    "................",
], {"M": "#DCBC64", "m": "#9C7830", "s": "#EAE6DA", "v": "#8478C0", "V": "#C5BCEF", "P": "#F6F3FF", "g": "#E8B23C"})

ICONS["mural_bracelet"] = ([
    "................",
    "................",
    "................",
    ".....SSSSSSS....",
    "...gS.......Sg..",
    "..sW.........Ws.",
    "..sW.........Ws.",
    "..sW.........Ws.",
    "..gs.........sg.",
    "...SWWWWWWWWWS..",
    "...sdsdsdsdsds..",
    "...s.d.d.d.d.s..",
    "....sssssssss...",
    "................",
    "................",
    "................",
], {"S": "#D8D3C4", "s": "#A09A8C", "W": "#F8F6EE", "d": "#5E5A50", "g": "#DCBC64"})

ICONS["mane_charm"] = ([
    "................",
    "......mMmM......",
    ".....M....m.....",
    ".....m....M.....",
    "......MmmM......",
    ".......gg.......",
    ".....SSMMSS.....",
    ".....S.mm.S.....",
    ".....S....s.....",
    ".....S....s.....",
    ".....SssssS.....",
    ".......gg.......",
    "......MmmM......",
    "......mMMm......",
    "......M..M......",
    "................",
], {"M": "#DCBC64", "m": "#9C7830", "g": "#E8B23C", "S": "#EAE6DA", "s": "#A09A8C"})

ICONS["broken_seal_charm"] = ([
    "................",
    ".......mm.......",
    "......m..m......",
    ".......mm.......",
    "......SSSS......",
    ".....SWWWSS.....",
    "....SWsWWsWS....",
    "....SWWssWW.....",
    "....SWsWWsW.....",
    "....SWWWWW......",
    ".....SWWW.......",
    "......SS........",
    "................",
    "................",
    "................",
    "................",
], {"m": "#DCBC64", "S": "#A09A8C", "W": "#EAE6DA", "s": "#7E786C"})

ICONS["jade_necklace"] = ([
    "................",
    "....bBbBbBbB....",
    "...B........b...",
    "..b..........B..",
    "..B..........b..",
    "..b..........B..",
    "...B........b...",
    "....bB....Bb....",
    "......BbbB......",
    ".......bb.......",
    "......BJJB......",
    ".....BJjJJB.....",
    ".....BJJjJB.....",
    "......BJJB......",
    ".......BB.......",
    "................",
], {"B": "#DEC06A", "b": "#7E6222", "J": "#48C488", "j": "#C8F6DC"})

ICONS["sentinel_bracelet"] = ([
    "................",
    "................",
    "................",
    ".....bBBBBb.....",
    "...bB......BBB..",
    "..bB......BBjBB.",
    "..bB......BjJjB.",
    "..bB......BBjBB.",
    "..bb.......BBB..",
    "...bBBBBBBBBb...",
    "...bbrbbbrbbb...",
    "....ddddddddd...",
    "................",
    "................",
    "................",
    "................",
], {"B": "#DEC06A", "b": "#A28232", "d": "#5A4416", "r": "#F2DC98", "J": "#C8F6DC", "j": "#48C488"})

ICONS["lens_charm"] = ([
    "................",
    "......lLlL......",
    ".....L....l.....",
    ".....l....L.....",
    "......LllL......",
    ".......bb.......",
    "......BBBB......",
    ".....BbbbbB.....",
    "....BbEEEEbB....",
    "....BbEwEEbB....",
    "....BbEEEEbB....",
    ".....BbbbbB.....",
    "......BBBB......",
    ".......ll.......",
    ".......lL.......",
    "................",
], {"l": "#3A261A", "L": "#654430", "B": "#DEC06A", "b": "#7E6222", "E": "#8CC2DA", "w": "#EEF9FF"})

ICONS["altar_knot_charm"] = ([
    "................",
    ".......bb.......",
    "......b..b......",
    ".......bb.......",
    "......BBB.......",
    ".....BJJBB......",
    "....BJjJJB......",
    "....BJJjJBB.....",
    ".....BJJJJB.....",
    "......BBJJB.....",
    ".......BBB......",
    "................",
    "................",
    "................",
    "................",
    "................",
], {"b": "#7E6222", "B": "#DEC06A", "J": "#48C488", "j": "#C8F6DC"})

# 素材
ICONS["old_bark"] = ([
    "................",
    "................",
    "...oooooooo.....",
    "..oBbBBbBBbo....",
    "..obBrBBbBBbo...",
    "..oBbBbBBrbBo...",
    "..obBBbBbBBbo...",
    "..oBbrBBbBbBo...",
    "...obBBbBrBBbo..",
    "...oBbBBbBbBBo..",
    "...obBbBBbBbo...",
    "....oBbBrBBbo...",
    ".....ooooooo....",
    "................",
    "................",
    "................",
], {"o": "#1A110B", "B": "#875D3E", "b": "#523723", "r": "#B86A38"})

ICONS["glowcap"] = ([
    "................",
    "................",
    ".....oooooo.....",
    "...ooCCcCCCoo...",
    "..oCCCCCCcCCCo..",
    "..oCcCCCCCCCCo..",
    "..oCCCCcCCCcCo..",
    "..oDDDDDDDDDDo..",
    "...ooosSSsooo...",
    "......sSSs......",
    "......sSSs......",
    "......sSSs......",
    ".....ossSSo.....",
    "......oooo......",
    "................",
    "................",
], {"o": "#0E2C28", "C": "#86DCCB", "c": "#E2FBF5", "D": "#3E9A8A", "S": "#DAD4BA", "s": "#A8A084"})

ICONS["wisp_wick"] = ([
    "................",
    ".......oo.......",
    "......oWWo......",
    "......oWYo......",
    ".....oWYYWo.....",
    ".....oYYYYo.....",
    "......oYYo......",
    ".......YY.......",
    ".......yy.......",
    ".......yy.......",
    ".......yy.......",
    ".......yy.......",
    "......oyyo......",
    "......oooo......",
    "................",
    "................",
], {"o": "#3A2008", "W": "#FFF8E6", "Y": "#FFD46A", "y": "#C98A1F"})

ICONS["shade_tail"] = ([
    "................",
    "..........oo....",
    ".........oFFo...",
    "........oFfFFo..",
    ".......oFFfFFo..",
    "......oFfFFfFo..",
    ".....oFFFfFFo...",
    "....oFfFFFfo....",
    "...oFFfFFfFo....",
    "...oFfFFFFo.....",
    "..oFFFfFFo......",
    "..oFfFFfo.......",
    "..oLLFFo........",
    "...oLLo.........",
    "....oo..........",
    "................",
], {"o": "#0B100C", "F": "#3A4F3B", "f": "#172119", "L": "#62795E"})

ICONS["statue_shard"] = ([
    "................",
    "................",
    "......oo........",
    ".....oWWoo......",
    "....oWWSWWo.....",
    "...oWSWWWSWo....",
    "...oWWdWWWWWo...",
    "..oSWWWWdWWSo...",
    "..oWWdWWWWWWo...",
    "..oWWWWWSWWo....",
    "...oSWWWWWo.....",
    "....oWWdWo......",
    ".....oooo.......",
    "................",
    "................",
    "................",
], {"o": "#3E3A32", "W": "#D8D3C4", "S": "#F8F6EE", "d": "#7E786C"})

ICONS["song_crystal"] = ([
    "................",
    ".......oo.......",
    "......oPVo......",
    ".....oPVVvo.....",
    ".....oVPVvo.....",
    "....oPVVPVvo....",
    "....oVVPVVvo....",
    "....oVPVVPvo....",
    ".....oVVPvo.....",
    ".....oVPVvo.....",
    "......oVvo......",
    ".......oo.......",
    "....n......n....",
    "...n.n....n.n...",
    "................",
    "................",
], {"o": "#2A2448", "P": "#F6F3FF", "V": "#C5BCEF", "v": "#8478C0", "n": "#A49ADB"})

ICONS["beetle_shell"] = ([
    "................",
    "................",
    ".....oooooo.....",
    "...ooCCcCCCoo...",
    "..oCCCCCcCCCCo..",
    "..oCcCCCCCCCDo..",
    "..oCCCCCCCcCDo..",
    "..oCCcCCCCCCDo..",
    "..oDCCCCcCCDDo..",
    "...oDDCCCCDDo...",
    "....ooDDDDoo....",
    "......oooo......",
    "................",
    "................",
    "................",
    "................",
], {"o": "#1A2430", "C": "#97C3C9", "c": "#E6F4F5", "D": "#558592"})

ICONS["sacred_mane"] = ([
    "................",
    ".....o..o.......",
    "....oMo.oMo.....",
    "....oMmooMmo....",
    "...oMmMMMmMo....",
    "...oMMmMMMmo....",
    "..oMmMMWmMMo....",
    "..oMMmMMMmMo....",
    "..oMmMMmMMMo....",
    "...oMMMmMMo.....",
    "...oMWMMmMo.....",
    "....oMMmMo......",
    ".....oMMo.......",
    "......oo........",
    "................",
    "................",
], {"o": "#3A2A0A", "M": "#DCBC64", "m": "#9C7830", "W": "#F8F6EE"})

ICONS["prayer_stone"] = ([
    "................",
    "................",
    "......oooo......",
    "....ooWWWWoo....",
    "...oWWPWWWWWo...",
    "...oWPWWWWWWo...",
    "..oWWWWWWWWWWo..",
    "..oWWWWWWWWWYo..",
    "..oWWWWWWWWYYo..",
    "...oWWWWWWYYo...",
    "...oYWWWWYYYo...",
    "....ooYYYYoo....",
    "......oooo......",
    "................",
    "................",
    "................",
], {"o": "#5E5446", "W": "#F4EEDC", "P": "#FFFFFF", "Y": "#D8C29A"})

ICONS["watcher_lens"] = ([
    "................",
    "................",
    ".....oooooo.....",
    "...ooBBBBBBoo...",
    "...oBLLLLLLBo...",
    "..oBLWLLLLLLBo..",
    "..oBLLWLLLLLBo..",
    "..oBLLLLLLLLBo..",
    "..oBLLLLLLDLBo..",
    "..oBLLLLLDDLBo..",
    "...oBLLLLLLBo...",
    "...ooBBBBBBoo...",
    ".....oooooo.....",
    "................",
    "................",
    "................",
], {"o": "#1E2A36", "B": "#A28232", "L": "#8CC2DA", "W": "#EEF9FF", "D": "#3E7698"})

ICONS["sentinel_plate"] = ([
    "................",
    "................",
    "...oooooooooo...",
    "...oBBBBBBBBo...",
    "...oBrBBBBrBo...",
    "...oBBbbbbBBo...",
    "...oBBbBBbBBo...",
    "...oBBbbbbBBo...",
    "...oBBBBBBBBo...",
    "....oBrBBrBo....",
    ".....oBBBBo.....",
    "......oBBo......",
    ".......oo.......",
    "................",
    "................",
    "................",
], {"o": "#3A2A0E", "B": "#C4A248", "b": "#7E6222", "r": "#F2DC98"})

ICONS["drone_spring"] = ([
    "................",
    "........oo......",
    ".......oBBo.....",
    "......oBbbBo....",
    ".....oBo..oBo...",
    "......oBBBBo....",
    ".....oBbbbBo....",
    "....oBo..oBo....",
    ".....oBBBBo.....",
    "....oBbbbBo.....",
    "...oBo..oBo.....",
    "....oBBBBo......",
    ".....oBBo.......",
    "......oo........",
    "................",
    "................",
], {"o": "#2A2010", "B": "#C4A248", "b": "#7E6222"})

ICONS["jade_core_shard"] = ([
    "................",
    ".......oo.......",
    "......oJjo......",
    ".....oJjJJo.....",
    ".....oJJjJo.....",
    "....oJjJJjJo....",
    "....oJJWJJJo....",
    "....oJjJWjJo....",
    ".....oJJjJo.....",
    ".....oJjJJo.....",
    "......oJJo......",
    ".......oo.......",
    "................",
    "................",
    "................",
    "................",
], {"o": "#07261A", "J": "#48C488", "j": "#1A7A52", "W": "#C8F6DC"})

ICON_NAMES = list(ICONS.keys())


def paint_icon(name):
    from PIL import Image
    rows, pal = ICONS[name]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), (name, [len(r) for r in rows])
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), (*hexc(pal[ch]), 255))
    # 外側のふち（となりに色のない所）は、いちばん暗い色で縁取る：指輪とお守りは pixelart の outline と同じ見え方にする
    return img


# ---------------------------------------------------------------- 書き出しと確認画像

def build_all():
    out = []
    for build in BUILDERS:
        g = build()
        g.pack()
        frames = g.paint(FloorAccessoryPainter(g), frames=1)
        out.append((g, frames))
    return out


NOTES = {
    "glowcap_necklace": ("光茸の首飾り（2階）", "葉影の尾の黒緑の紐に、光茸のかさを3つ\n（まん中が大きい。淡い青緑に白い斑）"),
    "rootknot_bracelet": ("根結びの腕輪（2階）", "樹皮の4つの部品を、根のこぶで留めた輪\n外に、錆びた古代の管のかけらと苔鉄の輪"),
    "wisp_charm": ("迷い灯のお守り（2階）", "苔鉄の小さな灯籠（笠・4本の柱・台）の中に\n琥珀色の芯。下に葉影の尾の房"),
    "clearwater_necklace": ("澄み水の首飾り（3階）", "ナマズのひげの細い紐と緑青の留め金に\n流水の結晶の雫（中に水の筋）"),
    "cogwheel_bracelet": ("歯車の腕輪（3階）", "緑青の青銅の4つの部品に、外向きの歯\n歯の先はすれて地の青銅が出る"),
    "whisker_charm": ("ナマズひげのお守り（3階）", "青銅の小さな水門の札（表にハンドル）\n下に、ゆるく曲がったナマズのひげ2本"),
    "chime_necklace": ("鈴音の首飾り（4階）", "聖獣の金のたてがみの紐に、白い石の玉と\n歌声の結晶の鈴（すそが広がり、金の舌）"),
    "mural_bracelet": ("壁画の腕輪（4階）", "白い祈りの石の輪に、手をつなぐ人の列を彫り\n角を金のたてがみで巻いて留める"),
    "mane_charm": ("聖獣の毛のお守り（4階）", "白い石の四角い輪に金のたてがみを巻き\n下に金の房（ところどころ白く抜けた毛）"),
    "jade_necklace": ("翠核の首飾り（5階）", "細い真鍮の鎖に、真鍮の枠で包んだ翠の核\n（中に光の糸の結び目）"),
    "sentinel_bracelet": ("防衛兵の腕輪（5階）", "真鍮の輪の外に、防衛兵の盾の縁を写した板\nリベット2つと、まん中に翠の粒"),
    "lens_charm": ("監視眼のお守り（5階）", "暗い革の紐に、真鍮の目玉と\nまん中に監視眼のレンズ"),
}


class _Icon:
    """確認画像の枠に入れる絵（setsheet.py は .image() で受け取る）。"""

    def __init__(self, img):
        self._img = img

    def image(self):
        return self._img


def disk_icon(repo, name):
    """書き出した持ち物の絵（enhance_icons.py のあとなら 32×32）。"""
    from PIL import Image
    path = os.path.join(repo, "src/main/resources/assets/amemaru/textures/item", name + ".png")
    return _Icon(Image.open(path).convert("RGBA"))


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    repo = args[0] if args else "."
    out = args[1] if len(args) > 1 else "/tmp/floor_acc"
    os.makedirs(out, exist_ok=True)
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    accs = build_all()
    if "--sheets" not in sys.argv:
        # 確認画像だけ作り直すとき（enhance_icons.py で絵を 32×32 にしたあと）は --sheets
        for g, frames in accs:
            g.save(repo, "accessory", frames)
        for name in ICON_NAMES:
            paint_icon(name).save(os.path.join(base, "textures/item", name + ".png"))
    sheets(repo, out, accs)
    print("モデル", len(accs), "絵", len(ICON_NAMES))


def sheets(repo, out, accs):
    import make_gear_ancient_armor as AA
    from setsheet import make_set_sheet
    import shutil
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    plate = AA.build_plate()
    ptex = AA.paint(plate)[0]
    chest = AA.PIECE_BONES["chest"] + AA.PIECE_BONES["legs"]
    floors = [("2階", accs[0:3]), ("3階", accs[3:6]), ("4階", accs[6:9]), ("5階", accs[9:12])]
    for label, group in floors:
        rows = []
        for g, frames in group:
            title, note = NOTES[g.name]
            rows.append({"label": title, "rarity": "アンコモン（光らない）", "rarity_color": (120, 220, 120), "note": note,
                         "pairs": [(man, mtex), (g, frames[0])], "icon": disk_icon(repo, g.name),
                         "views": [("正面", 180, 0), ("右の横", -90, 0), ("左の横", 90, 0), ("斜め", -145, 14)]})
        on = [(armored(g, True), f[0]) for g, f in group]
        off = [(g, f[0]) for g, f in group]
        details = [
            {"label": "首飾り", "pairs": [(man, mtex)] + off, "center": (0, 21, -2), "yaw": 180, "pitch": 6, "scale": 30},
            {"label": "重鎧の上に着けたとき", "pairs": [(man, mtex), (AA.subset(plate, chest), ptex)] + on,
             "center": (0, 15, 0), "yaw": -150, "pitch": 10, "scale": 13},
            {"label": "腕輪とお守り（前から）", "pairs": [(man, mtex)] + off, "center": (0, 11.5, -1), "yaw": 165, "pitch": 10,
             "scale": 15},
        ]
        num = label[0]
        path = os.path.join(repo, "docs", "gear", "accessories_f%s_sheet.png" % num)
        make_set_sheet(path, "階ごとのアクセサリー（%s）" % label,
                       "アンコモン：光らない・動かない｜指輪とレアのお守りは持ち物の絵だけ（下の絵の一覧）", rows, details=details)
        shutil.copy(path, os.path.join(out, os.path.basename(path)))


if __name__ == "__main__":
    main()
