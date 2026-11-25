"""8階「沈黙の湖」の仕掛けのブロック・ボスのレア「湖主の髭弓」・状態のアイコン・歌い手の姿の絵とモデルを作る（11月25日【補完】）。

  python3 tools/artgen/make_lake_things.py [リポジトリ] [確認画像の出力先]

ブロック：
  lake_ice           湖の氷：灰青の透きとおった氷（半透明。氷の下を泳ぐ大きな影が見える）。白い細い筋と、ところどころ閉じこめられた泡
  thin_ice_0〜3      薄氷：湖の氷より白く薄い。ひび（crack）が 0〜3 で増える（3 は割れる寸前。白い割れ目が網の目に）
  lake_bell          湖の鐘：静氷の台に、霜銀の枠。まん中に、古い青銅の鐘（緑青）と、青い声石の舌
  lake_bell_lit      （鳴った）鐘が淡い金色に光り、舌の声石が明るい水色に光る
  song_stele         歌の碑：灰白の石の碑。表に五線のような横の刻みと、淡い水色に光る音のしるし
  lake_tablet        湖底の祠の石板：低い台に立てた、丸い頭の石板。歌の刻みと、まん中に青く光る音のしるし
品：
  lake_lord_whisker_bow  湖主の髭弓（8階のボスのレア。エピック）：霜銀の弓の形を、湖の主の白い鱗の色に塗り替え、弦と握りの石と
                         弓の先を、ムーネのひげの光る水色にする（光る部品は forge_data の明るさ15）。持ち物の絵（32×32）も同じ
状態のアイコン（18×18）：
  mob_effect/lost_sever    断線：二人の胸像のあいだの赤い糸が、まん中で切れている
  mob_effect/lost_silence  静寂：灰白の鐘に、斜めの線（鳴らない）
人の姿（村人と同じ 64×64 の肌。make_village_things.py の PEOPLE）：
  textures/entity/village/singer.png・singer_statue.png  最後の歌い手（長い黒髪・白い衣・青い肩掛け）
"""
import colorsys
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from make_iceforest_things import ICE7, IceThingPainter, write_json  # noqa: E402
from materials import hsh, pick, ramp  # noqa: E402

CREDIT = "AMEMARU: 8階の仕掛け（tools/artgen/make_lake_things.py で生成）"
LAKE8 = {
    "silent_stone": ramp("#4E5662", "#666E7A", "#7E8692", "#98A0AA", "#B2B9C2", "#CBD1D8", "#E2E6EA"),
    "old_bronze": ramp("#1E2E2A", "#2C423C", "#3C5850", "#4E7064", "#628A7A", "#7CA494", "#9CC0B0"),
    "glow_bell": ramp("#9A7A2A", "#C09A3A", "#DDBB55", "#F2D77A", "#FBEAA8", "#FFF6D6", "#FFFFFF"),
    "voice": ramp("#1E3C9A", "#2A52BA", "#3E6CD2", "#5E8CE4", "#88AEF0", "#B6D0F8", "#E6F0FF"),
    "glow_voice": ramp("#2A7A9A", "#3E98B8", "#5AB6D2", "#80CEE4", "#AAE2F2", "#D4F2FA", "#FFFFFF"),
    "glow_song": ramp("#3E8AA4", "#58A8C2", "#7EC4DA", "#A8DCEC", "#C8EAF4", "#E4F6FA", "#FFFFFF"),
}


class LakeThingPainter(IceThingPainter):
    def paint_silent_stone(self, c):
        tone = self.std(c, "silent_stone", base=3.8, noise=0.25, blob=0.4, up=1.1, down=1.0, specks=0.03)
        tone += self.grooves(c)
        if c.el.tags.get("staff") and abs(c.n[2]) > 0.6:
            # 歌の碑の表：五線のような横の刻み（4本）
            v = (c.j + 0.5) / c.H
            u = (c.i + 0.5) / c.W
            if 0.12 < u < 0.88 and any(abs(v - y) < 0.035 for y in (0.22, 0.34, 0.46, 0.58)):
                tone -= 1.5
        return pick(LAKE8["silent_stone"], tone)

    def paint_old_bronze(self, c):
        tone = self.std(c, "old_bronze", base=3.4, noise=0.3, blob=0.6, up=1.4, down=1.0)
        if self.blob(c, 1.2, 501) < 0.25:
            tone += 0.8   # 緑青の明るい斑
        return pick(LAKE8["old_bronze"], tone)

    def paint_glow_bell(self, c):
        tone = 4.4 + self.bevel(c, up=1.2, down=0.4) + (self.tex(c, 511) - 0.5) * 0.4
        return pick(LAKE8["glow_bell"], tone)

    def paint_voice(self, c):
        tone = 3.4 + self.bevel(c, up=1.6, down=0.8)
        return pick(LAKE8["voice"], tone)

    def paint_glow_voice(self, c):
        tone = 4.6 + self.bevel(c, up=1.2, down=0.4)
        return pick(LAKE8["glow_voice"], tone)

    def paint_glow_song(self, c):
        tone = 4.6 + self.bevel(c, up=1.0, down=0.4)
        return pick(LAKE8["glow_song"], tone)

    def paint_song_mark(self, c):
        """音のしるし（板）：まるい頭と、たての棒と、旗（音符の形）。まわりは透明"""
        u = (c.i + 0.5) / c.W
        v = (c.j + 0.5) / c.H
        head = (u - 0.38) ** 2 / 0.03 + (v - 0.74) ** 2 / 0.02 < 1.0
        stem = abs(u - 0.55) < 0.07 and 0.18 < v < 0.74
        flag = 0.55 < u < 0.82 and abs(v - (0.2 + (u - 0.55) * 0.6)) < 0.07
        if not (head or stem or flag):
            return None
        return pick(LAKE8["glow_song"], 4.4 + (self.tex(c, 521) - 0.5) * 0.6)


def box(m, name, frm, to, mat, **kw):
    return m.box(name, frm, to, mat, **kw)


# ---------------------------------------------------------------- ブロック（立体）

def build_lake_bell(lit):
    m = JavaModel("lake_bell_lit" if lit else "lake_bell", 64, 64)
    box(m, "base", (2, 0, 4), (14, 2, 12), "silent_stone", tags={"grooves": [1.0]})
    box(m, "base_top", (3, 2, 5), (13, 2.5, 11), "silent_stone")
    for k, x in enumerate((3, 11.5)):
        box(m, f"post_{k}", (x, 2.5, 7), (x + 1.5, 15, 9), "frost_silver")
    box(m, "beam", (2, 15, 6.5), (14, 16, 9.5), "frost_silver")
    box(m, "hanger", (7.5, 13.5, 7.5), (8.5, 15, 8.5), "frost_silver")
    bell = "glow_bell" if lit else "old_bronze"
    box(m, "bell_top", (6.5, 12.5, 6.5), (9.5, 13.5, 9.5), bell, glow=lit)
    box(m, "bell", (5.5, 8.5, 5.5), (10.5, 12.5, 10.5), bell, glow=lit)
    box(m, "bell_lip", (5, 7.5, 5), (11, 8.5, 11), bell, glow=lit)
    tongue = "glow_voice" if lit else "voice"
    box(m, "tongue", (7.5, 5.5, 7.5), (8.5, 7.5, 8.5), tongue, glow=lit)
    return m


def build_song_stele():
    m = JavaModel("song_stele", 64, 64)
    box(m, "plinth", (1, 0, 4), (15, 2, 12), "silent_stone", tags={"grooves": [1.0]})
    box(m, "slab", (2.5, 2, 6), (13.5, 14, 10), "silent_stone", tags={"staff": True})
    box(m, "crown", (3.5, 14, 6.5), (12.5, 15.5, 9.5), "silent_stone")
    box(m, "crown_top", (5.5, 15.5, 7), (10.5, 16, 9), "silent_stone")
    # 表（南）の五線の上に、光る音符を4つ（だんだん高くなる旋律）。裏（北）にも2つ
    notes(m, "s", [(4.0, 6.6), (6.4, 8.0), (8.8, 9.4), (11.0, 10.8)], z=10, facing="south")
    notes(m, "n", [(10.5, 7.3), (7.5, 9.4)], z=6, facing="north")
    return m


def notes(m, side, heads, z, facing):
    """面（z）の上に、光る音符（まるい頭の箱と、右の棒）を並べる"""
    for k, (x, y) in enumerate(heads):
        if facing == "south":
            z0, z1 = z, z + 0.4
        else:
            z0, z1 = z - 0.4, z
        box(m, "note_%s%d" % (side, k), (x, y, z0), (x + 1.5, y + 1, z1), "glow_song", glow=True)
        sx = x + 1.0 if facing == "south" else x
        box(m, "stem_%s%d" % (side, k), (sx, y + 1, z0), (sx + 0.5, y + 3, z1), "glow_song", glow=True)


def build_lake_tablet():
    m = JavaModel("lake_tablet", 64, 64)
    box(m, "base", (1, 0, 3), (15, 2, 13), "silent_stone", tags={"grooves": [1.0]})
    box(m, "tablet", (3, 2, 7), (13, 12, 9), "silent_stone", tags={"staff": True})
    box(m, "tablet_top", (4, 12, 7), (12, 13, 9), "silent_stone")
    box(m, "tablet_cap", (5.5, 13, 7), (10.5, 14, 9), "silent_stone")
    notes(m, "s", [(4.2, 5.6), (7.2, 7.0), (10.0, 8.4)], z=9, facing="south")
    return m


BLOCKS = [lambda: build_lake_bell(False), lambda: build_lake_bell(True), build_song_stele, build_lake_tablet]


# ---------------------------------------------------------------- ブロック（立方体：湖の氷・薄氷）

def ice_texture(seed, base_alpha, light, crack):
    """16×16 の氷の絵。light=1 で白く薄い（薄氷）。crack 0〜3 で、白い割れ目が増える"""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    deep = np.array([118, 140, 166], dtype=float)
    pale = np.array([214, 226, 238], dtype=float)
    for y in range(16):
        for x in range(16):
            t = 0.28 + light * 0.4 + (hsh(x, y, seed) - 0.5) * 0.1 + (hsh(x // 4, y // 4, seed + 1) - 0.5) * 0.12
            # 斜めの、細い白い筋（音を吸いこんだ跡）
            if (x * 2 + y + seed) % 11 == 0 and hsh(x, y, seed + 2) < 0.7:
                t += 0.32
            # 閉じこめられた泡
            if hsh(x, y, seed + 3) > 0.985:
                t += 0.5
            t = max(0.0, min(1.0, t))
            rgb = deep * (1 - t) + pale * t
            alpha = base_alpha + int(t * 40)
            img[y, x] = (*np.clip(rgb, 0, 255).astype(np.uint8), min(255, alpha))
    # 枠（となりの氷との境を、ほんの少し明るく）
    for k in range(16):
        for (x, y) in ((k, 0), (0, k)):
            img[y, x, :3] = np.minimum(255, img[y, x, :3].astype(int) + 18)
    if crack:
        # 割れ目：まん中から外へのびる白い線（crack が増えるほど本数と長さが増え、3 では枝分かれする）
        rays = [(1, 0.35), (-1, 0.8), (0.4, -1), (-0.7, -0.6), (0.9, 0.9), (-0.2, 1)]
        count = (2, 4, 6)[crack - 1]
        length = (5, 7, 9)[crack - 1]
        cx, cy = 7.5, 8.0
        for k in range(count):
            dx, dy = rays[k]
            n = (dx * dx + dy * dy) ** 0.5
            dx, dy = dx / n, dy / n
            x, y = cx, cy
            for s in range(length):
                wob = (hsh(k, s, seed + 9) - 0.5) * 0.9
                x += dx + wob * -dy * 0.5
                y += dy + wob * dx * 0.5
                ix, iy = int(round(x)), int(round(y))
                if 0 <= ix < 16 and 0 <= iy < 16:
                    img[iy, ix] = (246, 250, 254, 235)
                if crack == 3 and s == length // 2:
                    # 枝
                    bx, by = x, y
                    for _ in range(3):
                        bx += dy * 0.9 + dx * 0.4
                        by += -dx * 0.9 + dy * 0.4
                        jx, jy = int(round(bx)), int(round(by))
                        if 0 <= jx < 16 and 0 <= jy < 16:
                            img[jy, jx] = (236, 242, 250, 225)
        if crack >= 2:
            img[int(cy), int(cx)] = (255, 255, 255, 245)
    return Image.fromarray(img, "RGBA")


def cube_model(texture, render_type):
    return {"parent": "minecraft:block/cube_all", "render_type": render_type, "textures": {"all": texture}}


# ---------------------------------------------------------------- 湖主の髭弓（霜銀の弓の塗り替え）

def _hsv(h, s, v):
    return colorsys.hsv_to_rgb(h / 360.0, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))


def recolor_pixel(rgb, kind):
    r, g, b = rgb
    _, ss, vv = colorsys.rgb_to_hsv(r, g, b)
    if kind == "whisker":
        return _hsv(188, 0.62 - vv * 0.38, 0.5 + vv * 0.5)
    if kind == "grip":
        return _hsv(214, 0.28, 0.16 + vv * 0.55)
    if kind == "band":
        return _hsv(210, 0.07, 0.36 + vv * 0.62)
    # 湖の主の白い鱗の色（灰白に、ほんの少し青）
    return _hsv(208, 0.06 + ss * 0.2, min(1.0, 0.18 + vv * 0.9))


ELEMENT_KIND = {"string": "whisker", "rest_gem": "whisker", "nock_top": "whisker", "nock_bottom": "whisker",
                "tip_top": "whisker", "tip_bottom": "whisker", "grip": "grip", "grip_plate": "grip",
                "band_0": "band", "band_1": "band"}


def bow_model_texture(src, three):
    a = np.asarray(src.convert("RGBA")).astype(float) / 255.0
    out = a.copy()
    done = np.zeros(a.shape[:2], dtype=bool)
    tw, th = three.get("texture_size", [64, 64])
    sx, sy = a.shape[1] / tw, a.shape[0] / th
    for e in three["elements"]:
        kind = ELEMENT_KIND.get(e.get("name"), "scale")
        for face in e.get("faces", {}).values():
            u0, v0, u1, v1 = face["uv"]
            x0, x1 = sorted((int(round(u0 / 16 * tw * sx)), int(round(u1 / 16 * tw * sx))))
            y0, y1 = sorted((int(round(v0 / 16 * th * sy)), int(round(v1 / 16 * th * sy))))
            for y in range(y0, max(y1, y0 + 1)):
                for x in range(x0, max(x1, x0 + 1)):
                    if y >= a.shape[0] or x >= a.shape[1] or done[y, x] or a[y, x, 3] == 0:
                        continue
                    out[y, x, :3] = recolor_pixel(a[y, x, :3], kind)
                    done[y, x] = True
    # どの面にも当たらない画素も、鱗の色へ
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            if not done[y, x] and a[y, x, 3] > 0:
                out[y, x, :3] = recolor_pixel(a[y, x, :3], "scale")
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def bow_icon(src):
    """持ち物の絵（32×32）：右の弓の体は鱗の色、握りの石と弓の先は光る水色。左上から斜めに入る矢は、光るひげの筋にする"""
    a = np.asarray(src.convert("RGBA")).astype(float) / 255.0
    out = a.copy()
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            r, g, b, al = a[y, x]
            if al == 0:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r, g, b)
            arrow = -3 <= x - y <= 4 and y <= 21 and x <= 21
            if ss >= 0.22 and 170 <= hh * 360 <= 250:
                out[y, x, :3] = recolor_pixel((r, g, b), "whisker")
            elif arrow:
                out[y, x, :3] = recolor_pixel((r, g, b), "whisker") if vv > 0.1 else _hsv(196, 0.5, 0.22)
            elif vv > 0.82 and (y <= 5 or y >= 24):
                out[y, x, :3] = recolor_pixel((r, g, b), "whisker")   # 弓の先
            else:
                out[y, x, :3] = recolor_pixel((r, g, b), "scale")
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def whisker_bow(base):
    tex = os.path.join(base, "textures/item")
    item = os.path.join(base, "models/item")
    with open(os.path.join(item, "frost_silver_bow_3d.json"), encoding="utf-8") as fp:
        three = json.load(fp)
    model_tex = bow_model_texture(Image.open(os.path.join(tex, "frost_silver_bow_model.png")), three)
    model_tex.save(os.path.join(tex, "lake_lord_whisker_bow_model.png"))
    icon = bow_icon(Image.open(os.path.join(tex, "frost_silver_bow.png")))
    icon.save(os.path.join(tex, "lake_lord_whisker_bow.png"))
    three["credit"] = CREDIT
    three["textures"] = {"0": "amemaru:item/lake_lord_whisker_bow_model", "particle": "amemaru:item/lake_lord_whisker_bow_model"}
    lit = 0
    for e in three["elements"]:
        if ELEMENT_KIND.get(e.get("name")) == "whisker":
            e["forge_data"] = {"block_light": 15, "sky_light": 15}
            lit += 1
        else:
            e.pop("forge_data", None)
    write_json(os.path.join(item, "lake_lord_whisker_bow_3d.json"), three)
    with open(os.path.join(item, "frost_silver_bow.json"), encoding="utf-8") as fp:
        flat = fp.read()
    write_json(os.path.join(item, "lake_lord_whisker_bow.json"), json.loads(flat.replace("frost_silver_bow", "lake_lord_whisker_bow")))
    return model_tex, icon, lit


# ---------------------------------------------------------------- 状態のアイコン

def effect_lost_sever():
    from make_phase2_art import Canvas, hexc
    c = Canvas(18, 18)
    body = hexc("D8E0EA")
    # 左と右の二人の胸像
    for x0 in (0, 11):
        c.ellipse(x0 + 1, 3, x0 + 5, 7, fill=body)
        c.poly([(x0, 15), (x0 + 1, 11), (x0 + 3, 9), (x0 + 5, 11), (x0 + 6, 15)], body)
    # 二人をつなぐ赤い糸が、まん中で切れている（切れ端が下へ垂れる）
    red = hexc("D04040")
    for (x, y) in ((6, 11), (7, 11), (8, 12), (8, 13), (11, 11), (10, 11), (9, 10), (9, 9)):
        c.px(x, y, red)
    c.px(9, 8, hexc("F4F0E8"))
    c.outline_alpha()
    return c.img


def effect_lost_silence():
    from make_phase2_art import Canvas, hexc
    c = Canvas(18, 18)
    bell = hexc("C8D0D8")
    dark = hexc("76808C")
    c.rect(8, 2, 9, 3, dark)
    c.poly([(5, 13), (6, 6), (8, 4), (10, 4), (12, 6), (13, 13)], bell)
    c.rect(3, 13, 14, 14, dark)
    c.rect(8, 15, 9, 16, hexc("3E6CD2"))
    # 鳴らない：左上から右下への斜めの線
    line = hexc("2A3448")
    for k in range(15):
        c.px(1 + k, 1 + k, line)
        if k < 14:
            c.px(2 + k, 1 + k, line)
    c.outline_alpha()
    return c.img


# ---------------------------------------------------------------- 書き出し

def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    built = []
    for build in BLOCKS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        tex = m.paint(LakeThingPainter(m), frames=1)[0]
        Image.fromarray(tex, "RGBA").save(os.path.join(base, "textures/block", m.name + ".png"))
        m.save_json(os.path.join(base, "models/block", m.name + ".json"), "amemaru:block/" + m.name, CREDIT,
                    extra={"parent": "minecraft:block/block", "render_type": "minecraft:cutout"})
        built.append((m, tex))
    # 湖の氷・薄氷（半透明の立方体）
    ice_texture(801, 150, 0.0, 0).save(os.path.join(base, "textures/block/lake_ice.png"))
    write_json(os.path.join(base, "models/block/lake_ice.json"), cube_model("amemaru:block/lake_ice", "minecraft:translucent"))
    for k in range(4):
        ice_texture(811, 120, 1.0, k).save(os.path.join(base, "textures/block/thin_ice_%d.png" % k))
        write_json(os.path.join(base, "models/block/thin_ice_%d.json" % k),
                   cube_model("amemaru:block/thin_ice_%d" % k, "minecraft:translucent"))
    write_json(os.path.join(base, "blockstates/lake_ice.json"), {"variants": {"": {"model": "amemaru:block/lake_ice"}}})
    write_json(os.path.join(base, "blockstates/thin_ice.json"),
               {"variants": {"crack=%d" % k: {"model": "amemaru:block/thin_ice_%d" % k} for k in range(4)}})
    facing_y = {"north": 0, "east": 90, "south": 180, "west": 270}
    bell = {}
    for f, y in facing_y.items():
        for lit in (False, True):
            v = {"model": "amemaru:block/lake_bell" + ("_lit" if lit else "")}
            if y:
                v["y"] = y
            bell["facing=%s,lit=%s" % (f, "true" if lit else "false")] = v
    write_json(os.path.join(base, "blockstates/lake_bell.json"), {"variants": bell})
    # 碑と石板の表は南向き（facing=south で回さない）
    south_y = {"south": 0, "west": 90, "north": 180, "east": 270}
    for name in ("song_stele", "lake_tablet"):
        variants = {}
        for f, y in south_y.items():
            v = {"model": "amemaru:block/" + name}
            if y:
                v["y"] = y
            variants["facing=" + f] = v
        write_json(os.path.join(base, "blockstates", name + ".json"), {"variants": variants})
    for name, model in (("lake_ice", "lake_ice"), ("thin_ice", "thin_ice_0"), ("lake_bell", "lake_bell"), ("song_stele", "song_stele"),
                        ("lake_tablet", "lake_tablet")):
        write_json(os.path.join(base, "models/item", name + ".json"), {"parent": "amemaru:block/" + model})
    model_tex, icon, lit = whisker_bow(base)
    effect_lost_sever().save(os.path.join(base, "textures/mob_effect/lost_sever.png"))
    effect_lost_silence().save(os.path.join(base, "textures/mob_effect/lost_silence.png"))
    # 最後の歌い手（記憶の氷像）
    from make_village_things import PEOPLE, person
    vdir = os.path.join(base, "textures/entity/village")
    person("singer", PEOPLE["singer"], False).save(os.path.join(vdir, "singer.png"))
    person("singer", PEOPLE["singer"], True).save(os.path.join(vdir, "singer_statue.png"))
    if out:
        check(base, built, model_tex, icon, out)
    print("ok（湖主の髭弓の光る部品：%d）" % lit)


def check(base, built, model_tex, icon, out):
    from PIL import ImageDraw
    from render import render_many
    from make_forest_models import sheet_font
    os.makedirs(out, exist_ok=True)
    tiles = []
    for m, tex in built:
        for yaw, pitch in ((150, 22), (-30, 22)):
            tiles.append(render_many([(m, tex)], yaw=yaw, pitch=pitch, scale=13, size=(250, 270), center=(8, 8, 8)))
    cols = 8
    rows = (len(tiles) + cols - 1) // cols
    s = Image.new("RGBA", (250 * cols, 270 * rows + 24 + 170), (52, 60, 70, 255))
    for k, t in enumerate(tiles):
        s.alpha_composite(t, (250 * (k % cols), 24 + 270 * (k // cols)))
    y = 24 + 270 * rows
    x = 10
    for name in ["lake_ice"] + ["thin_ice_%d" % k for k in range(4)]:
        im = Image.open(os.path.join(base, "textures/block", name + ".png")).convert("RGBA").resize((128, 128), Image.NEAREST)
        bg = Image.new("RGBA", (128, 128), (20, 30, 50, 255))
        bg.alpha_composite(im)
        s.alpha_composite(bg, (x, y + 10))
        x += 140
    s.alpha_composite(icon.convert("RGBA").resize((128, 128), Image.NEAREST), (x, y + 10))
    s.alpha_composite(model_tex.convert("RGBA").resize((128, 128), Image.NEAREST), (x + 140, y + 10))
    for k, eff in enumerate((effect_lost_sever(), effect_lost_silence())):
        s.alpha_composite(eff.convert("RGBA").resize((108, 108), Image.NEAREST), (x + 290 + k * 120, y + 20))
    for k, name in enumerate(("singer", "singer_statue")):
        im = Image.open(os.path.join(base, "textures/entity/village", name + ".png")).convert("RGBA").resize((128, 128), Image.NEAREST)
        s.alpha_composite(im, (x + 540 + k * 140, y + 10))
    ImageDraw.Draw(s).text((6, 2), "湖の鐘・鳴った鐘・歌の碑・湖底の石板（斜め前・斜め後ろ）／湖の氷・薄氷0〜3（暗い水の上）・湖主の髭弓の絵とモデルの絵・"
                                   "状態「断線」「静寂」・歌い手の肌と氷像", fill=(235, 235, 220), font=sheet_font(15))
    s.save(os.path.join(out, "lake_things_check.png"))


if __name__ == "__main__":
    main()
