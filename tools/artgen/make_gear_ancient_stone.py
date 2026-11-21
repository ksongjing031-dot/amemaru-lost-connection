"""古代石シリーズの武器と盾（工房で作る、古代の見張りの装備を写したもの）：剣・大剣・杖・盾。

  python3 tools/artgen/make_gear_ancient_stone.py <リポジトリ> [確認画像の出力先]

共通の要素：①面取りした灰青色の古代石に、道標と同じ「文字の溝」 ②苔鉄の芯と帯（リベット）
③光らない結晶の欠片 ④道標の柱のような段々の先。握りはツノモフの毛の紐（1階の素材）。
仕様書：docs/gear/ancient_stone.md
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from materials import Painter, RAMPS, pick  # noqa: E402
import weapons  # noqa: E402

CREDIT = "AMEMARU: 古代石シリーズ（tools/artgen/make_gear_ancient_stone.py で生成。docs/gear/ancient_stone.md）"


# ---------------------------------------------------------------- 剣

def build_sword():
    m = JavaModel("ancient_stone_sword", 64, 64)
    # 柄頭：小さな道標（段々の先が下を向く）
    m.box("pommel", (7, -3, 7), (9, -0.5, 9), "stone", tags={"grooves": [-1.75]})
    m.box("pommel_cap", (7.4, -3.7, 7.4), (8.6, -3, 8.6), "stone")
    # 握り：苔鉄の芯に、ツノモフの毛の紐を巻く
    m.box("core_grip", (7.4, -0.5, 7.4), (8.6, 4.75, 8.6), "iron")
    m.box("grip", (7.25, 0, 7.25), (8.75, 4.25, 8.75), "wool", tags={"wrap": True})
    # 鍔：石のまぐさ。まん中に苔鉄の帯と結晶の欠片
    m.box("guard", (4.75, 4.5, 6.9), (11.25, 6.5, 9.1), "stone")
    m.box("guard_end_l", (4.0, 4.9, 7.3), (4.75, 6.1, 8.7), "stone")
    m.box("guard_end_r", (11.25, 4.9, 7.3), (12.0, 6.1, 8.7), "stone")
    m.box("guard_band", (7, 4.25, 6.6), (9, 6.75, 9.4), "iron", tags={"rivets": [(8.0, 6.25)]})
    m.box("guard_shard", (7.5, 4.75, 9.4), (8.5, 5.75, 9.8), "crystal_dim")
    # 刃：苔鉄の芯（刃のふちとして見える）を、石の板2枚で挟む
    m.box("core", (6.25, 6.5, 7.75), (9.75, 20, 8.25), "iron", tags={"edge_iron": True})
    m.box("slab_front", (6.75, 6.5, 8.25), (9.25, 19.5, 8.75), "stone", tags={"grooves": [9.5, 12.5, 15.5]})
    m.box("slab_back", (6.75, 6.5, 7.25), (9.25, 19.5, 7.75), "stone", tags={"grooves": [9.5, 12.5, 15.5]})
    m.box("tip_1", (7.25, 19.5, 7.4), (8.75, 20.5, 8.6), "stone")
    m.box("tip_2", (7.6, 20.5, 7.6), (8.4, 21.5, 8.4), "stone")
    m.box("tip_core", (7.75, 20, 7.8), (8.25, 22.25, 8.2), "iron", tags={"edge_iron": True})
    return m


# ---------------------------------------------------------------- 大剣

def build_greatsword():
    m = JavaModel("ancient_stone_greatsword", 64, 64)
    # 柄頭：重い苔鉄のおもり
    m.box("pommel", (6.75, -4.5, 6.75), (9.25, -2, 9.25), "iron", tags={"rivets": [(8.0, -3.25)]})
    m.box("pommel_cap", (7.25, -5, 7.25), (8.75, -4.5, 8.75), "iron")
    # 長い握り：苔鉄の芯に毛の紐を2か所
    m.box("core_grip", (7.4, -2, 7.4), (8.6, 8, 8.6), "iron")
    m.box("grip_low", (7.25, -1.5, 7.25), (8.75, 3, 8.75), "wool", tags={"wrap": True})
    m.box("grip_high", (7.25, 4, 7.25), (8.75, 7.5, 8.75), "wool", tags={"wrap": True})
    # 鍔：石の肩と苔鉄の帯
    m.box("guard", (4.5, 8, 6.75), (11.5, 10.5, 9.25), "stone", tags={"grooves": [9.25]})
    m.box("guard_end_l", (3.75, 8.5, 7.1), (4.5, 10, 8.9), "stone")
    m.box("guard_end_r", (11.5, 8.5, 7.1), (12.25, 10, 8.9), "stone")
    m.box("guard_band", (7, 7.75, 6.5), (9, 10.75, 9.5), "iron", tags={"rivets": [(8.0, 10.25)]})
    # 刃：先ほど幅の広い石の板（左に苔鉄の背、右が刃）。先が重い
    m.box("spine", (5.25, 10.5, 6.9), (6, 30.5, 9.1), "iron", tags={"rivets": [(5.6, 14.0), (5.6, 20.0), (5.6, 26.0)]})
    m.box("slab_1", (6, 10.5, 7.25), (10, 17, 8.75), "stone", tags={"grooves": [14.0], "seamless": True})
    m.box("slab_2", (6, 17, 7.25), (11, 23, 8.75), "stone", tags={"seamless": True})
    m.box("slab_3", (6, 23, 7.25), (12, 28.2, 8.75), "stone", tags={"grooves": [25.0], "seamless": True})
    m.box("bevel_1", (10, 10.5, 7.55), (10.6, 17, 8.45), "stone", tags={"chisel": True})
    m.box("bevel_2", (11, 17, 7.55), (11.6, 23, 8.45), "stone", tags={"chisel": True})
    m.box("bevel_3", (12, 23, 7.55), (12.6, 27.4, 8.45), "stone", tags={"chisel": True})
    # 先：背（左）から刃（右）へ斜めに下がる切っ先（22.5度に傾けた石）
    m.box("slant", (5.6, 28.2, 7.35), (12.6, 29.7, 8.65), "stone", rot=((9.1, 28.95, 8.0), "z", -22.5))
    m.box("slant_edge", (11.2, 26.9, 7.55), (12.9, 28.0, 8.45), "stone", rot=((12.05, 27.45, 8.0), "z", -22.5),
          tags={"chisel": True})
    m.box("spine_cap", (5.25, 30.5, 7.1), (6, 31.25, 8.9), "iron")
    m.box("shard", (7.25, 11.25, 8.75), (8.75, 12.75, 9.15), "crystal_dim")
    return m


# ---------------------------------------------------------------- 杖

def build_staff():
    m = JavaModel("ancient_stone_staff", 64, 64)
    m.box("spike", (7.5, -8, 7.5), (8.5, -6, 8.5), "iron")
    m.box("spike_tip", (7.75, -8.75, 7.75), (8.25, -8, 8.25), "iron")
    # 柄：苔鉄の芯に、古代石の輪を積む（間から芯が見える）。まん中は毛の紐の握り
    m.box("core", (7.6, -6, 7.6), (8.4, 15, 8.4), "iron")
    for k, (y0, y1) in enumerate(((-6, -3.5), (-3, -0.5), (6.5, 9), (9.5, 12), (12.5, 14.5))):
        w = 1.1 if k % 2 == 0 else 1.0
        m.box(f"ring_{k}", (8 - w, y0, 8 - w), (8 + w, y1, 8 + w), "stone", tags={"grooves": [(y0 + y1) / 2]})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "wool", tags={"wrap": True})
    # 先：小さな道標の柱。表と裏の窓に、結晶の欠片
    m.box("collar", (6.25, 14.5, 6.25), (9.75, 15.25, 9.75), "iron", tags={"rivets": [(8.0, 14.9)]})
    m.box("head_base", (6.5, 15.25, 6.5), (9.5, 16.5, 9.5), "stone", tags={"grooves": [15.9]})
    m.box("head_l", (6.5, 16.5, 6.5), (7.25, 19.5, 9.5), "stone")
    m.box("head_r", (8.75, 16.5, 6.5), (9.5, 19.5, 9.5), "stone")
    m.box("head_top", (6.5, 19.5, 6.5), (9.5, 21, 9.5), "stone", tags={"grooves": [20.25]})
    m.box("shard", (7.25, 16.5, 7.25), (8.75, 19.5, 8.75), "crystal_dim")
    m.box("cap_1", (7, 21, 7), (9, 22, 9), "stone")
    m.box("cap_2", (7.5, 22, 7.5), (8.5, 22.75, 8.5), "stone")
    return m


# ---------------------------------------------------------------- 盾

def build_shield():
    m = JavaModel("ancient_stone_shield_model", 64, 64)
    # 石の板：まん中の板と、上下へ段々に細くなる板（上下のとがった六角形）
    m.box("slab_mid", (-5.5, -5, 1), (5.5, 5, 2.75), "stone")
    for k, (y0, y1, half) in enumerate(((5, 7.5, 4.5), (7.5, 9.5, 3), (9.5, 10.75, 1.5))):
        m.box(f"slab_top_{k}", (-half, y0, 1.1), (half, y1, 2.6 - 0.15 * k), "stone",
              tags={"grooves": [y0 + 1.0]} if k == 0 else None)
        m.box(f"slab_low_{k}", (-half, -y1, 1.1), (half, -y0, 2.6 - 0.15 * k), "stone",
              tags={"grooves": [-y0 - 1.25]} if k == 0 else None)
    # 苔鉄の帯：上下2本で、板をまとめて挟む
    for k, y in enumerate((4.5, -5.5)):
        m.box(f"band_{k}", (-6, y, 0.6), (6, y + 1, 3.15), "iron",
              tags={"rivets": [(-4.5, y + 0.5), (0.0, y + 0.5), (4.5, y + 0.5)]})
    # 見張りの目：盛り上がった石と、横の溝に結晶の欠片
    m.box("eye", (-2.5, -1.5, 2.75), (2.5, 1.5, 3.5), "stone", tags={"eye_slot": True})
    m.box("eye_shard", (-0.75, -0.5, 3.5), (0.75, 0.5, 3.85), "crystal_dim")
    # 裏：握り
    m.box("grip_bracket_top", (-1, 1.5, -3), (1, 2.5, 1), "iron")
    m.box("grip_bracket_low", (-1, -2.5, -3), (1, -1.5, 1), "iron")
    m.box("grip", (-1, -2.5, -4.5), (1, 2.5, -3), "wool", tags={"wrap": True})
    return m


# ---------------------------------------------------------------- 色と模様

class AncientPainter(Painter):
    def paint_iron(self, c):
        tone = self.std(c, "iron", base=3.0, noise=0.2, blob=0.35, up=1.6, down=0.9)
        tone += self.rivet(c)
        if c.el.tags.get("edge_iron") and self.facing(c):
            # 研いだ苔鉄の刃：いちばん外のふちだけ明るい
            if c.i == 0 or c.i == c.W - 1:
                tone += 2.2
        col = pick(RAMPS["iron"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 500 + self.eid(c)) < 0.18:
            col = (75, 97, 64)
        return col

    def bevel(self, c, up=1.2, down=1.0):
        if c.el.tags.get("seamless") and self.facing(c):
            # 大剣の刃の板：上下の継ぎ目は見せない（1枚の板に見えるように）。左右のふちだけ
            d = c.edge_dist()
            if d["left"] == 0:
                return up if c.du[0] > 0 else -down
            if d["right"] == 0:
                return -down if c.du[0] > 0 else up
            return 0.0
        return super().bevel(c, up, down)

    def paint_stone(self, c):
        tone = self.std(c, "stone", base=3.15, noise=0.18, blob=0.4, up=1.3, down=1.1, specks=0.03)
        tone += self.grooves(c)
        if c.el.tags.get("chisel"):
            tone += 0.8  # のみで削った刃の面（明るい）
        if c.el.tags.get("eye_slot") and c.n[2] > 0.6 and abs(c.p[1]) < 0.5:
            tone -= 1.8  # 見張りの目の横の溝
        if abs(c.n[0]) > 0.6:
            tone += 0.35
        col = pick(RAMPS["stone"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 300 + self.eid(c)) < 0.12:
            col = (75, 97, 64)
        return col


# ---------------------------------------------------------------- 持ち物の絵（16×16）

ICON = {"s": "#B7C3C8", "S": "#D8E1E3", "t": "#7C8A92", "T": "#606D75", "u": "#454F56",
        "i": "#434A47", "I": "#757E79", "k": "#232827",
        "w": "#E9DFC8", "v": "#BCAF96", "c": "#BFDDE0", "C": "#72A4AE"}
OUTLINE = "#16191C"


def icon_sword():
    from pixelart import Pixel
    S = ICON
    p = Pixel()
    # 刃：石の面（明・中）と、左右の苔鉄のふち
    p.diag_band(8, 13, lambda t: [S["I"], S["S"], S["s"], S["t"], S["i"]], dx0=-2)
    x, y = p.axis(14)
    p.put(x - 1, y, S["s"])
    p.put(x, y, S["T"])
    p.put(x + 1, y - 1, S["I"])  # 芯の先
    for t in (9, 11, 13):  # 文字の溝
        x, y = p.axis(t)
        p.put(x, y, S["T"])
    for u in range(-2, 3):  # 石のまぐさの鍔
        x, y = p.axis(7)
        p.put(x + u, y + u, S["s"])
        p.put(x + u + 1, y + u, S["T"])
    x, y = p.axis(7)
    p.put(x, y, S["c"])
    for t in (4, 5, 6):  # 毛の紐の握り
        x, y = p.axis(t)
        p.put(x, y, S["w"] if t % 2 == 0 else S["v"])
        p.put(x + 1, y, S["v"] if t % 2 == 0 else S["w"])
    x, y = p.axis(2)  # 道標の柄頭
    p.put(x, y, S["s"])
    p.put(x + 1, y, S["t"])
    p.put(x, y + 1, S["t"])
    p.put(x + 1, y - 1, S["T"])
    p.outline(OUTLINE)
    return p.icon()


def icon_greatsword():
    from pixelart import Pixel
    S = ICON
    p = Pixel()
    # 先ほど広い刃（先の数行は5ピクセル）
    p.diag_band(7, 14, lambda t: ([S["i"], S["S"], S["s"], S["t"]] + ([S["s"], S["T"]] if t >= 11 else [S["T"]])), dx0=-2)
    x, y = p.axis(15)
    for k, col in enumerate((S["i"], S["s"], S["t"])):
        p.put(x - 2 + k, y, col)
    for t in (8, 10, 12):
        x, y = p.axis(t)
        p.put(x, y, S["T"])
    for u in range(-3, 4):
        x, y = p.axis(5)
        p.put(x + u, y + u, S["s"])
        p.put(x + u + 1, y + u, S["T"])
    x, y = p.axis(6)
    p.put(x, y, S["c"])
    for t in (2, 3, 4):
        x, y = p.axis(t)
        p.put(x, y, S["w"] if t % 2 else S["v"])
        p.put(x + 1, y, S["v"] if t % 2 else S["w"])
    x, y = p.axis(1)
    p.put(x, y, S["I"])
    p.put(x + 1, y, S["i"])
    p.put(x, y + 1, S["i"])
    p.outline(OUTLINE)
    return p.icon()


def icon_staff():
    from pixelart import Pixel
    S = ICON
    p = Pixel()
    for t in range(0, 10):  # 柄：石の輪と、間の芯
        x, y = p.axis(t)
        if t in (3, 4, 5):
            p.put(x, y, S["w"])
            p.put(x + 1, y, S["v"])
        elif t % 2 == 0:
            p.put(x, y, S["s"])
            p.put(x + 1, y, S["t"])
        else:
            p.put(x, y, S["I"])
            p.put(x + 1, y, S["i"])
    # 帯と、道標の柱（斜めに置いた3×4の石）。まん中の窓に結晶
    x, y = p.axis(10)
    for dx, col in ((-1, S["I"]), (0, S["I"]), (1, S["i"]), (2, S["i"])):
        p.put(x + dx, y, col)
    head = {(11, 3): "S", (12, 3): "s", (13, 3): "t",
            (11, 2): "s", (12, 2): "c", (13, 2): "C", (14, 2): "T",
            (12, 1): "s", (13, 1): "c", (14, 1): "t",
            (13, 0): "S", (14, 0): "T", (12, 4): "t", (10, 3): "S"}
    for (px, py), k in head.items():
        p.put(px, py, S[k])
    p.outline(OUTLINE)
    return p.icon()


# ---------------------------------------------------------------- 持ち方

def weapon_display(kind):
    d = {
        "sword": ([0, -75, 5], [1.13, 1.5, 1.13], 0.55, 0.75, 3.0),
        "greatsword": ([0, -75, 10], [1.13, 1.0, 1.13], 0.45, 0.7, 2.0),
        "staff": ([0, -80, 5], [1.13, 0.5, 1.13], 0.48, 0.8, 2.25),
    }[kind]
    rot, tr, fs, ts, ty = d
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, ty, 0.5], "scale": [ts] * 3},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, ty, 0.5], "scale": [ts] * 3},
        "firstperson_righthand": {"rotation": rot, "translation": tr, "scale": [fs] * 3},
        "firstperson_lefthand": {"rotation": [rot[0], -rot[1], -rot[2]], "translation": tr, "scale": [fs] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }


SHIELD_FIRST = {"rotation": [-8, 165, 6], "translation": [12, 0, -12], "scale": [0.95, 0.95, 0.95]}
SHIELD_BLOCK = {"rotation": [0, 180, -5], "translation": [6, 5, -12], "scale": [1.05, 1.05, 1.05]}
SHIELD_GUI = {"rotation": [15, -25, -5], "translation": [3.5, 3, 0], "scale": [0.66, 0.66, 0.66]}


def build_all():
    items = []
    for build, kind, icon, label, note in (
            (build_sword, "sword", icon_sword, "古代石の剣", "苔鉄の芯を、石の板2枚で挟んだ刃\n（横から見ると石・鉄・石の3層）\n鍔は石のまぐさ、柄頭は小さな道標"),
            (build_greatsword, "greatsword", icon_greatsword, "古代石の大剣", "先ほど広く重い石の板\n左に苔鉄の背、右がのみで削った刃\n文字の溝と、根元の結晶の欠片"),
            (build_staff, "staff", icon_staff, "古代石の杖", "苔鉄の芯に、古代石の輪を積んだ柄\n先は小さな道標の柱\n表と裏の窓に、結晶の欠片"),
            (build_shield, "shield", None, "古代石の盾", "石の板を苔鉄の帯で挟んだ\n上下のとがった六角形\nまん中に見張りの「目」の溝")):
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(AncientPainter(m), frames=1)
        items.append({"kind": kind, "model": m, "frames": frames, "tex": frames[0],
                      "icon": icon() if icon else None, "label": label, "note": note})
    return items


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_ancient"
    os.makedirs(out, exist_ok=True)
    items = build_all()
    for it in items:
        m = it["model"]
        if it["kind"] == "shield":
            normal, blocking = weapons.shield_display(SHIELD_FIRST, SHIELD_BLOCK, third_scale=0.95, gui=SHIELD_GUI)
            weapons.export_shield(repo, "ancient_stone_shield", m, it["frames"], CREDIT, normal, blocking)
            it["gui"] = SHIELD_GUI
        else:
            weapons.export_weapon(repo, m.name, m, it["frames"], it["icon"], it["kind"], CREDIT,
                                  display=weapon_display(it["kind"]))
    from setsheet import make_set_sheet
    R = RAMPS
    rows = [{"label": it["label"], "rarity": "コモン（工房で作る。強化・品質で上がる）", "note": it["note"],
             "model": it["model"], "tex": it["tex"], "icon": it["icon"], "gui": it.get("gui")} for it in items]
    sw, gs, st, sh = (it["model"] for it in items)
    tx = {it["model"].name: it["tex"] for it in items}
    details = [
        {"label": "剣：石・鉄・石の3層の刃", "model": sw, "tex": tx[sw.name], "center": (8, 13, 8), "yaw": -60, "pitch": 10,
         "scale": 22, "note": "苔鉄の芯が刃のふちになる"},
        {"label": "剣：石のまぐさの鍔と結晶", "model": sw, "tex": tx[sw.name], "center": (8, 5.5, 8), "yaw": -25, "pitch": 12,
         "scale": 30, "note": "帯のリベットと、光らない結晶の欠片"},
        {"label": "大剣：先の重い石の板", "model": gs, "tex": tx[gs.name], "center": (8.5, 26, 8), "yaw": -30, "pitch": 12,
         "scale": 18, "note": "苔鉄の背と、のみで削った刃"},
        {"label": "杖：道標の柱の窓", "model": st, "tex": tx[st.name], "center": (8, 18.5, 8), "yaw": -30, "pitch": 14,
         "scale": 26, "note": "窓の奥に結晶の欠片"},
    ]
    palette = [
        ("主：古代石（灰青色）", [R["stone"][i] for i in (1, 2, 3, 4, 5, 6)]),
        ("副：苔鉄（芯・帯・背）と苔", [R["iron"][i] for i in (1, 2, 3, 4, 5)] + [(75, 97, 64)]),
        ("差し色：結晶の欠片（光らない）", [R["crystal_dim"][i] for i in (2, 3, 4, 5)]),
        ("握り：ツノモフの毛の紐", [R["wool"][i] for i in (2, 3, 4, 5)]),
    ]
    sheet_path = os.path.join(repo, "docs", "gear", "ancient_stone_sheet.png")
    make_set_sheet(sheet_path, "古代石シリーズ（工房で作る、古代の見張りの装備の写し）",
                   "コモン・光る部分なし｜共通：灰青色の石と道標の文字の溝・苔鉄の芯と帯・光らない結晶の欠片・道標の段々の先",
                   rows, details=details, palette=palette)
    import shutil
    shutil.copy(sheet_path, os.path.join(out, "ancient_stone_sheet.png"))


if __name__ == "__main__":
    main()
