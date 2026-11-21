"""街のNPCのアニメーション（GeckoLib）：idle と walk。make_npc_models.py から使う。

- 骨の向きは、モデルの初めの形（腕を下ろした形）からの角度。持ち物を構えた腕の形（model.pose）は、どのコマにも入れる。
- 頭の上下・左右は、ゲームの中で見ている人の方を向く（NpcGeoModel が毎コマ決める）ので、ここでは首のかしげ（z）だけ。
- まばたき：まぶた（eyelids）は、ふだん頭の中に隠れていて、まばたきの一瞬だけ顔の前に出る（z を −0.65）。
"""
from npcgen import kf


def _pose(model, bone):
    return list(model.pose.get(bone, (0, 0, 0)))


def _add(a, b):
    return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]


def _sway(model, bone, length, amp, phase=0.0, axis=2, steps=4):
    """骨を初めの形のまわりでゆっくり揺らす（axis：0 が x、2 が z）。"""
    base = _pose(model, bone)
    pts = []
    for k in range(steps + 1):
        t = length * k / steps
        import math
        v = math.sin((k / steps + phase) * math.pi * 2) * amp
        d = [0, 0, 0]
        d[axis] = v
        pts.append((t, _add(base, d)))
    return {"rotation": kf(pts)}


def _blink(length, at):
    return {"position": kf([(0, [0, 0, 0]), (at, [0, 0, 0]), (at + 0.05, [0, 0, -0.65]), (at + 0.15, [0, 0, -0.65]),
                            (at + 0.2, [0, 0, 0]), (length, [0, 0, 0])])}


def _fixed(model, bone, length):
    p = _pose(model, bone)
    return {"rotation": kf([(0, p), (length, p)])}


def animations_for(name, model):
    spec = SPECS[name]
    idle_len = 4.0
    idle = {
        "body": {"position": kf([(0, [0, 0, 0]), (idle_len / 2, [0, 0.18, 0]), (idle_len, [0, 0, 0])])},
        "eyelids": _blink(idle_len, spec.get("blink", 2.9)),
    }
    tilt = spec.get("tilt", 0.0)
    idle["head"] = {"rotation": kf([(0, [0, 0, tilt]), (idle_len / 2, [0, 0, tilt + spec.get("tilt_sway", 1.5)]),
                                    (idle_len, [0, 0, tilt])])}
    for bone, (amp, axis, phase) in spec.get("sway", {}).items():
        idle[bone] = _sway(model, bone, idle_len, amp, phase, axis)
    for bone in spec.get("hold", ()):
        idle[bone] = _fixed(model, bone, idle_len)
    for bone in ("right_arm", "left_arm"):
        if bone not in idle:
            idle[bone] = _sway(model, bone, idle_len, 1.2, 0.0 if bone == "right_arm" else 0.5, 2)

    walk_len = 1.0
    swing = spec.get("leg_swing", 26)
    walk = {
        "right_leg": {"rotation": kf([(0, [swing, 0, 0]), (0.5, [-swing, 0, 0]), (1.0, [swing, 0, 0])])},
        "left_leg": {"rotation": kf([(0, [-swing, 0, 0]), (0.5, [swing, 0, 0]), (1.0, [-swing, 0, 0])])},
        "body": {"position": kf([(0, [0, 0, 0]), (0.25, [0, 0.5, 0]), (0.5, [0, 0, 0]), (0.75, [0, 0.5, 0]), (1.0, [0, 0, 0])])},
        "eyelids": _blink(walk_len, 0.6),
    }
    for bone, (amp, axis, phase) in spec.get("sway", {}).items():
        walk[bone] = _sway(model, bone, walk_len, amp * 2.2, phase, axis)
    arm_swing = spec.get("arm_swing", 22)
    for bone, sign in (("right_arm", -1), ("left_arm", 1)):
        if bone in spec.get("hold", ()):
            walk[bone] = _fixed(model, bone, walk_len)
        else:
            base = _pose(model, bone)
            walk[bone] = {"rotation": kf([(0, _add(base, [sign * arm_swing, 0, 0])), (0.5, _add(base, [-sign * arm_swing, 0, 0])),
                                          (1.0, _add(base, [sign * arm_swing, 0, 0]))])}
    for bone in spec.get("walk_hold", ()):
        walk[bone] = _fixed(model, bone, walk_len)
    prefix = "animation.npc_" + name + "."
    return {
        prefix + "idle": {"loop": True, "animation_length": idle_len, "bones": idle},
        prefix + "walk": {"loop": True, "animation_length": walk_len, "bones": walk},
    }


# 人ごと：tilt（首のかしげ）・sway（{骨: (揺れ幅, 軸, ずれ)}）・hold（構えたまま動かさない骨）・blink（まばたきの時）
SPECS = {
    "lilia": {"tilt": 4.0, "tilt_sway": 2.0, "blink": 2.9,
              "sway": {"hair_tail": (4.0, 2, 0.0), "hair_tail2": (5.0, 2, 0.15), "ahoge": (6.0, 2, 0.25)},
              "hold": ("left_arm", "book"), "arm_swing": 26},
    "serena": {"tilt": 2.0, "tilt_sway": 1.2, "blink": 3.3,
               "sway": {"hair_back": (2.5, 0, 0.1)},
               "hold": ("right_arm", "left_arm", "bell"), "leg_swing": 14},
    "gard": {"tilt": 0.0, "tilt_sway": 0.6, "blink": 3.6,
             "sway": {"cloak": (2.5, 0, 0.0)},
             "hold": ("left_arm", "sword"), "arm_swing": 18},
    "doran": {"tilt": -1.0, "tilt_sway": 0.8, "blink": 2.4,
              "sway": {"beard": (1.2, 0, 0.2)},
              "hold": ("hammer",), "arm_swing": 16, "leg_swing": 20},
    "bard": {"tilt": 0.0, "tilt_sway": 0.5, "blink": 3.1,
             "sway": {"cape": (1.5, 2, 0.3)},
             "hold": ("right_arm", "spear"), "arm_swing": 16},
}
