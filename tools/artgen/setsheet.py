"""シリーズ（何個かの装備）の確認画像を1枚にまとめる。1つの装備が1段：名前・正面・側面・背面・斜め・持ち物の枠。

  make_set_sheet(path, title, subtitle, items, details=[], palette=[], dark=False)

items の1つ：{"label": 名前, "note": 一言, "model": JavaModel, "tex": 絵, "icon": Icon か None, "gui": 盾の gui の display か None,
            "views": [(見出し, yaw, pitch), ...]（省略時は 正面・側面・背面・斜め）, "transform": 描く前の変換（任意）}
details の1つ：{"label", "model", "tex", "center", "yaw", "pitch", "scale", "note"}
"""
import numpy as np
from PIL import Image, ImageDraw

from render import render, display_transform
from sheet import font, panel, fit, BG, TEXT, SUB, PANEL, PANEL_DARK

DEFAULT_VIEWS = [("正面", 0, 0), ("側面", -90, 0), ("背面", 180, 0), ("斜め", -35, 16)]


def model_extent(model, transform=None):
    pts = []
    for el in model.elements:
        for f in el.faces.values():
            for c in f.corners_world():
                pts.append(transform[0](c) if transform else c)
    pts = np.array(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    return lo, hi


def make_set_sheet(path, title, subtitle, items, details=(), palette=(), row_h=380, dark=None):
    W = 1800
    head = 150
    det_h = 360 if details else 0
    pal_h = 170 if palette else 0
    H = head + len(items) * (row_h + 14) + (det_h + 18 if details else 0) + pal_h + 30
    sheet = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((40, 26), title, font=font(46, True), fill=TEXT)
    d.text((42, 92), subtitle, font=font(21), fill=SUB)
    d.line((40, 132, W - 40, 132), fill=(70, 78, 90, 255), width=2)

    y = head
    for it in items:
        views = it.get("views", DEFAULT_VIEWS)
        transform = it.get("transform")
        lo, hi = model_extent(it["model"], transform)
        center = tuple((lo + hi) / 2)
        size = hi - lo
        name_w = 250
        icon_w = 250
        avail_w = W - 80 - name_w - icon_w - 32
        vw = avail_w // len(views)
        # 1段に収まる大きさ（いちばん大きい向きで決める）
        extent = max(size[1], 1.0)
        width = max(size[0], size[2], 1.0)
        scale = min((row_h - 70) / extent, (vw - 20) / width, 26)
        panel(sheet, (40, y, W - 40, y + row_h), "")
        d.text((60, y + 18), it["label"], font=font(28, True), fill=TEXT)
        if it.get("rarity"):
            d.text((60, y + 58), it["rarity"], font=font(18), fill=it.get("rarity_color", SUB))
        yy = y + 92
        for line in it.get("note", "").split("\n"):
            d.text((60, yy), line, font=font(17), fill=SUB)
            yy += 26
        for k, (label, yaw, pitch) in enumerate(views):
            x0 = 40 + name_w + k * vw
            d.text((x0 + 10, y + 12), label, font=font(18, True), fill=SUB)
            img = render(it["model"], it["tex"], yaw=yaw, pitch=pitch, scale=scale, size=(vw * 2, row_h * 2),
                         center=center, transform=transform)
            img = fit(img, vw - 16, row_h - 50)
            sheet.alpha_composite(img, (x0 + (vw - img.width) // 2, y + 40 + (row_h - 50 - img.height) // 2))
        # 持ち物の枠
        ix = W - 40 - icon_w
        d.text((ix + 10, y + 12), "持ち物の枠", font=font(18, True), fill=SUB)
        slot_img = None
        if it.get("icon") is not None:
            slot_img = it["icon"].image()
        elif it.get("gui") is not None:
            slot_img = render(it["model"], it["tex"], scale=1, size=(16, 16), ss=1,
                              transform=display_transform(it["gui"]), light="gui")
        if slot_img is not None:
            big = Image.new("RGBA", (16 * 8 + 16, 16 * 8 + 16), (139, 139, 139, 255))
            ImageDraw.Draw(big).rectangle((0, 0, big.width - 1, big.height - 1), outline=(55, 55, 55, 255), width=4)
            big.alpha_composite(slot_img.resize((128, 128), Image.NEAREST), (8, 8))
            sheet.alpha_composite(big, (ix + 20, y + 50))
            real = Image.new("RGBA", (40, 40), (139, 139, 139, 255))
            ImageDraw.Draw(real).rectangle((0, 0, 39, 39), outline=(55, 55, 55, 255), width=2)
            real.alpha_composite(slot_img.resize((32, 32), Image.NEAREST), (4, 4))
            sheet.alpha_composite(real, (ix + 20, y + 205))
            d.text((ix + 70, y + 212), "実際の大きさ\n（GUIの大きさ2）", font=font(15), fill=SUB)
        y += row_h + 14

    if details:
        n = len(details)
        dw = (W - 80 - (n - 1) * 16) // n
        for k, det in enumerate(details):
            x0 = 40 + k * (dw + 16)
            panel(sheet, (x0, y, x0 + dw, y + det_h), det["label"], dark=det.get("dark", False))
            img = render(det["model"], det["tex"], yaw=det.get("yaw", 0), pitch=det.get("pitch", 0),
                         scale=det.get("scale", 30), size=(dw - 20, det_h - 90), center=det["center"],
                         transform=det.get("transform"), darkness=det.get("darkness"))
            sheet.alpha_composite(img, (x0 + 10, y + 44))
            if det.get("note"):
                d.text((x0 + 14, y + det_h - 34), det["note"], font=font(16), fill=SUB)
        y += det_h + 18

    if palette:
        panel(sheet, (40, y, W - 40, y + pal_h - 10), "色（3系統）")
        x = 60
        for label, colors in palette:
            d.text((x, y + 48), label, font=font(17), fill=TEXT)
            for i, c in enumerate(colors):
                d.rectangle((x + i * 30, y + 80, x + i * 30 + 26, y + 104), fill=c + (255,))
            x += max(230, len(colors) * 30 + 40, int(d.textlength(label, font=font(17))) + 30)
    sheet.save(path)
    return sheet
