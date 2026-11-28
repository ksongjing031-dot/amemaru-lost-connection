"""ユニーク武器の固有スキルと「認証」の音を作る（11月28日【補完】）。

  python3 -I tools/audio/unique_sounds.py

音はすべてこのファイルの計算で作る（録音や既存の音は使わない）。tools/audio/synth.py の楽器を使う。

- unique/awaken.ogg：固有スキルを使った瞬間の「武器が目を覚ます」音。低い衝撃のあと、結晶の鐘の和音（下から上へ速く重ねる）と、
  きらめきの粒。1.6秒。武器ごとの技の音（スキルの定義の sound）の下に重ねて鳴る
- unique/ward.ogg：「第七の門鍵」の認証が働きはじめた音。翠の結晶が共鳴する、やわらかいハム（和音）と小さな鐘。1.4秒

書き出すもの：src/main/resources/assets/amemaru/sounds/unique/*.ogg と sounds.json の「unique.awaken」「unique.ward」
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

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "amemaru")
OUT = os.path.join(ASSETS, "sounds", "unique")


def place(buf, sig, at, gain=1.0, pan=0.0):
    """ステレオの束 buf に、音 sig を at 秒から置く（pan は -1 左〜1 右）。"""
    sig = S.soften(np.asarray(sig, dtype=float), attack=0.001, release=0.03)
    start = int(at * S.SR)
    end = min(buf.shape[1], start + len(sig))
    if end <= start:
        return
    left = gain * (1.0 - max(0.0, pan))
    right = gain * (1.0 + min(0.0, pan))
    buf[0, start:end] += sig[:end - start] * left
    buf[1, start:end] += sig[:end - start] * right


def with_reverb(dry, decay=1.6, wet=0.32):
    irs = S.reverb_ir(decay=decay, predelay=0.015, damp=8000.0, seed=11, width=0.8)
    out = np.zeros((2, dry.shape[1]))
    mono = dry.mean(axis=0)
    for ch in range(2):
        conv = np.convolve(mono, irs[ch])[:dry.shape[1]]
        out[ch] = dry[ch] * (1.0 - wet) + conv * wet
    return out


def finish(buf, peak=0.82):
    buf = buf - np.mean(buf, axis=1, keepdims=True)
    for ch in range(2):
        buf[ch] = S.soften(buf[ch], attack=0.002, release=0.08)
    top = np.max(np.abs(buf))
    if top > 0:
        buf *= peak / top
    return buf


def awaken():
    length = 1.6
    buf = np.zeros((2, int(length * S.SR)))
    rng = np.random.default_rng(28)
    # 低い衝撃（武器が目を覚ます）
    place(buf, S.boom(rng, length=1.5, f0=52.0) * 0.55, 0.0, 1.0)
    # 結晶の鐘の和音を、下から上へ速く重ねる（E5・B5・E6・G#6）
    for k, note in enumerate(["E5", "B5", "E6", "G#6"]):
        place(buf, S.bell(S.freq(note), length=1.4, index=2.4, ratio=3.01), 0.03 + k * 0.045, 0.55 - k * 0.06,
              pan=(-0.4 + k * 0.27))
    # きらめきの粒（高い短い音を散らす）
    for i in range(10):
        f = S.freq("C7") * (1.0 + rng.random() * 0.9)
        place(buf, S.kalimba(f, on=0.08) * 0.18, 0.12 + i * 0.05 + rng.random() * 0.03, 1.0, pan=rng.uniform(-0.8, 0.8))
    return finish(with_reverb(buf, decay=1.8, wet=0.35))


def ward():
    length = 1.4
    buf = np.zeros((2, int(length * S.SR)))
    # 翠の結晶の共鳴（やわらかい和音のハム。A3・E4・C#5）
    hum = S.pad(S.chord("A3 E4 C#5"), 0.9, bright=1600.0, a=0.12, r=0.45)
    place(buf, hum * 2.2, 0.0, 1.0)
    # 小さな鐘（2つ）
    place(buf, S.bell(S.freq("A5"), length=1.1, index=1.6, ratio=2.0), 0.05, 0.35, pan=-0.3)
    place(buf, S.bell(S.freq("E6"), length=1.0, index=1.4, ratio=2.0), 0.16, 0.28, pan=0.3)
    return finish(with_reverb(buf, decay=1.5, wet=0.3), peak=0.7)


def encode(stereo, path, serial):
    pcm16 = (np.clip(stereo.T, -1.0, 1.0) * 32767.0).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "in.wav")
        wavfile.write(wav, S.SR, pcm16)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", "-serial_offset", str(serial), path], check=True)


def update_sounds():
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        sounds = json.load(f)
    sounds["unique.awaken"] = {"sounds": [{"name": "amemaru:unique/awaken"}], "subtitle": "subtitles.amemaru.unique.awaken"}
    sounds["unique.ward"] = {"sounds": [{"name": "amemaru:unique/ward"}], "subtitle": "subtitles.amemaru.unique.ward"}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(sounds, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    os.makedirs(OUT, exist_ok=True)
    encode(awaken(), os.path.join(OUT, "awaken.ogg"), 9201)
    encode(ward(), os.path.join(OUT, "ward.ogg"), 9202)
    update_sounds()
    print("wrote", os.path.relpath(OUT, ROOT), "awaken.ogg ward.ogg and sounds.json")


if __name__ == "__main__":
    main()
