"""手に持つ装備（剣・大剣・杖・盾）を、Modの素材として書き出す共通の処理と、持ったときの向き（display）の基準。

武器（Java版のモデル）：
  models/item/<名前>_3d.json   … 部品と、手に持ったときの向き
  models/item/<名前>.json      … 持ち物の枠・額縁・地面ではドット絵、それ以外は立体（forge:separate_transforms）
  textures/item/<名前>_model.png … 立体の絵
  textures/item/<名前>.png       … 持ち物の枠のドット絵（16×16）

盾（Java版のモデル。持ち物の枠でも立体）：
  models/item/<名前>_model.json・<名前>.json（構えの切りかえ）・<名前>_blocking.json
  textures/item/<名前>_model.png

武器のモデルの置き方：刃（杖の先）が +y、握りのまん中が (8, 2, 8) あたり。表（刃の広い面）は ±z を向く。
盾のモデルの置き方：中心が (0, 0, 0)、表が +z、握りが −z 側（バニラの盾と同じ）。
"""
import json
import os

import numpy as np

from javamodel import save_frames, rot_matrix

# 武器の種類ごとの、手に持ったときの向き（Phase 3の立体の武器と同じ。ゲームの中の写真で確かめた値）
WEAPON_DISPLAY = {
    "sword": {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    },
}
WEAPON_DISPLAY["greatsword"] = json.loads(json.dumps(WEAPON_DISPLAY["sword"]))
WEAPON_DISPLAY["staff"] = json.loads(json.dumps(WEAPON_DISPLAY["sword"]))
for side in ("thirdperson_righthand", "thirdperson_lefthand"):
    WEAPON_DISPLAY["staff"][side]["translation"] = [0, 2, 0.5]


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def export_weapon(repo, name, model, frames, icon, kind, credit, display=None, frametime=4):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    models = os.path.join(base, "models/item")
    textures = os.path.join(base, "textures/item")
    tex_ref = "amemaru:item/" + name + "_model"
    model.save_json(os.path.join(models, name + "_3d.json"), tex_ref, credit,
                    display=display or WEAPON_DISPLAY[kind])
    icon_ref = "amemaru:item/" + name
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": icon_ref}}
    write_json(os.path.join(models, name + ".json"), {
        "loader": "forge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": "amemaru:item/" + name + "_3d"},
        "perspectives": {"gui": flat, "fixed": flat, "ground": flat},
    })
    save_frames(frames, os.path.join(textures, name + "_model.png"), frametime=frametime)
    if os.path.exists(os.path.join(textures, name + "_model.png.mcmeta")) and len(frames) == 1:
        os.remove(os.path.join(textures, name + "_model.png.mcmeta"))
    icon.save(os.path.join(textures, name + ".png"))


def right_from_left(left):
    """左手の置き方を、左右に写した右手の置き方にする（盾の中心がモデルの原点にある場合）。"""
    rx, ry, rz = left["rotation"]
    sx, sy, sz = left["scale"]
    R = rot_matrix("x", rx) @ rot_matrix("y", ry) @ rot_matrix("z", rz)
    shift = R @ np.array([sx, 0.0, 0.0]) * 16.0
    t = [round(float(left["translation"][i] + shift[i]), 2) for i in range(3)]
    return {"rotation": list(left["rotation"]), "translation": t, "scale": list(left["scale"])}


def shield_display(first_l, first_block_l, third_scale=0.9, gui=None, fixed=None):
    """盾の持ち方（ふだん・構え）。三人称はバニラの盾と同じ置き方。一人称は盾ごとに調整した左手の値から作る。"""
    third_l = {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [third_scale] * 3}
    normal = {
        "thirdperson_righthand": right_from_left(third_l),
        "thirdperson_lefthand": third_l,
        "firstperson_righthand": right_from_left(first_l),
        "firstperson_lefthand": first_l,
        "gui": gui or {"rotation": [15, -25, -5], "translation": [3, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": fixed or {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.5, 0.5, 0.5]},
        "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
    }
    third_bl = {"rotation": [45, 135, 0], "translation": [13.51, 3, 5], "scale": [third_scale] * 3}
    blocking = dict(normal)
    blocking.update({
        "thirdperson_righthand": right_from_left(third_bl),
        "thirdperson_lefthand": third_bl,
        "firstperson_righthand": right_from_left(first_block_l),
        "firstperson_lefthand": first_block_l,
    })
    return normal, blocking


def export_shield(repo, name, model, frames, credit, normal, blocking, frametime=4):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    models = os.path.join(base, "models/item")
    textures = os.path.join(base, "textures/item")
    tex_ref = "amemaru:item/" + name + "_model"
    model.save_json(os.path.join(models, name + "_model.json"), tex_ref, credit, extra={"gui_light": "front"})
    write_json(os.path.join(models, name + ".json"), {
        "parent": "amemaru:item/" + name + "_model", "display": normal,
        "overrides": [{"predicate": {"blocking": 1}, "model": "amemaru:item/" + name + "_blocking"}]})
    write_json(os.path.join(models, name + "_blocking.json"), {
        "parent": "amemaru:item/" + name + "_model", "display": blocking})
    save_frames(frames, os.path.join(textures, name + "_model.png"), frametime=frametime)
    mc = os.path.join(textures, name + "_model.png.mcmeta")
    if os.path.exists(mc) and len(frames) == 1:
        os.remove(mc)
