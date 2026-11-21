"""街の灯り（都市のルール⑦）：古代の結晶灯・壁のランタン・再建のランタン灯・鎖で吊るす灯り・ろうそく。

どの建物でも同じ形になるように、ここでまとめて作る。座標は World の (x, y, z)（x が東、y が上、z が南）。
out・arms の向きは "north"・"south"・"east"・"west"。
"""
from .world import block, fence, lantern, slab, stairs, wall

STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}

# 壁のランタンの材料：木（トウヒの家。濃い木の柵の腕木）と鉄（工房。横に渡した鎖を、鍛冶屋が打った細い鉄の腕木に見立てる）
STYLES = {
    "wood": {"arm": lambda out: fence("dark_oak"), "brace": lambda f: stairs("spruce", f, "top")},
    "iron": {"arm": lambda out: block("chain", axis="x" if out in ("east", "west") else "z", waterlogged=False),
             "brace": lambda f: stairs("stone_brick", f, "top")},
}


def chain_y():
    return block("chain", axis="y", waterlogged=False)


def wall_lantern(w, x, y, z, out, style="wood", reach=2, chain=1):
    """壁から出した腕木に吊るすランタン。(x, y, z) は壁のブロック（腕木の付け根）、y は腕木の高さ。

    形（out の向きへ）：腕木 reach 本（柵か鉄格子）、付け根の下に持ち送り（逆さの階段）、
    先から鎖 chain 本とランタン。ランタンの高さは y - 1 - chain。
    """
    dx, dz = STEP[out]
    st = STYLES[style]
    for i in range(1, reach + 1):
        w.put(x + dx * i, y, z + dz * i, st["arm"](out))
    w.put(x + dx, y - 1, z + dz, st["brace"](OPPOSITE[out]))
    ex, ez = x + dx * reach, z + dz * reach
    for j in range(1, chain + 1):
        w.put(ex, y - j, ez, chain_y())
    w.put(ex, y - 1 - chain, ez, lantern(True))


def lamp_post(w, x, y, z, arms=("south",), base="stone_brick", post="spruce", cap="dark_oak", chain=1):
    """再建のランタン灯。(x, y, z) は台の位置（地面の上）。

    形：石の塀の台1段、柵の柱4段、いちばん上の柱から arms の向きへ腕木（その下に持ち送り）、腕木の先から
    鎖 chain 本とランタン、柱の上に笠（ハーフ）。灯りは台から2段上（人の頭より上）。高さは6。
    """
    top = y + 4
    w.put(x, y, z, wall(base))
    for k in range(1, 5):
        w.put(x, y + k, z, fence(post))
    w.put(x, top + 1, z, slab(cap, "bottom"))
    for a in arms:
        dx, dz = STEP[a]
        w.put(x + dx, top, z + dz, fence(post))
        for j in range(1, chain + 1):
            w.put(x + dx, top - j, z + dz, chain_y())
        w.put(x + dx, top - 1 - chain, z + dz, lantern(True))


def hang_lantern(w, x, y, z, chain=1):
    """天井（y+1 のブロック）から鎖で吊るすランタン。(x, y, z) は鎖のいちばん上。ランタンは y - chain。"""
    for j in range(chain):
        w.put(x, y - j, z, chain_y())
    w.put(x, y - chain, z, lantern(True))


def candles(w, x, y, z, n=3, lit=True):
    """台の上のろうそく（1〜4本）。"""
    w.put(x, y, z, block("candle", candles=n, lit=lit, waterlogged=False))


def crystal_lamp(w, x, y, z, lit=True):
    """古代の結晶灯（大通り・中央広場・塔前広場・教会前広場。12ごとに左右互い違い）。(x, y, z) は台の位置（地面の上）。

    形：磨いた閃緑岩の台1段、石英の柱3段、灯の頭（点いている＝シーランタン、消えている＝遮光ガラス）、石英の半ブロックの笠。高さ6。
    襲撃の夜に消えた灯を、プレイヤーがともし直すときは、頭のブロックだけを入れ替える。
    """
    w.put(x, y, z, block("polished_diorite"))
    for k in range(1, 4):
        w.put(x, y + k, z, block("quartz_pillar", axis="y"))
    w.put(x, y + 4, z, block("sea_lantern") if lit else block("tinted_glass"))
    w.put(x, y + 5, z, slab("quartz", "bottom"))
