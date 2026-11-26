"""カジノの地下遊技場と、2階の新台の間の台の並び（11月27日【補完】）。

地図（tools/mapgen/build_casino.py の地下遊技場・新台の間）と、データ（tools/datagen/build_parlor.py が書く casino/default.json の
"parlor"）の両方がこの並びを使う。座標はカジノと同じ：建物の中の (u, v)（u 0 = 世界の x 13、v 0 = 世界の z 72）と、道からの高さ y。

  地下遊技場（はじめから。開場の日から遊べる）：床のブロック −10、足もと −9、天井 −4。1階の北の側廊の西（休み場だった所。
  u 8..11、v 1..5）の折り返しの階段で下りる。島（台の列）は南北に4つ（西の2つがパチンコ、東の2つがスロット）、1つの島に12台
  （背中合わせの2列 × 6台）。北の端に島のデータの掲示板、南に交換所の窓口
  2階の新台の間（共同事業⑥「カジノの新台入替」のあと）：宴の間の真ん中に、島を2つ（12台ずつ）

台番号は本物の店と同じく、4と9のつく番号を使わない（縁起）。
"""

BX, BZ = 13, 72                  # 建物の北西の角（u 0, v 0）の世界の x・z
B_FLOOR, B_FOOT, B_CEIL = -10, -9, -4   # 地下遊技場の床・足もと・天井
F2_FOOT = 10                     # 2階の足もと

# 島：ID・名前・機種・2列の u（西の列は西向き、東の列は東向き）・台の v の範囲・足もとの高さ・階（base：地下・new：2階）・最初の台番号
ISLANDS = [
    ("b1", "輝きの塔", "p_tower", (4, 5), (9, 14), B_FOOT, "base", 1),
    ("b2", "結び目の森 甘", "p_forest", (10, 11), (9, 14), B_FOOT, "base", 16),
    ("b3", "ノットランプ", "s_knot", (17, 18), (9, 14), B_FOOT, "base", 101),
    ("b4", "星詠みの塔", "s_star", (23, 24), (9, 14), B_FOOT, "base", 116),
    ("n1", "沈黙の湖 LT", "p_lake", (10, 11), (9, 14), F2_FOOT, "new", 201),
    ("n2", "月影の大鐘", "s_moon", (16, 17), (9, 14), F2_FOOT, "new", 301),
]

# 地下遊技場の交換所の窓口（玉とメダルをチップに戻す。右クリックでカジノの画面の「両替」）：南の壁ぎわの台
COUNTER = ((11, 22), (18, 22))   # u 11..18、v 22（台の高さは足もと）


def lucky(n):
    """4と9のつかない台番号か"""
    return "4" not in str(n) and "9" not in str(n)


def numbers(start, count):
    out = []
    n = start
    while len(out) < count:
        if lucky(n):
            out.append(n)
        n += 1
    return out


def machines():
    """台の一覧：台番号・機種・島・(u, v)・台の高さ（足もと + 1。足もとには台の下の受け皿）・向き・階"""
    out = []
    for (iid, name, model, (uw, ue), (v0, v1), foot, floor, start) in ISLANDS:
        cells = [(uw, v, "west") for v in range(v0, v1 + 1)] + [(ue, v, "east") for v in range(v1, v0 - 1, -1)]
        nos = numbers(start, len(cells))
        for no, (u, v, facing) in zip(nos, cells):
            out.append({"no": no, "model": model, "island": iid, "u": u, "v": v, "y": foot + 1, "facing": facing,
                        "floor": floor})
    return out


def boards():
    """島の北の端の掲示板（2マス。島のデータを見る）：(島, u, v, 高さ)"""
    out = []
    for (iid, name, model, (uw, ue), (v0, v1), foot, floor, start) in ISLANDS:
        for u in (uw, ue):
            out.append((iid, u, v0 - 1, foot + 1))
    return out


def world(u, v):
    return BX + u, BZ + v
