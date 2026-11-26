"""CIで録った音に、ボス戦の曲が本当に入っているかを確かめる（11月26日「ボス戦のBGMがなっていない」のご指摘）。

CIの機械には音の出口がないので、クライアントを動かすとき OpenAL Soft の「wave」（鳴った音を、そのまま WAV ファイルに書く）を使う
（.github/workflows/build.yml の「音をファイルに録る設定」）。ゲームは、音のしくみが動きはじめた時刻（audio clock）と、ボス戦の
写真の段取り（p8_bossfx）の時刻をログに書く（ClientModEvents・CiPhase8）。このスクリプトは：

  1. ログから、録音のはじまりの時刻と、ボス戦ごとの時刻・ボスの名前を読む
  2. Mod の jar から、1〜8階のボスの曲（Ogg）を取り出し、録音と同じ速さ（サンプリング周波数）・1チャンネルにする
  3. ボス戦の時刻から少しあとの6秒の録音が、そのボスの曲のどこかと、波の形が似ているか（正規化した相互相関）を調べる。
     ほかのボスの曲とも比べ、そのボスの曲だけがよく似ていれば「鳴っている」とする。追いこんだあと（HP30%以下）は曲が
     少し速く高くなるので、速さを変えた曲とも比べる
  4. 結果の表をログと、ジョブのまとめ（GITHUB_STEP_SUMMARY）に書く。ボス戦の前後20秒の録音を、聞いて確かめられるように保存する

  python3 tools/ci/boss_audio.py <録った音.wav> <ゲームのログ（いくつでも）> --mods run/mods --out audio
"""
import argparse
import glob
import io
import os
import re
import struct
import sys
import zipfile
from fractions import Fraction

import numpy as np
from scipy import signal

CLOCK = re.compile(r"\[amemaru-ci\] audio clock start=(\d+)")
EVENT = re.compile(r"\[amemaru-ci\] boss fx \(([a-z0-9_]+)\): boss=([a-z_]*) track=([a-z_]*) active=(\w+) retries=(\d+)"
                   r" master=([0-9.]+) volume=([0-9.]+) clock=(\d+)")
# 曲を確かめる6秒の録音は、写真の段取りの時刻から、これだけあと（曲は1.5秒でふわっと大きくなる）
WINDOW_FROM = 1.6
WINDOW_SECONDS = 6.0
# 「鳴っている」とする、似ている度合い（0〜1）と、ほかのボスの曲との差
MIN_SCORE = 0.30
MIN_RATIO = 1.6
# 追いこんだあとは、曲が少しずつ 1.04 倍の速さになる
PITCHES = (1.0, 1.02, 1.04)


def read_wav(path):
    """OpenAL Soft の wave が書いた WAV を読む（ゲームが落ちて、大きさの欄が書かれていなくても読む）。"""
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("WAV ではありません")
    pos = 12
    fmt = None
    while pos + 8 <= len(data):
        cid = data[pos:pos + 4]
        size = struct.unpack("<I", data[pos + 4:pos + 8])[0]
        body = pos + 8
        if cid == b"fmt ":
            tag, channels, rate, _, align, bits = struct.unpack("<HHIIHH", data[body:body + 16])
            if tag == 0xFFFE:
                tag = struct.unpack("<H", data[body + 24:body + 26])[0]
            fmt = (tag, channels, rate, align, bits)
        elif cid == b"data":
            if fmt is None:
                raise ValueError("fmt の欄がありません")
            end = len(data) if size in (0, 0xFFFFFFFF) or body + size > len(data) else body + size
            tag, channels, rate, align, bits = fmt
            raw = data[body:end]
            raw = raw[:len(raw) - len(raw) % align]
            if tag == 3 and bits == 32:
                samples = np.frombuffer(raw, dtype="<f4").astype(np.float64)
            elif bits == 16:
                samples = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
            elif bits == 32:
                samples = np.frombuffer(raw, dtype="<i4").astype(np.float64) / 2147483648.0
            elif bits == 8:
                samples = (np.frombuffer(raw, dtype="u1").astype(np.float64) - 128.0) / 128.0
            else:
                raise ValueError(f"読めない形式です（{tag}・{bits}ビット）")
            samples = samples.reshape(-1, channels).mean(axis=1)
            return samples, rate
        pos = body + size + (size & 1)
    raise ValueError("data の欄がありません")


def write_wav(path, samples, rate):
    pcm = np.clip(samples * 32767.0, -32768, 32767).astype("<i2").tobytes()
    with open(path, "wb") as f:
        f.write(b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVE")
        f.write(b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, rate, rate * 2, 2, 16))
        f.write(b"data" + struct.pack("<I", len(pcm)) + pcm)


def boss_songs(mods_dir, rate):
    """Mod の jar から、ボスの曲を取り出して、録音と同じ速さの1チャンネルにする。"""
    import soundfile
    songs = {}
    for jar in sorted(glob.glob(os.path.join(mods_dir, "*.jar"))):
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                m = re.fullmatch(r"assets/amemaru/sounds/music/boss_([a-z_]+)\.ogg", name)
                if not m:
                    continue
                audio, song_rate = soundfile.read(io.BytesIO(z.read(name)), dtype="float64", always_2d=True)
                mono = audio.mean(axis=1)
                ratio = Fraction(rate, song_rate).limit_denominator(1000)
                songs[m.group(1)] = signal.resample_poly(mono, ratio.numerator, ratio.denominator)
    return songs


def best_match(song, clip):
    """曲（くり返す）のどこかと clip が、どれだけ似ているか（正規化した相互相関のいちばん大きい値）。"""
    n = len(clip)
    looped = np.concatenate([song, song[:n]])
    clip = clip - clip.mean()
    norm = np.linalg.norm(clip)
    if norm < 1e-9:
        return 0.0
    num = signal.fftconvolve(looped, clip[::-1], mode="valid")
    sq = np.concatenate([[0.0], np.cumsum(looped * looped)])
    s = np.concatenate([[0.0], np.cumsum(looped)])
    energy = (sq[n:] - sq[:-n]) - (s[n:] - s[:-n]) ** 2 / n
    score = num / (np.sqrt(np.maximum(energy, 1e-12)) * norm)
    return float(np.max(score))


def match_any_pitch(song, clip):
    best = 0.0
    for pitch in PITCHES:
        if pitch == 1.0:
            sped = song
        else:
            ratio = Fraction(1.0 / pitch).limit_denominator(200)
            sped = signal.resample_poly(song, ratio.numerator, ratio.denominator)
        best = max(best, best_match(sped, clip))
    return best


def db(x):
    rms = float(np.sqrt(np.mean(x * x))) if len(x) else 0.0
    return -120.0 if rms <= 1e-6 else 20.0 * np.log10(rms)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("wav")
    ap.add_argument("logs", nargs="+")
    ap.add_argument("--mods", default="run/mods")
    ap.add_argument("--out", default="audio")
    args = ap.parse_args()
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    lines = ["## ボス戦の曲（CIで録った音）", ""]

    def done(text, warn=True):
        print(("::warning::" if warn else "") + text)
        lines.append(text)
        if summary:
            with open(summary, "a", encoding="utf-8") as f:
                f.write("\n".join(lines) + "\n")
        return 0

    clocks = []
    events = []
    for path in args.logs:
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                m = CLOCK.search(line)
                if m:
                    clocks.append(int(m.group(1)))
                m = EVENT.search(line)
                if m:
                    events.append(m.groups())
    if not os.path.exists(args.wav) or os.path.getsize(args.wav) < 1000:
        return done("録った音のファイルがありません（音のしくみが動かなかった？ ログの OpenAL の行を見る）")
    if not clocks:
        return done("ログに、録音のはじまりの時刻（audio clock）がありません")
    if not events:
        return done("ログに、ボス戦の段取り（boss fx）がありません")
    samples, rate = read_wav(args.wav)
    seconds = len(samples) / rate
    print(f"録った音：{seconds:.0f}秒（{rate}Hz）。音のしくみの開始：{len(clocks)}回")
    songs = boss_songs(args.mods, rate)
    print("ボスの曲：" + "・".join(f"{k}（{len(v) / rate:.0f}秒）" for k, v in sorted(songs.items())))
    os.makedirs(args.out, exist_ok=True)

    lines += ["| 場面 | ボス | エンジン | 音量(dB) | そのボスの曲 | ほかの曲で一番 | 判定 |", "|---|---|---|---|---|---|---|"]
    bad = []
    for label, boss, track, active, retries, master, volume, clock in events:
        clock = int(clock)
        start = max([c for c in clocks if c <= clock], default=clocks[0])
        offset = (clock - start) / 1000.0
        a = int((offset + WINDOW_FROM) * rate)
        b = int((offset + WINDOW_FROM + WINDOW_SECONDS) * rate)
        engine = f"{active}（かけ直し{retries}）"
        if b > len(samples):
            row = f"| {label} | {boss} | {engine} | - | - | - | 録音の外（{offset:.0f}秒） |"
            print(row)
            lines.append(row)
            bad.append(label)
            continue
        clip = samples[a:b]
        excerpt = samples[max(0, int((offset - 3) * rate)):int((offset + 17) * rate)]
        write_wav(os.path.join(args.out, f"boss_{label}.wav"), excerpt, rate)
        own = match_any_pitch(songs[boss], clip) if boss in songs else 0.0
        others = [(match_any_pitch(v, clip), k) for k, v in songs.items() if k != boss]
        other, other_name = max(others) if others else (0.0, "-")
        ok = own >= MIN_SCORE and own >= other * MIN_RATIO
        verdict = "鳴っている" if ok else "見つからない"
        row = f"| {label} | {boss} | {engine} | {db(clip):.1f} | {own:.2f} | {other:.2f}（{other_name}） | {verdict} |"
        print(row)
        lines.append(row)
        if not ok:
            bad.append(label)
    lines.append("")
    if bad:
        return done("ボスの曲が録音に見つからない場面があります：" + "・".join(bad))
    return done(f"すべての場面（{len(events)}）で、そのボスの曲が鳴っていました", warn=False)


if __name__ == "__main__":
    sys.exit(main())
