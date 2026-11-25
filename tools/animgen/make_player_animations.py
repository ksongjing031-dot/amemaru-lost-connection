"""AMEMARU: LOST CONNECTION のスキルのモーション（仮）を作る。

使い方: python3 tools/animgen/make_player_animations.py <assets/amemaru のフォルダ>

出力: player_animation/<名前>.json（GeckoLib の書き出しの形。playerAnimator が読む）
  - ファイルの中のアニメーションの名前が、スキルの "animation" / "cast_animation" の値になる。
  - 部位（ボーン）: head / torso / right_arm / left_arm / right_leg / left_leg / body
    body は体全体（腰のあたりを中心に回る）。torso は胴の箱だけ。
  - 回転は度。腕の x は「マイナスで前に上がる」（-90 で真っすぐ前、-180 で真上）。
    腕の z は「右腕はプラスで外へ、左腕はマイナスで外へ」（腕を下ろしているとき）。
    body の x は「プラスで前に傾く」。
  - 書いていない部位は、ふだんの動き（歩く・腕を振る）のまま。

Blockbench で作り直すときは、playerAnimator のプレイヤーのひな形（bend 付きの player モデル）で、
同じボーン名・同じアニメーション名で書き出せば、そのまま差し替わる（docs/phase2.md の8章）。

時刻は tick（1/20 秒）で書き、ファイルには少しだけずらした秒で書く
（読み込み側が「秒×20 を切り捨て」「長さ×20 を切り上げ」するため、ちょうどの値だと1tick ずれることがある）。
"""
import json
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"
OUT = os.path.join(ROOT, "player_animation")

# ふだんの右腕（物を持っているとき、少し前に出ている）
R_IDLE = [-15, 0, 0]
L_IDLE = [0, 0, 0]
ZERO = [0, 0, 0]


def key(tick):
    return "%.3f" % (tick / 20.0 + 0.001)


def length(ticks):
    return round(ticks / 20.0 - 0.001, 3)


def track(frames, default_ease="linear"):
    """frames: [(tick, [x, y, z], ease)] または [(tick, {"pre": [...], "post": [...]}, ease)]。

    ease は「その時刻から次の時刻まで」の動き方（playerAnimator の GeckoLib 読み込みの決まり）。
    """
    out = {}
    for frame in frames:
        tick, value = frame[0], frame[1]
        ease = frame[2] if len(frame) > 2 else default_ease
        if isinstance(value, dict):
            entry = dict(value)
        else:
            entry = {"vector": value}
        if ease != "linear":
            entry["easing"] = ease
        out[key(tick)] = entry
    return {"rotation": out}


def animation(name, ticks, bones, loop=False):
    body = {"animation_length": length(ticks), "bones": bones}
    if loop:
        body["loop"] = True
    return {"format_version": "1.8.0", "animations": {name: body}}


ANIMS = {}


def define(name, ticks, loop=False, **bones):
    ANIMS[name] = animation(name, ticks, {bone: track(frames) for bone, frames in bones.items()}, loop)


# ================================================================ ナイト
# 呼び声：盾（左手）を高く掲げて、敵を呼ぶ
define("knight_call", 14,
       left_arm=[(0, L_IDLE, "easeOutBack"), (3, [-165, 0, 12]), (9, [-165, 0, 12], "easeInOutSine"), (14, [-20, 0, 0])],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-30, 0, 25]), (9, [-30, 0, 25], "easeInOutSine"), (14, [-15, 0, 5])],
       body=[(0, ZERO, "easeOutQuad"), (3, [-6, 0, 0]), (9, [-6, 0, 0], "easeInOutSine"), (14, ZERO)])

# 盾結び：盾を味方の方へ差し出す
define("knight_shield_bond", 12,
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-85, 15, 0]), (8, [-80, 10, 0], "easeInOutSine"), (12, [-20, 0, 0])],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [10, 0, 10]), (8, [10, 0, 10], "easeInOutSine"), (12, R_IDLE)])

# 城壁の構え：盾を体の前に、足を開いて踏ん張る
define("knight_rampart", 12,
       left_arm=[(0, L_IDLE, "easeOutBack"), (3, [-75, 40, 0]), (9, [-75, 40, 0], "easeInOutSine"), (12, [-30, 15, 0])],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-40, -10, 10]), (9, [-40, -10, 10], "easeInOutSine"), (12, R_IDLE)],
       right_leg=[(0, ZERO, "easeOutQuad"), (3, [0, 0, 7]), (9, [0, 0, 7], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (3, [0, 0, -7]), (9, [0, 0, -7], "easeInOutSine"), (12, ZERO)])

# 突進斬り：振りかぶって踏み込み、振り下ろす
define("knight_charge_slash", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-160, 0, -10]), (4, [-165, 0, -10], "easeInQuad"), (6, [-25, -15, 0]),
                  (8, [-25, -15, 0], "easeInOutSine"), (12, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-40, 0, -15]), (6, [20, 0, -20], "easeInOutSine"), (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [12, 0, 0]), (6, [18, 0, 0], "easeInOutSine"), (12, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (2, [-25, 0, 0]), (6, [-30, 0, 0], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (2, [20, 0, 0]), (6, [25, 0, 0], "easeInOutSine"), (12, ZERO)])

# 旋風断：腕を広げて、その場で一回転
define("knight_whirlwind", 11,
       body=[(0, ZERO), (1, ZERO, "easeOutQuad"), (5, [0, -180, 0]), (8, {"pre": [0, -360, 0], "post": [0, 0, 0]}), (11, ZERO)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (1, [-10, 0, 85]), (8, [-10, 0, 85], "easeInOutSine"), (11, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (1, [-10, 0, -70]), (8, [-10, 0, -70], "easeInOutSine"), (11, L_IDLE)])

# 不屈の誓い：剣を顔の前に立てる（騎士の礼）
define("knight_unyielding_vow", 18,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-85, -25, 0]), (12, [-85, -25, 0], "easeInOutSine"), (18, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-30, 30, 0]), (12, [-30, 30, 0], "easeInOutSine"), (18, L_IDLE)])

# 不落の城：剣を天に掲げ、振り下ろして盾を構える
define("knight_unfallen_castle", 20,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (5, [-175, 0, -5]), (10, [-175, 0, -5], "easeInQuad"), (12, [-45, 0, 0]),
                  (17, [-45, 0, 0], "easeInOutSine"), (20, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (5, [-20, 0, -30]), (10, [-20, 0, -30], "easeInQuad"), (12, [-70, 35, 0]),
                 (17, [-70, 35, 0], "easeInOutSine"), (20, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (5, [-5, 0, 0]), (10, [-5, 0, 0], "easeInQuad"), (12, [10, 0, 0]),
             (17, [10, 0, 0], "easeInOutSine"), (20, ZERO)],
       right_leg=[(0, ZERO), (10, ZERO, "easeInQuad"), (12, [-15, 0, 6]), (17, [-15, 0, 6], "easeInOutSine"), (20, ZERO)],
       left_leg=[(0, ZERO), (10, ZERO, "easeInQuad"), (12, [15, 0, -6]), (17, [15, 0, -6], "easeInOutSine"), (20, ZERO)])

# ================================================================ ウィザード
# 灯火弾：杖を前に突き出す
define("wizard_lamp_bolt", 7,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (1, [-95, -5, 0]), (4, [-90, -5, 0], "easeInOutSine"), (7, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (1, [10, 0, -10]), (4, [10, 0, -10], "easeInOutSine"), (7, L_IDLE)])

# 氷結陣：杖を振り上げ、地面を突く
define("wizard_frost_circle", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-140, 0, -10], "easeInQuad"), (5, [-55, -10, 0]),
                  (9, [-55, -10, 0], "easeInOutSine"), (12, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-60, 20, 0]), (5, [-75, 10, 0]), (9, [-75, 10, 0], "easeInOutSine"), (12, L_IDLE)])

# 流星群（詠唱）：両手を空へ。くり返し（足は歩ける）
define("wizard_meteor_cast", 20, loop=True,
       right_arm=[(0, [-150, 0, -20], "easeInOutSine"), (10, [-160, 0, -12], "easeInOutSine"), (20, [-150, 0, -20])],
       left_arm=[(0, [-150, 0, 20], "easeInOutSine"), (10, [-160, 0, 12], "easeInOutSine"), (20, [-150, 0, 20])])

# 流星群（放つ）：両手を振り下ろす
define("wizard_meteor_release", 10,
       right_arm=[(0, [-160, 0, -15], "easeInQuad"), (2, [-65, -15, 0]), (6, [-65, -15, 0], "easeInOutSine"), (10, [-20, 0, 0])],
       left_arm=[(0, [-160, 0, 15], "easeInQuad"), (2, [-65, 15, 0]), (6, [-65, 15, 0], "easeInOutSine"), (10, L_IDLE)],
       body=[(0, [-4, 0, 0], "easeInQuad"), (2, [8, 0, 0]), (6, [8, 0, 0], "easeInOutSine"), (10, ZERO)])

# 雷鎖：左手を突き出す（体をひねる）
define("wizard_chain_lightning", 9,
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-90, 10, 0]), (5, [-88, 5, 0], "easeInOutSine"), (9, L_IDLE)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-30, 0, 30]), (5, [-30, 0, 30], "easeInOutSine"), (9, R_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [0, -15, 0]), (5, [0, -15, 0], "easeInOutSine"), (9, ZERO)])

# 魔力の糸：両手を味方へ差し出す
define("wizard_mana_thread", 14,
       right_arm=[(0, R_IDLE, "easeOutSine"), (4, [-80, -15, 0]), (10, [-75, -10, 0], "easeInOutSine"), (14, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutSine"), (4, [-80, 15, 0]), (10, [-75, 10, 0], "easeInOutSine"), (14, L_IDLE)])

# 瞬転：腕を胸の前で交差し、ぱっと開く
define("wizard_blink", 8,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-70, -45, 0], "easeOutQuad"), (4, [-20, 0, 60]), (8, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-70, 45, 0], "easeOutQuad"), (4, [-20, 0, -60]), (8, L_IDLE)])

# 星落とし（詠唱）：杖を高く掲げ、左手を横へ。くり返し（足は歩ける）
define("wizard_starfall_cast", 24, loop=True,
       right_arm=[(0, [-170, 0, -8], "easeInOutSine"), (12, [-176, 0, -3], "easeInOutSine"), (24, [-170, 0, -8])],
       left_arm=[(0, [-30, 0, -50], "easeInOutSine"), (12, [-40, 0, -40], "easeInOutSine"), (24, [-30, 0, -50])])

# 星落とし（放つ）：杖を振り下ろし、体ごと前へ
define("wizard_starfall_release", 16,
       right_arm=[(0, [-175, 0, -5], "easeInQuad"), (4, [-40, -20, 0]), (11, [-40, -20, 0], "easeInOutSine"), (16, [-20, 0, 0])],
       left_arm=[(0, [-35, 0, -45], "easeInQuad"), (4, [-45, 25, 0]), (11, [-45, 25, 0], "easeInOutSine"), (16, L_IDLE)],
       body=[(0, [-6, 0, 0], "easeInQuad"), (4, [14, 0, 0]), (11, [14, 0, 0], "easeInOutSine"), (16, ZERO)])


# ================================================================ スキルの拡張（11月17日）：ナイト
# 縛鎖：盾を前へ突き出して鎖を放ち、ぐっと引き寄せる
define("knight_binding_chain", 14,
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-95, 20, 0], "easeInOutSine"), (6, [-90, 15, 0], "easeInQuad"), (9, [-40, 30, -10]),
                 (11, [-40, 30, -10], "easeInOutSine"), (14, L_IDLE)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-20, 0, 25]), (9, [-30, 0, 15], "easeInOutSine"), (14, R_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [6, 15, 0], "easeInQuad"), (9, [-6, -12, 0], "easeInOutSine"), (14, ZERO)])

# 受け流し：盾を斜めに、さっと前へ（短く鋭い構え）
define("knight_parry", 12,
       left_arm=[(0, L_IDLE, "easeOutBack"), (2, [-80, 45, -10]), (8, [-80, 45, -10], "easeInOutSine"), (12, L_IDLE)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-35, -20, 20]), (8, [-35, -20, 20], "easeInOutSine"), (12, R_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [4, 20, 0]), (8, [4, 20, 0], "easeInOutSine"), (12, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (2, [12, 0, 4]), (8, [12, 0, 4], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (2, [-14, 0, -4]), (8, [-14, 0, -4], "easeInOutSine"), (12, ZERO)])

# 誓いの円陣：盾を高く掲げ、地面に突き立てる
define("knight_oath_circle", 18,
       left_arm=[(0, L_IDLE, "easeOutQuad"), (5, [-170, 0, 10], "easeInQuad"), (8, [-60, 10, 0]), (14, [-60, 10, 0], "easeInOutSine"),
                 (18, L_IDLE)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (5, [-150, 0, -15], "easeInQuad"), (8, [-55, -10, 0]),
                  (14, [-55, -10, 0], "easeInOutSine"), (18, R_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (5, [-6, 0, 0], "easeInQuad"), (8, [16, 0, 0]), (14, [16, 0, 0], "easeInOutSine"), (18, ZERO)],
       right_leg=[(0, ZERO), (5, ZERO, "easeInQuad"), (8, [-18, 0, 5]), (14, [-18, 0, 5], "easeInOutSine"), (18, ZERO)],
       left_leg=[(0, ZERO), (5, ZERO, "easeInQuad"), (8, [18, 0, -5]), (14, [18, 0, -5], "easeInOutSine"), (18, ZERO)])

# 溜め斬り（溜め）：剣を肩の後ろへ大きく引き、体をひねって力を溜める。くり返し（足は歩ける）
define("knight_heavy_slash_charge", 20, loop=True,
       right_arm=[(0, [-150, 40, 40], "easeInOutSine"), (10, [-156, 40, 44], "easeInOutSine"), (20, [-150, 40, 40])],
       left_arm=[(0, [-60, -30, -20], "easeInOutSine"), (10, [-64, -30, -22], "easeInOutSine"), (20, [-60, -30, -20])],
       body=[(0, [6, 30, 0], "easeInOutSine"), (10, [8, 32, 0], "easeInOutSine"), (20, [6, 30, 0])])

# 溜め斬り（放つ）：引いた剣を、前を薙ぐように大きく振り抜く
define("knight_heavy_slash", 14,
       right_arm=[(0, [-150, 40, 40], "easeInQuad"), (3, [-85, -60, 0]), (6, [-70, -80, -10]), (10, [-70, -80, -10], "easeInOutSine"),
                  (14, R_IDLE)],
       left_arm=[(0, [-60, -30, -20], "easeInQuad"), (3, [-20, 20, -30]), (10, [-20, 20, -30], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, [6, 30, 0], "easeInQuad"), (3, [14, -30, 0]), (6, [14, -40, 0]), (10, [14, -40, 0], "easeInOutSine"), (14, ZERO)],
       right_leg=[(0, ZERO), (3, [-25, 0, 0]), (10, [-25, 0, 0], "easeInOutSine"), (14, ZERO)],
       left_leg=[(0, ZERO), (3, [22, 0, 0]), (10, [22, 0, 0], "easeInOutSine"), (14, ZERO)])

# 跳躍斬（跳ぶ）：しゃがんで跳び、剣を頭上に振りかぶったまま飛ぶ（着地で knight_leap_land に替わる）
define("knight_leap_slash", 30,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-170, 0, -15]), (26, [-175, 0, -10], "easeInOutSine"), (30, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-150, 0, 20]), (26, [-155, 0, 15], "easeInOutSine"), (30, L_IDLE)],
       body=[(0, [14, 0, 0], "easeOutQuad"), (3, [-10, 0, 0]), (26, [-8, 0, 0], "easeInOutSine"), (30, ZERO)],
       right_leg=[(0, [-30, 0, 0], "easeOutQuad"), (4, [-40, 0, 0]), (26, [-35, 0, 0], "easeInOutSine"), (30, ZERO)],
       left_leg=[(0, [20, 0, 0], "easeOutQuad"), (4, [10, 0, 0]), (26, [12, 0, 0], "easeInOutSine"), (30, ZERO)])

# 跳躍斬（着地）：剣を地面へ叩きつける
define("knight_leap_land", 12,
       right_arm=[(0, [-175, 0, -10], "easeInQuad"), (2, [-40, 0, 0]), (8, [-40, 0, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, [-155, 0, 15], "easeInQuad"), (2, [-45, 0, 10]), (8, [-45, 0, 10], "easeInOutSine"), (12, L_IDLE)],
       body=[(0, [-8, 0, 0], "easeInQuad"), (2, [24, 0, 0]), (8, [24, 0, 0], "easeInOutSine"), (12, ZERO)],
       right_leg=[(0, ZERO), (2, [-30, 0, 6]), (8, [-30, 0, 6], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO), (2, [25, 0, -6]), (8, [25, 0, -6], "easeInOutSine"), (12, ZERO)])

# 誓気解放：剣を引いてから、まっすぐ突き出す
define("knight_oath_release", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-70, 30, 30], "easeInQuad"), (6, [-95, -5, 0]), (10, [-95, -5, 0], "easeInOutSine"),
                  (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-50, 0, -20], "easeInQuad"), (6, [15, 0, -20]), (10, [15, 0, -20], "easeInOutSine"),
                 (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [-4, 20, 0], "easeInQuad"), (6, [16, -10, 0]), (10, [16, -10, 0], "easeInOutSine"), (14, ZERO)],
       right_leg=[(0, ZERO), (4, ZERO, "easeInQuad"), (6, [-30, 0, 0]), (10, [-30, 0, 0], "easeInOutSine"), (14, ZERO)],
       left_leg=[(0, ZERO), (4, ZERO, "easeInQuad"), (6, [24, 0, 0]), (10, [24, 0, 0], "easeInOutSine"), (14, ZERO)])

# ================================================================ スキルの拡張（11月17日）：ウィザード
# 灯火の連弾（溜め）：杖を前に構え、左手で灯火を集める。くり返し（足は歩ける）
define("wizard_lamp_barrage_charge", 20, loop=True,
       right_arm=[(0, [-85, -10, 0], "easeInOutSine"), (10, [-90, -12, 0], "easeInOutSine"), (20, [-85, -10, 0])],
       left_arm=[(0, [-70, 30, -10], "easeInOutSine"), (5, [-80, 10, -20], "easeInOutSine"), (10, [-70, -5, -10], "easeInOutSine"),
                 (15, [-60, 15, 0], "easeInOutSine"), (20, [-70, 30, -10])])

# 灯火の連弾（放つ）：杖を横へ払い、灯火を扇に放つ
define("wizard_lamp_barrage", 10,
       right_arm=[(0, [-85, -10, 0], "easeOutQuad"), (2, [-90, 40, 0]), (5, [-90, -45, 0]), (7, [-85, -45, 0], "easeInOutSine"),
                  (10, [-20, 0, 0])],
       left_arm=[(0, [-70, 30, -10], "easeOutQuad"), (3, [-20, 0, -40]), (7, [-20, 0, -40], "easeInOutSine"), (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [0, 15, 0]), (5, [0, -15, 0]), (7, [0, -15, 0], "easeInOutSine"), (10, ZERO)])

# 光の帳：杖を掲げ、左の手のひらを味方へ向ける
define("wizard_light_veil", 14,
       right_arm=[(0, R_IDLE, "easeOutSine"), (4, [-150, 0, -10]), (10, [-150, 0, -10], "easeInOutSine"), (14, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutSine"), (4, [-95, 15, 0]), (10, [-95, 15, 0], "easeInOutSine"), (14, L_IDLE)])

# 星爆：両手を握って胸の前へ集め、ぱっと上へ開く
define("wizard_star_burst", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-70, -40, 0], "easeInQuad"), (5, [-160, 0, -40]), (9, [-160, 0, -40], "easeInOutSine"),
                  (12, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-70, 40, 0], "easeInQuad"), (5, [-160, 0, 40]), (9, [-160, 0, 40], "easeInOutSine"),
                 (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [8, 0, 0], "easeInQuad"), (5, [-8, 0, 0]), (9, [-8, 0, 0], "easeInOutSine"), (12, ZERO)])

# 氷柱：杖を引いて、前へ鋭く突く
define("wizard_icicle", 9,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-60, 20, 20], "easeInQuad"), (4, [-100, -5, 0]), (6, [-100, -5, 0], "easeInOutSine"),
                  (9, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-40, 0, -20]), (6, [-10, 0, -30], "easeInOutSine"), (9, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [-4, 15, 0], "easeInQuad"), (4, [10, -10, 0]), (6, [10, -10, 0], "easeInOutSine"), (9, ZERO)])

# 凍てつく波動：杖を両手で高く掲げ、足もとへ突き下ろす
define("wizard_frost_wave", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-170, 0, 5], "easeInQuad"), (7, [-45, 0, 0]), (11, [-45, 0, 0], "easeInOutSine"),
                  (14, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-165, 0, -5], "easeInQuad"), (7, [-50, 0, 0]), (11, [-50, 0, 0], "easeInOutSine"),
                 (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [-8, 0, 0], "easeInQuad"), (7, [22, 0, 0]), (11, [22, 0, 0], "easeInOutSine"), (14, ZERO)],
       right_leg=[(0, ZERO), (4, ZERO, "easeInQuad"), (7, [-15, 0, 5]), (11, [-15, 0, 5], "easeInOutSine"), (14, ZERO)],
       left_leg=[(0, ZERO), (4, ZERO, "easeInQuad"), (7, [15, 0, -5]), (11, [15, 0, -5], "easeInOutSine"), (14, ZERO)])

# 雷霆：杖を天に掲げ、狙った地点へ振り下ろす
define("wizard_thunderbolt", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-178, 0, -5]), (7, [-178, 0, -5], "easeInQuad"), (9, [-70, -10, 0]),
                  (12, [-70, -10, 0], "easeInOutSine"), (14, [-20, 0, 0])],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-30, 0, -45]), (12, [-30, 0, -45], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [-6, 0, 0]), (7, [-6, 0, 0], "easeInQuad"), (9, [10, 0, 0]), (12, [10, 0, 0], "easeInOutSine"),
             (14, ZERO)])


# ================================================================ 奥義のもう一つ（11月17日）
# 天断ち（構え）：両手で剣を天へ突き上げ、光の大剣を形づくる。足を開いて踏ん張る。くり返し（足は歩ける）
define("knight_heaven_cleave_cast", 20, loop=True,
       right_arm=[(0, [-178, 0, 8], "easeInOutSine"), (10, [-180, 0, 4], "easeInOutSine"), (20, [-178, 0, 8])],
       left_arm=[(0, [-176, 0, -10], "easeInOutSine"), (10, [-178, 0, -6], "easeInOutSine"), (20, [-176, 0, -10])],
       body=[(0, [-8, 0, 0], "easeInOutSine"), (10, [-10, 0, 0], "easeInOutSine"), (20, [-8, 0, 0])])

# 天断ち（断つ）：天の大剣ごと、両手で前の地面へ振り下ろす
define("knight_heaven_cleave", 18,
       right_arm=[(0, [-180, 0, 4], "easeInQuad"), (3, [-30, 0, 10]), (12, [-30, 0, 10], "easeInOutSine"), (18, R_IDLE)],
       left_arm=[(0, [-178, 0, -6], "easeInQuad"), (3, [-35, 0, -10]), (12, [-35, 0, -10], "easeInOutSine"), (18, L_IDLE)],
       body=[(0, [-10, 0, 0], "easeInQuad"), (3, [28, 0, 0]), (12, [28, 0, 0], "easeInOutSine"), (18, ZERO)],
       right_leg=[(0, ZERO), (3, [-35, 0, 6]), (12, [-35, 0, 6], "easeInOutSine"), (18, ZERO)],
       left_leg=[(0, ZERO), (3, [28, 0, -6]), (12, [28, 0, -6], "easeInOutSine"), (18, ZERO)])

# 氷華の檻（詠唱）：杖を前の地面へ向け、左手を下へ広げて冷気を集める。くり返し（足は歩ける）
define("wizard_ice_garden_cast", 24, loop=True,
       right_arm=[(0, [-60, -10, 0], "easeInOutSine"), (12, [-66, -12, 0], "easeInOutSine"), (24, [-60, -10, 0])],
       left_arm=[(0, [-20, 0, -55], "easeInOutSine"), (8, [-30, 10, -60], "easeInOutSine"), (16, [-15, -10, -50], "easeInOutSine"),
                 (24, [-20, 0, -55])],
       body=[(0, [6, 0, 0], "easeInOutSine"), (12, [8, 0, 0], "easeInOutSine"), (24, [6, 0, 0])])

# 氷華の檻（放つ）：杖を高く上げ、地面へ突き立てる
define("wizard_ice_garden_release", 16,
       right_arm=[(0, [-66, -12, 0], "easeOutQuad"), (4, [-175, 0, -5], "easeInQuad"), (7, [-40, -5, 0]),
                  (12, [-40, -5, 0], "easeInOutSine"), (16, [-20, 0, 0])],
       left_arm=[(0, [-20, 0, -55], "easeOutQuad"), (4, [-40, 0, -60], "easeInQuad"), (7, [-10, 0, -70]),
                 (12, [-10, 0, -70], "easeInOutSine"), (16, L_IDLE)],
       body=[(0, [8, 0, 0], "easeOutQuad"), (4, [-6, 0, 0], "easeInQuad"), (7, [16, 0, 0]), (12, [16, 0, 0], "easeInOutSine"),
             (16, ZERO)])


# ================================================================ Phase 6 その2（11月22日）：アーチャー
# 弓は右手（利き手）で持ち、左手で弦を引く（バニラの弓の構えと同じ向き）。
BOW_R = [-90, -6, 0]
BOW_L_DRAWN = [-90, 30, 0]
BOW_L_LOOSE = [-80, 12, 0]

# 射る：さっと引いて放つ
define("archer_shot", 8,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, BOW_R), (5, BOW_R, "easeInOutSine"), (8, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, BOW_L_DRAWN, "easeInQuad"), (3, BOW_L_LOOSE), (5, BOW_L_LOOSE, "easeInOutSine"),
                 (8, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [0, 10, 0]), (5, [0, 10, 0], "easeInOutSine"), (8, ZERO)])

# 重い一矢：深く引いて、体ごと放つ
define("archer_heavy", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, BOW_R), (9, BOW_R, "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-88, 45, 0], "easeInQuad"), (5, [-70, 0, -10]), (9, [-70, 0, -10], "easeInOutSine"),
                 (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [-4, 18, 0], "easeInQuad"), (5, [4, 8, 0]), (9, [4, 8, 0], "easeInOutSine"), (12, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (4, [-12, 0, 0]), (9, [-12, 0, 0], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (4, [10, 0, 0]), (9, [10, 0, 0], "easeInOutSine"), (12, ZERO)])

# 引き絞る（溜め）：弦を顔の横まで引いて、静かに息を整える。くり返し
define("archer_draw", 20, loop=True,
       right_arm=[(0, BOW_R, "easeInOutSine"), (10, [-91, -6, 0], "easeInOutSine"), (20, BOW_R)],
       left_arm=[(0, [-88, 48, 0], "easeInOutSine"), (10, [-89, 50, 0], "easeInOutSine"), (20, [-88, 48, 0])],
       body=[(0, [0, 16, 0], "easeInOutSine"), (10, [0, 17, 0], "easeInOutSine"), (20, [0, 16, 0])])

# 天穿ち（溜め）：空へ向けて引き絞る。くり返し
define("archer_draw_sky", 20, loop=True,
       right_arm=[(0, [-150, -6, 0], "easeInOutSine"), (10, [-152, -6, 0], "easeInOutSine"), (20, [-150, -6, 0])],
       left_arm=[(0, [-140, 40, 0], "easeInOutSine"), (10, [-142, 42, 0], "easeInOutSine"), (20, [-140, 40, 0])],
       body=[(0, [-10, 12, 0], "easeInOutSine"), (10, [-11, 13, 0], "easeInOutSine"), (20, [-10, 12, 0])])

# 放つ（溜めの後）：引いた弦を離し、反動で少し下がる
define("archer_release", 12,
       right_arm=[(0, BOW_R, "easeOutQuad"), (2, [-96, -6, 0]), (8, [-92, -6, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, [-88, 48, 0], "easeOutQuad"), (2, [-60, -10, -25]), (8, [-60, -10, -25], "easeInOutSine"), (12, L_IDLE)],
       body=[(0, [0, 16, 0], "easeOutQuad"), (2, [-6, 6, 0]), (8, [-4, 6, 0], "easeInOutSine"), (12, ZERO)])

# 扇に放つ：引いたまま、体を左から右へ払う
define("archer_fan", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, BOW_R), (7, BOW_R, "easeInOutSine"), (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, BOW_L_DRAWN, "easeInQuad"), (4, BOW_L_LOOSE), (7, BOW_L_LOOSE, "easeInOutSine"),
                 (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [0, 35, 0], "easeInOutQuad"), (5, [0, -25, 0]), (7, [0, -25, 0], "easeInOutSine"), (10, ZERO)])

# 三連射：すばやく3回引いて放つ
define("archer_triple", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, BOW_R), (11, BOW_R, "easeInOutSine"), (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, BOW_L_DRAWN), (3, BOW_L_LOOSE), (5, BOW_L_DRAWN), (6, BOW_L_LOOSE), (8, BOW_L_DRAWN),
                 (9, BOW_L_LOOSE), (11, BOW_L_LOOSE, "easeInOutSine"), (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [0, 10, 0]), (11, [0, 10, 0], "easeInOutSine"), (14, ZERO)])

# 鷹の眼差し：左手をひさしにして遠くを見る
define("archer_gaze", 18,
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-140, -40, 10]), (13, [-140, -40, 10], "easeInOutSine"), (18, L_IDLE)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-10, 0, 10]), (13, [-10, 0, 10], "easeInOutSine"), (18, R_IDLE)],
       head=[(0, ZERO, "easeOutQuad"), (4, [-8, 0, 0]), (8, [-8, -20, 0], "easeInOutSine"), (13, [-8, 20, 0], "easeInOutSine"), (18, ZERO)])

# 狙撃の構え：片ひざをつき、弓を構える
define("archer_kneel", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, BOW_R), (12, BOW_R, "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-85, 35, 0]), (12, [-85, 35, 0], "easeInOutSine"), (16, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [14, 12, 0]), (12, [14, 12, 0], "easeInOutSine"), (16, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (4, [-70, 0, 0]), (12, [-70, 0, 0], "easeInOutSine"), (16, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (4, [60, 0, 0]), (12, [60, 0, 0], "easeInOutSine"), (16, ZERO)])

# 流れ星の一矢（詠唱）：天へ向けて弓を掲げ、星の光を集める。くり返し
define("archer_star_cast", 20, loop=True,
       right_arm=[(0, [-130, -6, 0], "easeInOutSine"), (10, [-135, -6, 0], "easeInOutSine"), (20, [-130, -6, 0])],
       left_arm=[(0, [-125, 40, 0], "easeInOutSine"), (10, [-128, 44, 0], "easeInOutSine"), (20, [-125, 40, 0])],
       body=[(0, [-6, 14, 0], "easeInOutSine"), (10, [-7, 15, 0], "easeInOutSine"), (20, [-6, 14, 0])])

# 流れ星の一矢（放つ）：振り下ろすように前へ放つ
define("archer_star_release", 16,
       right_arm=[(0, [-130, -6, 0], "easeInQuad"), (3, BOW_R), (11, BOW_R, "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, [-125, 40, 0], "easeInQuad"), (3, [-88, 50, 0]), (4, [-55, -10, -30]), (11, [-55, -10, -30], "easeInOutSine"),
                 (16, L_IDLE)],
       body=[(0, [-6, 14, 0], "easeInQuad"), (3, [6, 16, 0]), (4, [-6, 4, 0]), (11, [-6, 4, 0], "easeInOutSine"), (16, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (3, [-15, 0, 0]), (11, [-15, 0, 0], "easeInOutSine"), (16, ZERO)])

# 後跳び：後ろへ跳びながら、前へ一矢
define("archer_back", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, BOW_R), (8, BOW_R, "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, BOW_L_DRAWN), (4, BOW_L_LOOSE), (8, BOW_L_LOOSE, "easeInOutSine"), (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [-14, 0, 0]), (8, [-10, 0, 0], "easeInOutSine"), (12, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (2, [-35, 0, 0]), (8, [-30, 0, 0], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (2, [-20, 0, 0]), (8, [-15, 0, 0], "easeInOutSine"), (12, ZERO)])

# 矢の雨：空へ向けて放つ
define("archer_sky", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-150, -6, 0]), (9, [-150, -6, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-140, 40, 0], "easeInQuad"), (4, [-120, 10, -20]), (9, [-120, 10, -20], "easeInOutSine"),
                 (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [-12, 12, 0]), (9, [-12, 12, 0], "easeInOutSine"), (12, ZERO)])

# 旋風の矢：弓を構えたまま、その場で一回転
define("archer_spin", 12,
       body=[(0, ZERO), (1, ZERO, "easeOutQuad"), (5, [0, -180, 0]), (9, {"pre": [0, -360, 0], "post": [0, 0, 0]}), (12, ZERO)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (1, [-90, 0, 40]), (9, [-90, 0, 40], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (1, [-90, 0, -40]), (9, [-90, 0, -40], "easeInOutSine"), (12, L_IDLE)])

# 矢嵐：弓を構えて、まわりを次々に射る。くり返し
define("archer_storm", 20, loop=True,
       right_arm=[(0, BOW_R, "easeInOutSine"), (10, [-100, -6, 0], "easeInOutSine"), (20, BOW_R)],
       left_arm=[(0, BOW_L_DRAWN), (3, BOW_L_LOOSE), (5, BOW_L_DRAWN), (8, BOW_L_LOOSE), (10, BOW_L_DRAWN), (13, BOW_L_LOOSE),
                 (15, BOW_L_DRAWN), (18, BOW_L_LOOSE), (20, BOW_L_DRAWN)],
       body=[(0, [0, 20, 0], "easeInOutSine"), (10, [0, -20, 0], "easeInOutSine"), (20, [0, 20, 0])])

# 疾風の弓舞：くるりと回って弓を掲げる
define("archer_dance", 16,
       body=[(0, ZERO), (1, ZERO, "easeOutQuad"), (6, [0, 180, 0]), (10, {"pre": [0, 360, 0], "post": [0, 0, 0]}), (16, ZERO)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-160, 0, 20]), (12, [-160, 0, 20], "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-20, 0, -60]), (12, [-20, 0, -60], "easeInOutSine"), (16, L_IDLE)])

# ================================================================ Phase 6 その2：ヒーラー（鈴杖は右手）
# 癒しの鈴：鈴杖を前に上げて、小さく振る
define("healer_ring", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-115, 0, 0]), (4, [-115, 0, 12]), (6, [-115, 0, -8]), (8, [-115, 0, 6]),
                  (9, [-110, 0, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-20, 0, -15]), (9, [-20, 0, -15], "easeInOutSine"), (12, L_IDLE)])

# 祈りの輪：両手を胸の前で合わせる
define("healer_pray", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-65, 30, 0]), (12, [-65, 30, 0], "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-65, -30, 0]), (12, [-65, -30, 0], "easeInOutSine"), (16, L_IDLE)],
       head=[(0, ZERO, "easeOutQuad"), (4, [15, 0, 0]), (12, [15, 0, 0], "easeInOutSine"), (16, ZERO)])

# 差し向ける：鈴杖を相手へ向ける
define("healer_point", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-95, -8, 0]), (7, [-92, -8, 0], "easeInOutSine"), (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [10, 0, -12]), (7, [10, 0, -12], "easeInOutSine"), (10, L_IDLE)])

# 掲げる：鈴杖を高く掲げる
define("healer_raise", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-170, 0, -5]), (10, [-170, 0, -5], "easeInOutSine"), (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-30, 0, -25]), (10, [-30, 0, -25], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [-5, 0, 0]), (10, [-5, 0, 0], "easeInOutSine"), (14, ZERO)])

# 呼び戻し（詠唱）：倒れた仲間へ手を伸ばし、鈴杖を胸に。くり返し
define("healer_call_cast", 20, loop=True,
       right_arm=[(0, [-60, 20, 0], "easeInOutSine"), (10, [-64, 22, 0], "easeInOutSine"), (20, [-60, 20, 0])],
       left_arm=[(0, [-85, 10, 0], "easeInOutSine"), (10, [-95, 10, 0], "easeInOutSine"), (20, [-85, 10, 0])],
       body=[(0, [12, 0, 0], "easeInOutSine"), (10, [14, 0, 0], "easeInOutSine"), (20, [12, 0, 0])])

# 呼び戻し（放つ）：両手を天へ
define("healer_call", 16,
       right_arm=[(0, [-60, 20, 0], "easeOutQuad"), (4, [-165, 0, -15]), (12, [-165, 0, -15], "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, [-85, 10, 0], "easeOutQuad"), (4, [-165, 0, 15]), (12, [-165, 0, 15], "easeInOutSine"), (16, L_IDLE)],
       body=[(0, [12, 0, 0], "easeOutQuad"), (4, [-6, 0, 0]), (12, [-6, 0, 0], "easeInOutSine"), (16, ZERO)])

# ふくらむ鈴（溜め）：鈴杖を胸の前で抱え、光を込める。くり返し
define("healer_charge", 20, loop=True,
       right_arm=[(0, [-70, 25, 0], "easeInOutSine"), (10, [-76, 25, 0], "easeInOutSine"), (20, [-70, 25, 0])],
       left_arm=[(0, [-60, -25, 0], "easeInOutSine"), (10, [-66, -25, 0], "easeInOutSine"), (20, [-60, -25, 0])])

# ふくらむ鈴（放つ）：抱えた鈴杖を前へ差し出す
define("healer_release", 12,
       right_arm=[(0, [-70, 25, 0], "easeOutQuad"), (3, [-100, -5, 0]), (8, [-100, -5, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, [-60, -25, 0], "easeOutQuad"), (3, [-90, 10, 0]), (8, [-90, 10, 0], "easeInOutSine"), (12, L_IDLE)])

# 清めの鈴音：鈴杖を横へ大きく払う
define("healer_chime", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-95, 50, 0], "easeInQuad"), (5, [-95, -50, 0]), (7, [-90, -50, 0], "easeInOutSine"),
                  (10, R_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [0, 20, 0], "easeInQuad"), (5, [0, -20, 0]), (7, [0, -20, 0], "easeInOutSine"), (10, ZERO)])

# 響き合う癒し：両手を左右へ広げる
define("healer_resonance", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-90, 0, 70]), (12, [-90, 0, 70], "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-90, 0, -70]), (12, [-90, 0, -70], "easeInOutSine"), (16, L_IDLE)])

# 加護の灯：鈴杖を灯のように頭の上へ
define("healer_lantern", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-150, 0, 20]), (10, [-150, 0, 20], "easeInOutSine"), (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-40, 0, -20]), (10, [-40, 0, -20], "easeInOutSine"), (14, L_IDLE)])

# 鈴杖を地面へ：振り上げて、突く
define("healer_slam", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-165, 0, -5], "easeInQuad"), (6, [-45, 0, 0]), (10, [-45, 0, 0], "easeInOutSine"),
                  (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-60, 0, -20]), (6, [-40, 0, -10]), (10, [-40, 0, -10], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [-6, 0, 0], "easeInQuad"), (6, [16, 0, 0]), (10, [16, 0, 0], "easeInOutSine"), (14, ZERO)])

# つながりの聖域：両手を大きく広げてから、地へ下ろす
define("healer_sanctuary", 18,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-160, 0, 40]), (9, [-160, 0, 40], "easeInQuad"), (13, [-40, 0, 40]),
                  (15, [-40, 0, 40], "easeInOutSine"), (18, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-160, 0, -40]), (9, [-160, 0, -40], "easeInQuad"), (13, [-40, 0, -40]),
                 (15, [-40, 0, -40], "easeInOutSine"), (18, L_IDLE)])

# 大いなる呼び声（詠唱）：両手を空へ広げて祈る。くり返し
define("healer_great_call_cast", 24, loop=True,
       right_arm=[(0, [-150, 0, 30], "easeInOutSine"), (12, [-158, 0, 24], "easeInOutSine"), (24, [-150, 0, 30])],
       left_arm=[(0, [-150, 0, -30], "easeInOutSine"), (12, [-158, 0, -24], "easeInOutSine"), (24, [-150, 0, -30])],
       head=[(0, [-15, 0, 0], "easeInOutSine"), (12, [-18, 0, 0], "easeInOutSine"), (24, [-15, 0, 0])])

# 大いなる呼び声（放つ）：掲げた手を、まわりへ振り下ろす
define("healer_great_call", 18,
       right_arm=[(0, [-150, 0, 30], "easeInQuad"), (4, [-175, 0, 5]), (8, [-60, 0, 60]), (14, [-60, 0, 60], "easeInOutSine"), (18, R_IDLE)],
       left_arm=[(0, [-150, 0, -30], "easeInQuad"), (4, [-175, 0, -5]), (8, [-60, 0, -60]), (14, [-60, 0, -60], "easeInOutSine"),
                 (18, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [-8, 0, 0]), (8, [10, 0, 0]), (14, [10, 0, 0], "easeInOutSine"), (18, ZERO)])

# ================================================================ Phase 6 その2：ローグ（短剣は両手）
# 乱れ刃：左右の短剣で、交互にすばやく突く
define("rogue_flurry", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-95, -10, 0]), (4, [-40, 0, 10]), (6, [-95, -10, 0]), (8, [-40, 0, 10]),
                  (10, [-40, 0, 10], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-40, 0, -10]), (5, [-95, 10, 0]), (7, [-40, 0, -10]), (9, [-95, 10, 0]),
                 (10, [-60, 0, -10], "easeInOutSine"), (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [8, -10, 0]), (5, [8, 10, 0]), (8, [8, -10, 0]), (10, [6, 0, 0], "easeInOutSine"), (12, ZERO)])

# 毒刃：斜めに斬り下ろす
define("rogue_slash", 9,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-150, 0, -30], "easeInQuad"), (4, [-30, -40, 20]), (6, [-30, -40, 20], "easeInOutSine"),
                  (9, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-60, 20, -20]), (6, [-30, 0, -15], "easeInOutSine"), (9, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [-4, 10, 0], "easeInQuad"), (4, [10, -15, 0]), (6, [10, -15, 0], "easeInOutSine"), (9, ZERO)])

# 十字斬り：両手を交差させて斬る
define("rogue_cross", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-150, 0, 30], "easeInQuad"), (4, [-50, 0, -35]), (7, [-50, 0, -35], "easeInOutSine"),
                  (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-150, 0, -30], "easeInQuad"), (4, [-50, 0, 35]), (7, [-50, 0, 35], "easeInOutSine"),
                 (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [-6, 0, 0], "easeInQuad"), (4, [14, 0, 0]), (7, [14, 0, 0], "easeInOutSine"), (10, ZERO)])

# 急所突き：身を低くして、深く一突き
define("rogue_thrust", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-60, 0, 10], "easeInQuad"), (4, [-95, -5, 0]), (7, [-95, -5, 0], "easeInOutSine"),
                  (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-30, 0, -30]), (7, [20, 0, -20], "easeInOutSine"), (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [10, 15, 0], "easeInQuad"), (4, [22, -10, 0]), (7, [22, -10, 0], "easeInOutSine"), (10, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (4, [-35, 0, 0]), (7, [-35, 0, 0], "easeInOutSine"), (10, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (4, [25, 0, 0]), (7, [25, 0, 0], "easeInOutSine"), (10, ZERO)])

# 影討ち：背後へ回り、低い姿勢から斬り上げる
define("rogue_behind", 10,
       right_arm=[(0, [-20, 0, 20], "easeOutQuad"), (2, [-30, 0, 30], "easeInQuad"), (5, [-150, -20, 0]), (7, [-150, -20, 0], "easeInOutSine"),
                  (10, R_IDLE)],
       left_arm=[(0, [-20, 0, -20], "easeOutQuad"), (2, [-30, 0, -30]), (7, [-70, 10, -10], "easeInOutSine"), (10, L_IDLE)],
       body=[(0, [20, 0, 0], "easeOutQuad"), (2, [24, 0, 0], "easeInQuad"), (5, [-4, 0, 0]), (7, [-4, 0, 0], "easeInOutSine"), (10, ZERO)])

# 走る：前かがみで、短剣を後ろへ
define("rogue_dash", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [40, 0, 15]), (7, [40, 0, 15], "easeInOutSine"), (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [40, 0, -15]), (7, [40, 0, -15], "easeInOutSine"), (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [25, 0, 0]), (7, [25, 0, 0], "easeInOutSine"), (10, ZERO)])

# 影刃の舞：短剣を広げて、その場で一回転
define("rogue_spin", 10,
       body=[(0, ZERO), (1, ZERO, "easeOutQuad"), (5, [0, -180, 0]), (8, {"pre": [0, -360, 0], "post": [0, 0, 0]}), (10, ZERO)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (1, [-80, 0, 80]), (8, [-80, 0, 80], "easeInOutSine"), (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (1, [-80, 0, -80]), (8, [-80, 0, -80], "easeInOutSine"), (10, L_IDLE)])

# 千の刃：回りながら、左右で斬り続ける
define("rogue_thousand", 20,
       body=[(0, ZERO), (1, ZERO, "easeInQuad"), (5, [0, -180, 0]), (9, {"pre": [0, -360, 0], "post": [0, 0, 0]}), (13, [0, -180, 0]),
             (17, {"pre": [0, -360, 0], "post": [0, 0, 0]}), (20, ZERO)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-100, 0, 70]), (6, [-60, 0, 90]), (10, [-100, 0, 70]), (14, [-60, 0, 90]),
                  (17, [-100, 0, 70], "easeInOutSine"), (20, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-60, 0, -90]), (6, [-100, 0, -70]), (10, [-60, 0, -90]), (14, [-100, 0, -70]),
                 (17, [-60, 0, -90], "easeInOutSine"), (20, L_IDLE)])

# 影分身：両手を交差させて、身を沈める
define("rogue_clone", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-70, 40, 0]), (10, [-70, 40, 0], "easeInOutSine"), (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-70, -40, 0]), (10, [-70, -40, 0], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [12, 0, 0]), (10, [12, 0, 0], "easeInOutSine"), (14, ZERO)])

# 投げる（煙玉・毒霧）：右手で放る
define("rogue_throw", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-170, 0, -10], "easeInQuad"), (5, [-80, 0, 0]), (7, [-80, 0, 0], "easeInOutSine"),
                  (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-60, 0, -20]), (7, [-20, 0, -15], "easeInOutSine"), (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [-6, 15, 0], "easeInQuad"), (5, [10, -10, 0]), (7, [10, -10, 0], "easeInOutSine"), (10, ZERO)])

# 糸を放つ：両手を前へ、扇に広げる
define("rogue_throw_wide", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-90, 30, 0], "easeInQuad"), (5, [-90, -30, 40]), (7, [-90, -30, 40], "easeInOutSine"),
                  (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-90, -30, 0], "easeInQuad"), (5, [-90, 30, -40]), (7, [-90, 30, -40], "easeInOutSine"),
                 (10, L_IDLE)])

# 痺れ針：手首だけで、すばやく投げる
define("rogue_needle", 7,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-60, 0, 20], "easeInQuad"), (3, [-95, -10, 0]), (5, [-95, -10, 0], "easeInOutSine"),
                  (7, R_IDLE)])

# 毒の弾け：指を鳴らす
define("rogue_snap", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-100, -20, 0]), (4, [-110, -20, 0]), (7, [-100, -20, 0], "easeInOutSine"), (10, R_IDLE)],
       head=[(0, ZERO, "easeOutQuad"), (3, [5, 0, 0]), (7, [5, 0, 0], "easeInOutSine"), (10, ZERO)])

# 毒塗り：短剣の刃に、毒を引く
define("rogue_coat", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-60, 30, 0]), (12, [-60, 30, 0], "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-55, -20, 0]), (6, [-55, 10, 0]), (9, [-55, -20, 0]), (12, [-55, 10, 0], "easeInOutSine"),
                 (16, L_IDLE)],
       head=[(0, ZERO, "easeOutQuad"), (3, [20, 0, 0]), (12, [20, 0, 0], "easeInOutSine"), (16, ZERO)])

# 毒花：両手を下から上へ開き、花を咲かせる
define("rogue_bloom", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-30, 0, 20], "easeInQuad"), (8, [-160, 0, 50]), (12, [-160, 0, 50], "easeInOutSine"),
                  (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-30, 0, -20], "easeInQuad"), (8, [-160, 0, -50]), (12, [-160, 0, -50], "easeInOutSine"),
                 (16, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [12, 0, 0], "easeInQuad"), (8, [-6, 0, 0]), (12, [-6, 0, 0], "easeInOutSine"), (16, ZERO)])

# ================================================================ Phase 6 その2：モンク（籠手は両手）
FIST_R = [-45, 20, 0]
FIST_L = [-45, -20, 0]

# 連打：左右の拳で、すばやく突く
define("monk_jab", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (1, [-95, -8, 0]), (3, FIST_R), (5, [-95, -8, 0]), (7, FIST_R, "easeInOutSine"),
                  (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-95, 8, 0]), (4, FIST_L), (6, [-95, 8, 0]), (7, FIST_L, "easeInOutSine"), (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (1, [4, -12, 0]), (2, [4, 12, 0]), (3, [4, -12, 0]), (4, [4, 12, 0]), (5, [4, -12, 0]),
             (7, [2, 0, 0], "easeInOutSine"), (10, ZERO)])

# 掌打：体重をのせて、掌を突き出す
define("monk_palm_light", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, FIST_R, "easeInQuad"), (4, [-90, -5, 0]), (7, [-90, -5, 0], "easeInOutSine"), (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, FIST_L), (7, [-20, 0, -15], "easeInOutSine"), (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [4, 15, 0], "easeInQuad"), (4, [10, -15, 0]), (7, [10, -15, 0], "easeInOutSine"), (10, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (4, [-25, 0, 0]), (7, [-25, 0, 0], "easeInOutSine"), (10, ZERO)])

# 発勁：深く腰を落とし、両の掌を一気に突き出す
define("monk_palm", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-30, 30, 10], "easeInQuad"), (5, [-90, 10, 0]), (10, [-90, 10, 0], "easeInOutSine"),
                  (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-30, -30, -10], "easeInQuad"), (5, [-90, -10, 0]), (10, [-90, -10, 0], "easeInOutSine"),
                 (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [-4, 0, 0], "easeInQuad"), (5, [16, 0, 0]), (10, [16, 0, 0], "easeInOutSine"), (14, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (5, [-30, 0, 8]), (10, [-30, 0, 8], "easeInOutSine"), (14, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (5, [25, 0, -8]), (10, [25, 0, -8], "easeInOutSine"), (14, ZERO)])

# 旋脚：体を回して、脚で払う
define("monk_kick", 11,
       body=[(0, ZERO), (1, ZERO, "easeOutQuad"), (5, [0, -180, 0]), (8, {"pre": [0, -360, 0], "post": [0, 0, 0]}), (11, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (2, [-80, 0, 30]), (8, [-80, 0, 30], "easeInOutSine"), (11, ZERO)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-20, 0, 60]), (8, [-20, 0, 60], "easeInOutSine"), (11, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-20, 0, -60]), (8, [-20, 0, -60], "easeInOutSine"), (11, L_IDLE)])

# 崩拳：踏み込みながら、まっすぐ打ち抜く
define("monk_dash_punch", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-20, 20, 10], "easeInQuad"), (4, [-95, 0, 0]), (9, [-95, 0, 0], "easeInOutSine"),
                  (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, FIST_L), (4, [20, 0, -20]), (9, [20, 0, -20], "easeInOutSine"), (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [10, 15, 0], "easeInQuad"), (4, [20, -15, 0]), (9, [20, -15, 0], "easeInOutSine"), (12, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (4, [-40, 0, 0]), (9, [-40, 0, 0], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (4, [30, 0, 0]), (9, [30, 0, 0], "easeInOutSine"), (12, ZERO)])

# 昇龍：低く構えてから、拳を天へ突き上げる
define("monk_rising", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-20, 0, 20], "easeInQuad"), (6, [-175, 0, -5]), (10, [-175, 0, -5], "easeInOutSine"),
                  (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, FIST_L), (6, [10, 0, -30]), (10, [10, 0, -30], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (3, [18, 0, 0], "easeInQuad"), (6, [-10, -20, 0]), (10, [-10, -20, 0], "easeInOutSine"), (14, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (3, [-30, 0, 0], "easeInQuad"), (6, [-10, 0, 0]), (14, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (3, [20, 0, 0], "easeInQuad"), (6, [30, 0, 0]), (14, ZERO)])

# 百烈拳：前へ、拳を雨のように打ち続ける
define("monk_rush", 30,
       right_arm=[(0, R_IDLE, "easeOutQuad")] + [(1 + i * 2, [-95 if i % 2 == 0 else -60, -5, 0]) for i in range(14)]
       + [(28, FIST_R, "easeInOutSine"), (30, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad")] + [(2 + i * 2, [-95 if i % 2 == 0 else -60, 5, 0]) for i in range(13)]
       + [(28, FIST_L, "easeInOutSine"), (30, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [10, 0, 0]), (28, [10, 0, 0], "easeInOutSine"), (30, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (2, [-20, 0, 0]), (28, [-20, 0, 0], "easeInOutSine"), (30, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (2, [15, 0, 0]), (28, [15, 0, 0], "easeInOutSine"), (30, ZERO)])

# 破山撃（跳ぶ）：両の拳を頭の上で組む
define("monk_slam", 30,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-170, 15, 0]), (26, [-175, 15, 0], "easeInOutSine"), (30, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-170, -15, 0]), (26, [-175, -15, 0], "easeInOutSine"), (30, L_IDLE)],
       body=[(0, [14, 0, 0], "easeOutQuad"), (3, [-10, 0, 0]), (26, [-8, 0, 0], "easeInOutSine"), (30, ZERO)],
       right_leg=[(0, [-30, 0, 0], "easeOutQuad"), (4, [-45, 0, 0]), (26, [-40, 0, 0], "easeInOutSine"), (30, ZERO)],
       left_leg=[(0, [20, 0, 0], "easeOutQuad"), (4, [-30, 0, 0]), (26, [-25, 0, 0], "easeInOutSine"), (30, ZERO)])

# 破山撃（着地）：組んだ拳を地面へ叩きつける
define("monk_slam_land", 12,
       right_arm=[(0, [-175, 15, 0], "easeInQuad"), (2, [-50, 15, 0]), (8, [-50, 15, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, [-175, -15, 0], "easeInQuad"), (2, [-50, -15, 0]), (8, [-50, -15, 0], "easeInOutSine"), (12, L_IDLE)],
       body=[(0, [-8, 0, 0], "easeInQuad"), (2, [30, 0, 0]), (8, [30, 0, 0], "easeInOutSine"), (12, ZERO)],
       right_leg=[(0, ZERO), (2, [-40, 0, 8]), (8, [-40, 0, 8], "easeInOutSine"), (12, ZERO)],
       left_leg=[(0, ZERO), (2, [30, 0, -8]), (8, [30, 0, -8], "easeInOutSine"), (12, ZERO)])

# 雷拳（溜め）：右の拳を腰に引き、雷を込める。くり返し
define("monk_charge", 20, loop=True,
       right_arm=[(0, [20, 20, 20], "easeInOutSine"), (10, [24, 20, 22], "easeInOutSine"), (20, [20, 20, 20])],
       left_arm=[(0, [-70, -10, 0], "easeInOutSine"), (10, [-74, -10, 0], "easeInOutSine"), (20, [-70, -10, 0])],
       body=[(0, [6, 25, 0], "easeInOutSine"), (10, [8, 27, 0], "easeInOutSine"), (20, [6, 25, 0])],
       right_leg=[(0, [-20, 0, 6]), (20, [-20, 0, 6])],
       left_leg=[(0, [20, 0, -6]), (20, [20, 0, -6])])

# 雷拳（放つ）：引いた拳を、体ごと打ち抜く
define("monk_heavy", 14,
       right_arm=[(0, [20, 20, 20], "easeInQuad"), (3, [-95, -5, 0]), (9, [-95, -5, 0], "easeInOutSine"), (14, R_IDLE)],
       left_arm=[(0, [-70, -10, 0], "easeInQuad"), (3, [20, 0, -25]), (9, [20, 0, -25], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, [6, 25, 0], "easeInQuad"), (3, [18, -25, 0]), (9, [18, -25, 0], "easeInOutSine"), (14, ZERO)],
       right_leg=[(0, [-20, 0, 6], "easeInQuad"), (3, [-40, 0, 6]), (9, [-40, 0, 6], "easeInOutSine"), (14, ZERO)],
       left_leg=[(0, [20, 0, -6], "easeInQuad"), (3, [30, 0, -6]), (9, [30, 0, -6], "easeInOutSine"), (14, ZERO)])

# 百烈・結：連打のあと、両の拳で締める
define("monk_hundred", 24,
       right_arm=[(0, R_IDLE, "easeOutQuad")] + [(1 + i * 2, [-95 if i % 2 == 0 else -60, -5, 0]) for i in range(8)]
       + [(17, [-20, 20, 20], "easeInQuad"), (19, [-95, 5, 0]), (22, [-95, 5, 0], "easeInOutSine"), (24, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad")] + [(2 + i * 2, [-95 if i % 2 == 0 else -60, 5, 0]) for i in range(7)]
       + [(17, [-20, -20, -20], "easeInQuad"), (19, [-95, -5, 0]), (22, [-95, -5, 0], "easeInOutSine"), (24, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [8, 0, 0]), (17, [0, 0, 0], "easeInQuad"), (19, [18, 0, 0]), (22, [18, 0, 0], "easeInOutSine"),
             (24, ZERO)])

# 寸勁：構えた拳を、ほんの少しだけ突き出す
define("monk_inch", 12,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-85, 0, 0]), (5, [-85, 0, 0], "easeInQuad"), (6, [-92, 0, 0]),
                  (9, [-92, 0, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, FIST_L), (9, FIST_L, "easeInOutSine"), (12, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (5, [2, 0, 0], "easeInQuad"), (6, [8, 0, 0]), (9, [8, 0, 0], "easeInOutSine"), (12, ZERO)])

# 流水の構え：両手で水の流れを描き、構える
define("monk_stance", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-80, 30, 30]), (8, [-60, -10, 10], "easeInOutSine"), (12, [-70, 20, 0], "easeInOutSine"),
                  (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-60, 10, -10]), (8, [-80, -30, -30], "easeInOutSine"), (12, [-60, -20, 0], "easeInOutSine"),
                 (16, L_IDLE)],
       right_leg=[(0, ZERO, "easeOutQuad"), (4, [-10, 0, 10]), (12, [-10, 0, 10], "easeInOutSine"), (16, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (4, [10, 0, -10]), (12, [10, 0, -10], "easeInOutSine"), (16, ZERO)])

# 天地流転：両手を天と地へ分け、ゆっくり回す
define("monk_heaven", 24,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (5, [-170, 0, 10]), (12, [-170, 0, 10], "easeInOutSine"), (18, [-60, 30, 0], "easeInOutSine"),
                  (24, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (5, [-10, 0, -40]), (12, [-10, 0, -40], "easeInOutSine"), (18, [-60, -30, 0], "easeInOutSine"),
                 (24, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (5, [-6, 0, 0]), (12, [-6, 0, 0], "easeInOutSine"), (18, [6, 0, 0], "easeInOutSine"), (24, ZERO)],
       right_leg=[(0, ZERO, "easeOutQuad"), (5, [-15, 0, 12]), (18, [-15, 0, 12], "easeInOutSine"), (24, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (5, [15, 0, -12]), (18, [15, 0, -12], "easeInOutSine"), (24, ZERO)])

# 気功・分け与え：掌を仲間へ向ける
define("monk_ki_give", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-90, -10, 0]), (10, [-90, -10, 0], "easeInOutSine"), (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-60, 20, 0]), (10, [-60, 20, 0], "easeInOutSine"), (14, L_IDLE)])

# 縮地：一瞬で踏み込む（身を低く）
define("monk_step", 8,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [30, 0, 15]), (4, [-95, 0, 0]), (6, [-95, 0, 0], "easeInOutSine"), (8, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [30, 0, -15]), (6, [-20, 0, -10], "easeInOutSine"), (8, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [25, 0, 0]), (4, [12, 0, 0]), (6, [12, 0, 0], "easeInOutSine"), (8, ZERO)])

# 気弾：両の掌を合わせて、前へ押し出す
define("monk_push", 10,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-40, 30, 0], "easeInQuad"), (4, [-90, 15, 0]), (7, [-90, 15, 0], "easeInOutSine"),
                  (10, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-40, -30, 0], "easeInQuad"), (4, [-90, -15, 0]), (7, [-90, -15, 0], "easeInOutSine"),
                 (10, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [-4, 0, 0], "easeInQuad"), (4, [10, 0, 0]), (7, [10, 0, 0], "easeInOutSine"), (10, ZERO)])

# 転身投げ：つかんで、背負って投げる
define("monk_throw", 14,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-90, -10, 0]), (5, [-170, 0, 0], "easeInQuad"), (8, [-30, 0, 0]),
                  (11, [-30, 0, 0], "easeInOutSine"), (14, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-90, 10, 0]), (5, [-170, 0, 0], "easeInQuad"), (8, [-30, 0, 0]),
                 (11, [-30, 0, 0], "easeInOutSine"), (14, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [6, 0, 0]), (5, [-10, 90, 0], "easeInQuad"), (8, [30, 180, 0]), (11, [30, 180, 0], "easeInOutSine"),
             (14, {"pre": [0, 360, 0], "post": [0, 0, 0]})])

# 気の渦：両手で大きく円を描き、引き寄せる
define("monk_vortex", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-90, 0, 70]), (7, [-150, 0, 20]), (11, [-60, 30, 0]), (13, [-60, 30, 0], "easeInOutSine"),
                  (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-90, 0, -70]), (7, [-150, 0, -20]), (11, [-60, -30, 0]), (13, [-60, -30, 0], "easeInOutSine"),
                 (16, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (7, [-4, 0, 0]), (11, [8, 0, 0]), (13, [8, 0, 0], "easeInOutSine"), (16, ZERO)])

# ================================================================ Phase 6 その2：ナイト・ウィザード（足した段）
# 鉄の誓い：剣を胸に当て、盾を仲間へ向ける
define("knight_vow", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-70, 40, 0]), (12, [-70, 40, 0], "easeInOutSine"), (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-90, -30, 0]), (12, [-90, -30, 0], "easeInOutSine"), (16, L_IDLE)])

# 盾打ち：盾を前へ叩きつける
define("knight_bash", 10,
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-60, 40, 0], "easeInQuad"), (4, [-95, 0, 0]), (7, [-95, 0, 0], "easeInOutSine"), (10, L_IDLE)],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-20, 0, 15]), (7, [-20, 0, 15], "easeInOutSine"), (10, R_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (2, [-4, -20, 0], "easeInQuad"), (4, [14, 15, 0]), (7, [14, 15, 0], "easeInOutSine"), (10, ZERO)],
       left_leg=[(0, ZERO, "easeOutQuad"), (4, [-30, 0, 0]), (7, [-30, 0, 0], "easeInOutSine"), (10, ZERO)])

# 大車輪：大剣を振り回し、回り続ける（2秒）
define("knight_great_wheel", 40,
       body=[(0, ZERO)] + [(2 + i * 8, [0, -180, 0]) if i % 2 == 0 else (2 + i * 8, {"pre": [0, -360, 0], "post": [0, 0, 0]})
                           for i in range(5)] + [(40, {"pre": [0, -360, 0], "post": [0, 0, 0]})],
       right_arm=[(0, R_IDLE, "easeOutQuad"), (2, [-90, 0, 85]), (36, [-90, 0, 85], "easeInOutSine"), (40, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (2, [-90, 0, -60]), (36, [-90, 0, -60], "easeInOutSine"), (40, L_IDLE)])

# 地裂き：大剣を振り上げ、地面へ打ち下ろす
define("knight_earth_split", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-175, 0, -5], "easeInQuad"), (7, [-35, 0, 0]), (12, [-35, 0, 0], "easeInOutSine"),
                  (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-170, 0, 5], "easeInQuad"), (7, [-40, 0, 0]), (12, [-40, 0, 0], "easeInOutSine"),
                 (16, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [-10, 0, 0], "easeInQuad"), (7, [26, 0, 0]), (12, [26, 0, 0], "easeInOutSine"), (16, ZERO)],
       right_leg=[(0, ZERO), (7, [-30, 0, 6]), (12, [-30, 0, 6], "easeInOutSine"), (16, ZERO)],
       left_leg=[(0, ZERO), (7, [25, 0, -6]), (12, [25, 0, -6], "easeInOutSine"), (16, ZERO)])

# 星の環：杖で頭の上に円を描く
define("wizard_star_ring", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-160, 0, 30]), (6, [-160, 30, 0]), (9, [-160, 0, -30]), (12, [-160, -30, 0], "easeInOutSine"),
                  (16, R_IDLE)])

# 彗星（詠唱）：杖を天へ、星を呼ぶ。くり返し
define("wizard_comet_cast", 20, loop=True,
       right_arm=[(0, [-165, 0, -10], "easeInOutSine"), (10, [-170, 0, -6], "easeInOutSine"), (20, [-165, 0, -10])],
       left_arm=[(0, [-100, 0, -30], "easeInOutSine"), (10, [-106, 0, -26], "easeInOutSine"), (20, [-100, 0, -30])])

# 彗星（放つ）：天の杖を、前へ振り下ろす
define("wizard_comet", 12,
       right_arm=[(0, [-165, 0, -10], "easeInQuad"), (3, [-80, 0, 0]), (8, [-80, 0, 0], "easeInOutSine"), (12, R_IDLE)],
       left_arm=[(0, [-100, 0, -30], "easeInQuad"), (3, [-20, 0, -30]), (8, [-20, 0, -30], "easeInOutSine"), (12, L_IDLE)],
       body=[(0, ZERO, "easeInQuad"), (3, [12, 0, 0]), (8, [12, 0, 0], "easeInOutSine"), (12, ZERO)])

# 吹雪：杖を掲げて、大きく回す
define("wizard_blizzard", 16,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (3, [-150, 0, 30]), (6, [-150, 30, 0]), (9, [-150, 0, -30]), (12, [-150, -30, 0], "easeInOutSine"),
                  (16, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (3, [-70, 0, -40]), (12, [-70, 0, -40], "easeInOutSine"), (16, L_IDLE)])

# 絶対零度：杖を地面へ突き立て、身を沈める
define("wizard_absolute_zero", 20,
       right_arm=[(0, R_IDLE, "easeOutQuad"), (4, [-175, 0, -5], "easeInQuad"), (7, [-40, 0, 0]), (15, [-40, 0, 0], "easeInOutSine"),
                  (20, R_IDLE)],
       left_arm=[(0, L_IDLE, "easeOutQuad"), (4, [-60, 0, -40]), (7, [-10, 0, -70]), (15, [-10, 0, -70], "easeInOutSine"), (20, L_IDLE)],
       body=[(0, ZERO, "easeOutQuad"), (4, [-8, 0, 0], "easeInQuad"), (7, [24, 0, 0]), (15, [24, 0, 0], "easeInOutSine"), (20, ZERO)],
       right_leg=[(0, ZERO), (7, [-40, 0, 8]), (15, [-40, 0, 8], "easeInOutSine"), (20, ZERO)],
       left_leg=[(0, ZERO), (7, [35, 0, -8]), (15, [35, 0, -8], "easeInOutSine"), (20, ZERO)])


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, data in ANIMS.items():
        path = os.path.join(OUT, name + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("wrote", path)
    print(len(ANIMS), "animations")
