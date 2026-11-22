"""Phase 6 その2：弾の見た目（矢・針）の仮素材を作る。

textures/entity/arrow_bolt.png（32×8）：光る矢。右が矢じり、左が羽根。
textures/entity/needle_bolt.png（32×8）：細い毒針。右が先。
どちらも LampBoltRenderer が、飛ぶ向きにのばして4面に貼る（光るので、明るい色ほど強く見える）。

使い方：python3 tools/artgen/make_phase6_bolts.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/amemaru/textures/entity"


def arrow() -> Image.Image:
    img = Image.new("RGBA", (32, 8), (0, 0, 0, 0))
    px = img.load()
    # 柄：真ん中の2行。後ろほど薄い
    for x in range(4, 27):
        t = (x - 4) / 22.0
        alpha = int(150 + 105 * t)
        px[x, 3] = (230, 244, 255, alpha)
        px[x, 4] = (200, 228, 255, alpha)
        if x % 3 == 0:
            px[x, 2] = (170, 210, 255, int(alpha * 0.35))
            px[x, 5] = (170, 210, 255, int(alpha * 0.35))
    # 矢じり（右。白く強く）
    head = {26: (2, 5), 27: (2, 5), 28: (2, 5), 29: (3, 4), 30: (3, 4), 31: (3, 4)}
    for x, (top, bottom) in head.items():
        for y in range(top, bottom + 1):
            core = y in (3, 4)
            px[x, y] = (255, 255, 255, 255) if core else (210, 240, 255, 230)
    px[25, 1] = (180, 225, 255, 160)
    px[25, 6] = (180, 225, 255, 160)
    # 羽根（左。淡い緑）
    for x in range(0, 7):
        spread = max(0, 3 - x // 2)
        for y in range(3 - spread, 5 + spread):
            if 0 <= y < 8:
                edge = y in (3 - spread, 4 + spread)
                px[x, y] = (190, 255, 215, 200 if edge else 140)
    return img


def needle() -> Image.Image:
    img = Image.new("RGBA", (32, 8), (0, 0, 0, 0))
    px = img.load()
    for x in range(8, 32):
        t = (x - 8) / 23.0
        alpha = int(90 + 165 * t)
        green = (170, 255, 120, alpha)
        px[x, 3] = green
        px[x, 4] = (140, 230, 100, int(alpha * 0.8))
    for x in range(28, 32):
        px[x, 3] = (245, 255, 235, 255)
    # 先からしたたる毒の粒
    px[14, 5] = (150, 255, 110, 120)
    px[20, 5] = (150, 255, 110, 150)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    arrow().save(OUT / "arrow_bolt.png")
    needle().save(OUT / "needle_bolt.png")
    print("wrote", OUT / "arrow_bolt.png", OUT / "needle_bolt.png")


if __name__ == "__main__":
    main()
