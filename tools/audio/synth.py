"""ボスの曲を作るための、小さなシンセサイザー（11月25日「ボス固有のBGMを追加する」）。

音はすべてこのファイルの計算で作る（録音や既存の曲は使わない）。numpy と scipy だけで動く。

- 楽器：キック・スネア・ハイハット・太鼓（タイコ）・ティンパニ・ベース・弦（重ねたノコギリ波）・金管・
  はじく音（ハープ・カリンバ）・鐘（FM）・合唱（声の響き＝フォルマント）・オルガン・笛・水の雫・金属の打撃・
  低い衝撃・ノイズの盛り上げ
- 曲（{@link Song}）：拍と小節で音を置き、楽器ごとの束（バス）にまとめる。束ごとに音量をそろえ、残響を足して混ぜる
- つなぎ目：曲の終わりからはみ出した音（残響・余韻）を曲の頭に重ねるので、くり返し流しても切れ目がない
"""

import math

import numpy as np
from scipy import signal

SR = 44100
NOTE_INDEX = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


# ---------------------------------------------------------------- 音の高さ

def midi(name):
    """"C#4"・"Bb3"・"A4" → MIDI番号。"""
    letter = name[0].upper()
    rest = name[1:]
    shift = 0
    while rest and rest[0] in "#b":
        shift += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    octave = int(rest)
    return 12 * (octave + 1) + NOTE_INDEX[letter] + shift


def freq(note):
    m = midi(note) if isinstance(note, str) else note
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def chord(names):
    """"D3 F3 A3" → 周波数の並び。"""
    return [freq(n) for n in names.split()]


# ---------------------------------------------------------------- 道具

def seconds(n):
    return np.arange(n) / SR


def env(on, a=0.01, d=0.1, s=0.7, r=0.2):
    """押している間 {@code on} 秒（アタック・ディケイ・サステイン）と、離してからの余韻 {@code r} 秒。"""
    n_on = max(1, int(on * SR))
    t = seconds(n_on)
    attack = np.clip(t / a, 0.0, 1.0) if a > 0 else np.ones(n_on)
    decay = s + (1.0 - s) * np.exp(-np.maximum(t - a, 0.0) / max(d, 1e-4))
    e = np.where(t < a, attack, decay)
    n_r = max(1, int(r * SR))
    tr = seconds(n_r)
    rel = e[-1] * np.exp(-tr / max(r / 5.0, 1e-4)) * np.linspace(1.0, 0.0, n_r)
    return np.concatenate([e, rel])


def lowpass(x, fc, order=2):
    fc = min(fc, SR * 0.45)
    sos = signal.butter(order, fc, "low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, fc, order=2):
    sos = signal.butter(order, fc, "high", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, min(hi, SR * 0.45)], "band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def resonator(x, f, bw):
    b, a = signal.iirpeak(f, f / bw, fs=SR)
    return signal.lfilter(b, a, x)


def phase_of(f):
    """周波数（1サンプルごと）→ 0〜1 の位相と、1サンプルの進み。"""
    f = np.asarray(f, dtype=float)
    inc = f / SR
    return np.cumsum(inc) % 1.0, inc


def _blep(ph, dt):
    y = np.zeros_like(ph)
    m = ph < dt
    t = ph[m] / dt[m]
    y[m] = t + t - t * t - 1.0
    m = ph > 1.0 - dt
    t = (ph[m] - 1.0) / dt[m]
    y[m] = t * t + t + t + 1.0
    return y


def saw(f):
    """エイリアスの少ないノコギリ波（polyBLEP）。f は数か、1サンプルごとの並び。"""
    ph, dt = phase_of(f)
    return 2.0 * ph - 1.0 - _blep(ph, dt)


def square(f, width=0.5):
    ph, dt = phase_of(f)
    a = 2.0 * ph - 1.0 - _blep(ph, dt)
    ph2 = (ph + width) % 1.0
    b = 2.0 * ph2 - 1.0 - _blep(ph2, dt)
    return (a - b) * 0.5


def sine(f, phase=0.0):
    ph, _ = phase_of(f)
    return np.sin(2.0 * np.pi * ph + phase)


def vibrato(f0, n, rate=5.5, depth=0.004, delay=0.15):
    t = seconds(n)
    grow = np.clip((t - delay) / 0.25, 0.0, 1.0)
    return f0 * (1.0 + depth * grow * np.sin(2.0 * np.pi * rate * t))


def noise(n, rng):
    return rng.standard_normal(n)


# ---------------------------------------------------------------- 打楽器

def kick(rng, punch=1.0, length=0.5, low=46.0):
    n = int(length * SR)
    t = seconds(n)
    f = low + 120.0 * punch * np.exp(-t * 30.0)
    body = np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6.5)
    click = highpass(noise(n, rng), 2500.0) * np.exp(-t * 400.0) * 0.35
    return np.tanh(1.6 * (body + click)) * 0.9


def snare(rng, tone=190.0, length=0.32, bright=1.0):
    n = int(length * SR)
    t = seconds(n)
    body = np.sin(2.0 * np.pi * tone * t + 2.0 * (1.0 - np.exp(-t * 40.0))) * np.exp(-t * 22.0) * 0.6
    sn = bandpass(noise(n, rng), 1400.0, 7500.0 * bright) * np.exp(-t * 15.0)
    return body + sn * 0.9


def hat(rng, open_=False, length=None):
    length = length or (0.32 if open_ else 0.06)
    n = int(length * SR)
    t = seconds(n)
    x = highpass(noise(n, rng), 7500.0, order=4)
    return x * np.exp(-t * (10.0 if open_ else 70.0)) * 0.5


def shaker(rng, length=0.09):
    n = int(length * SR)
    t = seconds(n)
    x = bandpass(noise(n, rng), 4500.0, 12000.0)
    e = np.minimum(t / 0.015, 1.0) * np.exp(-np.maximum(t - 0.015, 0) * 45.0)
    return x * e * 0.45


def taiko(rng, f0=70.0, length=1.1, hard=1.0):
    n = int(length * SR)
    t = seconds(n)
    f = f0 * (1.0 + 0.5 * np.exp(-t * 18.0))
    body = np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4.0)
    over = np.sin(2.0 * np.pi * np.cumsum(f * 2.3) / SR) * np.exp(-t * 9.0) * 0.3
    skin = lowpass(noise(n, rng), 900.0) * np.exp(-t * 25.0) * 0.6 * hard
    return np.tanh(1.3 * (body + over + skin))


def tom(rng, f0=110.0, length=0.5):
    n = int(length * SR)
    t = seconds(n)
    f = f0 * (1.0 + 0.35 * np.exp(-t * 25.0))
    body = np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9.0)
    skin = bandpass(noise(n, rng), 300.0, 3000.0) * np.exp(-t * 40.0) * 0.3
    return body + skin


def timpani(rng, f0=73.4, length=2.2):
    n = int(length * SR)
    t = seconds(n)
    glide = f0 * (1.0 + 0.03 * np.exp(-t * 8.0))
    x = np.zeros(n)
    for ratio, amp, dec in ((1.0, 1.0, 2.2), (1.5, 0.5, 3.0), (1.99, 0.35, 3.6), (2.44, 0.2, 4.5), (2.98, 0.12, 5.5)):
        x += amp * np.sin(2.0 * np.pi * np.cumsum(glide * ratio) / SR) * np.exp(-t * dec)
    mallet = lowpass(noise(n, rng), 1200.0) * np.exp(-t * 35.0) * 0.5
    return (x + mallet) * 0.6


def woodblock(rng, f0=900.0, length=0.12):
    n = int(length * SR)
    t = seconds(n)
    x = np.sin(2.0 * np.pi * f0 * t) * np.exp(-t * 60.0) + 0.5 * np.sin(2.0 * np.pi * f0 * 2.7 * t) * np.exp(-t * 90.0)
    return x * 0.6


def metal_hit(rng, f0=320.0, length=1.2, index=6.0):
    """金属を打つ音（FM。倍音が整っていない）。"""
    n = int(length * SR)
    t = seconds(n)
    mod = np.sin(2.0 * np.pi * f0 * 2.76 * t) * index * np.exp(-t * 5.0)
    x = np.sin(2.0 * np.pi * f0 * t + mod) * np.exp(-t * 3.5)
    x += 0.4 * np.sin(2.0 * np.pi * f0 * 4.07 * t + 0.5 * mod) * np.exp(-t * 7.0)
    x += highpass(noise(n, rng), 3000.0) * np.exp(-t * 50.0) * 0.4
    return x * 0.5


def boom(rng, length=2.5, f0=42.0):
    """低い衝撃（区切りの頭）。"""
    n = int(length * SR)
    t = seconds(n)
    f = f0 * (1.0 + 0.8 * np.exp(-t * 6.0))
    x = np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 2.2)
    x += lowpass(noise(n, rng), 400.0) * np.exp(-t * 4.0) * 0.8
    return np.tanh(1.5 * x) * 0.9


def riser(rng, length, top=9000.0):
    """だんだん明るく大きくなるノイズ（次の区切りへの盛り上げ）。"""
    n = int(length * SR)
    t = seconds(n) / length
    base = noise(n, rng)
    dark = lowpass(base, 500.0)
    mid = lowpass(base, 2500.0)
    bright = lowpass(base, top)
    x = np.where(t < 0.5, dark * (1 - t * 2) + mid * (t * 2), mid * (2 - t * 2) + bright * (t * 2 - 1))
    return x * (t ** 2) * 0.6


def drop(rng, f0=900.0):
    """水の雫（上がる短い音）。"""
    n = int(0.25 * SR)
    t = seconds(n)
    f = f0 * (1.0 + 1.4 * np.minimum(t / 0.03, 1.0))
    return np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 28.0) * 0.5


def reverse_swell(rng, f, length):
    """だんだん大きくなる弦（前のめりの「ふわっ」）。"""
    n = int(length * SR)
    t = seconds(n) / length
    x = sum(saw(np.full(n, f * c)) for c in (0.996, 1.0, 1.004))
    return lowpass(x, 3000.0) * (t ** 2.5) * 0.3


# ---------------------------------------------------------------- 音程のある楽器

def bass(f, on, rng=None, bright=700.0, kind="saw", sub=0.5, r=0.08):
    e = env(on, a=0.004, d=0.18, s=0.75, r=r)
    n = len(e)
    tone = saw(np.full(n, f)) if kind == "saw" else square(np.full(n, f), 0.5)
    t = seconds(n)
    dark = lowpass(tone, bright * 0.5)
    lit = lowpass(tone, bright * 1.8)
    mix = np.exp(-t * 14.0)
    x = dark * (1 - mix) + lit * mix
    x += sub * np.sin(2.0 * np.pi * f * t)
    return np.tanh(1.4 * x) * e * 0.7


def strings(freqs, on, a=0.25, r=0.6, bright=2800.0, detune=0.006, vib=True):
    """弦（重ねたノコギリ波）。和音でもよい。"""
    e = env(on, a=a, d=0.4, s=0.85, r=r)
    n = len(e)
    x = np.zeros(n)
    for f in freqs:
        base = vibrato(f, n, 5.2, 0.003, 0.2) if vib else np.full(n, f)
        for c in (-detune, -detune * 0.4, 0.0, detune * 0.45, detune):
            x += saw(base * (1.0 + c))
    x = lowpass(x, bright) / (5.0 * max(1, len(freqs)) ** 0.5)
    return x * e


def staccato_strings(f, on, bright=3500.0):
    """短く刻む弦（オスティナート）。"""
    e = env(on, a=0.006, d=0.06, s=0.55, r=0.07)
    n = len(e)
    x = sum(saw(np.full(n, f * (1.0 + c))) for c in (-0.004, 0.0, 0.004))
    x = lowpass(x, bright)
    return x * e * 0.33


def brass(freqs, on, bright=2600.0, a=0.03, r=0.18):
    """金管（明るさが息とともに開いて閉じる）。"""
    e = env(on, a=a, d=0.25, s=0.75, r=r)
    n = len(e)
    t = seconds(n)
    x = np.zeros(n)
    for f in freqs:
        scoop = f * (1.0 - 0.02 * np.exp(-t * 30.0))
        for c in (-0.003, 0.003):
            x += saw(scoop * (1.0 + c))
    dark = lowpass(x, bright * 0.25)
    lit = lowpass(x, bright)
    open_ = np.clip(t / 0.06, 0, 1) * (0.55 + 0.45 * np.exp(-t * 3.0))
    y = dark * (1 - open_) + lit * open_
    return np.tanh(y / (1.2 * max(1, len(freqs)) ** 0.5)) * e * 0.8


def pluck(f, on=0.6, bright=1.0, decay=3.0, harmonics=10):
    """はじく音（ハープ・カリンバ）。高い倍音ほど早く消える。"""
    n = int((on + 0.4) * SR)
    t = seconds(n)
    x = np.zeros(n)
    for k in range(1, harmonics + 1):
        if f * k > SR * 0.45:
            break
        amp = (1.0 / k) * (bright ** (k - 1)) * (1.0 if k == 1 else 0.8)
        x += amp * np.sin(2.0 * np.pi * f * k * t + k) * np.exp(-t * decay * (1.0 + 0.6 * (k - 1)))
    click = np.exp(-t * 300.0) * np.sin(2.0 * np.pi * f * 6.0 * t) * 0.2
    tail = np.linspace(1.0, 0.0, n) ** 0.3
    return (x + click) * tail * 0.5


def kalimba(f, on=0.5):
    n = int((on + 0.6) * SR)
    t = seconds(n)
    x = np.sin(2.0 * np.pi * f * t) * np.exp(-t * 3.2)
    x += 0.35 * np.sin(2.0 * np.pi * f * 5.95 * t) * np.exp(-t * 14.0)
    x += 0.15 * np.sin(2.0 * np.pi * f * 3.0 * t) * np.exp(-t * 9.0)
    return x * np.linspace(1.0, 0.0, n) ** 0.2 * 0.5


def bell(f, length=2.6, index=3.2, ratio=3.5):
    """鐘（FM）。"""
    n = int(length * SR)
    t = seconds(n)
    mod = np.sin(2.0 * np.pi * f * ratio * t) * index * np.exp(-t * 2.5)
    x = np.sin(2.0 * np.pi * f * t + mod) * np.exp(-t * 1.4)
    x += 0.3 * np.sin(2.0 * np.pi * f * 2.0 * t) * np.exp(-t * 2.4)
    return x * np.linspace(1.0, 0.0, n) ** 0.5 * 0.45


VOWELS = {
    "a": ((800, 80, 1.0), (1150, 90, 0.5), (2900, 120, 0.25), (3900, 130, 0.12)),
    "o": ((450, 70, 1.0), (800, 80, 0.45), (2830, 100, 0.15), (3800, 120, 0.08)),
    "u": ((325, 50, 1.0), (700, 60, 0.3), (2530, 170, 0.1), (3500, 180, 0.05)),
    "e": ((400, 60, 1.0), (1620, 80, 0.4), (2400, 100, 0.25), (3400, 120, 0.12)),
}


def choir(freqs, on, vowel="a", a=0.3, r=0.7, voices=3):
    """合唱（声の響き）。和音でもよい。"""
    e = env(on, a=a, d=0.5, s=0.9, r=r)
    n = len(e)
    src = np.zeros(n)
    rng = np.random.default_rng(int(sum(freqs)) % 9973)
    for f in freqs:
        for v in range(voices):
            det = 1.0 + (v - (voices - 1) / 2) * 0.006
            rate = 4.8 + 0.7 * rng.random()
            fv = vibrato(f * det, n, rate, 0.006, 0.1 + 0.1 * rng.random())
            src += saw(fv)
    breath = highpass(rng.standard_normal(n), 2000.0) * 0.04
    src = src + breath * src.std()
    out = np.zeros(n)
    for fc, bw, g in VOWELS[vowel]:
        out += g * resonator(src, fc, bw)
    out = lowpass(out, 5000.0)
    norm = max(1e-9, np.sqrt(np.mean(out ** 2)))
    return out / norm * 0.18 * e * (len(freqs) ** 0.5)


def organ(freqs, on, a=0.05, r=0.35, bars=(1.0, 0.6, 0.45, 0.25, 0.15, 0.1)):
    e = env(on, a=a, d=0.2, s=0.95, r=r)
    n = len(e)
    t = seconds(n)
    x = np.zeros(n)
    for f in freqs:
        for k, amp in zip((1.0, 2.0, 3.0, 4.0, 6.0, 8.0), bars):
            if f * k < SR * 0.45:
                x += amp * np.sin(2.0 * np.pi * f * k * t)
    x *= 1.0 + 0.12 * np.sin(2.0 * np.pi * 6.2 * t)
    return x * e * 0.16 / max(1, len(freqs)) ** 0.5


def flute(f, on, rng):
    e = env(on, a=0.06, d=0.2, s=0.85, r=0.18)
    n = len(e)
    fv = vibrato(f, n, 5.0, 0.006, 0.2)
    x = sine(fv) + 0.18 * sine(fv * 2.0) + 0.06 * sine(fv * 3.0)
    breath = bandpass(rng.standard_normal(n), f * 0.8, min(f * 6.0, 15000.0)) * 0.12
    return (x + breath) * e * 0.45


def lead(f, on, kind="square", bright=3200.0, vib=0.005):
    e = env(on, a=0.01, d=0.15, s=0.8, r=0.12)
    n = len(e)
    fv = vibrato(f, n, 5.6, vib, 0.18)
    x = square(fv, 0.45) if kind == "square" else saw(fv)
    x += 0.5 * (square(fv * 1.005, 0.3) if kind == "square" else saw(fv * 1.005))
    return lowpass(x, bright) * e * 0.35


def arp_synth(f, on, bright=4200.0):
    """短いシンセのはじく音（3階の機械）。"""
    e = env(on, a=0.002, d=0.12, s=0.25, r=0.1)
    n = len(e)
    t = seconds(n)
    x = saw(np.full(n, f)) + 0.6 * square(np.full(n, f * 1.003), 0.3)
    dark = lowpass(x, bright * 0.2)
    lit = lowpass(x, bright)
    m = np.exp(-t * 25.0)
    return (dark * (1 - m) + lit * m) * e * 0.3


def pad(freqs, on, bright=1800.0, a=0.6, r=1.2):
    """やわらかい和音（水の中のような）。"""
    e = env(on, a=a, d=0.8, s=0.9, r=r)
    n = len(e)
    x = np.zeros(n)
    for f in freqs:
        for c in (-0.004, 0.0, 0.004):
            x += saw(np.full(n, f * (1.0 + c))) * 0.5 + sine(np.full(n, f * (1.0 + c) * 0.5)) * 0.3
    t = seconds(n)
    wob = 1.0 + 0.25 * np.sin(2.0 * np.pi * 0.25 * t)
    x = lowpass(x, bright) * wob
    return x * e * 0.12 / max(1, len(freqs)) ** 0.5


# ---------------------------------------------------------------- 残響とこだま

def reverb_ir(decay=2.2, predelay=0.02, damp=7000.0, seed=7, width=0.65):
    n = int(decay * 1.6 * SR)
    t = seconds(n)
    rng = np.random.default_rng(seed)
    irs = []
    for ch in range(2):
        nz = rng.standard_normal(n)
        lo = lowpass(nz, 1200.0)
        hi = nz - lo
        ir = lo * np.exp(-t * 6.9 / decay) + 0.7 * hi * np.exp(-t * 6.9 / (decay * 0.5))
        ir = lowpass(ir, damp)
        # はじめの反射（数本）
        for k in range(6):
            pos = int((0.007 + 0.011 * k + 0.004 * ch) * SR)
            ir[pos] += (0.6 - 0.08 * k) * (1 if (k + ch) % 2 == 0 else -1)
        ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
        irs.append(ir / np.sqrt(np.sum(ir ** 2)))
    if width < 1.0:
        mid = (irs[0] + irs[1]) * 0.5
        irs = [mid + (irs[0] - mid) * width, mid + (irs[1] - mid) * width]
    return irs


def echo(stereo, delay, feedback=0.45, taps=5):
    """拍に合わせたこだま（左右に行き来する）。"""
    out = stereo.copy()
    d = int(delay * SR)
    src = lowpass(stereo.mean(axis=0), 4500.0)
    for k in range(1, taps + 1):
        shift = d * k
        if shift >= stereo.shape[1]:
            break
        g = feedback ** k
        ch = k % 2
        out[ch, shift:] += src[:-shift] * g
        out[1 - ch, shift:] += src[:-shift] * g * 0.3
    return out


# ---------------------------------------------------------------- 曲

class Song:
    """拍と小節で音を置く。束（バス）ごとに音量をそろえ、残響を足して、くり返せる1本の音にする。"""

    def __init__(self, bpm, bars, beats_per_bar=4, tail=7.0, seed=1):
        self.bpm = bpm
        self.beats_per_bar = beats_per_bar
        self.bars = bars
        self.spb = 60.0 / bpm
        self.length = bars * beats_per_bar * self.spb
        self.n = int(round(self.length * SR))
        self.total = self.n + int(tail * SR)
        self.rng = np.random.default_rng(seed)
        self.buses = {}
        self.settings = {}

    def beat(self, bar, beat=0.0):
        return (bar * self.beats_per_bar + beat) * self.spb

    def dur(self, beats):
        return beats * self.spb

    def bus(self, name, level_db=-20.0, send=0.25, pan=0.0, highpass_hz=None, echo_beats=None, echo_fb=0.4):
        if name not in self.buses:
            self.buses[name] = np.zeros((2, self.total))
            self.settings[name] = dict(level_db=level_db, send=send, highpass=highpass_hz, echo=echo_beats, echo_fb=echo_fb)
        return self.buses[name]

    def put(self, bus, time, sig, pan=0.0, gain=1.0):
        buf = self.buses[bus]
        if time < 0:
            # 頭より少し前（人の手のゆれ）：くり返しの前の回の終わりに置く
            time += self.length
        i = int(round(time * SR))
        if i >= self.total:
            return
        sig = soften(sig)
        end = min(i + len(sig), self.total)
        m = end - i
        angle = (pan + 1.0) * math.pi / 4.0
        buf[0, i:end] += sig[:m] * gain * math.cos(angle) * math.sqrt(2.0)
        buf[1, i:end] += sig[:m] * gain * math.sin(angle) * math.sqrt(2.0)

    def pattern(self, bus, sound, steps, bars, pan=0.0, gain=1.0, steps_per_bar=16, humanize=0.0, accent=None):
        """"x..x" の並び（x：強く・o：ふつう・.：休み）を、小節ごとに置く。"""
        step = self.beats_per_bar / steps_per_bar
        for bar in bars:
            for k, c in enumerate(steps):
                if c in "xXo":
                    g = gain * (1.0 if c in "xX" else 0.6)
                    if accent and k % accent == 0:
                        g *= 1.15
                    jitter = (self.rng.random() - 0.5) * humanize
                    snd = sound() if callable(sound) else sound
                    self.put(bus, self.beat(bar, k * step) + jitter, snd, pan=pan, gain=g)

    def mixdown(self, ir, master_db=-15.5, ceiling=0.93):
        dry = np.zeros((2, self.total))
        wet_send = np.zeros((2, self.total))
        for name, buf in self.buses.items():
            st = self.settings[name]
            x = buf
            if st["highpass"]:
                x = np.vstack([highpass(x[0], st["highpass"]), highpass(x[1], st["highpass"])])
            if st["echo"]:
                x = echo(x, st["echo"] * self.spb, st["echo_fb"])
            level = loud(x[:, :self.n])
            if level <= 0:
                continue
            x = x * (10 ** (st["level_db"] / 20.0) / level)
            dry += x
            wet_send += x * st["send"]
        wet = np.vstack([signal.fftconvolve(wet_send[c], ir[c])[:self.total] for c in range(2)])
        mix = dry + wet
        # つなぎ目：終わりからはみ出した音を頭に重ねる
        loop = mix[:, :self.n].copy()
        over = mix[:, self.n:]
        k = 0
        while k < over.shape[1]:
            m = min(self.n, over.shape[1] - k)
            loop[:, :m] += over[:, k:k + m]
            k += m
        # 仕上げ（くり返しのつなぎ目で切れないよう、2回分つなげて処理し、後ろの1回分を使う）
        loop *= 10 ** (-16.0 / 20.0) / max(loud(loop), 1e-9)
        twice = np.concatenate([loop, loop], axis=1)
        twice = np.vstack([highpass(twice[0], 40.0), highpass(twice[1], 40.0)])
        twice = glue(twice)
        out = twice[:, self.n:]
        out *= 10 ** (master_db / 20.0) / max(loud(out), 1e-9)
        out = soft_clip(out, 0.72)
        peak = np.max(np.abs(out))
        if peak > ceiling:
            out *= ceiling / peak
        return out


def soften(sig, attack=0.0015, release=0.006):
    """音の頭と終わりを、ごく短くなめらかにする（いきなり始まる・切れる「プツッ」を消す）。"""
    n = len(sig)
    a = min(n // 4, int(attack * SR))
    r = min(n // 4, int(release * SR))
    if (a > 1 and abs(sig[0]) > 1e-4) or (r > 1 and abs(sig[-1]) > 1e-4):
        sig = np.array(sig, dtype=float, copy=True)
        if a > 1 and abs(sig[0]) > 1e-4:
            sig[:a] *= 0.5 - 0.5 * np.cos(np.linspace(0.0, np.pi, a))
        if r > 1 and abs(sig[-1]) > 1e-4:
            sig[-r:] *= 0.5 + 0.5 * np.cos(np.linspace(0.0, np.pi, r))
    return sig


def loud(x):
    """鳴っているところの大きさ（50msごとのRMSの上から2割の値）。"""
    mono = x.mean(axis=0) if x.ndim == 2 else x
    frame = int(0.05 * SR)
    usable = len(mono) // frame * frame
    if usable == 0:
        return 0.0
    rms = np.sqrt(np.mean(mono[:usable].reshape(-1, frame) ** 2, axis=1))
    rms = rms[rms > 1e-6]
    if len(rms) == 0:
        return 0.0
    return float(np.percentile(rms, 80))


def glue(x, threshold_db=-11.0, ratio=2.0, attack=0.012, release=0.18):
    """ゆるいコンプレッサー（全体をまとめる）。"""
    mono = np.abs(x).max(axis=0)
    a = math.exp(-1.0 / (attack * SR))
    r = math.exp(-1.0 / (release * SR))
    # 包絡線（上がるときは速く、下がるときはゆっくり）：区切って numpy で
    env_ = signal.lfilter([1 - r], [1, -r], mono)
    env_ = np.maximum(env_, signal.lfilter([1 - a], [1, -a], mono))
    level_db = 20 * np.log10(np.maximum(env_, 1e-9))
    over = np.maximum(level_db - threshold_db, 0.0)
    gain_db = -over * (1.0 - 1.0 / ratio)
    gain = 10 ** (gain_db / 20.0)
    return x * gain


def soft_clip(x, threshold=0.7):
    y = x.copy()
    a = np.abs(x)
    m = a > threshold
    y[m] = np.sign(x[m]) * (threshold + (1.0 - threshold) * np.tanh((a[m] - threshold) / (1.0 - threshold)))
    return y
