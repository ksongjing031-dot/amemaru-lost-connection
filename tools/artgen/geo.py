"""GeckoLib（Bedrock 形式）のモデル・アニメーション・絵を、コードで組み立てる小さな道具。

仮素材（Phase 0-B 12章「単色の箱に名前を書いた絵」を、形の分かる箱の組み合わせにしたもの）を作るために使う。
モデルを作る人は、同じファイル名・同じボーンの名前で Blockbench のモデルに置き換えれば、コードを変えずに差し替えられる。

  Model(name, width, height)
    .bone(name, pivot, parent=None, rotation=None)
    .cube(bone, origin, size, color, glow=None, inflate=0, pattern=None)
  model.save(root, kind)  →  geo/<kind>/<name>.geo.json と textures/<kind>/<name>.png（光る部分は _glowmask.png）
  anim(name, {"idle": {...}, ...}).save(root, kind)  →  animations/<kind>/<name>.animation.json
"""
import json
import os
import random

from PIL import Image


def rgb(hex_color):
    hex_color = hex_color.lstrip('#')
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


class Model:
    def __init__(self, name, tex_w=64, tex_h=64, bounds=(2.0, 2.5, 1.0), seed=1):
        self.name = name
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.bounds = bounds
        self.bones = []
        self.bone_index = {}
        self.cubes = []  # (bone, cube)
        self.rand = random.Random(seed)

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        b["cubes"] = []
        self.bones.append(b)
        self.bone_index[name] = b
        return self

    def cube(self, bone, origin, size, color, glow=None, inflate=0.0, pattern=None, rotation=None, pivot=None):
        """size は整数（箱のUVで絵の場所を決めるため）。glow を指定すると、その色で光る（_glowmask）。"""
        size = [int(round(s)) for s in size]
        c = {"origin": list(origin), "size": size, "_color": rgb(color) if isinstance(color, str) else color,
             "_glow": (rgb(glow) if isinstance(glow, str) else glow) if glow else None, "_pattern": pattern}
        if inflate:
            c["inflate"] = inflate
        if rotation:
            c["rotation"] = list(rotation)
            c["pivot"] = list(pivot if pivot else origin)
        self.bone_index[bone]["cubes"].append(c)
        self.cubes.append(c)
        return self

    # ---------------------------------------------------------------- 絵（箱のUVを詰めて、面ごとに色を塗る）

    def _pack(self):
        """箱のUV（幅 2(d+w)、高さ d+h）を、上から順に棚に詰める。"""
        items = sorted(self.cubes, key=lambda c: -(c["size"][2] + c["size"][1]))
        x, y, shelf = 0, 0, 0
        for c in items:
            w, h, d = c["size"]
            need_w = 2 * (d + w)
            need_h = d + h
            if need_w > self.tex_w:
                raise ValueError(f"{self.name}: cube too wide for texture: {c['size']}")
            if x + need_w > self.tex_w:
                x = 0
                y += shelf
                shelf = 0
            if y + need_h > self.tex_h:
                raise ValueError(f"{self.name}: texture {self.tex_w}x{self.tex_h} too small")
            c["uv"] = [x, y]
            x += need_w
            shelf = max(shelf, need_h)

    def _paint(self):
        base = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        glow = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        bp = base.load()
        gp = glow.load()
        for c in self.cubes:
            u, v = c["uv"]
            w, h, d = c["size"]
            col = c["_color"]
            faces = [
                ((u + d, v), (w, d), 1.12),            # 上
                ((u + d + w, v), (w, d), 0.70),        # 下
                ((u, v + d), (d, h), 0.86),            # 右
                ((u + d, v + d), (w, h), 1.00),        # 前（north）
                ((u + d + w, v + d), (d, h), 0.86),    # 左
                ((u + 2 * d + w, v + d), (w, h), 0.80),  # 後ろ
            ]
            for (fx, fy), (fw, fh), light in faces:
                for yy in range(fh):
                    for xx in range(fw):
                        px, py = fx + xx, fy + yy
                        if px >= self.tex_w or py >= self.tex_h:
                            continue
                        f = light
                        pattern = c["_pattern"]
                        noise = self.rand.uniform(-0.06, 0.06)
                        if pattern == "fluff":
                            noise = self.rand.uniform(-0.10, 0.08)
                        elif pattern == "stone":
                            noise = self.rand.choice([-0.12, -0.04, 0.0, 0.0, 0.05])
                        elif pattern == "crystal":
                            noise = 0.18 if (xx + yy) % 4 == 0 else (-0.05 if (xx - yy) % 5 == 0 else 0.0)
                        elif pattern == "metal":
                            noise = 0.10 if yy == 0 else (-0.08 if yy == fh - 1 else 0.0)
                        # 面のふちを少し暗くして、箱の形を読みやすくする
                        edge = (xx == 0 or yy == 0 or xx == fw - 1 or yy == fh - 1) and fw > 2 and fh > 2
                        if edge and pattern != "crystal":
                            f *= 0.88
                        r, g, b = shade(col, f + noise)
                        a = 255 if len(col) < 4 else col[3]
                        bp[px, py] = (r, g, b, a)
                        if c["_glow"]:
                            gr, gg, gb = c["_glow"][:3]
                            gp[px, py] = (gr, gg, gb, 255)
        return base, glow

    def save(self, root, kind):
        self._pack()
        base, glow = self._paint()
        geo = {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.{self.name}",
                    "texture_width": self.tex_w,
                    "texture_height": self.tex_h,
                    "visible_bounds_width": self.bounds[0],
                    "visible_bounds_height": self.bounds[1],
                    "visible_bounds_offset": [0, self.bounds[2], 0]
                },
                "bones": []
            }]
        }
        for b in self.bones:
            out = {k: v for k, v in b.items() if k != "cubes"}
            cubes = []
            for c in b["cubes"]:
                cubes.append({k: v for k, v in c.items() if not k.startswith("_")})
            if cubes:
                out["cubes"] = cubes
            geo["minecraft:geometry"][0]["bones"].append(out)
        geo_path = os.path.join(root, "geo", kind, self.name + ".geo.json")
        os.makedirs(os.path.dirname(geo_path), exist_ok=True)
        with open(geo_path, "w", encoding="utf-8") as f:
            json.dump(geo, f, indent=1)
            f.write("\n")
        tex_path = os.path.join(root, "textures", kind, self.name + ".png")
        os.makedirs(os.path.dirname(tex_path), exist_ok=True)
        base.save(tex_path)
        if any(c["_glow"] for c in self.cubes):
            glow.save(os.path.join(root, "textures", kind, self.name + "_glowmask.png"))
        return geo_path


def anim(name, animations):
    """animations：{"idle": {"loop": True, "length": 2.0, "bones": {...}}, ...}"""
    out = {"format_version": "1.8.0", "animations": {}}
    for key, a in animations.items():
        entry = {}
        if a.get("loop"):
            entry["loop"] = True if a["loop"] is True else a["loop"]
        entry["animation_length"] = a["length"]
        bones = {}
        for bone, channels in a.get("bones", {}).items():
            bones[bone] = {}
            for channel, frames in channels.items():
                if isinstance(frames, dict):
                    bones[bone][channel] = {f"{float(t):.4g}": list(v) for t, v in frames.items()}
                else:
                    bones[bone][channel] = list(frames)
        entry["bones"] = bones
        out["animations"][f"animation.{name}.{key}"] = entry
    return out


def save_anim(root, kind, name, animations):
    path = os.path.join(root, "animations", kind, name + ".animation.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(anim(name, animations), f, indent=1)
        f.write("\n")
    return path
