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


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, data in ANIMS.items():
        path = os.path.join(OUT, name + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("wrote", path)
    print(len(ANIMS), "animations")
