"""塔の階で共通に使う形（11月24日〜）。

- next_circle：ボスの部屋の「次の階への円」。出口の円（白い石英と灰色の縁。門へ戻る）と並べて置く。
  金の縁と黄色い砂岩の輪で、ひと目で出口の円と見分けられるようにする。真ん中は光る石（暗い部屋でも見つかる）。
"""
import math

from mapgen.world import block

# 出口の円の中心から、次の階への円の中心までの東向きのずれ（ブロック）。
# 描いた円の半径 3.3 が2つ並んでも重ならず、立つと移る範囲（半径 2.6）のあいだに 1.8 ブロックのすきまが空く
NEXT_DX = 7


def next_circle(put, cx, y, cz):
    """次の階への円を床に描く。put(x, y, z, block) は各階の置き方（f.put・f.solid）"""
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d <= 0.6:
                put(x, y, z, block("glowstone"))
            elif d <= 1.7:
                put(x, y, z, block("quartz_bricks"))
            elif d <= 2.7:
                put(x, y, z, block("cut_sandstone") if (x + z) % 2 == 0 else block("smooth_sandstone"))
            elif d <= 3.3:
                put(x, y, z, block("gold_block"))
