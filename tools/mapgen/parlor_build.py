"""カジノの地下遊技場（casino_parlor.nbt）と、2階の新台の間（共同事業⑥「カジノの新台入替」の重ねる形）を組み立てる
（11月27日のご指示「パチンコとスロットを追加する。結構な人が遊びそうなので少し多めに用意する。場所が足りない場合は地下なども用意する」
【補完】）。台の並びは tools/mapgen/parlor_layout.py（データの casino/default.json の "parlor" と同じ）。

地下遊技場（はじめから）
  - 1階の北の側廊の西（休み場だった所。u 8..11、v 1..4）の床をあけ、折り返しの階段（4段・4段・4段）で地下へ。1階の口に手すりと
    吊り看板「地下遊技場」。階段の下は、東（v 1..2）と南（u 8..9）の2つの口
  - 地下は u 0..29、v 0..25（建物の下いっぱい）。床 −10・足もと −9・天井 −4（白い天井に海のランタンの明かり）。壁は下が黒い石、
    上が白く、いちばん上に紫の帯（ラッキーノットの色）。通路は赤い絨毯、真ん中の通路は紫
  - 島4つ（西の2つがパチンコ、東の2つがスロット）。島の北の端に、島のデータの掲示板と、島の名前の札。台の前に丸い腰かけ
  - 北はロビー（階段の下・案内の看板）、南は交換所の窓口（u 11..18、v 22。玉とメダルをチップに戻す）と、東西の休み場

構造物の (0, 0, 0) は世界の (x 13, 道の高さ −11, z 72)。構造物の中では X = u、Y = y + 11、Z = v。カジノ（casino.nbt）の
あとに置き、1階の休み場の所と、階段の通る土台のマスを上書きする（カジノの形は変えない）。
"""
from mapgen.world import AIR, block, fence, lantern, sign_nbt, slab, stairs
import parlor_layout as lay

PX, PY, PZ = 13, -11, 72          # 構造物の (0, 0, 0) の世界の x・道からの y・z
PW, PH, PD = 30, 20, 26           # u 0..29、y −11..8、v 0..25
FL, FT, CE = lay.B_FLOOR, lay.B_FOOT, lay.B_CEIL
STAIR = (8, 11, 1, 5)             # 階段の所（u 8..11、v 1..5）
LOBBY_V = (1, 7)
REST_W = (1, 6, 18, 24)           # 西の休み場（u 1..6、v 18..24）
REST_E = (22, 28, 18, 24)         # 東の休み場（u 22..28、v 18..24）
CENTER_U = (13, 15)               # 真ん中の通路（紫の絨毯。パチンコとスロットの境）


class Parlor:
    def __init__(self, world, owned):
        self.w = world
        self.owned = owned

    def put(self, u, v, y, state, nbt=None):
        X, Y, Z = u, y - PY, v
        if 0 <= X < PW and 0 <= Y < PH and 0 <= Z < PD:
            self.w.put(X, Y, Z, state, nbt)
            self.owned.add((X, Y, Z))

    def get(self, u, v, y):
        return self.w.get(u, y - PY, v)

    def frame(self, u, v, y, facing, item, name=None):
        self.w.item_frame(u, y - PY, v, facing, item, name=name)
        self.owned.add((u, y - PY, v))


def model_kind(model):
    return "p" if model.startswith("p_") else "s"


def machine_states(model, facing):
    """台の下の受け皿と、台"""
    base = block("amemaru:parlor_base_" + model_kind(model), facing=facing)
    top = block("amemaru:parlor_" + model, facing=facing, lit=False)
    return base, top


def numbers_text(iid):
    nos = [m["no"] for m in lay.machines() if m["island"] == iid]
    return "%d〜%d番台" % (min(nos), max(nos))


# ---------------------------------------------------------------- 地下遊技場
def build_parlor(world, owned):
    p = Parlor(world, owned)
    shell(p)
    stairwell(p)
    lobby(p)
    for isl in lay.ISLANDS:
        if isl[6] == "base":
            island(p.put, isl, carpet_color="red")
    counter(p)
    rest(p, REST_W, "east")
    rest(p, REST_E, "west")
    walls_deco(p)
    carpets(p)
    return p


def wall_state(y):
    if y == FT:
        return block("polished_blackstone_bricks")
    if y == CE - 1:
        return block("purple_terracotta")
    return block("smooth_quartz")


def shell(p):
    """床・壁・天井（天井は白に海のランタン。島の上と通路の上に並べる）"""
    for u in range(PW):
        for v in range(PD):
            p.put(u, v, FL - 1, block("stone"))
            p.put(u, v, FL, block("polished_deepslate"))
            wall = u in (0, PW - 1) or v in (0, PD - 1)
            for y in range(FT, CE):
                p.put(u, v, y, wall_state(y) if wall else AIR)
            light = not wall and u % 3 == 1 and v % 3 == 1
            p.put(u, v, CE, block("sea_lantern") if light else block("smooth_quartz"))


def stairwell(p):
    """1階の休み場の床をあけて、折り返しの階段（u 8..11、v 1..5）。3段ずつの階段4つを、東の列（u 10..11）と西の列（u 8..9）で
    交互に下る：東を北へ → 北の踊り場で西へ → 西を南へ → 南の踊り場で東へ → 東を北へ → 北の踊り場で西へ → 西を南へ下りて地下の床。
    どの階段も、いちばん上の段は上の床（踊り場）と同じ高さ（上るときも跳ばなくてよい）。下りた所（v 5）から南の口（u 8..9、v 6）へ出る"""
    u0, u1, v0, v1 = STAIR
    # まず、階段の所の土台と床と1階の休み場を空ける（−9..6）
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            for y in range(FT, 7):
                p.put(u, v, y, AIR)
    # 1階の床：v 5 と、西の列（u 8..9）の上は残す（1階の口は東の列 u 10..11 だけ）。西の列の上は、働く人の扉（u 7、v 3）から
    # 休み場へ出る通り道。北の側廊と同じトウヒの板
    for u in range(u0, u1 + 1):
        p.put(u, 5, 2, block("spruce_planks"))
    for u in (8, 9):
        for v in range(v0, 5):
            p.put(u, v, 2, block("spruce_planks"))

    def flight(us, vs, top, facing):
        for i, v in enumerate(vs):
            for u in us:
                p.put(u, v, top - i, stairs("polished_deepslate", facing))

    def landing(v, y):
        for u in range(u0, u1 + 1):
            p.put(u, v, y, block("polished_deepslate"))

    # 1つ目（u 10..11、北へ下る）：段 2・1・0（足もと 3・2・1）、北の踊り場 −1（足もと 0）
    flight((10, 11), (4, 3, 2), 2, "south")
    landing(1, -1)
    # 2つ目（u 8..9、南へ下る）：段 −1・−2・−3、南の踊り場 −4（足もと −3）
    flight((8, 9), (2, 3, 4), -1, "north")
    landing(5, -4)
    # 3つ目（u 10..11、北へ下る）：段 −4・−5・−6、北の踊り場 −7（足もと −6）
    flight((10, 11), (4, 3, 2), -4, "south")
    landing(1, -7)
    # 4つ目（u 8..9、南へ下る）：段 −7・−8・−9、下（v 5）は地下の床（足もと −9）
    flight((8, 9), (2, 3, 4), -7, "north")
    # 地下の階段の囲い：西（u 7）・東（u 12）・南（v 6。u 8..9 は口）
    for v in range(v0, v1 + 1):
        for y in range(FT, CE):
            p.put(7, v, y, wall_state(y))
            p.put(12, v, y, wall_state(y))
    for u in range(u0, u1 + 1):
        for y in range(FT, CE):
            if u >= 10 or y > FT + 2:
                p.put(u, 6, y, wall_state(y))
    # 天井より上（−3..1。カジノの土台の土の中）は、階段の囲いを石レンガで張る（土が見えないように）
    for y in range(CE + 1, 2):
        for v in range(v0, v1 + 1):
            p.put(7, v, y, block("stone_bricks"))
            p.put(12, v, y, block("stone_bricks"))
        for u in range(u0, u1 + 1):
            p.put(u, v0 - 1, y, block("stone_bricks"))
            p.put(u, v1 + 1, y, block("stone_bricks"))
    # 看板（地下）：ロビーから見える東の囲いと、南の口の上
    p.put(13, 3, FT + 2, block("spruce_wall_sign", facing="east", waterlogged=False),
          sign_nbt(["", "1階へ", "（階段の口は南）", ""]))
    p.put(10, 7, FT + 2, block("spruce_wall_sign", facing="south", waterlogged=False),
          sign_nbt(["", "← 1階へ", "（カジノの大広間）", ""]))
    # 階段の明かり：土台の壁に海のランタンを埋める（地下・途中・1階の下）
    for (u, v, y) in ((7, 3, -7), (12, 4, -7), (7, 2, -1), (12, 3, 0), (7, 4, -4), (12, 2, -4)):
        p.put(u, v, y, block("sea_lantern"))
    # 1階の口の手すり（東の u 12 と、西の通り道との間の u 9。v 1..3。いちばん上の段の v 4 は通り道からも下りられる）と吊り看板
    for v in range(1, 5):
        p.put(12, v, 3, fence("dark_oak"))
    for v in range(1, 4):
        p.put(9, v, 3, fence("dark_oak"))
    p.put(10, 6, 7, block("chain", axis="y", waterlogged=False))      # 上の梁（y 8）から吊る
    p.put(10, 6, 6, block("spruce_hanging_sign", rotation=0, attached=False, waterlogged=False),
          sign_nbt(["地下遊技場", "パチンコ", "スロット", "↓"], ["地下遊技場", "パチンコ", "スロット", "↓"], hanging=True))


def lobby(p):
    """ロビー（北、v 1..7）：大きな看板、案内の看板、鉢植え"""
    # 北の壁の大看板（紫の地に金の文字。看板を並べる）
    for i, (u, text) in enumerate(((16, ["ラッキーノット"]), (17, ["地下遊技場"]), (18, ["パチンコ"]), (19, ["スロット"]))):
        p.put(u, 1, FT + 2, block("spruce_wall_sign", facing="south", waterlogged=False),
              sign_nbt(["", text[0], "", ""], color="purple", glow=True))
    for u in range(15, 21):
        p.put(u, 0, FT + 2, block("purple_concrete"))
        p.put(u, 0, FT + 3, block("gold_block"))
    # 案内（パチンコは西・スロットは東）
    p.put(13, 2, FT + 2, block("spruce_wall_sign", facing="south", waterlogged=False),
          sign_nbt(["← パチンコ", "輝きの塔", "結び目の森 甘", ""]))
    p.put(22, 1, FT + 2, block("spruce_wall_sign", facing="south", waterlogged=False),
          sign_nbt(["スロット →", "ノットランプ", "星詠みの塔", ""]))
    # 案内の柱（看板の後ろの支え。u 13、v 1 は3段）
    p.put(13, 1, FT + 2, block("polished_blackstone_bricks"))
    p.put(13, 1, FT + 1, block("polished_blackstone_bricks"))
    p.put(13, 1, FT, block("polished_blackstone_bricks"))
    p.put(13, 2, FT, block("polished_blackstone_bricks"))
    p.put(13, 2, FT + 1, block("polished_blackstone_bricks"))
    # 両替の案内（南の交換所へ）
    p.put(26, 1, FT + 2, block("spruce_wall_sign", facing="south", waterlogged=False),
          sign_nbt(["玉・メダルは", "チップ1枚で", "25玉・5枚", "交換所は南"]))
    for (u, v) in ((1, 1), (28, 1), (1, 7), (28, 7)):
        p.put(u, v, FT, block("potted_bamboo"))


def island(put, isl, carpet_color="red"):
    """島ひとつ：背中合わせの2列の台（受け皿・台）、台の前の腰かけ、北の端の掲示板と札、南の端の飾り"""
    (iid, name, model, (uw, ue), (v0, v1), foot, floor, start) = isl
    for v in range(v0, v1 + 1):
        for (u, facing, su) in ((uw, "west", uw - 1), (ue, "east", ue + 1)):
            base, top = machine_states(model, facing)
            put(u, v, foot, base)
            put(u, v, foot + 1, top)
            put(su, v, foot, block("amemaru:parlor_stool"))
    # 北の端：掲示板（島のデータ）と名前の札
    for u in (uw, ue):
        put(u, v0 - 1, foot, block("polished_blackstone_bricks"))
        put(u, v0 - 1, foot + 1, block("amemaru:parlor_board", facing="north"))
    kind = "パチンコ" if model.startswith("p_") else "スロット"
    put(uw, v0 - 1, foot + 2, block("spruce_sign", rotation=8, waterlogged=False),
        sign_nbt([kind, name, "", ""], color="white", glow=True))
    put(ue, v0 - 1, foot + 2, block("spruce_sign", rotation=8, waterlogged=False),
        sign_nbt(["", numbers_text(iid), "データは", "この掲示板"], color="white", glow=True))
    # 南の端：鉢植えと灯り
    for u in (uw, ue):
        put(u, v1 + 1, foot, block("polished_blackstone_bricks"))
    put(uw, v1 + 1, foot + 1, block("potted_bamboo"))
    put(ue, v1 + 1, foot + 1, lantern(False))


def counter(p):
    """交換所の窓口（南、u 11..18、v 22）：台と、奥の景品の棚（樽と額縁）、吊り看板"""
    (u0, vc), (u1, _) = lay.COUNTER
    for u in range(u0, u1 + 1):
        p.put(u, vc, FT, block("dark_oak_planks"))
        p.put(u, vc, FT + 1, slab("dark_oak", "bottom"))
        for v in range(vc + 1, 25):
            p.put(u, v, FT, AIR)
        # 奥の棚（v 24）：樽を2段、景品を額縁に
        p.put(u, 24, FT, block("barrel", facing="north", open=False))
        p.put(u, 24, FT + 1, block("barrel", facing="north", open=False))
    prizes = (("cookie", "ノットのクッキー"), ("honey_bottle", "蜂蜜の飲み物"), ("glow_berries", "光る木の実"),
              ("cake", "お祝いのケーキ"), ("pumpkin_pie", "かぼちゃのパイ"), ("sweet_berries", "甘い木の実"),
              ("golden_carrot", "金のにんじん"), ("melon_slice", "すいか"))
    for i, u in enumerate(range(u0, u1 + 1)):
        item, nm = prizes[i % len(prizes)]
        p.frame(u, 23, FT + 1, "north", item, name=nm)
    for u in (u0, u1):
        p.put(u, vc, CE - 1, block("chain", axis="y", waterlogged=False))
    p.put(14, vc - 1, CE - 1, block("spruce_hanging_sign", rotation=8, attached=False, waterlogged=False),
          sign_nbt(["交換所", "玉・メダル", "→ チップ", "（等価）"], ["交換所", "玉・メダル", "→ チップ", "（等価）"], hanging=True))
    p.put(15, vc - 1, CE - 1, block("spruce_hanging_sign", rotation=8, attached=False, waterlogged=False),
          sign_nbt(["窓口を", "右クリック", "", ""], ["窓口を", "右クリック", "", ""], hanging=True))


def rest(p, area, facing):
    """休み場：長椅子（壁ぎわ）と低い卓、鉢植え、飲み物の棚"""
    u0, u1, v0, v1 = area
    wall_u = u0 if facing == "east" else u1
    for v in range(v0 + 1, v1):
        p.put(wall_u, v, FT, stairs("dark_oak", "west" if facing == "east" else "east"))
    tu = wall_u + (2 if facing == "east" else -2)
    for v in (v0 + 2, v1 - 2):
        p.put(tu, v, FT, block("dark_oak_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False))
    p.put(tu, v0 + 3, FT, block("potted_azalea_bush"))
    # 飲み物の棚（白い箱に光るガラス）
    du = u1 if facing == "east" else u0
    p.put(du, v1, FT, block("white_concrete"))
    p.put(du, v1, FT + 1, block("light_blue_stained_glass"))
    p.put(du, v1, FT + 2, block("white_concrete"))


def walls_deco(p):
    """壁の飾り：機種の色の旗（台のポスター）を東西の壁に"""
    from mapgen.world import banner_nbt
    colors = (("blue", [("gra", "light_blue"), ("mc", "yellow")]), ("green", [("gra", "lime"), ("flo", "yellow")]),
              ("purple", [("gra", "magenta"), ("mr", "yellow")]), ("blue", [("gra", "cyan"), ("cre", "white")]))
    for i, v in enumerate((9, 11, 13, 15)):
        c, pat = colors[i % 2]
        p.put(1, v, FT + 2, block(c + "_wall_banner", facing="east"), banner_nbt(pat))
        c2, pat2 = colors[2 + i % 2]
        p.put(28, v, FT + 2, block(c2 + "_wall_banner", facing="west"), banner_nbt(pat2))


def carpets(p):
    """空いている床に絨毯（通路は赤、真ん中の通路は紫に金のふち、休み場は茶、ロビーは赤）"""
    for u in range(1, PW - 1):
        for v in range(1, PD - 1):
            if p.get(u, v, FT) is not None:
                continue
            if STAIR[0] <= u <= STAIR[1] and STAIR[2] <= v <= STAIR[3] + 1:
                continue
            if v >= 22 and lay.COUNTER[0][0] <= u <= lay.COUNTER[1][0]:
                continue
            if CENTER_U[0] <= u <= CENTER_U[1] and 8 <= v <= 20:
                color = "purple" if u == 14 else "yellow"
            elif (REST_W[0] <= u <= REST_W[1] or REST_E[0] <= u <= REST_E[1]) and v >= REST_W[2]:
                color = "brown"
            else:
                color = "red"
            p.put(u, v, FT, block(color + "_carpet"))


# ---------------------------------------------------------------- 2階の新台の間（共同事業⑥）
NEW_AREA = (9, 19, 7, 18)         # 書く所（u 9..19、v 7..18）。前の魚レースの所（u 9..19、v 9..17）を含む


def build_new_machines(put, base_get, foot):
    """共同事業⑥のあと：宴の間の真ん中を新台の間に。put(u, v, y, state, nbt) はカジノの重ねる形に書く道具、base_get(u, v, y) は
    はじめの形のブロック（前の魚レースの所を、はじめの形に戻してから置く）"""
    u0, u1, v0, v1 = NEW_AREA
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            for y in range(foot, foot + 4):
                st = base_get(u, v, y)
                put(u, v, y, st if st is not None else AIR)
    # 舞台の後ろの札（前の魚レースの札の所）も、はじめの形に
    for v in (12, 13):
        st = base_get(21, v, foot + 2)
        put(21, v, foot + 2, st if st is not None else AIR)
    for isl in lay.ISLANDS:
        if isl[6] == "new":
            island(put, isl, carpet_color="red")
    # 通路の絨毯（u 9..18、v 7..16）
    for u in range(u0, u1):
        for v in range(v0, 17):
            st = base_get(u, v, foot)
            if st is None and not any(u in (isl[3][0] - 1, isl[3][0], isl[3][1], isl[3][1] + 1)
                                      and isl[4][0] - 1 <= v <= isl[4][1] + 1 for isl in lay.ISLANDS if isl[6] == "new"):
                put(u, v, foot, block("red_carpet"))
    # 舞台の後ろの壁の札（新台入替）
    put(21, 12, foot + 2, block("spruce_wall_sign", facing="west", waterlogged=False),
        sign_nbt(["新台入替！", "沈黙の湖 LT", "月影の大鐘", ""], color="yellow", glow=True))
    put(21, 13, foot + 2, block("spruce_wall_sign", facing="west", waterlogged=False),
        sign_nbt(["パチンコ 201〜", "スロット 301〜", "台は右クリック", ""]))
