"""祈願（ガチャ）の祈願札と、ガチャ限のアクセサリーの持ち物の絵（11月27日）。

  python3 tools/artgen/make_gacha_art.py <リポジトリ> [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only <下の NAMES>   （32×32 にする）

祈願札（8枚）【補完】：細い紐で下げる縦長の札。ふちと紋は、その階の祈願の色。紋は階ごと
  （1 草原＝二本の角・2 森＝若葉・3 水路＝しずく・4 神殿＝鈴・5 祭壇＝翠の立方体・6 雪村＝雪の結晶・7 氷林＝鏡のひし形・8 湖＝真珠）。
  紋の下に、祈りの文字の二行。すそに、祈願の色の房。
ガチャ限のアクセサリー（16。レジェンダリー。どれも体には出さない指輪とお守り）【補完】：
  指輪は、少し見下ろした輪と上の飾り（tools/artgen/make_floor_accessory_art.py の指輪と同じ形）。お守りは、紐で下げる形を品ごとに描く。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from materials import hexc  # noqa: E402

OUTLINE = "#14161A"

# ---------------------------------------------------------------- 祈願札

TAG = [
    "................",
    ".......cc.......",
    "......c..c......",
    "....BBBBBBBB....",
    "....BWWWWWWB....",
    "....BW....WB....",
    "....BW....WB....",
    "....BW....WB....",
    "....BW....WB....",
    "....BWWWWWWB....",
    "....BWkkkkWB....",
    "....BWWWWWWB....",
    "....BWkkkWWB....",
    "....BBBBBBBB....",
    ".....t.tt.t.....",
    "................",
]

EMBLEMS = {
    1: ["E..E", "E..E", ".EE.", ".ee."],      # 二本の角
    2: ["..EE", ".EeE", "EeE.", "EE.."],      # 若葉
    3: ["..E.", ".EEE", "EeEE", ".EE."],      # しずく
    4: [".ee.", ".EE.", "EEEE", ".e.."],      # 鈴
    5: ["EEEE", "EeeE", "EeeE", "EEEE"],      # 立方体
    6: ["E..E", ".ee.", ".ee.", "E..E"],      # 雪の結晶
    7: [".EE.", "EeeE", "EeeE", ".EE."],      # 鏡のひし形
    8: [".EE.", "EeEE", "EEEE", ".EE."],      # 真珠
}

# (ID, 階, ふちの色, 紋の色, 紋の明るい色, 紙の色, 房の色)
TOKENS = [
    ("wish_tag_grassland", 1, "#9A7426", "#C9962E", "#FFE7A8", "#F4ECD6", "#E2B65A"),
    ("wish_tag_forest", 2, "#3F7A2C", "#5FAE4A", "#C8F5B0", "#EEF4E2", "#7ED957"),
    ("wish_tag_waterway", 3, "#7A5228", "#2E7EC2", "#B6E4FA", "#EAF2F6", "#4FB6F0"),
    ("wish_tag_temple", 4, "#A87C1E", "#E2B65A", "#FFF2C0", "#F8F4EA", "#F2C94C"),
    ("wish_tag_altar", 5, "#5D4322", "#2BAA81", "#B8F4DC", "#F1EFE8", "#4FCC9E"),
    ("wish_tag_village", 6, "#5E7890", "#7FC8F0", "#FFFFFF", "#F2F6FA", "#BFEFFF"),
    ("wish_tag_iceforest", 7, "#6C5AA8", "#9A7CF0", "#F0E8FF", "#F3F2FA", "#C9A8FF"),
    ("wish_tag_lake", 8, "#3E7C96", "#5FA8C8", "#F4F2EB", "#EEF6F8", "#A8F7FF"),
]


def token_art(floor, pal):
    rows = [list(r) for r in TAG]
    emblem = EMBLEMS[floor]
    for dy, row in enumerate(emblem):
        for dx, ch in enumerate(row):
            if ch != ".":
                rows[5 + dy][6 + dx] = ch
            else:
                rows[5 + dy][6 + dx] = "W"
    return ["".join(r) for r in rows], pal


# ---------------------------------------------------------------- アクセサリー：指輪

def ring_rows(top):
    return [
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


RINGS = {
    # 1階：草笛（緑の葉を巻いた金の輪に、草笛の筒）
    "reed_whistle_ring": (["....GgGGgGG.....", ".....GkGkG......", "......SSS......."],
                          {"B": "#E8C35A", "b": "#B88A34", "d": "#7A5A1C", "S": "#5FAE4A", "G": "#B8E07A", "g": "#7AAE48",
                           "k": "#3E6A24"}),
    # 2階：苔衣（樹皮の輪に、苔のかたまり）
    "moss_cloak_ring": ([".....gGGGg......", "....gGmGmGg.....", ".....gGGGg......"],
                        {"B": "#A57750", "b": "#6C4930", "d": "#3A2618", "S": "#4C644B", "G": "#7ED957", "g": "#3F7A2C",
                         "m": "#C8F5B0"}),
    # 3階：歯車（緑青の青銅の輪に、真鍮の歯車）
    "cogwheel_ring": (["....G.GGG.G.....", ".....GgOgG......", "....G.GGG.G....."],
                      {"B": "#7EB0A0", "b": "#4A786C", "d": "#2A4A42", "S": "#AD8247", "G": "#E2C385", "g": "#AD8247",
                       "O": "#5A4416"}),
    # 4階：残響（銀の輪に、紫の音符の石）
    "echo_note_ring": ([".......vV.......", "......VvV.......", ".....VVV........"],
                       {"B": "#E6EAF0", "b": "#AEB4BE", "d": "#6A707A", "S": "#E2B65A", "V": "#B58CFF", "v": "#F0E6FF"}),
    # 5階：結び目（白い石の輪に、翠の格子）
    "knot_lattice_ring": (["....qQqQqQ......", "....QfQfQq......", "....qQqQqQ......"],
                          {"B": "#F3F1EA", "b": "#C4BEB2", "d": "#7F786D", "S": "#B68D50", "Q": "#2BAA81", "q": "#8CE8C3",
                           "f": "#D3FFEE"}),
    # 6階：霜編み（白と水色の編み目の輪に、雪の結晶）
    "frost_weave_ring": (["....i..i..i.....", ".....iIiIi......", "....i..i..i....."],
                         {"B": "#F2F6FA", "b": "#7FC8F0", "d": "#3E6E9A", "S": "#BFEFFF", "I": "#FFFFFF", "i": "#9CC8E8"}),
    # 7階：鏡片（白銀の輪に、プリズムのかけら）
    "mirror_shard_ring": ([".......Pp.......", "......PpPa......", ".....aPPpPa....."],
                          {"B": "#E6EDF4", "b": "#AEBBCC", "d": "#6C7A8E", "S": "#9A7CF0", "P": "#F0F4FF", "p": "#C9A8FF",
                           "a": "#8FF0FF"}),
    # 8階：波紋（真珠色の輪に、水の波紋）
    "ripple_ring": (["....rRrRrRr.....", ".....RpPpR......", "....rRrRrRr....."],
                    {"B": "#F4F2EB", "b": "#C8D0D8", "d": "#76808C", "S": "#5FA8C8", "R": "#A8F7FF", "r": "#5FA8C8",
                     "p": "#F4F2EB", "P": "#FFFFFF"}),
}

# ---------------------------------------------------------------- アクセサリー：お守り

CHARMS = {
    # 1階：陽だまり（木の枠の中の、丸い琥珀。下に麦の房）
    "sunny_spot_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        ".......ww.......",
        ".....wWWWWw.....",
        "....wWAAAAWw....",
        "....WAaAAAAW....",
        "....WAAAAAAW....",
        "....WAAAAAOW....",
        "....wWAAAOWw....",
        ".....wWWWWw.....",
        ".......ff.......",
        "......f.ff......",
        "......f..f......",
        "................",
        "................",
    ], {"c": "#7A5A1C", "w": "#6C4930", "W": "#A57750", "A": "#E8AA35", "a": "#FFF0B0", "O": "#A36714", "f": "#E2C576"}),
    # 2階：森灯（葉の形の器の中の、迷い灯の光）
    "forest_light_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        ".......LL.......",
        "......LllL......",
        ".....LlYYlL.....",
        "....LlYyyYlL....",
        "....LlYyyYlL....",
        ".....LlYYlL.....",
        "......LllL......",
        ".......LL.......",
        ".......k........",
        "......k.........",
        "................",
        "................",
        "................",
    ], {"c": "#3F7A2C", "L": "#3F7A2C", "l": "#7ED957", "Y": "#FFD46A", "y": "#FFF6D0", "k": "#523723"}),
    # 3階：澄み水（青銅の小瓶に、青い水。栓は真鍮）
    "clear_water_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        ".......GG.......",
        ".......gg.......",
        "......BBBB......",
        ".....B....B.....",
        "....B.wWWw.B....",
        "....BwWWWWwB....",
        "....BWWwWWWB....",
        "....BWWWWWWB....",
        ".....BWWWWB.....",
        "......BBBB......",
        "................",
        "................",
        "................",
    ], {"c": "#2A4A42", "G": "#E2C385", "g": "#AD8247", "B": "#4A786C", "W": "#4F9ED8", "w": "#B6E4FA"}),
    # 4階：鈴音（金の鈴と、紫の房）
    "bell_tone_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        ".......GG.......",
        "......GggG......",
        ".....GgGGgG.....",
        ".....GgGGgG.....",
        "....GgGGGGgG....",
        "....GGGGGGGG....",
        "...hhhhhhhhhh...",
        ".......VV.......",
        "......V..V......",
        "......v..v......",
        "................",
        "................",
        "................",
    ], {"c": "#7A520E", "G": "#E8B23C", "g": "#FFF0B0", "h": "#A36F16", "V": "#B58CFF", "v": "#7A66C4"}),
    # 5階：記録核（真鍮の枠の中の、翠の立方体）
    "record_core_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        ".......bb.......",
        ".....bBBBBb.....",
        ".....B.QQ.B.....",
        ".....BQfqQB.....",
        ".....BQqQQB.....",
        ".....B.QQ.B.....",
        ".....bBBBBb.....",
        ".......bb.......",
        ".......Q........",
        "................",
        "................",
        "................",
        "................",
    ], {"c": "#5A4416", "b": "#A28232", "B": "#DEC06A", "Q": "#2BAA81", "q": "#8CE8C3", "f": "#D3FFEE"}),
    # 6階：暖炉火（霜銀の小さな灯籠の中の火）
    "hearth_fire_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        "......SSSS......",
        ".....SsssSs.....",
        "......S..S......",
        "......SoOS......",
        "......SOyS......",
        "......SoOS......",
        ".....SSSSSS.....",
        "......ssss......",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"c": "#4A5668", "S": "#CFD8E4", "s": "#8C9AAE", "o": "#E8822E", "O": "#FFB45A", "y": "#FFF0B0"}),
    # 7階：薄明（夜の色の枠に、薄紫と水色の光のひし形）
    "twilight_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        ".......nn.......",
        "......nPPn......",
        ".....nPppPn.....",
        "....nPpaapPn....",
        "....nPpaapPn....",
        ".....nPppPn.....",
        "......nPPn......",
        ".......nn.......",
        ".......a........",
        "................",
        "................",
        "................",
        "................",
    ], {"c": "#2A3448", "n": "#38445C", "P": "#C9A8FF", "p": "#E8DCFF", "a": "#8FF0FF"}),
    # 8階：真珠（霧の糸で包んだ灰色の真珠）
    "pearl_charm": ([
        "................",
        ".......cc.......",
        "......c..c......",
        ".......tt.......",
        "......tPPt......",
        ".....tPpPPt.....",
        ".....PPPPPP.....",
        ".....tPPPPt.....",
        "......tPPt......",
        ".......tt.......",
        "......t..t......",
        ".....t....t.....",
        "................",
        "................",
        "................",
        "................",
    ], {"c": "#76808C", "t": "#AEB8C2", "P": "#DEDCD4", "p": "#FFFFFF"}),
}


def paint(rows, pal, outline=True):
    from PIL import Image
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    filled = set()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), (*hexc(pal[ch]), 255))
                filled.add((x, y))
    if outline:
        line = hexc(OUTLINE)
        for (x, y) in list(filled):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in filled and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    img.putpixel(q, (*line, 255))
    return img


def all_art():
    out = {}
    for tid, floor, border, emblem, light, paper, tassel in TOKENS:
        pal = {"c": "#5A4A3A", "B": border, "W": paper, "E": emblem, "e": light, "k": "#6A6052", "t": tassel}
        rows, pal = token_art(floor, pal)
        out[tid] = (rows, pal)
    for name, (top, pal) in RINGS.items():
        out[name] = (ring_rows(top), pal)
    for name, (rows, pal) in CHARMS.items():
        out[name] = (rows, pal)
    return out


NAMES = [t[0] for t in TOKENS] + list(RINGS) + list(CHARMS)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru/textures/item")
    art = all_art()
    for name, (rows, pal) in art.items():
        paint(rows, pal).save(os.path.join(base, name + ".png"))
    if out:
        os.makedirs(out, exist_ok=True)
        sheet(base, os.path.join(out, "gacha_icons.png"))
    print("絵", len(art), ",".join(NAMES))


def sheet(base, path):
    from PIL import Image, ImageDraw
    k = 6
    cols = 8
    rows = (len(NAMES) + cols - 1) // cols
    img = Image.new("RGBA", (cols * (16 * k + 8) + 8, rows * (16 * k + 8) + 8), (40, 44, 56, 255))
    for i, name in enumerate(NAMES):
        icon = Image.open(os.path.join(base, name + ".png")).convert("RGBA")
        icon = icon.resize((16 * k, 16 * k), Image.NEAREST)
        x = 8 + (i % cols) * (16 * k + 8)
        y = 8 + (i // cols) * (16 * k + 8)
        img.alpha_composite(icon, (x, y))
    ImageDraw.Draw(img)
    img.save(path)


if __name__ == "__main__":
    main()
