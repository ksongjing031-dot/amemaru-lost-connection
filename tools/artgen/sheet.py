"""装備のモデルの確認画像（正面・側面・背面・斜め・細部・シルエット・持ち物の枠・暗い所・色）を1枚にまとめる。

  make_sheet(model, texture, info, path)

info の例は make_floodgate_key_shield.py の SHEET を参照。すべてモデルのファイル（要素と絵）から描くので、
確認画像とゲームの中の見た目がずれない。
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from render import render, display_transform

FONT_REGULAR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONT_BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

BG = (30, 34, 41, 255)
PANEL = (40, 45, 54, 255)
PANEL_DARK = (12, 15, 21, 255)
TEXT = (222, 228, 235, 255)
SUB = (150, 160, 172, 255)
ACCENT = (110, 200, 240, 255)


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size, index=0)


def panel(sheet, box, label, dark=False):
    d = ImageDraw.Draw(sheet)
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, radius=10, fill=PANEL_DARK if dark else PANEL)
    d.text((x0 + 14, y0 + 8), label, font=font(20, True), fill=TEXT)


def fit(img, w, h):
    """透明なふちを切って、枠に収まるように置く（拡大はピクセルのまま）。"""
    a = np.array(img)[..., 3]
    ys, xs = np.nonzero(a > 8)
    if len(xs) == 0:
        return img
    img = img.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    k = min(w / img.width, h / img.height, 1.0)
    if k < 1.0:
        img = img.resize((max(1, int(img.width * k)), max(1, int(img.height * k))), Image.LANCZOS)
    return img


def paste_center(sheet, img, box):
    x0, y0, x1, y1 = box
    img = fit(img, x1 - x0, y1 - y0)
    sheet.alpha_composite(img, (x0 + (x1 - x0 - img.width) // 2, y0 + (y1 - y0 - img.height) // 2))


def make_sheet(model, tex, info, path):
    W = 1800
    H = 1500
    sheet = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((40, 26), info["title"], font=font(46, True), fill=TEXT)
    d.text((42, 92), info["subtitle"], font=font(22), fill=SUB)
    d.line((40, 132, W - 40, 132), fill=(70, 78, 90, 255), width=2)

    # ---- 1段目：正面・側面・背面・斜め前・斜め後ろ
    views = info.get("views", [
        ("正面", 0, 0), ("側面", -90, 0), ("背面", 180, 0), ("斜め前", -35, 18), ("斜め後ろ", 145, 18)])
    top = 150
    pw = (W - 80 - 4 * 16) // 5
    ph = 560
    for k, (label, yaw, pitch) in enumerate(views):
        x0 = 40 + k * (pw + 16)
        panel(sheet, (x0, top, x0 + pw, top + ph), label)
        img = render(model, tex, yaw=yaw, pitch=pitch, scale=info.get("scale", 19), size=(760, 760),
                     center=info.get("center", (0, 0, 0)))
        paste_center(sheet, img, (x0 + 10, top + 44, x0 + pw - 10, top + ph - 12))

    # ---- 2段目：細部
    top2 = top + ph + 18
    details = info["details"]
    n = len(details)
    dw = (W - 80 - (n - 1) * 16) // n
    dh = 360
    for k, det in enumerate(details):
        x0 = 40 + k * (dw + 16)
        panel(sheet, (x0, top2, x0 + dw, top2 + dh), det["label"])
        img = render(model, tex, yaw=det.get("yaw", 0), pitch=det.get("pitch", 0), scale=det.get("scale", 40),
                     size=det.get("size", (330, 290)), center=det["center"])
        sheet.alpha_composite(img, (x0 + (dw - img.width) // 2, top2 + 46))
        if det.get("note"):
            d.text((x0 + 14, top2 + dh - 34), det["note"], font=font(17), fill=SUB)

    # ---- 3段目：シルエット・持ち物の枠・暗い所・色
    top3 = top2 + dh + 18
    h3 = H - top3 - 30
    # シルエット（遠目でも形が分かるか）
    sw = 330
    panel(sheet, (40, top3, 40 + sw, top3 + h3), "シルエット（遠目）")
    for k, (yaw, sc) in enumerate(((0, 7), (-90, 7), (0, 2))):
        img = render(model, tex, yaw=yaw, scale=sc, size=(300, 300), ss=2, silhouette=(14, 16, 20))
        img = fit(img, 200, h3 - 70)
        sheet.alpha_composite(img, (60 + [0, 110, 210][k], top3 + 50 + (h3 - 70 - img.height) // 2))
    # 持ち物の枠（GUIの大きさ2）
    gx = 40 + sw + 16
    gw = 300
    panel(sheet, (gx, top3, gx + gw, top3 + h3), "持ち物の枠")
    gui = info["gui"]
    for k, px in enumerate((2, 4)):
        icon = render(model, tex, scale=px, size=(16 * px, 16 * px), ss=1, transform=display_transform(gui), light="gui")
        slot = Image.new("RGBA", (16 * px + 4 * px // 2, 16 * px + 4 * px // 2), (139, 139, 139, 255))
        sd = ImageDraw.Draw(slot)
        sd.rectangle((0, 0, slot.width - 1, slot.height - 1), outline=(55, 55, 55, 255), width=max(1, px // 2))
        slot.alpha_composite(icon, (px, px))
        big = slot.resize((slot.width * (3 if px == 2 else 1), slot.height * (3 if px == 2 else 1)), Image.NEAREST)
        sheet.alpha_composite(big, (gx + 20 + k * 130, top3 + 60))
    d.text((gx + 20, top3 + h3 - 58), "左：実際の大きさを3倍に拡大", font=font(16), fill=SUB)
    d.text((gx + 20, top3 + h3 - 34), "右：GUIの大きさ4", font=font(16), fill=SUB)
    # 暗い所
    nx = gx + gw + 16
    nw = 520
    panel(sheet, (nx, top3, nx + nw, top3 + h3), "暗い所（光るのは水・鍵穴・結晶だけ）", dark=True)
    for k, (yaw, pitch) in enumerate(((0, 0), (-35, 18))):
        img = render(model, tex, yaw=yaw, pitch=pitch, scale=9, size=(360, 360), darkness=0.2)
        img = fit(img, 230, h3 - 64)
        sheet.alpha_composite(img, (nx + 25 + k * 250, top3 + 48 + (h3 - 60 - img.height) // 2))
    # 色（3系統）
    cx = nx + nw + 16
    cw = W - 40 - cx
    panel(sheet, (cx, top3, cx + cw, top3 + h3), "色（3系統）")
    y = top3 + 50
    for label, colors in info["palette"]:
        d.text((cx + 16, y), label, font=font(18), fill=TEXT)
        for i, c in enumerate(colors):
            d.rectangle((cx + 16 + i * 34, y + 28, cx + 16 + i * 34 + 30, y + 52), fill=c + (255,))
        y += 66
    sheet.save(path)
    return sheet
