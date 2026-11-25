"""ボスごとの曲を作る（11月25日のご指示「ボス戦ではもっとエフェクトやボス固有のBGMを追加する」）。

5体のボスに1曲ずつ。どれもこのファイルの楽譜と synth.py の計算だけで作る、オリジナルの曲です（既存の曲・録音は使わない）。
くり返し流しても切れ目がないように作り、Ogg Vorbis で書き出して、sounds.json に「music.boss.<ボス>」として足す。

  python3 tools/audio/boss_music.py            # 5曲を作り直す
  python3 tools/audio/boss_music.py veldhorn   # 1曲だけ
  python3 tools/audio/boss_music.py --wav /tmp/wav  # 確かめ用に WAV も書き出す

曲（【補完】題・調・速さはこちらで決めたもの）：
- 1階 ヴェルドホーン「草原を裂く角」：ニ短調・148。太鼓と駆けるベース、金管の旋律
- 2階 オールドウォード「古樹は目を覚ます」：ホ短調（ドリア）・112。木の打楽器・カリンバ・笛・森の合唱
- 3階 ハイドラム「大水門の機関」：ハ短調・136。機械の脈打つベース・金属の打撃・水の雫・こだまするシンセ
- 4階 カンターレ「祈りの残響」：イ短調（フリギア）・3拍子・138。合唱・鐘・オルガン・ティンパニ
- 5階 古代守護者「祭壇の守護者」：嬰ハ短調・156。弦の刻み・金管・合唱・太鼓・ティンパニ
- 6階 村長の残響オルド「凍てついた村の灯」：ニ短調・3拍子・132。オルゴールの鈴・弦・合唱・そりの鈴・ティンパニ
  （11月25日。雪の村のワルツが、追いこむほど重く速く聞こえるように）
"""

import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import synth as S  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "amemaru")
OUT_DIR = os.path.join(ASSETS, "sounds", "music")

TITLES = {
    "veldhorn": "草原を裂く角",
    "old_ward": "古樹は目を覚ます",
    "hydram": "大水門の機関",
    "cantare": "祈りの残響",
    "ancient_guardian": "祭壇の守護者",
    "ordo": "凍てついた村の灯",
}


# ---------------------------------------------------------------- 楽譜の道具

def line(text):
    """"D5:1 A4:.5 r:.5 | ..." → [(音名 か None, 拍)]。| は読みやすさのためだけ。"""
    out = []
    for tok in text.replace("|", " ").split():
        name, beats = tok.split(":")
        out.append((None if name == "r" else name, float(beats)))
    return out


def play(song, bus, bar, text, inst, pan=0.0, gain=1.0, legato=0.92, octave=0):
    t = song.beat(bar)
    for name, beats in line(text):
        if name is not None:
            f = S.freq(S.midi(name) + 12 * octave)
            song.put(bus, t, inst(f, song.dur(beats) * legato), pan=pan, gain=gain)
        t += song.dur(beats)


def triad(names):
    return [S.freq(n) for n in names.split()]


def chords_each_bar(song, bus, bars, voicings, inst, beats=None, pan=0.0, gain=1.0):
    beats = beats or song.beats_per_bar
    for bar, v in zip(bars, voicings):
        song.put(bus, song.beat(bar), inst(triad(v), song.dur(beats) * 0.98), pan=pan, gain=gain)


def arpeggio(song, bus, bar, notes, order, step_beats, inst, pan=0.0, gain=1.0, length=0.9, pan_walk=0.0):
    """和音の音を order の順に、step_beats ごとに鳴らす（1小節分）。"""
    count = int(round(song.beats_per_bar / step_beats))
    fs = [S.freq(n) for n in notes.split()]
    for k in range(count):
        f = fs[order[k % len(order)] % len(fs)]
        p = pan + (pan_walk * (1 if k % 2 == 0 else -1))
        song.put(bus, song.beat(bar, k * step_beats), inst(f, song.dur(step_beats) * length), pan=p, gain=gain)


# ---------------------------------------------------------------- 1階 ヴェルドホーン

def veldhorn():
    song = S.Song(148, 32, seed=11)
    rng = song.rng
    song.bus("kick", -15.0, 0.05)
    song.bus("snare", -18.5, 0.22)
    song.bus("hats", -27.0, 0.12)
    song.bus("taiko", -18.0, 0.3)
    song.bus("boom", -23.0, 0.25)
    song.bus("riser", -27.0, 0.3)
    song.bus("bass", -19.0, 0.04)
    song.bus("pad", -26.0, 0.45)
    song.bus("ost", -24.5, 0.25)
    song.bus("stab", -22.0, 0.3)
    song.bus("lead", -18.5, 0.3)

    roots = ["D2", "D2", "Bb1", "C2", "D2", "D2", "Bb1", "A1",
             "G1", "D2", "Bb1", "F1", "G1", "D2", "Eb2", "A1",
             "D2", "D2", "Bb1", "C2", "D2", "D2", "Bb1", "A1",
             "Bb1", "C2", "D2", "D2", "Bb1", "C2", "A1", "A1"]
    voice = {"D2": "D3 A3 D4 F4", "Bb1": "Bb2 F3 Bb3 D4", "C2": "C3 G3 C4 E4", "A1": "A2 E3 A3 C#4",
             "G1": "G2 D3 G3 Bb3", "F1": "F2 C3 F3 A3", "Eb2": "Eb3 Bb3 Eb4 G4"}
    arp = {"D2": "D4 F4 A4 D5", "Bb1": "Bb3 D4 F4 Bb4", "C2": "C4 E4 G4 C5", "A1": "A3 C#4 E4 A4",
           "G1": "G3 Bb3 D4 G4", "F1": "F3 A3 C4 F4", "Eb2": "Eb4 G4 Bb4 Eb5"}

    kick = [S.kick(rng, 1.0) for _ in range(3)]
    sn = [S.snare(rng) for _ in range(3)]
    hc = [S.hat(rng) for _ in range(4)]
    ho = S.hat(rng, True)
    pick = lambda xs: (lambda: xs[int(rng.integers(len(xs)))])  # noqa: E731

    for bar in range(32):
        sec = bar // 8
        # キック・スネア・ハイハット
        if sec == 3 and bar >= 28:
            song.pattern("kick", pick(kick), "x...x...x...x...", [bar])
        elif sec == 1:
            song.pattern("kick", pick(kick), "x.....x.x.....x.", [bar])
        else:
            song.pattern("kick", pick(kick), "x.......x.....x.", [bar])
        if bar in (7, 23):
            song.pattern("snare", pick(sn), "....x.......x.xx", [bar])
        elif bar == 31:
            song.pattern("snare", pick(sn), "x.x.x.x.xxxxxxxx", [bar], gain=0.8)
        elif bar == 30:
            song.pattern("snare", pick(sn), "....x...x.x.x.x.", [bar], gain=0.8)
        else:
            song.pattern("snare", pick(sn), "....x.......x...", [bar])
        if sec in (1, 2):
            song.pattern("hats", pick(hc), "xoooxoooxoooxooo", [bar], humanize=0.004)
        else:
            song.pattern("hats", pick(hc), "x.o.x.o.x.o.x.o.", [bar], humanize=0.004)
        if bar % 4 == 3:
            song.put("hats", song.beat(bar, 3.5), ho, gain=0.7)
        # 太鼓：2小節ごとの頭と、区切りの前の連打
        if bar % 2 == 0:
            song.put("taiko", song.beat(bar), S.taiko(rng, 62.0), pan=-0.2)
        if bar % 8 == 7:
            for k, b in enumerate((2.0, 2.5, 3.0, 3.25, 3.5, 3.75)):
                song.put("taiko", song.beat(bar, b), S.taiko(rng, 80.0 + 12 * (k % 2), 0.7), pan=0.3 * (1 if k % 2 else -1), gain=0.8)
        if bar % 8 == 0:
            song.put("boom", song.beat(bar), S.boom(rng))
        if bar % 8 == 7 and bar != 31:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(4)))
        if bar == 30:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(8)))
        # 駆けるベース（タ・タタ）
        root = S.freq(roots[bar])
        for beat in range(4):
            for step in (0.0, 0.5, 0.75):
                song.put("bass", song.beat(bar, beat + step), S.bass(root, song.dur(0.22), bright=650.0), gain=1.0 if step == 0 else 0.8)
        # 弦の和音（ずっと）と、刻み（Aの前半はなし）
        song.put("pad", song.beat(bar), S.strings(triad(voice[roots[bar]]), song.dur(4) * 0.98, a=0.3), gain=0.8 if sec == 0 else 1.0)
        if not (sec == 0 and bar < 4):
            arpeggio(song, "ost", bar, arp[roots[bar]], [0, 2, 3, 2, 1, 2, 3, 2], 0.5, S.staccato_strings, pan=0.35, gain=0.85 if sec == 0 else 1.0)
        # 金管：Aは小節の頭で短く、Cは長く
        if sec == 3 and bar < 28:
            song.put("stab", song.beat(bar), S.brass(triad(voice[roots[bar]]), song.dur(3.8), a=0.25, bright=2200.0), pan=-0.25)
        elif bar % 2 == 0 or sec == 2:
            song.put("stab", song.beat(bar), S.brass(triad(voice[roots[bar]]), song.dur(0.7)), pan=-0.25)
            song.put("stab", song.beat(bar, 2.5), S.brass(triad(voice[roots[bar]]), song.dur(0.4)), pan=-0.25, gain=0.75)

    melody_a = ("D5:1.5 E5:.5 F5:1 A5:1 | G5:1 F5:.5 E5:.5 D5:2 | Bb4:1 D5:1 F5:1 G5:.5 F5:.5 | E5:2 C5:1 E5:1 |"
                " D5:1.5 E5:.5 F5:1 A5:1 | C6:1 Bb5:.5 A5:.5 G5:1 F5:1 | Bb5:1.5 A5:.5 G5:1 F5:1 | E5:1 F5:.5 E5:.5 C#5:2")
    melody_b = ("D5:2 Bb4:1 G4:1 | A4:3 F4:1 | D5:1.5 C5:.5 Bb4:1 D5:1 | C5:3 r:1 |"
                " G5:2 F5:1 D5:1 | F5:2 E5:1 D5:1 | Eb5:1.5 D5:.5 C5:1 Bb4:1 | A4:2 C#5:1 E5:1")
    melody_c = "r:4 | r:4 | r:4 | r:4 | F5:1 D5:1 F5:1 Bb5:1 | G5:1 E5:1 G5:1 C6:1 | A5:2 E5:1 C#5:1 | A5:4"
    brass_lead = lambda f, on: S.brass([f], on, bright=3000.0, a=0.02)  # noqa: E731
    saw_lead = lambda f, on: S.lead(f, on, kind="saw", bright=3800.0)  # noqa: E731
    play(song, "lead", 0, melody_a, brass_lead, pan=0.1)
    play(song, "lead", 8, melody_b, lambda f, on: S.strings([f], on, a=0.06, r=0.3, bright=3500.0), pan=-0.1, gain=1.3, octave=1)
    play(song, "lead", 16, melody_a, brass_lead, pan=0.1)
    play(song, "lead", 16, melody_a, saw_lead, pan=-0.15, gain=0.5, octave=-1)
    play(song, "lead", 24, melody_c, brass_lead, pan=0.1)
    return song, S.reverb_ir(1.9, 0.02, 7000.0, seed=3)


# ---------------------------------------------------------------- 2階 オールドウォード

def old_ward():
    song = S.Song(112, 32, seed=22)
    rng = song.rng
    song.bus("logs", -19.0, 0.3)
    song.bus("wood", -27.0, 0.3)
    song.bus("shaker", -29.0, 0.2)
    song.bus("taiko", -19.0, 0.35)
    song.bus("kal", -21.0, 0.35, echo_beats=0.75, echo_fb=0.35)
    song.bus("cello", -21.0, 0.35)
    song.bus("choir", -24.0, 0.55)
    song.bus("flute", -19.0, 0.4)
    song.bus("brass", -23.0, 0.35)
    song.bus("trem", -26.0, 0.3)
    song.bus("riser", -28.0, 0.4)

    names = ["Em", "Em", "C", "D", "Em", "Em", "C", "B",
             "Am", "Em", "C", "B", "Am", "Em", "C", "B",
             "Em", "D", "C", "D", "Em", "D", "C", "B",
             "C", "D", "Em", "Em", "C", "D", "B", "B"]
    voice = {"Em": "E3 B3 E4 G4", "C": "C3 G3 C4 E4", "D": "D3 A3 D4 F#4", "B": "B2 F#3 B3 D#4", "Am": "A2 E3 A3 C4"}
    low = {"Em": "E2", "C": "C2", "D": "D2", "B": "B1", "Am": "A1"}
    kal = {"Em": "E4 G4 B4 E5", "C": "C4 E4 G4 C5", "D": "D4 F#4 A4 D5", "B": "B3 D#4 F#4 B4", "Am": "A3 C4 E4 A4"}
    log_hi = [S.tom(rng, 180.0, 0.25) for _ in range(2)]
    log_lo = [S.tom(rng, 120.0, 0.3) for _ in range(2)]
    wb = [S.woodblock(rng, 950.0), S.woodblock(rng, 1250.0)]
    sh = [S.shaker(rng) for _ in range(4)]
    pick = lambda xs: (lambda: xs[int(rng.integers(len(xs)))])  # noqa: E731

    for bar in range(32):
        sec = bar // 8
        name = names[bar]
        # 木の太鼓（高・低）と拍子木
        song.pattern("logs", pick(log_lo), "x.....x...x.....", [bar], pan=-0.2, humanize=0.006)
        song.pattern("logs", pick(log_hi), "...x.....x...x.x" if sec >= 2 else "...x.......x....", [bar], pan=0.25, humanize=0.006)
        song.pattern("wood", pick(wb), "..o...o...o...o.", [bar], pan=0.5, humanize=0.004)
        if sec in (1, 2, 3):
            song.pattern("shaker", pick(sh), "xoooxoooxoooxooo", [bar], pan=-0.4, humanize=0.006)
        if sec >= 2 and bar % 2 == 0:
            song.put("taiko", song.beat(bar), S.taiko(rng, 58.0, 1.3))
        if sec == 3:
            song.pattern("taiko", lambda: S.taiko(rng, 72.0, 0.6), "x...x...x...x...", [bar], gain=0.6)
        if bar % 8 == 7:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(4), 6000.0))
        # カリンバ（ずっと。こだまが左右に）
        arpeggio(song, "kal", bar, kal[name], [0, 2, 3, 1, 2, 3, 1, 2], 0.5, lambda f, on: S.kalimba(f, on), pan=0.0, pan_walk=0.35)
        # 低い弦（チェロ）
        song.put("cello", song.beat(bar), S.strings([S.freq(low[name]), S.freq(low[name]) * 2], song.dur(4) * 0.98, a=0.4, r=0.8, bright=1600.0))
        # 森の合唱（B と A'）
        if sec in (1, 2):
            song.put("choir", song.beat(bar), S.choir(triad(voice[name]), song.dur(4) * 0.97, vowel="u"))
        if sec == 3 and bar % 2 == 0:
            song.put("choir", song.beat(bar), S.choir(triad(voice[name]), song.dur(8) * 0.97, vowel="o", a=0.8))
        # C：低い金管と、刻む弦
        if sec == 3:
            song.put("brass", song.beat(bar), S.brass(triad(voice[name]), song.dur(3.6), a=0.3, bright=1500.0), pan=-0.2)
            arpeggio(song, "trem", bar, kal[name], [0, 0, 2, 2, 1, 1, 3, 3], 0.25, lambda f, on: S.staccato_strings(f * 0.5, on, 2500.0), pan=0.3)

    flute = lambda f, on: S.flute(f, on, rng)  # noqa: E731
    melody_b = ("E5:1.5 D5:.5 C5:1 B4:1 | G4:2 B4:1 E5:1 | G5:1.5 F#5:.5 E5:1 D5:1 | D#5:2 F#5:1 B4:1 |"
                " C5:1 E5:1 A5:1.5 G5:.5 | F#5:1 E5:1 B4:2 | C5:1 E5:.5 G5:.5 F#5:1 E5:1 | D#5:3 r:1")
    melody_a2 = ("B4:1 E5:1 F#5:1 G5:1 | A5:1.5 G5:.5 F#5:2 | E5:1 G5:1 C6:1 B5:1 | A5:3 F#5:1 |"
                 " G5:1.5 F#5:.5 E5:1 B4:1 | D5:1 E5:.5 F#5:.5 A5:2 | G5:1 E5:1 C5:1 E5:1 | D#5:4")
    play(song, "flute", 8, melody_b, flute, pan=0.15)
    play(song, "flute", 16, melody_a2, flute, pan=0.15)
    play(song, "flute", 16, melody_a2, lambda f, on: S.kalimba(f, on), pan=-0.3, gain=0.5, octave=-1)
    melody_c = "r:4 | r:4 | E5:1 G5:1 B5:2 | A5:1 G5:1 F#5:2 | E5:1 C5:1 E5:1 G5:1 | F#5:1 D5:1 F#5:1 A5:1 | B5:4 | D#5:4"
    play(song, "flute", 24, melody_c, flute, pan=0.15)
    return song, S.reverb_ir(2.8, 0.03, 5200.0, seed=5)


# ---------------------------------------------------------------- 3階 ハイドラム

def hydram():
    song = S.Song(136, 32, seed=33)
    rng = song.rng
    song.bus("kick", -15.5, 0.05)
    song.bus("clap", -19.5, 0.3)
    song.bus("hats", -26.0, 0.12)
    song.bus("metal", -22.0, 0.35)
    song.bus("toms", -21.0, 0.3)
    song.bus("boom", -21.0, 0.3)
    song.bus("drops", -24.0, 0.5, echo_beats=0.75, echo_fb=0.4)
    song.bus("bass", -18.5, 0.03)
    song.bus("pad", -25.0, 0.5)
    song.bus("arp", -23.0, 0.25, echo_beats=0.75, echo_fb=0.42)
    song.bus("lead", -19.0, 0.3)
    song.bus("horn", -21.0, 0.3)
    song.bus("riser", -27.0, 0.3)

    names = ["Cm", "Cm", "Ab", "Bb", "Cm", "Cm", "Ab", "G",
             "Fm", "Cm", "Ab", "Eb", "Fm", "Cm", "Db", "G",
             "Cm", "Cm", "Ab", "Bb", "Cm", "Cm", "Ab", "G",
             "Ab", "Bb", "Cm", "Cm", "Ab", "Bb", "G", "G"]
    root = {"Cm": "C2", "Ab": "Ab1", "Bb": "Bb1", "G": "G1", "Fm": "F1", "Eb": "Eb2", "Db": "Db2"}
    voice = {"Cm": "C3 G3 C4 Eb4", "Ab": "Ab2 Eb3 Ab3 C4", "Bb": "Bb2 F3 Bb3 D4", "G": "G2 D3 G3 B3",
             "Fm": "F2 C3 F3 Ab3", "Eb": "Eb3 Bb3 Eb4 G4", "Db": "Db3 Ab3 Db4 F4"}
    arp = {"Cm": "C4 Eb4 G4 C5", "Ab": "Ab3 C4 Eb4 Ab4", "Bb": "Bb3 D4 F4 Bb4", "G": "G3 B3 D4 G4",
           "Fm": "F3 Ab3 C4 F4", "Eb": "Eb4 G4 Bb4 Eb5", "Db": "Db4 F4 Ab4 Db5"}
    kick = [S.kick(rng, 1.1, 0.45, 44.0) for _ in range(3)]
    clap = [S.snare(rng, 230.0, 0.28, 1.2) for _ in range(3)]
    hats = [S.hat(rng) for _ in range(4)]
    pick = lambda xs: (lambda: xs[int(rng.integers(len(xs)))])  # noqa: E731
    drop_notes = ["C5", "Eb5", "G5", "Bb5", "C6", "G5"]

    for bar in range(32):
        sec = bar // 8
        name = names[bar]
        breakdown = sec == 3 and bar < 28
        if not breakdown:
            song.pattern("kick", pick(kick), "x..x..x...x..x.." if sec != 1 else "x..x..x...x.....", [bar])
            song.pattern("clap", pick(clap), "....x.......x...", [bar])
            song.pattern("hats", pick(hats), "xoxoxoxoxoxoxoxo", [bar], humanize=0.003, accent=4)
            for k, step in enumerate((6, 14)):
                song.put("metal", song.beat(bar, step * 0.25), S.metal_hit(rng, 300.0 + 40 * k, 0.9, 5.0), pan=-0.5 if k == 0 else 0.5, gain=0.8)
        else:
            song.pattern("kick", pick(kick), "x...............", [bar])
            if bar >= 26:
                song.pattern("toms", lambda: S.tom(rng, 95.0 + 10 * rng.random(), 0.45), "x.x.x.x.x.x.xxxx", [bar], gain=0.7 + 0.1 * (bar - 26))
        if bar in (28, 29, 30, 31):
            song.pattern("kick", pick(kick), "x...x...x...x...", [bar])
        if bar % 8 == 0:
            song.put("boom", song.beat(bar), S.boom(rng, 2.8, 38.0))
            song.put("metal", song.beat(bar), S.metal_hit(rng, 180.0, 2.4, 8.0), gain=0.9)
        if bar % 8 == 7:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(4)))
        # 水の雫（Aと、Cのはじめ）
        if sec in (0, 3):
            for k in range(3):
                beat = float(rng.integers(0, 16)) * 0.25
                song.put("drops", song.beat(bar, beat), S.drop(rng, S.freq(drop_notes[int(rng.integers(len(drop_notes)))])),
                         pan=float(rng.uniform(-0.7, 0.7)), gain=0.8)
        # 脈打つベース（16分）
        if not breakdown:
            r = S.freq(root[name])
            for step in range(16):
                f = r * (2.0 if step in (3, 11) else 1.0)
                song.put("bass", song.beat(bar, step * 0.25), S.bass(f, song.dur(0.2), kind="square", bright=900.0, sub=0.6),
                         gain=1.0 if step % 4 == 0 else 0.75)
        else:
            song.put("bass", song.beat(bar), S.bass(S.freq(root[name]), song.dur(3.8), kind="saw", bright=400.0, sub=0.8, r=0.5))
        # 水の中のような和音
        song.put("pad", song.beat(bar), S.pad(triad(voice[name]), song.dur(4) * 0.98, bright=1400.0 if sec != 3 else 900.0))
        # こだまするシンセ（Bと A'）
        if sec in (1, 2):
            arpeggio(song, "arp", bar, arp[name], [0, 1, 2, 3, 2, 1, 3, 2, 0, 1, 2, 3, 2, 1, 2, 3], 0.25, lambda f, on: S.arp_synth(f, on),
                     pan=0.0, pan_walk=0.2)

    horn = lambda f, on: S.brass([f], on, bright=1800.0, a=0.05)  # noqa: E731
    melody_b = ("C5:2 Ab4:1 F4:1 | G4:3 Eb4:1 | C5:1.5 Bb4:.5 Ab4:1 C5:1 | Bb4:3 r:1 |"
                " F5:2 Eb5:1 C5:1 | Eb5:2 D5:1 C5:1 | Db5:1.5 C5:.5 Bb4:1 Ab4:1 | G4:2 B4:1 D5:1")
    melody_a2 = ("G5:1 Eb5:.5 F5:.5 G5:1 Bb5:1 | C6:1.5 Bb5:.5 G5:2 | Ab5:1 G5:.5 F5:.5 Eb5:1 C5:1 | D5:2 F5:1 Bb5:1 |"
                 " G5:1 Eb5:.5 F5:.5 G5:1 C6:1 | Eb6:1.5 D6:.5 C6:1 G5:1 | Ab5:1 C6:1 Bb5:1 Ab5:1 | G5:2 B4:1 D5:1")
    melody_c = "r:4 | r:4 | r:4 | r:4 | C5:1 Eb5:1 Ab5:2 | D5:1 F5:1 Bb5:2 | B5:2 G5:1 D5:1 | G5:4"
    play(song, "horn", 8, melody_b, horn, pan=-0.1)
    play(song, "lead", 16, melody_a2, lambda f, on: S.lead(f, on, kind="square", bright=3000.0), pan=0.1)
    play(song, "lead", 24, melody_c, lambda f, on: S.lead(f, on, kind="saw", bright=2600.0), pan=0.1)
    play(song, "horn", 24, melody_c, horn, pan=-0.1, gain=0.8, octave=-1)
    return song, S.reverb_ir(1.7, 0.015, 8000.0, seed=9)


# ---------------------------------------------------------------- 4階 カンターレ

def cantare():
    song = S.Song(138, 48, beats_per_bar=3, seed=44)
    rng = song.rng
    song.bus("bells", -20.0, 0.55)
    song.bus("choir", -21.0, 0.6)
    song.bus("lowchoir", -23.0, 0.5)
    song.bus("organ", -22.0, 0.45)
    song.bus("frame", -20.0, 0.35)
    song.bus("shaker", -29.0, 0.25)
    song.bus("timp", -19.0, 0.4)
    song.bus("brass", -22.0, 0.4)
    song.bus("ost", -24.0, 0.3)
    song.bus("riser", -28.0, 0.5)

    names = (["Am", "Am", "Bb", "Bb", "Am", "Am", "G", "G", "F", "F", "E", "E"]
             + ["Dm", "Dm", "Am", "Am", "Bb", "Bb", "F", "F", "Dm", "Dm", "E", "E"]
             + ["Am", "Am", "Bb", "Bb", "Am", "Am", "G", "G", "F", "F", "E", "E"]
             + ["F", "G", "Am", "Am", "F", "G", "E", "E", "Bb", "Bb", "E", "E"])
    voice = {"Am": "A3 C4 E4 A4", "Bb": "Bb3 D4 F4 Bb4", "G": "G3 B3 D4 G4", "F": "F3 A3 C4 F4", "E": "E3 G#3 B3 E4",
             "Dm": "D3 F3 A3 D4"}
    low = {"Am": "A1", "Bb": "Bb1", "G": "G1", "F": "F1", "E": "E1", "Dm": "D2"}
    ost = {"Am": "A3 C4 E4", "Bb": "Bb3 D4 F4", "G": "G3 B3 D4", "F": "F3 A3 C4", "E": "E3 G#3 B3", "Dm": "D3 F3 A3"}
    frame = [S.taiko(rng, 95.0, 0.9, 0.6) for _ in range(2)]
    sh = [S.shaker(rng) for _ in range(4)]
    pick = lambda xs: (lambda: xs[int(rng.integers(len(xs)))])  # noqa: E731

    for bar in range(48):
        sec = bar // 12
        name = names[bar]
        # 合唱の和音（2小節ずつ）
        if bar % 2 == 0:
            vowel = "a" if sec in (0, 2) else "o"
            song.put("choir", song.beat(bar), S.choir(triad(voice[name]), song.dur(6) * 0.97, vowel=vowel, a=0.5, r=1.0),
                     gain=0.8 if sec == 0 else 1.0)
        # 低い声の刻み（Bから）
        if sec >= 1:
            song.put("lowchoir", song.beat(bar), S.choir([S.freq(low[name]) * 2], song.dur(0.9), vowel="a", a=0.03, r=0.25), pan=-0.2)
            song.put("lowchoir", song.beat(bar, 2), S.choir([S.freq(low[name]) * 2], song.dur(0.6), vowel="a", a=0.03, r=0.2),
                     pan=-0.2, gain=0.7)
        # オルガンの低い音（Bから）
        if sec >= 1 and bar % 2 == 0:
            song.put("organ", song.beat(bar), S.organ([S.freq(low[name]) * 2, S.freq(low[name]) * 3], song.dur(6) * 0.97))
        # 太鼓とシェイカー
        if sec >= 1:
            song.put("frame", song.beat(bar), pick(frame)(), pan=0.1)
            if sec >= 2:
                song.put("frame", song.beat(bar, 2), pick(frame)(), pan=-0.1, gain=0.55)
            song.pattern("shaker", pick(sh), "xoxoxo", [bar], steps_per_bar=6, pan=0.4, humanize=0.006)
        if sec >= 2 and bar % 2 == 0:
            song.put("timp", song.beat(bar), S.timpani(rng, S.freq(low[name]) * 2))
        if bar in (46, 47):
            for k in range(12):
                song.put("timp", song.beat(bar, k * 0.25), S.timpani(rng, S.freq("E2"), 0.6), gain=0.35 + 0.05 * k + 0.4 * (bar - 46))
        if bar == 46:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(6), 7000.0))
        if bar % 12 == 11 and bar != 47:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(3), 6000.0))
        # Cの金管と弦の刻み
        if sec == 3:
            song.put("brass", song.beat(bar), S.brass(triad(voice[name]), song.dur(2.8), a=0.15, bright=2200.0), pan=-0.2)
            arpeggio(song, "ost", bar, ost[name], [0, 1, 2, 1, 2, 1], 0.5, S.staccato_strings, pan=0.3)

    bell = lambda f, on: S.bell(f, 2.4)  # noqa: E731
    melody_a = ("A5:1 C6:1 E6:1 | D6:2 C6:1 | Bb5:1 D6:1 F6:1 | E6:2 D6:1 | C6:1 B5:1 A5:1 | E5:3 |"
                " G5:1 B5:1 D6:1 | C6:2 B5:1 | A5:1 C6:1 F6:1 | E6:2 D6:1 | C6:1 B5:1 G#5:1 | E5:3")
    melody_b = ("D5:2 F5:1 | A5:3 | G5:1 F5:1 E5:1 | E5:3 | F5:2 D5:1 | Bb4:3 |"
                " C5:1 F5:1 A5:1 | G5:3 | F5:2 E5:1 | D5:3 | E5:1 G#5:1 B5:1 | E5:3")
    melody_c = ("A5:1 C6:1 A5:1 | B5:1 D6:1 B5:1 | C6:2 E6:1 | E6:3 | A5:1 C6:1 A5:1 | B5:1 D6:1 G6:1 | G#5:3 | B5:3 |"
                " Bb5:1 D6:1 F6:1 | D6:2 Bb5:1 | G#5:1 B5:1 E6:1 | E6:3")
    play(song, "bells", 0, melody_a, bell, pan=0.2)
    play(song, "choir", 12, melody_b, lambda f, on: S.choir([f], on, vowel="a", a=0.08, r=0.4), pan=0.1, gain=1.4)
    play(song, "bells", 24, melody_a, bell, pan=0.2)
    play(song, "choir", 24, melody_a, lambda f, on: S.choir([f], on, vowel="o", a=0.08, r=0.4), pan=-0.1, gain=1.2, octave=-1)
    play(song, "bells", 36, melody_c, bell, pan=0.2)
    play(song, "brass", 36, melody_c, lambda f, on: S.brass([f], on, bright=2600.0), pan=0.1, gain=0.9, octave=-1)
    return song, S.reverb_ir(4.2, 0.04, 6000.0, seed=13)


# ---------------------------------------------------------------- 5階 古代守護者

def ancient_guardian():
    song = S.Song(156, 40, seed=55)
    rng = song.rng
    song.bus("kick", -16.0, 0.08)
    song.bus("snare", -19.0, 0.3)
    song.bus("taiko", -17.5, 0.35)
    song.bus("timp", -19.0, 0.4)
    song.bus("boom", -20.0, 0.3)
    song.bus("hats", -28.0, 0.15)
    song.bus("bass", -19.5, 0.05)
    song.bus("ost", -22.0, 0.25)
    song.bus("pad", -25.0, 0.45)
    song.bus("brass", -20.0, 0.35)
    song.bus("choir", -22.0, 0.55)
    song.bus("bells", -24.0, 0.5)
    song.bus("lead", -18.5, 0.35)
    song.bus("riser", -26.0, 0.4)

    names = ["C#m", "C#m", "A", "A", "B", "B", "G#", "G#",
             "C#m", "A", "B", "G#m", "F#m", "D", "G#", "G#",
             "A", "B", "C#m", "C#m", "A", "B", "G#", "G#",
             "C#m", "C#m", "A", "A", "B", "B", "G#", "G#",
             "F#m", "G#m", "A", "B", "F#m", "G#m", "G#", "G#"]
    root = {"C#m": "C#2", "A": "A1", "B": "B1", "G#": "G#1", "G#m": "G#1", "F#m": "F#1", "D": "D2"}
    voice = {"C#m": "C#3 G#3 C#4 E4", "A": "A2 E3 A3 C#4", "B": "B2 F#3 B3 D#4", "G#": "G#2 D#3 G#3 B#3",
             "G#m": "G#2 D#3 G#3 B3", "F#m": "F#2 C#3 F#3 A3", "D": "D3 A3 D4 F#4"}
    kick = [S.kick(rng, 1.0) for _ in range(3)]
    sn = [S.snare(rng, 200.0) for _ in range(3)]
    hats = [S.hat(rng) for _ in range(4)]
    pick = lambda xs: (lambda: xs[int(rng.integers(len(xs)))])  # noqa: E731

    for bar in range(40):
        sec = bar // 8
        name = names[bar]
        half = sec == 2
        r = S.freq(root[name])
        if half:
            song.pattern("kick", pick(kick), "x...............", [bar])
            song.pattern("snare", pick(sn), "........x.......", [bar], gain=0.9)
            if bar % 2 == 0:
                song.put("timp", song.beat(bar), S.timpani(rng, r * 2))
        else:
            song.pattern("kick", pick(kick), "x..x....x..x..x." if sec in (1, 3) else "x.......x..x....", [bar])
            song.pattern("snare", pick(sn), "....x.......x...", [bar])
            song.pattern("hats", pick(hats), "x.x.x.x.x.x.x.x.", [bar], humanize=0.003)
            song.pattern("taiko", lambda: S.taiko(rng, 66.0, 0.9), "x.......x.......", [bar], pan=-0.15)
        if bar % 8 == 7:
            for k in range(8):
                song.put("taiko", song.beat(bar, 2 + k * 0.25), S.taiko(rng, 78.0 + 10 * (k % 2), 0.6), pan=0.35 * (1 if k % 2 else -1),
                         gain=0.6 + 0.05 * k)
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(4)))
        if bar % 8 == 0:
            song.put("boom", song.beat(bar), S.boom(rng, 2.6, 40.0))
        if bar in (38, 39):
            for k in range(16):
                song.put("timp", song.beat(bar, k * 0.25), S.timpani(rng, S.freq("G#2"), 0.5), gain=0.3 + 0.03 * k + 0.25 * (bar - 38))
        # ベース（8分）と弦の刻み（16分）
        if not half:
            for step in range(8):
                song.put("bass", song.beat(bar, step * 0.5), S.bass(r, song.dur(0.4), bright=600.0), gain=1.0 if step % 2 == 0 else 0.8)
            rr, ff = r * 4, r * 6
            seq = [rr, rr, ff, rr, rr * 2, rr, ff, rr, rr, rr, ff, rr, rr * 2, ff, rr * 2, ff]
            for k, f in enumerate(seq):
                song.put("ost", song.beat(bar, k * 0.25), S.staccato_strings(f, song.dur(0.2)), pan=0.3 if k % 2 else -0.3,
                         gain=1.0 if k % 4 == 0 else 0.75)
        else:
            song.put("bass", song.beat(bar), S.bass(r, song.dur(3.8), bright=350.0, sub=0.8, r=0.5))
        song.put("pad", song.beat(bar), S.strings(triad(voice[name]), song.dur(4) * 0.98, a=0.35, bright=2400.0))
        # 金管：A は打ちこみ、B と A' は長く、D は打ちこみ
        if sec in (0, 4):
            for b in (0.0, 1.5, 3.0):
                song.put("brass", song.beat(bar, b), S.brass(triad(voice[name]), song.dur(0.6)), pan=-0.2, gain=1.0 if b == 0 else 0.8)
        elif sec in (1, 3):
            song.put("brass", song.beat(bar), S.brass(triad(voice[name]), song.dur(3.8), a=0.12, bright=2400.0), pan=-0.2, gain=0.85)
        # 合唱（B・C・A'）と鐘（C）
        if sec in (1, 2, 3) and bar % 2 == 0:
            song.put("choir", song.beat(bar), S.choir(triad(voice[name]), song.dur(8) * 0.97, vowel="a" if sec != 2 else "o", a=0.4))
        if half:
            for b in (0.0, 1.5, 3.0):
                song.put("bells", song.beat(bar, b), S.bell(S.freq(voice[name].split()[2]) * 2, 2.0), pan=0.3, gain=0.8)

    brass_lead = lambda f, on: S.brass([f], on, bright=3200.0, a=0.02)  # noqa: E731
    melody_b = ("G#4:1.5 C#5:.5 E5:1 G#5:1 | F#5:1 E5:.5 D#5:.5 C#5:2 | D#5:1.5 E5:.5 F#5:1 B5:1 | G#5:2 F#5:1 D#5:1 |"
                " A5:1.5 G#5:.5 F#5:1 C#5:1 | D5:1 F#5:1 A5:1 D6:1 | G#5:1 B#5:1 D#6:1 B#5:1 | G#5:4")
    melody_c = ("r:4 | r:4 | E5:2 G#5:2 | F#5:1 E5:1 D#5:2 | C#5:2 E5:2 | D#5:1 F#5:1 B5:2 | B#5:2 G#5:2 | G#5:4")
    melody_a2 = ("C#5:1 E5:1 G#5:2 | F#5:1 E5:1 D#5:1 E5:1 | C#5:1.5 B4:.5 A4:1 C#5:1 | E5:3 r:1 |"
                 " D#5:1 F#5:1 B5:2 | A5:1 G#5:1 F#5:1 D#5:1 | B#4:1.5 D#5:.5 G#5:2 | G#5:4")
    melody_d = ("C#5:1 F#5:1 A5:2 | D#5:1 G#5:1 B5:2 | E5:1 A5:1 C#6:2 | F#5:1 B5:1 D#6:2 |"
                " A5:2 C#6:2 | B5:2 D#6:2 | G#5:4 | B#5:2 D#6:2")
    play(song, "lead", 8, melody_b, brass_lead, pan=0.1)
    play(song, "lead", 8, melody_b, lambda f, on: S.strings([f], on, a=0.05, r=0.25, bright=3600.0), pan=-0.2, gain=0.7, octave=-1)
    play(song, "lead", 16, melody_c, lambda f, on: S.choir([f], on, vowel="a", a=0.1, r=0.4), pan=0.0, gain=1.5)
    play(song, "lead", 24, melody_a2, brass_lead, pan=0.1)
    play(song, "lead", 24, melody_a2, lambda f, on: S.choir([f], on, vowel="a", a=0.08, r=0.3), pan=-0.1, gain=1.2)
    play(song, "lead", 32, melody_d, brass_lead, pan=0.1)
    return song, S.reverb_ir(2.4, 0.025, 7000.0, seed=17)


# ---------------------------------------------------------------- 6階 村長の残響オルド（11月25日）

def ordo():
    song = S.Song(132, 48, beats_per_bar=3, seed=66)
    rng = song.rng
    song.bus("box", -19.0, 0.6)
    song.bus("strings", -22.0, 0.5)
    song.bus("choir", -22.0, 0.55)
    song.bus("bass", -21.0, 0.25)
    song.bus("sleigh", -28.0, 0.3)
    song.bus("timp", -19.0, 0.4)
    song.bus("taiko", -21.0, 0.35)
    song.bus("ost", -24.0, 0.3)
    song.bus("brass", -23.0, 0.4)
    song.bus("riser", -28.0, 0.5)

    names = (["Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "A", "A", "Dm", "Dm", "F", "C", "Bb", "Gm", "A", "A"]
             + ["Gm", "Gm", "Dm", "Dm", "Bb", "Bb", "A", "A", "Gm", "C", "F", "Dm", "Bb", "Gm", "A", "A"]
             + ["Dm", "Bb", "Gm", "A", "Dm", "Bb", "C", "A", "Bb", "C", "Dm", "Dm", "Gm", "Bb", "A", "A"])
    voice = {"Dm": "D3 F3 A3 D4", "Bb": "Bb2 D3 F3 Bb3", "Gm": "G2 Bb2 D3 G3", "A": "A2 C#3 E3 A3", "F": "F2 A2 C3 F3",
             "C": "C3 E3 G3 C4"}
    low = {"Dm": "D2", "Bb": "Bb1", "Gm": "G1", "A": "A1", "F": "F1", "C": "C2"}
    ost = {"Dm": "D4 F4 A4", "Bb": "D4 F4 Bb4", "Gm": "D4 G4 Bb4", "A": "C#4 E4 A4", "F": "C4 F4 A4", "C": "C4 E4 G4"}
    sl = [S.shaker(rng, 0.06) for _ in range(4)]
    drum = [S.taiko(rng, 82.0, 0.9, 0.7) for _ in range(2)]
    pick = lambda xs: (lambda: xs[int(rng.integers(len(xs)))])  # noqa: E731

    for bar in range(48):
        sec = bar // 16
        name = names[bar]
        # 3拍子：1拍目に低い音、2・3拍目に和音（弦）
        song.put("bass", song.beat(bar), S.bass(S.freq(low[name]), song.dur(1.0), rng, bright=520.0, kind="sine", sub=0.7), pan=0.0)
        chord = triad(voice[name])
        for k in (1, 2):
            song.put("strings", song.beat(bar, k), S.strings(chord, song.dur(0.8), a=0.03, r=0.2, bright=2400.0), pan=0.15,
                     gain=0.7 if sec == 0 else 0.85)
        # そりの鈴（2から）
        if sec >= 1:
            song.pattern("sleigh", pick(sl), "xox", [bar], steps_per_bar=3, pan=0.4, humanize=0.006)
        # 合唱（2小節ずつ。3から強く）
        if bar % 2 == 0 and sec >= 1:
            song.put("choir", song.beat(bar), S.choir(chord, song.dur(6) * 0.97, vowel="o" if sec == 1 else "a", a=0.5, r=1.0),
                     gain=0.8 if sec == 1 else 1.0)
        # 太鼓とティンパニ（3から）
        if sec >= 2:
            song.put("taiko", song.beat(bar), pick(drum)(), pan=-0.1)
            if bar % 2 == 1:
                song.put("taiko", song.beat(bar, 2), pick(drum)(), pan=0.1, gain=0.6)
            arpeggio(song, "ost", bar, ost[name], [0, 1, 2, 1, 2, 1], 0.5, S.staccato_strings, pan=0.3)
        if sec >= 1 and bar % 4 == 0:
            song.put("timp", song.beat(bar), S.timpani(rng, S.freq(low[name]) * 2))
        if bar in (46, 47):
            for k in range(12):
                song.put("timp", song.beat(bar, k * 0.25), S.timpani(rng, S.freq("A1") * 2, 0.6), gain=0.35 + 0.05 * k + 0.4 * (bar - 46))
        if bar == 46:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(6), 6500.0))
        if bar % 16 == 15 and bar != 47:
            song.put("riser", song.beat(bar), S.riser(rng, song.dur(3), 5500.0))
        if sec == 2 and bar % 2 == 0:
            song.put("brass", song.beat(bar), S.brass(chord, song.dur(2.6), a=0.12, bright=2000.0), pan=-0.2, gain=0.8)

    box = lambda f, on: S.bell(f, 1.6, index=2.2, ratio=4.0)  # noqa: E731
    melody_a = ("A5:1 D6:1 E6:1 | F6:2 E6:1 | D6:1 F6:1 Bb6:1 | A6:3 | G6:1 F6:1 E6:1 | D6:2 Bb5:1 | A5:1 C#6:1 E6:1 | A5:3 |"
                " A5:1 D6:1 E6:1 | F6:2 G6:1 | A6:2 F6:1 | G6:1 E6:1 C6:1 | D6:2 Bb5:1 | Bb5:1 D6:1 G6:1 | E6:2 C#6:1 | A5:3")
    melody_b = ("Bb5:2 A5:1 | G5:3 | F5:1 A5:1 D6:1 | D6:3 | D6:2 C6:1 | Bb5:1 A5:1 G5:1 | A5:1 C#6:1 E6:1 | E6:3 |"
                " Bb5:2 A5:1 | G5:2 C6:1 | C6:1 A5:1 F5:1 | F5:3 | F5:1 G5:1 Bb5:1 | Bb5:2 G5:1 | A5:1 E5:1 C#5:1 | A4:3")
    melody_c = ("D6:1 F6:1 A6:1 | Bb6:2 A6:1 | G6:1 F6:1 E6:1 | E6:3 | D6:1 F6:1 A6:1 | Bb6:2 D7:1 | C7:1 Bb6:1 G6:1 | A6:3 |"
                " Bb6:2 A6:1 | C7:2 Bb6:1 | A6:1 F6:1 D6:1 | D6:3 | G6:1 F6:1 E6:1 | Bb6:2 G6:1 | E6:1 C#6:1 A5:1 | A5:3")
    play(song, "box", 0, melody_a, box, pan=0.25)
    play(song, "choir", 16, melody_b, lambda f, on: S.choir([f], on, vowel="a", a=0.08, r=0.4), pan=0.1, gain=1.4, octave=-1)
    play(song, "box", 16, melody_b, box, pan=0.25, gain=0.6)
    play(song, "box", 32, melody_c, box, pan=0.2)
    play(song, "brass", 32, melody_c, lambda f, on: S.brass([f], on, bright=2400.0), pan=0.1, gain=0.85, octave=-1)
    return song, S.reverb_ir(3.6, 0.035, 6500.0, seed=19)


SONGS = {
    "veldhorn": veldhorn,
    "old_ward": old_ward,
    "hydram": hydram,
    "cantare": cantare,
    "ancient_guardian": ancient_guardian,
    "ordo": ordo,
}


# ---------------------------------------------------------------- 書き出し

def encode(stereo, ogg_path, serial, wav_dir=None, name=""):
    pcm = np.clip(stereo.T, -1.0, 1.0)
    pcm16 = (pcm * 32767.0).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "in.wav")
        wavfile.write(wav, S.SR, pcm16)
        if wav_dir:
            os.makedirs(wav_dir, exist_ok=True)
            wavfile.write(os.path.join(wav_dir, name + ".wav"), S.SR, pcm16)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", "-serial_offset", str(serial), ogg_path], check=True)


def update_sounds(names):
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        sounds = json.load(f)
    for name in names:
        sounds["music.boss." + name] = {"sounds": [{"name": "amemaru:music/boss_" + name, "stream": True}]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(sounds, f, ensure_ascii=False, indent=2)
        f.write("\n")


def report(name, stereo, song):
    mono = stereo.mean(axis=0)
    peak = float(np.max(np.abs(stereo)))
    rms = float(np.sqrt(np.mean(stereo ** 2)))
    seam = float(np.max(np.abs(stereo[:, 0] - stereo[:, -1])))
    side = float(np.sqrt(np.mean((stereo[0] - stereo[1]) ** 2)) / max(1e-9, np.sqrt(np.mean(mono ** 2))))
    print(f"{name}: {song.length:.1f}秒・{song.bpm}BPM・{song.bars}小節 / ピーク {20 * np.log10(peak):.1f} dBFS・"
          f"RMS {20 * np.log10(rms):.1f} dBFS・つなぎ目の段差 {seam:.4f}・広がり {side:.2f}")


def main(argv):
    wav_dir = None
    if "--wav" in argv:
        i = argv.index("--wav")
        wav_dir = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    names = [a for a in argv if not a.startswith("-")] or list(SONGS)
    os.makedirs(OUT_DIR, exist_ok=True)
    for k, name in enumerate(SONGS):
        if name not in names:
            continue
        song, ir = SONGS[name]()
        stereo = song.mixdown(ir)
        report(name, stereo, song)
        encode(stereo, os.path.join(OUT_DIR, "boss_" + name + ".ogg"), 7100 + k, wav_dir, "boss_" + name)
    update_sounds(list(SONGS))


if __name__ == "__main__":
    main(sys.argv[1:])
