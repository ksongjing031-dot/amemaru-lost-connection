"""4階「崩壊した神殿」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（11月24日【補完】）。

  python3 tools/artgen/make_temple_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の4階：崩れた柱と浮かぶ瓦礫の神殿。顔の削れた石像。手をつないで結晶を囲む古代人の壁画。
作るもの（ファイルの名前は敵のIDと同じ。geo/entity・animations/entity・textures/entity）:
  silent_statue   沈黙の石像：白い大理石の、衣を着た人の像。顔だけが削り取られている。重い石の拳。眠っている間は肩と頭に
                  ほこりと苔（ボーン dust）。目覚めると、胸のひびが金色に光る（ボーン glow）
  prayer_echo     祈り手の残響：白い頭巾と衣の上半身だけが浮かぶ、半ば透けた姿。下は霧になって消えている。胸の前で手を合わせる。
                  頭巾の奥に、淡く光る顔の名残
  crystal_beetle  結晶甲虫：黒い藍の甲殻の大きな甲虫。背中の殻から、青白く光る結晶が何本も生えている。大きなあご
  broken_beast    欠けた聖獣：白い石の毛並みに金のたてがみの、獅子のような守護獣。体のところどころが白く抜け落ちている（ロスト）
  cantare         祈りの残響カンターレ：顔のない白い仮面の指揮者。金の縁の白い衣で宙に浮き、右手に指揮棒。頭のうしろに光の輪
                  （歌に守られている間だけ。ボーン halo）。まわりを4人の歌い手（白・青・金・紅。胸の前に光る音の玉）が回る
どれもアニメーションは idle・walk・attack・death と、敵ごとの技（skill_…）。名前は Java の actionNames と同じ。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import anims, loop, once, posed, sheet_font  # noqa: E402
from materials import Painter, RAMPS, hsh, pick, ramp  # noqa: E402

TEMPLE = {
    # 白い大理石（像・聖獣）：あたたかい白灰
    "marble": ramp("#4F4C47", "#6C6861", "#8A857D", "#A7A299", "#C2BDB3", "#D9D5CC", "#EEEBE5"),
    "marble_dark": ramp("#2F2D2A", "#423F3B", "#57534D", "#6D6861", "#847E76", "#9C968D", "#B5AFA6"),
    # ほこりと苔（眠っている像の肩）
    "dust": ramp("#3A3C2E", "#4C503A", "#5F6546", "#737B54", "#8A9265", "#A3AA7E", "#BDC29C"),
    # 残響の衣（白。少し青い）
    "echo": ramp("#6E7A86", "#8996A2", "#A4B0BB", "#BCC6CF", "#D1D9E0", "#E3E9EE", "#F4F7FA"),
    # 甲虫の甲殻（黒い藍）・殻（青灰）
    "chitin": ramp("#0C0D16", "#141625", "#1D2036", "#272B48", "#33395B", "#434A70", "#58608A"),
    "shell": ramp("#26313F", "#344356", "#44576E", "#566C86", "#6C839D", "#879CB3", "#A6B8CA"),
    # 金（たてがみ・飾り・縁）
    "gold": ramp("#4A3410", "#694B16", "#8B651D", "#AD8228", "#C9A03A", "#DFBD58", "#F1D985"),
    "mane": ramp("#5A3A12", "#7A5019", "#9A6820", "#B8822B", "#D19E3E", "#E4BA5C", "#F3D58A"),
    # 指揮者の衣（象牙色）
    "ivory": ramp("#5E584C", "#7A7466", "#979081", "#B3AC9B", "#CBC5B4", "#E0DBCB", "#F2EFE4"),
    # 歌い手の衣（白・青・金・紅）
    "singer_white": ramp("#6A6E74", "#878C92", "#A3A8AE", "#BEC3C8", "#D4D8DC", "#E6E9EC", "#F6F8F9"),
    "singer_blue": ramp("#10243F", "#183558", "#214872", "#2C5D8D", "#3B74A6", "#5590BE", "#77ADD3"),
    "singer_gold": ramp("#4A3410", "#694B16", "#8B651D", "#AD8228", "#C9A03A", "#DFBD58", "#F1D985"),
    "singer_red": ramp("#3A0D10", "#561419", "#721C22", "#8F262C", "#AA3637", "#C24E4A", "#D66C64"),
    # 暗い所（削れた顔のくぼみ・口）
    "pit": ramp("#100F0E", "#1A1816", "#24221F", "#2F2C28", "#3B3732", "#48433D", "#55504A"),
    # 光る：ひび（金）・残響の顔（淡い青白）・結晶（青白）・甲虫と聖獣の目・光の輪・音の玉
    "glow_crack": ramp("#7A5A12", "#A57D1E", "#CFA232", "#EDC75A", "#FBE38E", "#FFF3C8", "#FFFFFF"),
    "glow_echo": ramp("#4F6E8C", "#6A8CAA", "#88AAC6", "#A8C6DE", "#C8DEF0", "#E4F0FA", "#FFFFFF"),
    "glow_crystal": ramp("#2A6C8C", "#3A8CAE", "#55AACA", "#7EC6E0", "#AADDF0", "#D6F0FA", "#FFFFFF"),
    "glow_beast": ramp("#8A5A10", "#B07818", "#D29A28", "#EDBE4A", "#FAD97E", "#FFF0BC", "#FFFFFF"),
    "glow_halo": ramp("#9A7A2A", "#BE9A3A", "#DCB850", "#F0D474", "#FAE8A4", "#FFF6D6", "#FFFFFF"),
    "glow_mask": ramp("#8C8A84", "#A6A49E", "#BEBCB6", "#D4D2CC", "#E6E4E0", "#F4F3F0", "#FFFFFF"),
    "glow_note_white": ramp("#8A9098", "#A6ACB4", "#C0C6CC", "#D6DADF", "#E8EBEE", "#F5F7F8", "#FFFFFF"),
    "glow_note_blue": ramp("#1E5FA8", "#2A7DCC", "#3F9CE6", "#6CBDF4", "#A0D8FA", "#D2EEFD", "#FFFFFF"),
    "glow_note_gold": ramp("#8A6A12", "#B08A1E", "#D2AA32", "#EDC75A", "#FBE38E", "#FFF3C8", "#FFFFFF"),
    "glow_note_red": ramp("#8A1E1E", "#B02A28", "#D2423A", "#EA6A58", "#F69A86", "#FCCABC", "#FFFFFF"),
}
for _k, _v in TEMPLE.items():
    if _k.startswith("glow_"):
        RAMPS[_k] = _v
LOST_WHITE = (246, 248, 252)


class TemplePainter(Painter):
    # ---- 大理石：大きな斑と細いひび。上を向いた面は明るい ----
    def _marble(self, c, name, base):
        tone = self.std(c, name, base=base, noise=0.25, blob=0.5, blob_size=2.6, up=1.2, down=1.0, specks=0.02)
        if self.blob(c, 1.4, 501 + self.eid(c)) < 0.06:
            tone -= 1.0   # 灰色のすじ（大理石の模様）
        if c.el.tags.get("folds") and abs(float(c.n[1])) < 0.6:
            # 衣のひだ（縦の溝）
            along = c.p[0] if abs(float(c.n[2])) > 0.5 else c.p[2]
            if int(math.floor(along + 0.5)) % 3 == 0:
                tone -= 0.8
        return tone

    def paint_marble(self, c):
        if c.el.tags.get("faceless") and c.face.d == "front":
            return self.faceless(c)
        if c.el.tags.get("lost") and self.blob(c, 1.6, 511 + self.eid(c)) < 0.22:
            return LOST_WHITE
        return pick(TEMPLE["marble"], self._marble(c, "marble", 4.3))

    def paint_marble_dark(self, c):
        return pick(TEMPLE["marble_dark"], self._marble(c, "marble_dark", 3.8))

    def faceless(self, c):
        """削り取られた顔：表は荒れて、のみの跡のくぼみが散る。ふちだけ元の滑らかな石"""
        d = c.edge_dist()
        if min(d.values()) == 0:
            return pick(TEMPLE["marble"], 3.6 + self.bevel(c))
        r = self.tex(c, 521)
        if r < 0.22:
            return pick(TEMPLE["pit"], 4.0 + (r - 0.1) * 4)
        return pick(TEMPLE["marble"], 2.6 + (self.tex(c, 522) - 0.5) * 1.2)

    def paint_dust(self, c):
        """ほこりと苔：上を向いた面のまだらだけ（ほかは透明）"""
        if c.n[1] > 0.6:
            if self.blob(c, 1.2, 531) < 0.62:
                return pick(TEMPLE["dust"], 3.0 + (self.tex(c, 532) - 0.5) * 1.6)
            return None
        if c.n[1] > -0.5 and c.edge_dist()["top"] <= (1 if self.tex(c, 533) < 0.5 else 0) and self.blob(c, 1.0, 534) < 0.5:
            return pick(TEMPLE["dust"], 2.6 + (self.tex(c, 535) - 0.5))
        return None

    def paint_glow_crack(self, c):
        """胸のひび：ジグザグの線だけ光る（ほかは透明）"""
        path = c.el.tags.get("zig", 0)
        x = c.i
        want = 1 + ((c.j + path) // 2) % 2 if c.W >= 3 else 0
        if x == want or (c.j % 4 == 3 and abs(x - want) <= 1):
            return self.paint_glow(c, "glow_crack", base=4.6)
        return None

    # ---- 残響（半ば透ける） ----
    def _echo(self, c, alpha):
        tone = self.std(c, "echo", base=4.0, noise=0.3, blob=0.4, up=0.8, down=0.6)
        if c.el.tags.get("folds") and abs(float(c.n[1])) < 0.6:
            along = c.p[0] if abs(float(c.n[2])) > 0.5 else c.p[2]
            if int(math.floor(along + 0.5)) % 3 == 1:
                tone -= 0.7
        rgb = pick(TEMPLE["echo"], tone)
        return (*rgb, int(max(0, min(255, alpha))))

    def paint_echo(self, c):
        return self._echo(c, 205)

    def paint_echo_fade(self, c):
        """衣のすそ：下へ行くほど薄く"""
        t = (c.p[1] - c.el.frm[1]) / max(1.0, c.el.to[1] - c.el.frm[1])
        return self._echo(c, 90 + 120 * t)

    def paint_echo_hood(self, c):
        if c.face.d == "front":
            d = c.edge_dist()
            if d["left"] >= 1 and d["right"] >= 1 and d["top"] >= 1:
                return None   # 頭巾の口（奥の顔が見える）
        return self._echo(c, 215)

    def paint_mist(self, c):
        a = 110 + 80 * self.blob(c, 1.3, 541) + 30 * (self.tex(c, 542) - 0.5)
        if self.tex(c, 543) < 0.06:
            return None
        rgb = pick(TEMPLE["echo"], 4.6 + (self.tex(c, 544) - 0.5))
        return (*rgb, int(max(0, min(255, a))))

    def paint_glow_echo(self, c):
        """頭巾の奥の顔：ぼんやり光るだけ。目のあたりが少し暗い"""
        tone = 3.6 + (self.tex(c, 551) - 0.5) * 0.6
        if c.face.d == "front" and c.j == 2 and c.i in (1, c.W - 2):
            tone -= 1.6
        rgb = pick(TEMPLE["glow_echo"], tone)
        return (*rgb, 230)

    # ---- 甲虫 ----
    def paint_chitin(self, c):
        tone = self.std(c, "chitin", base=3.0, noise=0.2, blob=0.3, up=1.6, down=1.0)
        if c.n[1] > 0.6 and c.i % 4 == 0:
            tone += 0.8   # 甲殻の筋
        if self.tex(c, 561) > 0.96:
            tone += 1.6   # つやの点
        return pick(TEMPLE["chitin"], tone)

    def paint_chitin_dark(self, c):
        tone = self.std(c, "chitin", base=1.8, noise=0.2, blob=0.2, up=0.8, down=0.6)
        return pick(TEMPLE["chitin"], tone)

    def paint_shell(self, c):
        tone = self.std(c, "shell", base=3.0, noise=0.3, blob=0.6, blob_size=1.8, up=1.4, down=1.0)
        # 殻の継ぎ目（まんなかの線）
        if c.n[1] > 0.6 and abs(c.p[0]) < 0.5:
            tone -= 1.4
        if self.blob(c, 1.2, 571) < 0.12:
            return pick(TEMPLE["glow_crystal"], 2.4)   # 殻からのぞく結晶
        return pick(TEMPLE["shell"], tone)

    def paint_glow_crystal(self, c):
        tone = 3.8 + (self.tex(c, 581) - 0.5) * 0.6
        d = c.edge_dist()
        if d["left"] == 0 or d["top"] == 0:
            tone += 1.4   # 稜線の光
        if d["right"] == 0:
            tone -= 0.8
        return pick(TEMPLE["glow_crystal"], tone)

    # ---- 聖獣 ----
    def paint_fur(self, c):
        """白い石の毛並み：短い筆の跡。ところどころ白く抜け落ちている（ロスト）"""
        if c.el.tags.get("lost") and self.blob(c, 1.7, 591 + self.eid(c)) < 0.24:
            return LOST_WHITE
        strand = hsh(int(math.floor(c.p[0] * 2 + c.p[2])), int(math.floor(c.p[1] / 1.5)), 592)
        tone = 4.3 + (strand - 0.5) * 0.9 + self.bevel(c, up=0.9, down=0.9)
        if c.n[1] < -0.6:
            tone -= 1.0
        return pick(TEMPLE["marble"], tone)

    def paint_mane(self, c):
        if c.el.tags.get("lost") and self.blob(c, 1.7, 601 + self.eid(c)) < 0.18:
            return LOST_WHITE
        strand = hsh(int(math.floor(c.p[0] * 1.5)), int(math.floor(c.p[2] * 1.5)), int(math.floor(c.p[1] / 3.0)), 602)
        tone = 3.4 + (strand - 0.5) * 1.6 + self.bevel(c, up=0.8, down=1.0)
        if c.edge_dist()["bottom"] == 0 and c.i % 2 == 0:
            return None   # たてがみの先のぎざぎざ
        return pick(TEMPLE["mane"], tone)

    def paint_gold(self, c):
        tone = self.std(c, "gold", base=3.4, noise=0.25, blob=0.3, up=1.6, down=1.0)
        if self.tex(c, 611) > 0.95:
            tone += 1.4
        return pick(TEMPLE["gold"], tone)

    def paint_pit(self, c):
        return pick(TEMPLE["pit"], 2.4 + (self.tex(c, 621) - 0.5))

    def paint_glow_beast(self, c):
        return self.paint_glow(c, "glow_beast", base=4.4)

    # ---- 指揮者と歌い手 ----
    def paint_ivory(self, c):
        tone = self.std(c, "ivory", base=4.4, noise=0.25, blob=0.3, up=1.1, down=1.0)
        if c.el.tags.get("folds") and abs(float(c.n[1])) < 0.6:
            along = c.p[0] if abs(float(c.n[2])) > 0.5 else c.p[2]
            if int(math.floor(along + 0.5)) % 4 == 0:
                tone -= 0.9
        hem = c.el.tags.get("hem")
        if hem is not None and abs(float(c.n[1])) < 0.6:
            if c.p[1] < hem + 1.0:
                if c.edge_dist()["bottom"] == 0 and self.tex(c, 631) < 0.35:
                    return None   # すその破れ
                return pick(TEMPLE["gold"], 3.6 + (self.tex(c, 632) - 0.5))   # 金の縁
        return pick(TEMPLE["ivory"], tone)

    def paint_ivory_hood(self, c):
        if c.face.d == "front":
            d = c.edge_dist()
            if d["left"] >= 1 and d["right"] >= 1 and d["top"] >= 1 and d["bottom"] >= 1:
                return None
        return self.paint_ivory(c)

    def paint_stole(self, c):
        """金の帯に、青い音符のような小さな印"""
        tone = self.std(c, "gold", base=3.3, noise=0.2, blob=0.2, up=1.2, down=1.0)
        if self.facing(c) and c.j % 5 == 2 and c.i == c.W // 2:
            return pick(TEMPLE["singer_blue"], 4.0)
        return pick(TEMPLE["gold"], tone)

    def paint_glow_mask(self, c):
        """顔のない仮面：なめらかな白。表のまんなかがいちばん明るい"""
        tone = 4.4 + (self.tex(c, 641) - 0.5) * 0.3
        if c.face.d == "front":
            dx = (c.i + 0.5) / c.W - 0.5
            dy = (c.j + 0.5) / c.H - 0.45
            tone += 1.4 - 3.0 * (dx * dx + dy * dy)
        return pick(TEMPLE["glow_mask"], tone)

    def paint_glow_halo(self, c):
        return self.paint_glow(c, "glow_halo", base=4.6)

    def _singer(self, c, name):
        tone = self.std(c, name, base=3.4, noise=0.25, blob=0.3, up=1.2, down=1.0)
        if c.el.tags.get("hood") and c.face.d == "front":
            d = c.edge_dist()
            if d["left"] >= 1 and d["right"] >= 1 and d["top"] >= 1:
                return pick(TEMPLE["pit"], 1.6)   # 頭巾の奥（暗い）
        return pick(TEMPLE[name], tone)

    def paint_singer_white(self, c):
        return self._singer(c, "singer_white")

    def paint_singer_blue(self, c):
        return self._singer(c, "singer_blue")

    def paint_singer_gold(self, c):
        return self._singer(c, "singer_gold")

    def paint_singer_red(self, c):
        return self._singer(c, "singer_red")

    def paint_glow_note_white(self, c):
        return self.paint_glow(c, "glow_note_white", base=4.6)

    def paint_glow_note_blue(self, c):
        return self.paint_glow(c, "glow_note_blue", base=4.4)

    def paint_glow_note_gold(self, c):
        return self.paint_glow(c, "glow_note_gold", base=4.4)

    def paint_glow_note_red(self, c):
        return self.paint_glow(c, "glow_note_red", base=4.4)


# ================================================================ 沈黙の石像

def build_silent_statue():
    g = GeoModel("silent_statue", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("leg_right", (-2.5, 14, 0), parent="root")
    g.bone("leg_left", (2.5, 14, 0), parent="root")
    g.bone("body", (0, 14, 0), parent="root")
    g.bone("head", (0, 34, 0), parent="body")
    g.bone("arm_right", (-8, 32, 0), parent="body")
    g.bone("arm_left", (8, 32, 0), parent="body")
    g.bone("dust", (0, 14, 0), parent="body")
    g.bone("glow", (0, 14, 0), parent="body")
    # 足（衣のすその下）
    g.cube("leg_right", "leg_r", (-5, 0, -3), (5, 14, 6), "marble", tags={"folds": True})
    g.cube("leg_left", "leg_l", (0, 0, -3), (5, 14, 6), "marble", tags={"folds": True})
    g.cube("leg_right", "foot_r", (-5, 0, -4), (5, 2, 1), "marble_dark")
    g.cube("leg_left", "foot_l", (0, 0, -4), (5, 2, 1), "marble_dark")
    # 胴と腰の衣・帯・肩掛け
    g.cube("body", "hips", (-6, 14, -3.5), (12, 8, 7), "marble", tags={"folds": True})
    g.cube("body", "belt", (-6.5, 21, -4), (13, 2, 8), "marble_dark")
    g.cube("body", "torso", (-6, 23, -3.5), (12, 10, 7), "marble", tags={"folds": True})
    g.cube("body", "drape", (-6.5, 29, -4), (13, 4, 8), "marble")
    g.cube("body", "neck", (-2, 33, -2), (4, 1, 4), "marble_dark")
    # 頭：削られた顔と、頭を包む布
    g.cube("head", "head", (-4, 34, -4), (8, 8, 8), "marble", tags={"faceless": True})
    g.cube("head", "veil", (-4.5, 38, -4.5), (9, 5, 9), "marble", tags={"folds": True})
    g.cube("head", "veil_back", (-4.5, 33, 2.5), (9, 5, 2), "marble", tags={"folds": True})
    # 腕と、重い石の拳
    g.cube("arm_right", "arm_r", (-10, 22, -2.5), (4, 11, 5), "marble", tags={"folds": True})
    g.cube("arm_right", "shoulder_r", (-10.5, 30, -3), (5, 3, 6), "marble")
    g.cube("arm_right", "fist_r", (-10.5, 16, -3), (5, 6, 6), "marble_dark")
    g.cube("arm_left", "arm_l", (6, 22, -2.5), (4, 11, 5), "marble", tags={"folds": True})
    g.cube("arm_left", "shoulder_l", (5.5, 30, -3), (5, 3, 6), "marble")
    g.cube("arm_left", "fist_l", (5.5, 16, -3), (5, 6, 6), "marble_dark")
    # ほこりと苔（眠っている間）
    g.cube("dust", "dust_veil", (-4.5, 38, -4.5), (9, 5, 9), "dust", inflate=0.3)
    g.cube("dust", "dust_drape", (-6.5, 29, -4), (13, 4, 8), "dust", inflate=0.3)
    g.cube("dust", "dust_sh_r", (-10.5, 30, -3), (5, 3, 6), "dust", inflate=0.3)
    g.cube("dust", "dust_sh_l", (5.5, 30, -3), (5, 3, 6), "dust", inflate=0.3)
    # 胸のひび（目覚めると光る）
    g.cube("glow", "crack", (-2, 24, -3.7), (4, 9, 0), "glow_crack", glow=True, tags={"zig": 0})
    g.cube("glow", "crack_hip", (1, 16, -3.7), (3, 5, 0), "glow_crack", glow=True, tags={"zig": 1})
    return g


def silent_statue_anims():
    t = {
        # 像：ほとんど動かない（息もしない）
        "idle": loop(4.0, {
            "head": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 0], 2.2: [0, 2, 0], 4.0: [0, 0, 0]}},
        }),
        "walk": loop(1.6, {
            "leg_right": {"rotation": {0: [16, 0, 0], 0.8: [-16, 0, 0], 1.6: [16, 0, 0]}},
            "leg_left": {"rotation": {0: [-16, 0, 0], 0.8: [16, 0, 0], 1.6: [-16, 0, 0]}},
            "arm_right": {"rotation": {0: [-8, 0, 0], 0.8: [8, 0, 0], 1.6: [-8, 0, 0]}},
            "arm_left": {"rotation": {0: [8, 0, 0], 0.8: [-8, 0, 0], 1.6: [8, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.4: [0, -0.6, 0], 0.8: [0, 0, 0], 1.2: [0, -0.6, 0], 1.6: [0, 0, 0]}},
        }),
        # 石の拳（予兆 0.9 秒：振りかぶって、まっすぐ打つ）
        "attack": once(1.4, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.8: [-110, 0, 10], 0.95: [-75, 0, 0], 1.4: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.8: [0, 18, 0], 0.95: [6, -16, 0], 1.4: [0, 0, 0]}},
        }),
        # 目覚める（石がきしむ：小刻みにふるえて、うつむいた顔を上げる）
        "skill_wake": once(1.0, {
            "body": {"rotation": {0: [6, 0, 0], 0.15: [6, 0, 2], 0.3: [6, 0, -2], 0.45: [4, 0, 2], 0.6: [2, 0, -1], 1.0: [0, 0, 0]}},
            "head": {"rotation": {0: [20, 0, 0], 0.7: [-6, 0, 0], 1.0: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 0.5: [-20, 0, 8], 1.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.5: [-20, 0, -8], 1.0: [0, 0, 0]}},
        }),
        # 叩きつけ（予兆 1.4 秒：両腕を振り上げて、地面へ）
        "skill_quake": once(2.0, {
            "arm_right": {"rotation": {0: [0, 0, 0], 1.3: [-165, 0, -10], 1.45: [-20, 0, 0], 2.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.3: [-165, 0, 10], 1.45: [-20, 0, 0], 2.0: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.3: [-10, 0, 0], 1.45: [22, 0, 0], 2.0: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 1.45: [0, -2, 0], 2.0: [0, 0, 0]}},
        }),
        # 崩れる（前へ倒れて、沈む）
        "death": once(2.0, {
            "body": {"rotation": {0: [0, 0, 0], 0.4: [-8, 0, 0], 1.2: [80, 0, 6]}, "position": {0: [0, 0, 0], 1.2: [0, -10, -3]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 1.2: [-40, 0, 0]}},
            "leg_left": {"rotation": {0: [0, 0, 0], 1.2: [-20, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.2: [20, 0, 10]}},
        }, hold=True),
    }
    return anims("silent_statue", t)


# ================================================================ 祈り手の残響

def build_prayer_echo():
    g = GeoModel("prayer_echo", 64, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 10, 0), parent="root")
    g.bone("head", (0, 24, 0), parent="body")
    g.bone("arm_right", (-4.5, 23, 0), parent="body")
    g.bone("arm_left", (4.5, 23, 0), parent="body")
    g.bone("mist", (0, 10, 0), parent="body")
    g.cube("body", "torso", (-4, 14, -2.5), (8, 10, 5), "echo", tags={"folds": True})
    g.cube("body", "skirt", (-4.5, 8, -3), (9, 6, 6), "echo_fade", tags={"folds": True})
    g.cube("body", "hands", (-1, 17, -4.5), (2, 3, 2), "glow_echo")
    g.cube("mist", "mist_a", (-3.5, 4, -2.5), (7, 4, 5), "mist")
    g.cube("mist", "mist_b", (-2.5, 1, -1.5), (5, 3, 3), "mist")
    g.cube("head", "face", (-2.5, 24.5, -2.5), (5, 5, 5), "glow_echo")
    g.cube("head", "hood", (-3.5, 24, -3.5), (7, 7, 7), "echo_hood")
    g.cube("head", "hood_tail", (-3, 21, 2.5), (6, 4, 1), "echo", tags={"folds": True})
    # 手を合わせる腕（袖が前へ寄る）
    g.cube("arm_right", "sleeve_r", (-6, 15, -2), (2, 8, 3), "echo", rot=(-40, 0, -22), pivot=(-4.5, 23, 0))
    g.cube("arm_left", "sleeve_l", (4, 15, -2), (2, 8, 3), "echo", rot=(-40, 0, 22), pivot=(4.5, 23, 0))
    return g


def prayer_echo_anims():
    t = {
        "idle": loop(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.5: [0, 1.0, 0], 3.0: [0, 0, 0]}},
            "mist": {"scale": {0: [1, 1, 1], 1.5: [1.12, 0.9, 1.12], 3.0: [1, 1, 1]}},
            "head": {"rotation": {0: [4, 0, 0], 1.5: [0, 0, 0], 3.0: [4, 0, 0]}},
        }),
        "walk": loop(2.0, {
            "body": {"rotation": {0: [10, 0, 0], 2.0: [10, 0, 0]}, "position": {0: [0, 0, 0], 1.0: [0, 0.8, 0], 2.0: [0, 0, 0]}},
            "mist": {"rotation": {0: [-14, 0, 0], 2.0: [-14, 0, 0]}},
        }),
        # 嘆きの声（両腕を広げて、のけぞる）
        "attack": once(1.2, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.6: [30, 0, 75], 0.8: [30, 0, 85], 1.2: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [30, 0, -75], 0.8: [30, 0, -85], 1.2: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [-25, 0, 0], 1.2: [0, 0, 0]}},
            "body": {"scale": {0: [1, 1, 1], 0.7: [1.08, 1.08, 1.08], 1.2: [1, 1, 1]}},
        }),
        # 祈りの歌（3秒：手を上へ。体が少し浮く）
        "skill_hymn": once(3.0, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.6: [-120, 0, 20], 2.6: [-130, 0, 25], 3.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [-120, 0, -20], 2.6: [-130, 0, -25], 3.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [-25, 0, 0], 2.6: [-30, 0, 0], 3.0: [0, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.6: [0, 2, 0], 2.6: [0, 2.5, 0], 3.0: [0, 0, 0]}},
        }),
        # 消える（上へ昇りながら、小さくなる）
        "death": once(1.6, {
            "body": {"position": {0: [0, 0, 0], 1.6: [0, 14, 0]}, "scale": {0: [1, 1, 1], 1.0: [0.8, 1.2, 0.8], 1.6: [0.05, 0.05, 0.05]}},
        }, hold=True),
    }
    return anims("prayer_echo", t)


# ================================================================ 結晶甲虫

def build_crystal_beetle():
    g = GeoModel("crystal_beetle", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 6, 0), parent="root")
    g.bone("shell", (0, 9, 0), parent="body")
    g.bone("head", (0, 6, -9), parent="body")
    g.bone("jaw_right", (-2, 4.5, -13.5), parent="head")
    g.bone("jaw_left", (2, 4.5, -13.5), parent="head")
    g.cube("body", "body", (-6, 3, -9), (12, 6, 18), "chitin")
    g.cube("body", "belly", (-5, 2, -8), (10, 1, 16), "chitin_dark")
    g.cube("shell", "shell", (-7, 9, -8), (14, 2, 16), "shell")
    g.cube("shell", "shell_back", (-6, 7, 7), (12, 3, 3), "shell")
    for name, origin, size, rot, pivot in (
            ("c1", (-1.5, 11, -2), (3, 8, 3), (0, 45, 0), (0, 11, -0.5)),
            ("c2", (-5, 11, -6), (2, 5, 2), (0, 30, 18), (-4, 11, -5)),
            ("c3", (3, 11, -5), (2, 6, 2), (0, 20, -16), (4, 11, -4)),
            ("c4", (-4.5, 11, 2.5), (2, 4, 2), (-14, 0, 12), (-3.5, 11, 3.5)),
            ("c5", (2, 11, 2), (3, 6, 3), (12, 45, -12), (3.5, 11, 3.5)),
            ("c6", (-1, 11, 5), (2, 3, 2), (18, 0, 0), (0, 11, 6))):
        g.cube("shell", name, origin, size, "glow_crystal", rot=rot, pivot=pivot, glow=True)
    g.cube("head", "head", (-4, 3, -14), (8, 5, 5), "chitin")
    g.cube("head", "eye_r", (-3.5, 6, -14.1), (2, 1, 0), "glow_crystal", glow=True)
    g.cube("head", "eye_l", (1.5, 6, -14.1), (2, 1, 0), "glow_crystal", glow=True)
    g.cube("head", "antenna_r", (-3, 7, -18), (1, 1, 5), "chitin_dark", rot=(-28, -18, 0), pivot=(-2.5, 7.5, -13.5))
    g.cube("head", "antenna_l", (2, 7, -18), (1, 1, 5), "chitin_dark", rot=(-28, 18, 0), pivot=(2.5, 7.5, -13.5))
    g.cube("jaw_right", "jaw_r", (-3.5, 3.5, -18), (2, 2, 5), "chitin_dark", rot=(0, -18, 0), pivot=(-2, 4.5, -13.5))
    g.cube("jaw_left", "jaw_l", (1.5, 3.5, -18), (2, 2, 5), "chitin_dark", rot=(0, 18, 0), pivot=(2, 4.5, -13.5))
    for name, z in (("front", -6), ("mid", 0), ("back", 6)):
        for side, sx in (("right", -1), ("left", 1)):
            bone = "leg_%s_%s" % (name, side)
            g.bone(bone, (sx * 5.5, 4, z), parent="root")
            x0 = -10 if sx < 0 else 5
            g.cube(bone, bone + "_thigh", (x0, 4, z - 0.5), (5, 1, 1), "chitin_dark")
            xs = -10 if sx < 0 else 9
            g.cube(bone, bone + "_shin", (xs, 0, z - 0.5), (1, 4, 1), "chitin")
    return g


def crystal_beetle_anims():
    walk = {}
    for name, ph in (("front_right", 1), ("mid_left", 1), ("back_right", 1), ("front_left", -1), ("mid_right", -1), ("back_left", -1)):
        walk["leg_" + name] = {"rotation": {0: [0, 22 * ph, 0], 0.25: [0, -22 * ph, 0], 0.5: [0, 22 * ph, 0]}}
    walk["body"] = {"position": {0: [0, 0, 0], 0.125: [0, 0.3, 0], 0.25: [0, 0, 0], 0.375: [0, 0.3, 0], 0.5: [0, 0, 0]}}
    t = {
        "idle": loop(2.4, {
            "jaw_right": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 0], 1.1: [0, 14, 0], 1.2: [0, 0, 0], 2.4: [0, 0, 0]}},
            "jaw_left": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 0], 1.1: [0, -14, 0], 1.2: [0, 0, 0], 2.4: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.6: [0, 6, 0], 2.4: [0, 0, 0]}},
        }),
        "walk": loop(0.5, walk),
        # 噛みつき（頭を前へ出して、あごを閉じる）
        "attack": once(1.0, {
            "head": {"position": {0: [0, 0, 0], 0.5: [0, 0, 1], 0.65: [0, 0, -2.5], 1.0: [0, 0, 0]}},
            "jaw_right": {"rotation": {0: [0, 0, 0], 0.5: [0, 30, 0], 0.65: [0, -14, 0], 1.0: [0, 0, 0]}},
            "jaw_left": {"rotation": {0: [0, 0, 0], 0.5: [0, -30, 0], 0.65: [0, 14, 0], 1.0: [0, 0, 0]}},
        }),
        # 体当たり（頭を下げて、殻をすくめて、前へ）
        "skill_charge": once(1.6, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [10, 0, 0], 1.2: [-4, 0, 0], 1.6: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 1.0: [0, -1, 1.5], 1.2: [0, 0, -3], 1.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [18, 0, 0], 1.6: [0, 0, 0]}},
        }),
        "death": once(1.4, {
            "root": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 180]}, "position": {0: [0, 0, 0], 0.6: [0, 10, 0]}},
            "leg_front_right": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 40]}},
            "leg_back_left": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, -40]}},
        }, hold=True),
    }
    return anims("crystal_beetle", t)


# ================================================================ 欠けた聖獣

def build_broken_beast():
    g = GeoModel("broken_beast", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 14, 0), parent="root")
    g.bone("head", (0, 18, -10), parent="body")
    g.bone("jaw", (0, 15.5, -15), parent="head")
    g.bone("tail", (0, 17, 9), parent="body")
    g.bone("tail_tip", (0, 12.5, 15), parent="tail")
    g.cube("body", "body", (-5, 10, -8), (10, 9, 17), "fur", tags={"lost": True})
    g.cube("body", "chest", (-5.5, 9, -11), (11, 10, 4), "fur", tags={"lost": True})
    g.cube("body", "collar", (-6, 15, -11.5), (12, 2, 1), "gold")
    g.cube("head", "mane", (-6.5, 12, -12.5), (13, 13, 5), "mane", tags={"lost": True})
    g.cube("head", "mane_back", (-5.5, 14, -8), (11, 10, 3), "mane")
    g.cube("head", "head", (-4, 15, -17), (8, 8, 6), "fur")
    g.cube("head", "brow", (-4.5, 21, -17.5), (9, 2, 2), "gold")
    g.cube("head", "muzzle", (-2.5, 15, -19.5), (5, 4, 3), "fur")
    g.cube("head", "nose", (-1, 18, -19.9), (2, 1, 1), "pit")
    g.cube("head", "eye_r", (-3.5, 19.5, -17.1), (2, 1, 0), "glow_beast", glow=True)
    g.cube("head", "eye_l", (1.5, 19.5, -17.1), (2, 1, 0), "glow_beast", glow=True)
    g.cube("head", "ear_r", (-4.5, 23, -14), (2, 3, 1), "fur")
    g.cube("head", "ear_l", (2.5, 23, -14), (2, 3, 1), "fur")
    g.cube("jaw", "jaw", (-2.5, 13.5, -19), (5, 2, 4), "marble_dark")
    g.cube("tail", "tail", (-1, 15, 9), (2, 2, 7), "fur", rot=(-28, 0, 0), pivot=(0, 16, 9))
    g.cube("tail_tip", "tail_tip", (-1.5, 10.5, 14), (3, 4, 3), "mane")
    for name, x, z in (("leg_front_right", -3, -7), ("leg_front_left", 3, -7), ("leg_back_right", -3, 6),
                       ("leg_back_left", 3, 6)):
        g.bone(name, (x, 12, z), parent="root")
        g.cube(name, name, (x - 2, 2, z - 2), (4, 10, 4), "fur", tags={"lost": name.endswith("back_left")})
        g.cube(name, name + "_paw", (x - 2.5, 0, z - 3), (5, 2, 5), "marble_dark")
    return g


def broken_beast_anims():
    walk = {}
    for name, ph in (("leg_front_right", 1), ("leg_back_left", 1), ("leg_front_left", -1), ("leg_back_right", -1)):
        walk[name] = {"rotation": {0: [28 * ph, 0, 0], 0.4: [-28 * ph, 0, 0], 0.8: [28 * ph, 0, 0]}}
    walk["body"] = {"position": {0: [0, 0, 0], 0.2: [0, 0.6, 0], 0.4: [0, 0, 0], 0.6: [0, 0.6, 0], 0.8: [0, 0, 0]}}
    walk["tail"] = {"rotation": {0: [0, -12, 0], 0.4: [0, 12, 0], 0.8: [0, -12, 0]}}
    t = {
        "idle": loop(3.0, {
            "body": {"scale": {0: [1, 1, 1], 1.5: [1.02, 1.03, 1.0], 3.0: [1, 1, 1]}},
            "tail": {"rotation": {0: [0, -10, 0], 1.5: [0, 10, 0], 3.0: [0, -10, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 0], 2.3: [0, 15, 0], 2.7: [0, 15, 0], 3.0: [0, 0, 0]}},
        }),
        "walk": loop(0.8, walk),
        # 前足の爪（予兆 0.7 秒：右の前足を上げて、薙ぐ）
        "attack": once(1.2, {
            "leg_front_right": {"rotation": {0: [0, 0, 0], 0.6: [-95, 0, 20], 0.75: [-30, 0, -30], 1.2: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.6: [-12, 0, 0], 0.75: [4, 10, 0], 1.2: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.6: [25, 0, 0], 1.2: [0, 0, 0]}},
        }),
        # 跳びかかり（低く伏せて、前へ）
        "skill_charge": once(1.4, {
            "body": {"position": {0: [0, 0, 0], 0.9: [0, -2.5, 2], 1.05: [0, 1, -4], 1.4: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.9: [8, 0, 0], 1.05: [-12, 0, 0], 1.4: [0, 0, 0]}},
            "leg_front_right": {"rotation": {0: [0, 0, 0], 1.05: [-60, 0, 0], 1.4: [0, 0, 0]}},
            "leg_front_left": {"rotation": {0: [0, 0, 0], 1.05: [-60, 0, 0], 1.4: [0, 0, 0]}},
        }),
        # 咆哮（後ろ足で立ち上がって、吠える）
        "skill_roar": once(1.8, {
            "body": {"rotation": {0: [0, 0, 0], 0.5: [-28, 0, 0], 1.4: [-28, 0, 0], 1.8: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.5: [0, 3, 2], 1.4: [0, 3, 2], 1.8: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.5: [-18, 0, 0], 1.4: [-22, 0, 0], 1.8: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.5: [38, 0, 0], 1.4: [38, 0, 0], 1.8: [0, 0, 0]}},
            "leg_front_right": {"rotation": {0: [0, 0, 0], 0.5: [-40, 0, 0], 1.4: [-40, 0, 0], 1.8: [0, 0, 0]}},
            "leg_front_left": {"rotation": {0: [0, 0, 0], 0.5: [-40, 0, 0], 1.4: [-40, 0, 0], 1.8: [0, 0, 0]}},
        }),
        # 忘れる（立ち止まって、首をかしげて、あたりを見回す）
        "skill_forget": once(2.0, {
            "head": {"rotation": {0: [0, 0, 0], 0.3: [0, 0, 22], 0.9: [0, 30, 18], 1.5: [0, -30, 18], 2.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.4: [20, 0, 0], 1.6: [20, 0, 0], 2.0: [0, 0, 0]}},
        }),
        "death": once(1.8, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 85]}, "position": {0: [0, 0, 0], 1.0: [0, -7, 0]}},
            "leg_front_right": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 85]}, "position": {0: [0, 0, 0], 1.0: [-6, -4, 0]}},
            "leg_back_right": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 85]}, "position": {0: [0, 0, 0], 1.0: [-6, -4, 0]}},
            "leg_front_left": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 85]}, "position": {0: [0, 0, 0], 1.0: [-12, -12, 0]}},
            "leg_back_left": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 85]}, "position": {0: [0, 0, 0], 1.0: [-12, -12, 0]}},
        }, hold=True),
    }
    return anims("broken_beast", t)


# ================================================================ 祈りの残響カンターレ（ボス）

SINGERS = (("white", 0), ("blue", 90), ("gold", 180), ("red", 270))
ORBIT_R = 26


def build_cantare():
    g = GeoModel("cantare", 256, 256)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 22, 0), parent="root")
    g.bone("head", (0, 44, 0), parent="body")
    g.bone("arm_right", (-9, 41, 0), parent="body")
    g.bone("arm_left", (9, 41, 0), parent="body")
    g.bone("halo", (0, 50, 7), parent="body")
    g.bone("orbit", (0, 30, 0), parent="root")
    # 衣（宙に浮く。すそは金の縁で、破れている）
    g.cube("body", "robe_low", (-8, 8, -6), (16, 14, 12), "ivory", tags={"folds": True, "hem": 8})
    g.cube("body", "robe_tail", (-6, 3, -4), (12, 5, 8), "ivory", tags={"folds": True, "hem": 3})
    g.cube("body", "torso", (-7, 22, -4.5), (14, 18, 9), "ivory", tags={"folds": True})
    g.cube("body", "stole", (-2.5, 12, -6.2), (5, 28, 1), "stole")
    g.cube("body", "mantle", (-9.5, 37, -5.5), (19, 4, 11), "ivory")
    g.cube("body", "collar", (-5, 40, -4.5), (10, 2, 9), "gold")
    # 頭：頭巾と、顔のない白い仮面
    g.cube("head", "mask", (-4, 43, -4.5), (8, 9, 8), "glow_mask", glow=True)
    g.cube("head", "hood", (-5, 42, -5.5), (10, 12, 11), "ivory_hood")
    g.cube("head", "hood_top", (-3, 54, -3.5), (6, 2, 7), "ivory")
    # 腕：右手に指揮棒
    g.cube("arm_right", "sleeve_r", (-13, 28, -3.5), (5, 14, 7), "ivory", tags={"folds": True, "hem": 28})
    g.cube("arm_right", "hand_r", (-12, 25, -2), (3, 3, 3), "glow_mask")
    g.cube("arm_right", "baton", (-11, 26, -12), (1, 1, 10), "gold")
    g.cube("arm_right", "baton_tip", (-11.5, 25.5, -13), (2, 2, 1), "glow_halo", glow=True)
    g.cube("arm_left", "sleeve_l", (8, 28, -3.5), (5, 14, 7), "ivory", tags={"folds": True, "hem": 28})
    g.cube("arm_left", "hand_l", (9, 25, -2), (3, 3, 3), "glow_mask")
    # 光の輪（頭のうしろ）
    for k in range(10):
        a = math.radians(k * 36)
        cx, cy = 9.5 * math.cos(a), 50 + 9.5 * math.sin(a)
        g.cube("halo", "halo_%d" % k, (cx - 3, cy - 0.5, 7), (6, 1, 1), "glow_halo", rot=(0, 0, k * 36 + 90),
               pivot=(cx, cy, 7.5), glow=True)
    # 4人の歌い手（まわりを回る）
    for name, deg in SINGERS:
        a = math.radians(deg)
        sx, sz = ORBIT_R * math.sin(a), -ORBIT_R * math.cos(a)
        bone = "singer_" + name
        g.bone(bone, (sx, 30, sz), parent="orbit")
        mat = "singer_" + name
        g.cube(bone, bone + "_body", (sx - 3, 22, sz - 2.5), (6, 9, 5), mat)
        g.cube(bone, bone + "_skirt", (sx - 2, 18, sz - 2), (4, 4, 4), mat)
        g.cube(bone, bone + "_hood", (sx - 2.5, 31, sz - 2.5), (5, 5, 5), mat, tags={"hood": True})
        g.cube(bone, bone + "_note", (sx - 1, 26, sz - 5), (2, 2, 2), "glow_note_" + name, glow=True)
    return g


def cantare_anims():
    bob = {0: [0, 0, 0], 2.0: [0, 1.5, 0], 4.0: [0, 0, 0], 6.0: [0, 1.5, 0], 8.0: [0, 0, 0]}
    singers_pulse = {}
    for k, (name, _) in enumerate(SINGERS):
        t0 = 0.6 + k * 0.7
        singers_pulse["singer_" + name] = {"scale": {0: [1, 1, 1], t0: [1, 1, 1], t0 + 0.2: [1.35, 1.35, 1.35], t0 + 0.6: [1, 1, 1],
                                                     4.0: [1, 1, 1]},
                                           "position": {0: [0, 0, 0], t0 + 0.2: [0, 2, 0], t0 + 0.6: [0, 0, 0], 4.0: [0, 0, 0]}}
    t = {
        "idle": loop(8.0, {
            "body": {"position": bob},
            "orbit": {"rotation": {0: [0, 0, 0], 8.0: [0, 360, 0]}, "position": {0: [0, 0, 0], 2.0: [0, -1, 0], 4.0: [0, 0, 0],
                                                                                 6.0: [0, -1, 0], 8.0: [0, 0, 0]}},
            "halo": {"rotation": {0: [0, 0, 0], 8.0: [0, 0, 360]}},
            "arm_right": {"rotation": {0: [-20, 0, 0], 4.0: [-26, 0, 0], 8.0: [-20, 0, 0]}},
        }),
        "walk": loop(8.0, {
            "body": {"position": bob, "rotation": {0: [8, 0, 0], 8.0: [8, 0, 0]}},
            "orbit": {"rotation": {0: [0, 0, 0], 8.0: [0, 360, 0]}},
            "halo": {"rotation": {0: [0, 0, 0], 8.0: [0, 0, 360]}},
        }),
        "attack": once(1.0, {
            "arm_right": {"rotation": {0: [-20, 0, 0], 0.5: [-90, 0, 0], 0.65: [-10, 0, 0], 1.0: [-20, 0, 0]}},
        }),
        # 嘆きの波（予兆 1.2 秒：両腕を大きく横へ引いて、前へ払う）
        "skill_wave": once(2.0, {
            "arm_right": {"rotation": {0: [-20, 0, 0], 1.1: [-60, 0, 70], 1.3: [-80, 0, -20], 2.0: [-20, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.1: [-60, 0, -70], 1.3: [-80, 0, 20], 2.0: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.1: [-8, 0, 0], 1.3: [12, 0, 0], 2.0: [0, 0, 0]}},
        }),
        # 高音の柱（予兆 1.8 秒：指揮棒を高く上げて、振り下ろす）
        "skill_pillars": once(2.6, {
            "arm_right": {"rotation": {0: [-20, 0, 0], 1.6: [-175, 0, -10], 1.85: [-40, 0, 0], 2.6: [-20, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.6: [-160, 0, 15], 1.85: [-30, 0, 0], 2.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.6: [-20, 0, 0], 1.85: [10, 0, 0], 2.6: [0, 0, 0]}},
        }),
        # 選律の歌（指揮をして、歌い手が順にふくらむ）
        "skill_song": once(4.0, dict({
            "arm_right": {"rotation": {0: [-20, 0, 0], 0.6: [-120, 0, 0], 1.3: [-60, 0, 20], 2.0: [-120, 0, 0], 2.7: [-60, 0, 20],
                                       3.4: [-120, 0, 0], 4.0: [-20, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [-80, 0, -30], 3.4: [-80, 0, -30], 4.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [-15, 0, 0], 3.4: [-15, 0, 0], 4.0: [0, 0, 0]}},
        }, **singers_pulse)),
        # 祈り手を呼ぶ（両腕を広げる）
        "skill_call": once(2.0, {
            "arm_right": {"rotation": {0: [-20, 0, 0], 0.8: [-40, 0, 85], 1.6: [-40, 0, 85], 2.0: [-20, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.8: [-40, 0, -85], 1.6: [-40, 0, -85], 2.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.8: [-28, 0, 0], 1.6: [-28, 0, 0], 2.0: [0, 0, 0]}},
        }),
        # 無防備（歌い手が黙る：体が沈み、腕が垂れ、歌い手の輪が縮む）
        "skill_open": once(9.6, {
            "body": {"position": {0: [0, 0, 0], 0.6: [0, -7, 0], 9.0: [0, -7, 0], 9.6: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.6: [24, 0, 4], 9.0: [24, 0, 4], 9.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [28, 0, 0], 9.0: [28, 0, 0], 9.6: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [-20, 0, 0], 0.6: [18, 0, 6], 9.0: [18, 0, 6], 9.6: [-20, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [18, 0, -6], 9.0: [18, 0, -6], 9.6: [0, 0, 0]}},
            "orbit": {"scale": {0: [1, 1, 1], 0.6: [0.55, 0.8, 0.55], 9.0: [0.55, 0.8, 0.55], 9.6: [1, 1, 1]},
                      "position": {0: [0, 0, 0], 0.6: [0, -8, 0], 9.0: [0, -8, 0], 9.6: [0, 0, 0]}},
        }),
        # 消える（のけぞって昇り、歌い手が散る）
        "death": once(3.0, {
            "body": {"rotation": {0: [0, 0, 0], 1.5: [-30, 0, 0]}, "position": {0: [0, 0, 0], 3.0: [0, 20, 0]},
                     "scale": {0: [1, 1, 1], 2.0: [1, 1, 1], 3.0: [0.05, 0.05, 0.05]}},
            "orbit": {"scale": {0: [1, 1, 1], 1.5: [1.8, 1, 1.8], 3.0: [0.05, 0.05, 0.05]},
                      "rotation": {0: [0, 0, 0], 3.0: [0, 540, 0]}},
            "arm_right": {"rotation": {0: [-20, 0, 0], 1.5: [-170, 0, 20]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.5: [-170, 0, -20]}},
        }, hold=True),
    }
    return anims("cantare", t)


MODELS = [(build_silent_statue, silent_statue_anims), (build_prayer_echo, prayer_echo_anims),
          (build_crystal_beetle, crystal_beetle_anims), (build_broken_beast, broken_beast_anims), (build_cantare, cantare_anims)]


def build_all(repo):
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(TemplePainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def check_sheet(models, out_dir):
    from PIL import Image, ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    sheets = []
    layout = {
        "silent_statue": dict(scale=6.0, center=(0, 21, 0), size=(260, 320)),
        "prayer_echo": dict(scale=8.0, center=(0, 15, 0), size=(240, 300)),
        "crystal_beetle": dict(scale=8.0, center=(0, 6, -2), size=(300, 220)),
        "broken_beast": dict(scale=7.0, center=(0, 13, -3), size=(300, 260)),
        "cantare": dict(scale=3.2, center=(0, 30, 0), size=(300, 320)),
    }
    poses = {
        "silent_statue": {"arm_right": ((-160, 0, -10),), "arm_left": ((-160, 0, 10),), "dust": ((0, 0, 0), 0.0)},
        "prayer_echo": {"arm_right": ((-120, 0, 20),), "arm_left": ((-120, 0, -20),), "head": ((-25, 0, 0),)},
        "crystal_beetle": {"jaw_right": ((0, 30, 0),), "jaw_left": ((0, -30, 0),)},
        "broken_beast": {"body": ((-28, 0, 0), 1.0, (0, 3, 2)), "jaw": ((38, 0, 0),), "head": ((-20, 0, 0),)},
        "cantare": {"arm_right": ((-120, 0, 0),), "arm_left": ((-80, 0, -30),), "halo": ((0, 0, 0), 0.0)},
    }
    for name, (g, tex) in models.items():
        lay = layout[name]
        tiles = []
        for yaw, pitch in ((150, 14), (-30, 14), (180, 0), (90, 0)):
            tiles.append(render_many([(g, tex)], yaw=yaw, pitch=pitch, **lay))
        tiles.append(render_many([(posed(g, poses[name]), tex)], yaw=150, pitch=14, **lay))
        tiles.append(render_many([(g, tex)], yaw=150, pitch=14, darkness=0.2, **lay))
        w, h = lay["size"]
        s = Image.new("RGBA", (w * len(tiles), h + 22), (44, 56, 60, 255))
        for k, im in enumerate(tiles):
            s.alpha_composite(im, (w * k, 22))
        ImageDraw.Draw(s).text((6, 2), name + "  （斜め前・斜め後ろ・正面・横・技の形・暗い所）", fill=(235, 235, 220), font=sheet_font(15))
        sheets.append(s)
    W = max(s.size[0] for s in sheets)
    H = sum(s.size[1] for s in sheets)
    sheet = Image.new("RGBA", (W, H), (36, 44, 48, 255))
    y = 0
    for s in sheets:
        sheet.alpha_composite(s, (0, y))
        y += s.size[1]
    path = os.path.join(out_dir, "temple_models_check.png")
    sheet.save(path)
    return path


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
    models = build_all(repo)
    if len(sys.argv) > 2:
        print(check_sheet(models, sys.argv[2]))
    print("models written:", ", ".join(models))


if __name__ == "__main__":
    main()
