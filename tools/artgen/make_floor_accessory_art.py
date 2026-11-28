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
  6階 凍結した村      ：凍て毛皮の紐・霜銀の留め金と灯籠・溶けない氷晶（11月25日）
  7階 氷の森林        ：鏡氷（白銀に、淡い紫の映り込み）・枝分かれした氷角・つらら蝙蝠の夜の色の翼と、下がるつらら・
                        オーロラ色の玉（11月25日）
  8階 沈黙の湖        ：静氷（灰白の氷）・無音の大魚の白い鱗・霧の糸・凍て鶴の白い羽と黒い風切り・青く光る声石・
                        湖の真珠（11月25日）
  9階 氷結した王城    ：近衛の青い氷・前掛けのレース・金の額・霜銀の名札・青い旗の房・銀の綴じ糸（11月28日）
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
    # 6階（11月25日）
    "frost_silver": ramp("#4A5668", "#6A788C", "#8C9AAE", "#B0BCCC", "#CFD8E4", "#E6EDF4", "#F8FBFE"),
    "ice": ramp("#3E6E9A", "#5A8EBA", "#7AAED4", "#9CC8E8", "#BEDDF4", "#DDEEFA", "#FFFFFF"),
    "white_fur": ramp("#6A707A", "#868C96", "#A2A8B2", "#BEC4CC", "#D6DBE1", "#E8ECF0", "#F7F9FB"),
    # 7階（11月25日）
    "mirror": ramp("#4E5A6C", "#6C7A8E", "#8C9AAE", "#AEBBCC", "#CCD7E4", "#E6EDF4", "#FFFFFF"),
    "antler": ramp("#4E7EA6", "#6A98BE", "#8CB4D6", "#AECDE8", "#CFE6F6", "#E8F4FC", "#FFFFFF"),
    "bat": ramp("#0C1018", "#141A26", "#1E2636", "#2A3448", "#38445C", "#4A5874", "#5E6E8E"),
    "aurora": ramp("#3E2E7A", "#5A44A0", "#7A66C4", "#8FD6C8", "#B6EEDF", "#DDF9F1", "#FFFFFF"),
    # 8階（11月25日）
    "silent_ice": ramp("#5A6470", "#76808C", "#929CA8", "#AEB8C2", "#C8D0D8", "#DEE4EA", "#F2F5F8"),
    "lake_scale": ramp("#545C68", "#6E7884", "#8C96A2", "#AAB4BE", "#C8D0D8", "#E2E8EE", "#F8FAFC"),
    "mist": ramp("#4A5462", "#5E6878", "#747E8E", "#8A94A4", "#A2ACBA", "#BCC4D0", "#D6DCE4"),
    "crane_white": ramp("#7C8692", "#98A2AE", "#B4BEC8", "#CED6DE", "#E2E8EE", "#F0F4F8", "#FFFFFF"),
    "crane_black": ramp("#08090C", "#101218", "#181B22", "#22262E", "#2E333C", "#3C424C", "#4C535E"),
    "voice": ramp("#1E3C9A", "#2A52BA", "#3E6CD2", "#5E8CE4", "#88AEF0", "#B6D0F8", "#E6F0FF"),
    # 9階（11月28日）
    "guard_ice": ramp("#3A5A9A", "#4E70AE", "#6888C2", "#86A4D4", "#A6C0E4", "#C8DAF2", "#EAF2FC"),
    "lace": ramp("#8A8E98", "#A6AAB2", "#C0C4CA", "#D6D9DE", "#E6E8EC", "#F2F3F5", "#FCFCFD"),
    "gilt": ramp("#5A3E12", "#7C5A1C", "#A07A2A", "#C49C3C", "#DDBB58", "#F0D684", "#FFF0BC"),
    "royal_blue": ramp("#0C1430", "#121E46", "#1A2A5E", "#243878", "#304890", "#425CA8", "#5A76C0"),
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

    # ---- 6階（11月25日） ----
    def paint_frost_silver(self, c):
        tone = 4.0 + (self.tex(c, 81) - 0.5) * 0.3 + self.bevel(c, up=1.6, down=1.0)
        if c.el.tags.get("link") and (c.i + c.j) % 2 == 1:
            tone -= 0.6
        return pick(LOCAL["frost_silver"], tone)

    def paint_ice(self, c):
        tone = 4.2 + self.bevel(c, up=1.6, down=0.6)
        if c.i == 0 and c.j == 0:
            tone += 1.2
        return pick(LOCAL["ice"], tone)

    def paint_white_fur(self, c):
        return self._std(c, "white_fur", base=4.0, noise=0.6, blob=0.4, up=0.8, down=0.8)

    # ---- 7階（11月25日） ----
    def paint_mirror(self, c):
        """鏡氷：なめらかな白銀に、斜めの明るい映り込みの帯と、淡い紫のひとすじ。tags carved は、刻んだ合言葉の線"""
        tone = 4.3 + self.bevel(c, up=1.4, down=1.0)
        band = (c.i + c.j) % 5
        if band == 0:
            tone += 1.0
        if c.el.tags.get("carved") and abs(c.n[2]) > 0.6 and c.j % 2 == 1 and 0 < c.i < c.W - 1:
            tone -= 1.8
        if band == 3 and self.tex(c, 91) < 0.35:
            return pick(LOCAL["aurora"], 2.2)
        return pick(LOCAL["mirror"], tone)

    def paint_antler(self, c):
        tone = 4.0 + self.bevel(c, up=1.6, down=0.8)
        if c.j == 0 and c.n[1] > 0.6:
            tone += 1.0
        return pick(LOCAL["antler"], tone)

    def paint_bat(self, c):
        tone = 3.4 + (self.tex(c, 92) - 0.5) * 0.5 + self.bevel(c, up=1.0, down=0.8)
        if c.i % 3 == 0:
            tone += 0.8   # 翼の骨の筋
        return pick(LOCAL["bat"], tone)

    def paint_aurora(self, c):
        tone = 3.8 + self.bevel(c, up=1.6, down=0.8)
        return pick(LOCAL["aurora"], tone)

    # ---- 8階（11月25日） ----
    def paint_silent_ice(self, c):
        """静氷：つやのない灰白の氷。中に、細い白い筋（音を吸いこんだ跡）"""
        tone = 4.0 + self.bevel(c, up=1.2, down=1.0) + (self.tex(c, 101) - 0.5) * 0.4
        if (c.i * 2 + c.j) % 6 == 0:
            tone += 0.8
        return pick(LOCAL["silent_ice"], tone)

    def paint_lake_scale(self, c):
        """白い鱗：下のふちが少し暗く、上の面はつやで明るい"""
        tone = 4.4 + self.bevel(c, up=1.4, down=1.2)
        if c.j == c.H - 1:
            tone -= 0.7
        if c.i == 0 and c.j == 0:
            tone += 1.0
        return pick(LOCAL["lake_scale"], tone)

    def paint_mist(self, c):
        return self._std(c, "mist", base=4.2, noise=0.6, blob=0.3, up=0.8, down=0.8)

    def paint_crane_white(self, c):
        tone = 4.6 + self.bevel(c, up=1.0, down=1.0)
        if c.j % 2 == 1:
            tone -= 0.5   # 羽の重なり
        return pick(LOCAL["crane_white"], tone)

    def paint_crane_black(self, c):
        return pick(LOCAL["crane_black"], 3.4 + self.bevel(c, up=1.2, down=0.6))

    # ---- 9階 ----
    def paint_guard_ice(self, c):
        tone = 4.0 + (self.tex(c, 91) - 0.5) * 0.6 + self.bevel(c, up=1.3, down=0.8)
        if (c.i + c.j) % 4 == 0:
            tone += 0.7
        return pick(LOCAL["guard_ice"], tone)

    def paint_lace(self, c):
        if c.el.tags.get("lace") and c.edge_dist().get("bottom", 1) == 0 and c.i % 2 == 1:
            return None
        return self._std(c, "lace", base=4.6, noise=0.2, blob=0.2, up=0.8, down=0.8)

    def paint_gilt(self, c):
        return self._std(c, "gilt", base=4.0, noise=0.3, blob=0.2, up=1.4, down=1.0)

    def paint_royal_blue(self, c):
        return self._std(c, "royal_blue", base=3.6, noise=0.25, blob=0.2, up=1.0, down=1.0)

    def paint_voice(self, c):
        """声石：深い青の小石（アンコモンなので光らない。左上に白い点のつや）"""
        tone = 3.4 + self.bevel(c, up=1.6, down=0.8)
        if c.i == 0 and c.j == 0:
            tone += 1.6
        return pick(LOCAL["voice"], tone)


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


# ---------------------------------------------------------------- 6階（11月25日）

def build_ice_crystal_necklace():
    g = new_accessory("ice_crystal_necklace")
    B = "armorBody"
    neck_cord(g, "white_fur", inflate=-0.15)
    # 霜銀の留め金と、下へ細くなる氷晶の柱
    gcube(g, B, "bail", (-0.5, 21.7, -2.8), (1, 1, 1), "frost_silver", inflate=-0.2, tags={"link": True})
    gcube(g, B, "crystal", (-0.5, 18.6, -3.1), (1, 3, 1), "ice", inflate=0.05)
    gcube(g, B, "crystal_tip", (-0.5, 17.8, -3.1), (1, 1, 1), "ice", inflate=-0.2)
    gcube(g, B, "shard_r", (0.4, 19.4, -3.0), (1, 2, 1), "ice", inflate=-0.25)
    gcube(g, B, "shard_l", (-1.4, 19.4, -3.0), (1, 2, 1), "ice", inflate=-0.25)
    return g


def build_frozen_fur_bracelet():
    g = new_accessory("frozen_fur_bracelet")
    A = wrist_band(g, "white_fur", inflate=0.25)
    y0 = 14.5
    # 外に、霜銀の留め具
    gcube(g, A, "clasp", (8.45, y0 + 0.2, -1.0), (1, 2, 2), "frost_silver", inflate=-0.05)
    return g


def build_snowlight_charm():
    g = new_accessory("snowlight_charm")
    B = hip_cord(g, "leather_dark", "frost_silver")
    x = -5.5
    # 小さな霜銀の灯籠（屋根・柱・淡く光る氷晶）
    gcube(g, B, "roof", (x + 0.25, 8.6, -0.75), (2, 1, 2), "frost_silver", inflate=0.05)
    gcube(g, B, "light", (x + 0.5, 6.6, -0.5), (1, 2, 1), "ice", inflate=0.15)
    gcube(g, B, "post_l", (x + 0.0, 6.4, -0.6), (1, 2, 1), "frost_silver", inflate=-0.3)
    gcube(g, B, "post_r", (x + 1.0, 6.4, -0.6), (1, 2, 1), "frost_silver", inflate=-0.3)
    gcube(g, B, "base", (x + 0.25, 5.8, -0.75), (2, 1, 2), "frost_silver", inflate=-0.05)
    return g


# ---------------------------------------------------------------- 7階（11月25日）

def build_ice_antler_necklace():
    g = new_accessory("ice_antler_necklace")
    B = "armorBody"
    neck_cord(g, "white_fur", inflate=-0.15)
    # 霜銀の留め金に、枝分かれした氷角（まん中の幹と、左右に開いた枝）
    gcube(g, B, "bail", (-0.5, 21.7, -2.8), (1, 1, 1), "frost_silver", inflate=-0.2, tags={"link": True})
    gcube(g, B, "beam", (-0.5, 18.9, -3.1), (1, 3, 1), "antler", inflate=-0.05)
    gcube(g, B, "tine_r", (0.3, 19.6, -3.05), (1, 2, 1), "antler", inflate=-0.22, rot=(0, 0, -35), pivot=(0.3, 19.7, -2.6))
    gcube(g, B, "tine_l", (-1.3, 19.6, -3.05), (1, 2, 1), "antler", inflate=-0.22, rot=(0, 0, 35), pivot=(-0.3, 19.7, -2.6))
    gcube(g, B, "tip", (-0.5, 18.2, -3.1), (1, 1, 1), "antler", inflate=-0.28)
    return g


def build_icicle_bracelet():
    g = new_accessory("icicle_bracelet")
    A = wrist_band(g, "bat", inflate=0.2)
    y0 = 14.5
    # 外に霜銀の留め具と、下へ3本のつらら
    gcube(g, A, "clasp", (8.45, y0 + 0.5, -1.0), (1, 1, 2), "frost_silver", inflate=-0.05)
    for k, z in enumerate((-1.6, -0.5, 0.6)):
        gcube(g, A, "icicle_%d" % k, (8.55, y0 - 1.5, z), (1, 2, 1), "ice", inflate=-0.3)
    return g


def build_passphrase_charm():
    g = new_accessory("passphrase_charm")
    B = hip_cord(g, "leather_dark", "frost_silver")
    x = -5.5
    # 霜銀のかしらと、合言葉を刻んだ鏡氷の札。下にオーロラ色の玉
    gcube(g, B, "cap", (x + 0.25, 8.6, -0.6), (2, 1, 1), "frost_silver", inflate=-0.05)
    gcube(g, B, "plate", (x + 0.25, 6.0, -0.55), (2, 3, 1), "mirror", inflate=-0.05, tags={"carved": True})
    gcube(g, B, "bead", (x + 0.75, 5.2, -0.5), (1, 1, 1), "aurora", inflate=-0.2)
    return g


# ---------------------------------------------------------------- 8階（11月25日）

def build_fish_scale_necklace():
    g = new_accessory("fish_scale_necklace")
    B = "armorBody"
    neck_cord(g, "mist", inflate=-0.15)
    # 静氷の留め玉と、扇のように重ねて下げた白い鱗3枚（まん中が手前で大きい）
    gcube(g, B, "bead", (-0.5, 21.7, -2.8), (1, 1, 1), "silent_ice", inflate=-0.18)
    for side, sx in (("r", 1), ("l", -1)):
        gcube(g, B, f"scale_{side}", (sx * 0.9 - 0.75, 19.6, -2.9), (2, 2, 1), "lake_scale", inflate=-0.3,
              rot=(0, 0, -sx * 22), pivot=(sx * 0.4, 21.4, -2.6))
    gcube(g, B, "scale_mid", (-1, 19.2, -3.15), (2, 2, 1), "lake_scale", inflate=-0.22)
    return g


def build_frost_crane_bracelet():
    g = new_accessory("frost_crane_bracelet")
    A = wrist_band(g, "frost_silver", inflate=0.15)
    y0 = 14.5
    # 外に、凍て鶴の冠羽を編みこむ（白い羽2枚を斜めに。先は黒い風切り）
    for k, (z, tilt) in enumerate(((-1.3, 25), (0.3, -25))):
        gcube(g, A, f"plume_{k}", (8.4, y0 - 0.2, z), (1, 3, 1), "crane_white", inflate=-0.22, rot=(tilt, 0, 0),
              pivot=(8.9, y0 + 1.0, z + 0.5))
        gcube(g, A, f"tip_{k}", (8.4, y0 + 2.3, z + (0.55 if tilt > 0 else -0.55)), (1, 1, 1), "crane_black", inflate=-0.3,
              rot=(tilt, 0, 0), pivot=(8.9, y0 + 1.0, z + 0.5))
    return g


def build_bell_charm():
    g = new_accessory("bell_charm")
    B = hip_cord(g, "mist", "frost_silver")
    x = -5.5
    # 静氷を削った小さな鐘（つまみ・肩・すそが広がる）と、中に青い声石の舌
    gcube(g, B, "knob", (x + 0.75, 9.1, -0.5), (1, 1, 1), "silent_ice", inflate=-0.22)
    gcube(g, B, "shoulder", (x + 0.25, 8.0, -1.0), (2, 1, 2), "silent_ice", inflate=-0.12)
    gcube(g, B, "skirt", (x + 0.25, 7.0, -1.0), (2, 1, 2), "silent_ice", inflate=0.2)
    gcube(g, B, "clapper", (x + 0.75, 6.3, -0.5), (1, 1, 1), "voice", inflate=-0.28)
    return g


# ---------------------------------------------------------------- 9階（11月28日）

def build_portrait_necklace():
    g = new_accessory("portrait_necklace")
    B = "armorBody"
    neck_cord(g, "lace", inflate=-0.15)
    # 金の楕円の額（ふち）と、中の白い空白
    gcube(g, B, "frame", (-1, 19.2, -2.9), (2, 3, 1), "gilt", inflate=-0.1)
    gcube(g, B, "blank", (-0.5, 19.7, -3.15), (1, 2, 1), "lace", inflate=-0.25)
    gcube(g, B, "bow", (-0.5, 22.0, -2.8), (1, 1, 1), "royal_blue", inflate=-0.2)
    return g


def build_horseshoe_bracelet():
    g = new_accessory("horseshoe_bracelet")
    A = wrist_band(g, "frost_silver", inflate=0.15)
    y0 = 14.5
    # 外に、氷の蹄鉄（U の字を横に）
    gcube(g, A, "shoe_top", (8.4, y0 + 1.6, -1.4), (1, 1, 3), "guard_ice", inflate=-0.2)
    gcube(g, A, "shoe_l", (8.4, y0 - 0.2, -1.4), (1, 2, 1), "guard_ice", inflate=-0.2)
    gcube(g, A, "shoe_r", (8.4, y0 - 0.2, 0.6), (1, 2, 1), "guard_ice", inflate=-0.2)
    return g


def build_name_charm():
    g = new_accessory("name_charm")
    B = hip_cord(g, "royal_blue", "frost_silver")
    x = -5.5
    # 小さな銀の名札（刻みの線）と、上の青い結び
    gcube(g, B, "plate", (x + 0.25, 7.2, -0.55), (2, 2, 1), "frost_silver", inflate=-0.05, tags={"carved": True})
    gcube(g, B, "knot", (x + 0.75, 9.2, -0.5), (1, 1, 1), "royal_blue", inflate=-0.2)
    return g


BUILDERS = [build_glowcap_necklace, build_rootknot_bracelet, build_wisp_charm,
            build_clearwater_necklace, build_cogwheel_bracelet, build_whisker_charm,
            build_chime_necklace, build_mural_bracelet, build_mane_charm,
            build_jade_necklace, build_sentinel_bracelet, build_lens_charm,
            build_ice_crystal_necklace, build_frozen_fur_bracelet, build_snowlight_charm,
            build_ice_antler_necklace, build_icicle_bracelet, build_passphrase_charm,
            build_fish_scale_necklace, build_frost_crane_bracelet, build_bell_charm,
            build_portrait_necklace, build_horseshoe_bracelet, build_name_charm]


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

# 6階（11月25日）：霜銀の輪（闘環は牙の刻み、智環は氷晶）・氷晶の首飾り・凍て毛皮の腕輪・雪灯のお守り・群れ長の白牙・霜熊の氷の棘
ring("frost_silver_battle_ring", RING_KNUCKLE, {"B": "#E6EDF4", "b": "#B0BCCC", "d": "#6A788C", "S": "#8C9AAE", "k": "#FFFFFF"})
ring("frost_silver_wisdom_ring", RING_GEM, {"B": "#E6EDF4", "b": "#B0BCCC", "d": "#6A788C", "S": "#8C9AAE", "G": "#BEDDF4", "g": "#5A8EBA"})

ICONS["ice_crystal_necklace"] = ([
    "................",
    "....wWwWwWwW....",
    "...W........w...",
    "..w..........W..",
    "..W..........w..",
    "..w..........W..",
    "...W........w...",
    "....wW....Ww....",
    "......WwwW......",
    ".......SS.......",
    "......ICCI......",
    "......IcCI......",
    "......ICcI......",
    ".......IC.......",
    ".......I........",
    "................",
], {"W": "#E8ECF0", "w": "#A2A8B2", "S": "#B0BCCC", "I": "#5A8EBA", "C": "#BEDDF4", "c": "#FFFFFF"})

ICONS["frozen_fur_bracelet"] = ([
    "................",
    "................",
    "................",
    ".....wWWWWw.....",
    "...wW......WWW..",
    "..wW......WSsSW.",
    "..wW......WsSsW.",
    "..wW......WSsSW.",
    "..ww.......WWW..",
    "...wWWWWWWWWw...",
    "...wwfwwwfwww...",
    "....ddddddddd...",
    "................",
    "................",
    "................",
    "................",
], {"W": "#E8ECF0", "w": "#A2A8B2", "d": "#6A707A", "f": "#F7F9FB", "S": "#B0BCCC", "s": "#E6EDF4"})

ICONS["snowlight_charm"] = ([
    "................",
    "......lLlL......",
    ".....L....l.....",
    ".....l....L.....",
    "......LllL......",
    ".......ll.......",
    ".....SSSSSS.....",
    "....SsssssS.....",
    ".....S.CC.S.....",
    ".....S.cC.S.....",
    ".....S.CC.S.....",
    "....SSSSSSSS....",
    ".......ll.......",
    ".......lL.......",
    "................",
    "................",
], {"l": "#3A261A", "L": "#654430", "S": "#8C9AAE", "s": "#E6EDF4", "C": "#BEDDF4", "c": "#FFFFFF"})

ICONS["white_fang_charm"] = ([
    "................",
    ".......ll.......",
    "......l..l......",
    ".......ll.......",
    "......LLLL......",
    "......FFFF......",
    "......FfFF......",
    ".......FFF......",
    ".......FfF......",
    "........FF......",
    "........Fd......",
    ".........d......",
    "................",
    "................",
    "................",
    "................",
], {"l": "#3A261A", "L": "#654430", "F": "#F2F0EA", "f": "#FFFFFF", "d": "#B8B4AA"})

ICONS["bear_frost_charm"] = ([
    "................",
    ".......ll.......",
    "......l..l......",
    ".......ll.......",
    "......SSSS......",
    ".......II.......",
    "......ICcI......",
    "......ICCI......",
    "......ICcI......",
    ".......ICI......",
    ".......IC.......",
    ".......I........",
    "................",
    "................",
    "................",
    "................",
], {"l": "#3A261A", "S": "#8C9AAE", "I": "#5A8EBA", "C": "#BEDDF4", "c": "#FFFFFF"})

# 6階の素材（11月25日）：霜銀・氷晶・凍て毛皮・霜狼の牙・凍て鳥の羽根
ICONS["frost_silver"] = ([
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...oWWWWWWWSo...",
    "..oWwWWWWWSSSo..",
    "..oSSSSSSSSSSo..",
    "..oSsSSSSSSdSo..",
    "..oSSSSSSSdddo..",
    "...ooooooooooo..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
], {"o": "#2A3240", "W": "#F8FBFE", "w": "#FFFFFF", "S": "#B0BCCC", "s": "#E6EDF4", "d": "#6A788C"})
ICONS["ice_crystal"] = ([
    "................",
    "........o.......",
    ".......oCo......",
    "......oCcCo.....",
    "...o..oCCCo.....",
    "..oCo.oCcCo..o..",
    "..oCCooCCCo.oCo.",
    "..oCcCoCCCooCCo.",
    "...oCCoCCCoCCo..",
    "...oCCCCCCCCo...",
    "....oCCCCCCo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
    "................",
], {"o": "#3E6E9A", "C": "#9CC8E8", "c": "#FFFFFF"})
ICONS["frozen_fur"] = ([
    "................",
    "................",
    "....o.oooo.o....",
    "...oWoWWWWoWo...",
    "..oWWWWWWWWWWo..",
    "..oWwWWWWWWwWo..",
    "...oWWWWWWWWo...",
    "...oWWWwWWWWo...",
    "..oWWWWWWWWWWo..",
    "..oWWwWWWWwWWo..",
    "...oWoWWWWoWo...",
    "....o.oooo.o....",
    "................",
    "................",
    "................",
    "................",
], {"o": "#6A707A", "W": "#D6DBE1", "w": "#F7F9FB"})
ICONS["frost_fang"] = ([
    "................",
    "................",
    "......oooo......",
    ".....oFFFFo.....",
    ".....oFfFFo.....",
    "......oFFFo.....",
    "......oFfFo.....",
    ".......oFFo.....",
    ".......oFFo.....",
    "........oFo.....",
    "........oo......",
    "................",
    "................",
    "................",
    "................",
    "................",
], {"o": "#8A8C94", "F": "#F2F4F8", "f": "#BEDDF4"})
ICONS["ice_feather"] = ([
    "................",
    "...........oo...",
    "..........oCCo..",
    ".........oCcCo..",
    "........oCCCo...",
    ".......oCcCo....",
    "......oCCCo.....",
    ".....oCcCo......",
    "....oCCCo.......",
    "...oCCCo........",
    "...oCCo.........",
    "...so...........",
    "..s.............",
    "................",
    "................",
    "................",
], {"o": "#3E6E9A", "C": "#BEDDF4", "c": "#F2F8FE", "s": "#8C9AAE"})

# 第2章からの、生産職どうしの部品（11月25日。0-A 19章の三すくみ【提案】。tools/datagen/build_gear_frost_silver.py）
# 補強金具（武器職人が作る）：霜銀の角当てと留め鋲
ICONS["reinforcement_fitting"] = ([
    "................",
    "................",
    "..oooo..........",
    "..oSSo..........",
    "..oSro..........",
    "..oSSoooooooo...",
    "..oSSSSSSSSSSo..",
    "..oSsSSrSSSrSo..",
    "..oSSSSSSSSSdo..",
    "...ooooooooooo..",
    "................",
    "......oooo......",
    "......oSro......",
    "......oSdo......",
    ".......oo.......",
    "................",
], {"o": "#2A3240", "S": "#B0BCCC", "s": "#F8FBFE", "r": "#6A788C", "d": "#8C9AAE"})
# 台座布（防具職人が織る）：たたんだ厚い布と房
ICONS["pedestal_cloth"] = ([
    "................",
    "................",
    "................",
    "...oooooooooo...",
    "..oCCCCCCCCCCo..",
    "..oCcCCCCCCcCo..",
    "..oCCbbbbbbCCo..",
    "..oCCCCCCCCCCo..",
    "..oDDDDDDDDDDo..",
    "...oooooooooo...",
    "...f.f.f.f.f....",
    "...f.f.f.f.f....",
    "................",
    "................",
    "................",
    "................",
], {"o": "#1C2238", "C": "#3A4C86", "c": "#6A80C4", "b": "#C9A24A", "D": "#26305A", "f": "#C9A24A"})
# 結晶細工（装飾師が作る）：銀線で編んだ枠の中の氷晶
ICONS["crystal_filigree"] = ([
    "................",
    ".......ss.......",
    "......s..s......",
    ".....s.II.s.....",
    "....s.ICcI.s....",
    "...s.ICCcCI.s...",
    "...s.ICCCCI.s...",
    "...s.ICCCCI.s...",
    "....s.ICCI.s....",
    ".....s.II.s.....",
    "......s..s......",
    ".......ss.......",
    "................",
    "................",
    "................",
    "................",
], {"s": "#CFD8E4", "I": "#3E6E9A", "C": "#9CC8E8", "c": "#FFFFFF"})

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

# 7階（11月25日）：鏡氷の輪（闘環は紫の映り込みの刻み、智環はプリズムの石）・氷角の首飾り・つららの腕輪・合言葉の札・
# 白角の欠片・吹雪の灯と、7階の素材（鏡氷・氷角・つらら蝙蝠の翼・吹雪の芯・プリズムの欠片）、道具の結び紐
ring("mirror_ice_battle_ring", RING_KNUCKLE, {"B": "#E6EDF4", "b": "#AEBBCC", "d": "#6C7A8E", "S": "#8C9AAE", "k": "#C6BAF0"})
ring("mirror_ice_wisdom_ring", RING_GEM, {"B": "#E6EDF4", "b": "#AEBBCC", "d": "#6C7A8E", "S": "#8C9AAE", "G": "#E2DCFA",
                                          "g": "#8E74D2"})

ICONS["ice_antler_necklace"] = ([
    "................",
    "....wWwWwWwW....",
    "...W........w...",
    "..w..........W..",
    "..W..........w..",
    "..w..........W..",
    "...W........w...",
    "....wW....Ww....",
    "......WwwW......",
    ".......SS.......",
    "....A..AA..A....",
    ".....A.AA.A.....",
    "......AAAA......",
    ".......AA.......",
    ".......Aa.......",
    "................",
], {"W": "#E8ECF0", "w": "#A2A8B2", "S": "#B0BCCC", "A": "#CFE6F6", "a": "#7AAED4"})

ICONS["icicle_bracelet"] = ([
    "................",
    "................",
    "................",
    ".....bBBBBb.....",
    "...bB......BSS..",
    "..bB......BSsSB.",
    "..bB......BsSsB.",
    "..bB.......SSS..",
    "..bb........B...",
    "...bBBBBBBBBb...",
    "...bbIbbIbbIb...",
    "....dIddIddId...",
    ".....I..I..I....",
    ".....i..i..i....",
    "................",
    "................",
], {"b": "#2A3448", "B": "#4A5874", "d": "#141A26", "S": "#B0BCCC", "s": "#E6EDF4", "I": "#9CC8E8", "i": "#E2F2FC"})

ICONS["passphrase_charm"] = ([
    "................",
    "......lLlL......",
    ".....L....l.....",
    ".....l....L.....",
    "......LllL......",
    ".......ll.......",
    ".....SSSSSS.....",
    ".....MmMMmM.....",
    ".....MkkkkM.....",
    ".....MmMMmM.....",
    ".....MkkkkM.....",
    ".....MMMMMM.....",
    "......SSSS......",
    ".......PP.......",
    ".......Pp.......",
    "................",
], {"l": "#3A261A", "L": "#654430", "S": "#8C9AAE", "M": "#CCD7E4", "m": "#FFFFFF", "k": "#6C7A8E", "P": "#A996E4",
    "p": "#E2DCFA"})

ICONS["white_horn_charm"] = ([
    "................",
    ".......ll.......",
    "......l..l......",
    ".......ll.......",
    "......LLLL......",
    "......HHHH......",
    ".....H.HH.H.....",
    "....H..HH..H....",
    ".......HH.......",
    ".......HhH......",
    "........HH......",
    "........Hd......",
    ".........d......",
    "................",
    "................",
    "................",
], {"l": "#3A261A", "L": "#654430", "H": "#F4F6FA", "h": "#FFFFFF", "d": "#B8C4D0"})

ICONS["blizzard_lamp_charm"] = ([
    "................",
    ".......ll.......",
    "......l..l......",
    ".......ll.......",
    "......SSSS......",
    ".....SsssSS.....",
    ".....S.AA.S.....",
    ".....S.aA.S.....",
    ".....S.AP.S.....",
    ".....S.PP.S.....",
    "....SSSSSSSS....",
    ".....W.WW.W.....",
    "....W..WW..W....",
    "................",
    "................",
    "................",
], {"l": "#3A261A", "S": "#8C9AAE", "s": "#E6EDF4", "A": "#7DE3C4", "a": "#C8F7E8", "P": "#A996E4", "W": "#EAE8F2"})

ICONS["mirror_ice"] = ([
    "................",
    "................",
    "......oooo......",
    ".....oMMMMo.....",
    "....oMmmMMMo....",
    "...oMmMMMMMMo...",
    "...oMMMMMkMMo...",
    "...oMMMMkkMMo...",
    "...oMMMkkMMMo...",
    "....oMMMMMMo....",
    ".....oMMMMo.....",
    "......oooo......",
    "................",
    "................",
    "................",
    "................",
], {"o": "#4E5A6C", "M": "#CCD7E4", "m": "#FFFFFF", "k": "#8E74D2"})
ICONS["ice_antler"] = ([
    "................",
    "...a.......a....",
    "...A..a...A.....",
    "....A.A..A..a...",
    "....A.A..A.A....",
    ".....AA..AAA....",
    "......A..A......",
    "......AAAA......",
    ".......AA.......",
    ".......AA.......",
    "......AAa.......",
    "......aaa.......",
    "................",
    "................",
    "................",
    "................",
], {"A": "#CFE6F6", "a": "#7AAED4"})
ICONS["icicle_wing"] = ([
    "................",
    "................",
    "..bbb...........",
    "..bBBbb.........",
    "...bBBBbbb......",
    "...bBBBBBBbbb...",
    "....bBBBBBBBBb..",
    "....bbBBbBBbBb..",
    ".....b.Ib..Ib.b.",
    ".......I...I....",
    ".......i...I....",
    "...........i....",
    "................",
    "................",
    "................",
    "................",
], {"b": "#141A26", "B": "#38445C", "I": "#9CC8E8", "i": "#E2F2FC"})
ICONS["blizzard_core"] = ([
    "................",
    "................",
    "......wwww......",
    "....wwWWWWww....",
    "...wWWwwwwWWw...",
    "...wWw....wWw...",
    "..wWw..WW..Ww...",
    "..wWw.WccW.Ww...",
    "..wWw.WccW.Ww...",
    "...wWw.WW.wWw...",
    "...wWWw..wWWw...",
    "....wwWWWWww....",
    "......wwww......",
    "................",
    "................",
    "................",
], {"w": "#A6A2BC", "W": "#EAE8F2", "c": "#C6BAF0"})
ICONS["prism_shard"] = ([
    "................",
    "........o.......",
    ".......oPo......",
    "......oPpPo.....",
    "......oPPPo.....",
    ".....oPPpPPo....",
    ".....oCPPPYo....",
    "....oCCPPPYYo...",
    "....oCCcPPYYo...",
    ".....oCCPPYo....",
    "......oCPYo.....",
    ".......ooo......",
    "................",
    "................",
    "................",
    "................",
], {"o": "#5A3E9A", "P": "#C6BAF0", "p": "#FFFFFF", "C": "#86DCE8", "c": "#E6FBFF", "Y": "#F0E6A0"})
# 道具：結び紐（ロスト状態をほどく。赤と白の糸をより合わせた結び目）
ICONS["knot_cord"] = ([
    "................",
    "................",
    ".....rRw.wRr....",
    "....rR.wRw.Rr...",
    "....R..wRw..R...",
    "....rR.RwR.Rr...",
    ".....rRwRwRr....",
    "......wRwRw.....",
    ".......RwR......",
    "......Rw.wR.....",
    ".....Rw...wR....",
    "....rw.....wr...",
    "....r.......r...",
    "................",
    "................",
    "................",
], {"R": "#D04040", "r": "#8A2020", "w": "#F4F0E8"})

# 8階（11月25日）：静氷の輪（闘環は白い筋の刻み、智環は青い声石）・大魚の鱗の首飾り・凍て鶴の腕輪・鐘の音のお守り・
# 霜冠の冠羽・千の声の喉石と、8階の素材（静氷・大魚の鱗・声石・凍て鶴の冠羽・霧の糸・湖の真珠）、物語の品（湖底の祠の石板）
ring("silent_ice_battle_ring", RING_KNUCKLE, {"B": "#DEE4EA", "b": "#AEB8C2", "d": "#76808C", "S": "#929CA8", "k": "#F2F5F8"})
ring("silent_ice_wisdom_ring", RING_GEM, {"B": "#DEE4EA", "b": "#AEB8C2", "d": "#76808C", "S": "#929CA8", "G": "#88AEF0",
                                          "g": "#2A52BA"})

ICONS["fish_scale_necklace"] = ([
    "................",
    "....mMmMmMmM....",
    "...M........m...",
    "..m..........M..",
    "..M..........m..",
    "..m..........M..",
    "...M........m...",
    "....mM....Mm....",
    "......MmmM......",
    ".......II.......",
    "....SS.SS.SS....",
    "...SsSSsSSsSS...",
    "...SSSSSSSSSS...",
    "....dSSSSSSd....",
    "......dSSd......",
    "................",
], {"m": "#747E8E", "M": "#A2ACBA", "I": "#C8D0D8", "S": "#E2E8EE", "s": "#FFFFFF", "d": "#8C96A2"})

ICONS["frost_crane_bracelet"] = ([
    "................",
    "................",
    "....bBBBBBBb....",
    "...B........B...",
    "..b..........b..",
    "..B..........B..",
    "..b..........b..",
    "...B........B...",
    "....bBBSSBBb....",
    "......WSsW......",
    ".....WWSSWW.....",
    "....WWW..WWW....",
    "...WWw....wWW...",
    "...KK......KK...",
    "..KK........KK..",
    "................",
], {"b": "#6A788C", "B": "#B0BCCC", "S": "#8C9AAE", "s": "#F8FBFE", "W": "#F0F4F8", "w": "#B4BEC8", "K": "#22262E"})

ICONS["bell_charm"] = ([
    "................",
    ".......ll.......",
    "......l..l......",
    ".......ll.......",
    ".......SS.......",
    "......IIII......",
    ".....IiIIII.....",
    ".....IiIIII.....",
    ".....IIIIII.....",
    "....IIIIIIII....",
    "...IIIIIIIIII...",
    "...dddddddddd...",
    ".......VV.......",
    ".......Vv.......",
    "................",
    "................",
], {"l": "#747E8E", "S": "#8C9AAE", "I": "#C8D0D8", "i": "#F2F5F8", "d": "#76808C", "V": "#3E6CD2", "v": "#88AEF0"})

ICONS["frost_crown_plume"] = ([
    "................",
    "...p....c....y..",
    "...pP...C...Yy..",
    "....pP..C..Yy...",
    "....pPP.C.YYy...",
    ".....pPPCYYy....",
    "......pPCYy.....",
    ".......PCY......",
    "......SSSSS.....",
    ".......SsS......",
    "........S.......",
    ".......lll......",
    "......l...l.....",
    ".......lll......",
    "................",
    "................",
], {"p": "#C6BAF0", "P": "#F2E6FF", "c": "#A8DCEC", "C": "#E6FBFF", "y": "#F0E6A0", "Y": "#FFF8DC", "S": "#8C9AAE",
    "s": "#F8FBFE", "l": "#747E8E"})

ICONS["thousand_voice_stone"] = ([
    "................",
    "..a..........a..",
    ".a....oooo....a.",
    ".a..ooVVVVoo..a.",
    "a..oVvvVVVVVo..a",
    "a..oVvVVVVVVo..a",
    "a.oVVVVVVVVVVo.a",
    "a.oVVVVwVVVVVo.a",
    "a.oVVVwwwVVVVo.a",
    ".a.oVVVwVVVVo.a.",
    ".a.oVVVVVVVdo.a.",
    "..a.ooVVVVoo.a..",
    "......oooo......",
    "................",
    "................",
    "................",
], {"a": "#88AEF0", "o": "#1E3C9A", "V": "#3E6CD2", "v": "#B6D0F8", "w": "#E6F0FF", "d": "#2A52BA"})

ICONS["silent_ice"] = ([
    "................",
    "................",
    "......oooo......",
    ".....oIIIIoo....",
    "....oIiiIIIIo...",
    "...oIiIIIsIIo...",
    "...oIIIIsIIIo...",
    "...oIIIsIIIIIo..",
    "...oIIsIIIIIIo..",
    "....oIIIIIIIo...",
    "....oIIIIIIo....",
    ".....ooIIIo.....",
    ".......ooo......",
    "................",
    "................",
    "................",
], {"o": "#5A6470", "I": "#AEB8C2", "i": "#F2F5F8", "s": "#DEE4EA"})
ICONS["fish_scale"] = ([
    "................",
    "................",
    ".....oooooo.....",
    "....oSSSSSSo....",
    "...oSsSSSSSSo...",
    "...oSsSSSSSSo...",
    "...oSSddddSSo...",
    "...oSdSSSSdSo...",
    "...oSSSSSSSSo...",
    "....oSSddSSo....",
    "....oSdSSdSo....",
    ".....oSSSSo.....",
    "......oSSo......",
    ".......oo.......",
    "................",
    "................",
], {"o": "#6E7884", "S": "#E2E8EE", "s": "#FFFFFF", "d": "#AAB4BE"})
ICONS["voice_stone"] = ([
    "................",
    "................",
    "................",
    "......oooo......",
    "....ooVVVVoo....",
    "...oVvVVVVVVo...",
    "...oVvvVVVVVo...",
    "..oVVvVVVVVVVo..",
    "..oVVVVVVVVVVo..",
    "..oVVVVVVVVdVo..",
    "...oVVVVVVddo...",
    "....ooVVVVoo....",
    "......oooo......",
    "................",
    "................",
    "................",
], {"o": "#1E3C9A", "V": "#3E6CD2", "v": "#B6D0F8", "d": "#2A52BA"})
ICONS["crane_plume"] = ([
    "................",
    "...........KK...",
    "..........KKWK..",
    ".........KWWWK..",
    "........WWWWW...",
    ".......WWWsWW...",
    "......WWWsWWw...",
    ".....WWWsWWw....",
    "....WWWsWWw.....",
    "...wWWsWWw......",
    "...wWsWww.......",
    "....sww.........",
    "...s............",
    "..s.............",
    "................",
    "................",
], {"K": "#22262E", "W": "#F0F4F8", "w": "#B4BEC8", "s": "#7C8692"})
ICONS["mist_thread"] = ([
    "................",
    "................",
    "....mmm.........",
    "...m...mm.......",
    "...m.MMMMm......",
    "....MmMmMMM.....",
    "...MMmMmMmMM....",
    "...MmMmMmMmMm...",
    "....MmMmMmMMm...",
    ".....MMmMmMM....",
    "......MMMMm..m..",
    "...........m.m..",
    "............m...",
    "................",
    "................",
    "................",
], {"m": "#747E8E", "M": "#BCC4D0"})
ICONS["lake_pearl"] = ([
    "................",
    "................",
    "................",
    "......oooo......",
    ".....oPPPPo.....",
    "....oPppPPPo....",
    "....oPpPPPPo....",
    "....oPPPPPbo....",
    "....oPPPPbbo....",
    ".....oPPbbo.....",
    "......oooo......",
    "................",
    "................",
    "................",
    "................",
    "................",
], {"o": "#6E7884", "P": "#EEF0F2", "p": "#FFFFFF", "b": "#A8DCEC"})
# 物語の品：湖底の祠の石板（丸い頭の石板に、歌の刻み。下に青く光る音符のしるし）
ICONS["lake_shrine_tablet"] = ([
    "................",
    "......oooo......",
    "....ooTTTToo....",
    "...oTTTTTTTTo...",
    "...oTkkTkkkTo...",
    "...oTTTTTTTTo...",
    "...oTkkkTkkTo...",
    "...oTTTTTTTTo...",
    "...oTkTkkkkTo...",
    "...oTTTTbTTTo...",
    "...oTTTbBbTTo...",
    "...oTTTTbTTTo...",
    "...oTTTTTTTTo...",
    "..oooooooooooo..",
    "..oSSSSSSSSSSo..",
    "................",
], {"o": "#3A4250", "T": "#AEB8C2", "k": "#5A6470", "b": "#5E8CE4", "B": "#E6F0FF", "S": "#76808C"})

# 9階（11月28日）：近衛の氷の輪（闘環は盾の刻み、智環は青い頁の光）・肖像の首飾り・氷の蹄鉄の腕輪・名札のお守り・
# 旗手の房飾り・綴じ糸の栞
ring("guard_ice_battle_ring", RING_KNUCKLE, {"B": "#A6C0E4", "b": "#6888C2", "d": "#3A5A9A", "S": "#8C9AAE", "k": "#EAF2FC"})
ring("guard_ice_wisdom_ring", RING_GEM, {"B": "#A6C0E4", "b": "#6888C2", "d": "#3A5A9A", "S": "#8C9AAE", "G": "#D2ECF6",
                                         "g": "#58A2C6"})

ICONS["portrait_necklace"] = ([
    "................",
    "....lLlLlLlL....",
    "...L........l...",
    "..l..........L..",
    "..L..........l..",
    "...l........L...",
    "....lL....Ll....",
    "......lbbl......",
    "......gGGg......",
    ".....gGWWGg.....",
    ".....GWWWWG.....",
    ".....GWWWWG.....",
    ".....gGWWGg.....",
    "......gGGg......",
    "................",
    "................",
], {"l": "#A6AAB2", "L": "#E6E8EC", "b": "#304890", "g": "#A07A2A", "G": "#F0D684", "W": "#FCFCFD"})

ICONS["horseshoe_bracelet"] = ([
    "................",
    "................",
    "....bBBBBBBb....",
    "...B........B...",
    "..b..........b..",
    "..B..........B..",
    "..b..........b..",
    "...B........B...",
    "....bBBSSBBb....",
    "......IIII......",
    ".....Ii..iI.....",
    ".....I....I.....",
    ".....Ii..iI.....",
    ".....dd..dd.....",
    "................",
    "................",
], {"b": "#6A788C", "B": "#B0BCCC", "S": "#8C9AAE", "I": "#A6C0E4", "i": "#EAF2FC", "d": "#3A5A9A"})

ICONS["name_charm"] = ([
    "................",
    ".......bb.......",
    "......b..b......",
    ".......bb.......",
    ".......bb.......",
    "....oooooooo....",
    "....oHSSSSHo....",
    "....oSkkkkSo....",
    "....oSSSSSSo....",
    "....oSkkSkSo....",
    "....oHSSSSHo....",
    "....oooooooo....",
    "................",
    "................",
    "................",
    "................",
], {"b": "#304890", "o": "#4A5668", "H": "#E6EDF4", "S": "#B0BCCC", "k": "#4A5668"})

ICONS["bearer_tassel"] = ([
    "................",
    ".......ss.......",
    "......s..s......",
    ".......ss.......",
    "......SSSS......",
    ".....SHHHHS.....",
    "......bBBb......",
    ".....bBBBBb.....",
    ".....bBbBBb.....",
    "....bBBbBBBb....",
    "....bBbBBbBb....",
    "....bBbBBbBb....",
    "....b.b.b.b.b...",
    "....b.b.b.b.....",
    "................",
    "................",
], {"s": "#8C9AAE", "S": "#B0BCCC", "H": "#F8FBFE", "b": "#1A2A5E", "B": "#425CA8"})

ICONS["binder_bookmark"] = ([
    "................",
    "........ss......",
    ".......s..s.....",
    "........ss......",
    "........S.......",
    "......PPPPP.....",
    "......PkkkP.....",
    "......PPPPP.....",
    "......PkkPP.....",
    "......PPPPP.....",
    "......PkkkP.....",
    "......PPPPP.....",
    "......PP.PP.....",
    "......P...P.....",
    "................",
    "................",
], {"s": "#CFD8E4", "S": "#8C9AAE", "P": "#E6EDF4", "k": "#58A2C6"})

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
    "ice_crystal_necklace": ("氷晶の首飾り（6階）", "凍て毛皮の紐と霜銀の留め金に\n溶けない氷晶の柱（左右に小さなかけら）"),
    "frozen_fur_bracelet": ("凍て毛皮の腕輪（6階）", "白い毛皮を手首に巻き\n外に霜銀の留め具"),
    "snowlight_charm": ("雪灯のお守り（6階）", "霜銀の小さな灯籠（屋根・柱・台）の中に\n淡く光る氷晶"),
    "ice_antler_necklace": ("氷角の首飾り（7階）", "凍て毛皮の紐と霜銀の留め金に\n枝分かれした氷角（幹と、左右に開いた枝）"),
    "icicle_bracelet": ("つららの腕輪（7階）", "夜の色の翼の骨の輪に、霜銀の留め具\n外に、下へ3本のつらら"),
    "passphrase_charm": ("合言葉の札（7階）", "霜銀のかしらと、合言葉を刻んだ鏡氷の札\n下にオーロラ色の玉"),
    "fish_scale_necklace": ("大魚の鱗の首飾り（8階）", "霧の糸の紐と静氷の留め玉に\n扇のように重ねた白い鱗3枚"),
    "frost_crane_bracelet": ("凍て鶴の腕輪（8階）", "霜銀の輪の外に、凍て鶴の白い冠羽を2枚\n斜めに編みこむ（先は黒い風切り）"),
    "bell_charm": ("鐘の音のお守り（8階）", "霧の糸の紐に、静氷を削った小さな鐘\n中に青い声石の舌"),
    "portrait_necklace": ("肖像の首飾り（9階）", "前掛けのレースの紐に、青い結びと\n金の楕円の額（中は白い空白）"),
    "horseshoe_bracelet": ("氷の蹄鉄の腕輪（9階）", "霜銀の輪の外に\n近衛の青い氷の蹄鉄"),
    "name_charm": ("名札のお守り（9階）", "近衛の青い紐に\n名前を刻む小さな銀の札"),
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
    floors = [("2階", accs[0:3]), ("3階", accs[3:6]), ("4階", accs[6:9]), ("5階", accs[9:12]), ("6階", accs[12:15]),
              ("7階", accs[15:18]), ("8階", accs[18:21]), ("9階", accs[21:24])]
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
