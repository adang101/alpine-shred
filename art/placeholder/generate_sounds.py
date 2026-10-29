#!/usr/bin/env python3
"""Synthesizes the placeholder sounds (design doc section 17) and writes sounds.json.

Every sound is generated from noise and tones, so the repository needs no recorded audio until
real sounds replace these files one by one. Loops are built by filtering a periodic noise buffer in
the frequency domain and modulating it with whole cycles over the loop, so they wrap without a click.

Needs numpy and ffmpeg (libvorbis). Run from the repository root:
    python3 art/placeholder/generate_sounds.py
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 22050
ROOT = os.path.join("common", "src", "main", "resources", "assets", "alpineshred")
SOUNDS = os.path.join(ROOT, "sounds")
rng = np.random.default_rng(20261029)


def band_noise(seconds, low, high, tilt=0.0):
    """Periodic noise limited to [low, high] Hz; tilt < 0 darkens (dB per octave)."""
    n = int(RATE * seconds)
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    gain = ((freqs >= low) & (freqs <= high)).astype(float)
    # Soft band edges, a third of an octave wide.
    for edge, rising in ((low, True), (high, False)):
        width = edge * 0.26
        ramp = np.clip((freqs - (edge - width)) / (2 * width), 0, 1) if rising else np.clip(((edge + width) - freqs) / (2 * width), 0, 1)
        near = np.abs(freqs - edge) < width
        gain[near] = ramp[near]
    if tilt:
        with np.errstate(divide="ignore"):
            octaves = np.log2(np.maximum(freqs, 1) / max(low, 1))
        gain *= 10 ** (tilt * octaves / 20)
    return np.fft.irfft(spectrum * gain, n)


def periodic(seconds, cycles, depth, phase=0.0):
    """1 +- depth, with a whole number of cycles over the loop."""
    t = np.arange(int(RATE * seconds)) / (RATE * seconds)
    return 1 + depth * np.sin(2 * np.pi * cycles * t + phase)


def envelope(n, attack, decay, hold=0.0):
    t = np.arange(n) / RATE
    env = np.where(t < attack, t / max(attack, 1e-6), np.exp(-(t - attack - hold).clip(0) / decay))
    return env


def normalize(x, peak):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def tone(seconds, f0, f1=None, decay=0.1):
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    f = f0 if f1 is None else f0 + (f1 - f0) * t / seconds
    phase = 2 * np.pi * np.cumsum(f) / RATE
    return np.sin(phase) * np.exp(-t / decay)


def click(seconds, at, width=0.004, brightness=3000):
    n = int(RATE * seconds)
    x = np.zeros(n)
    i = int(at * RATE)
    w = int(width * RATE)
    burst = rng.standard_normal(w) * np.exp(-np.arange(w) / (w / 4))
    x[i:i + w] += burst[: max(0, min(w, n - i))]
    # crude high-pass: difference
    return np.diff(x, prepend=0) * (brightness / 3000)


def loops():
    s = {}
    s["ride/glide_snow"] = normalize(band_noise(2.0, 900, 5000, tilt=-3) * periodic(2.0, 3, 0.18) * periodic(2.0, 7, 0.07, 1.1), 0.55)
    s["ride/glide_ice"] = normalize(band_noise(2.0, 3500, 10000, tilt=-1) * periodic(2.0, 5, 0.10), 0.45)
    s["ride/glide_powder"] = normalize(band_noise(2.0, 250, 1600, tilt=-4) * periodic(2.0, 2, 0.30) * periodic(2.0, 9, 0.08), 0.55)
    grit = band_noise(2.0, 500, 4500, tilt=-2)
    crackle = np.zeros_like(grit)
    for i in rng.integers(0, len(grit) - 40, 220):
        crackle[i:i + 40] += rng.standard_normal(40) * np.exp(-np.arange(40) / 8) * rng.uniform(0.5, 2.0)
    s["ride/scrape"] = normalize(grit * periodic(2.0, 11, 0.25) + crackle * 0.6, 0.6)
    s["ride/carve"] = normalize(band_noise(2.0, 2000, 7000, tilt=-2) * periodic(2.0, 4, 0.35) ** 2, 0.5)
    s["ride/brake"] = normalize(band_noise(2.0, 600, 8000, tilt=-1.5) * periodic(2.0, 36, 0.35) * periodic(2.0, 3, 0.15), 0.6)
    return s


def one_shots():
    s = {}
    n = lambda sec: int(RATE * sec)
    # Pop: a low thump, a snap, a puff of snow.
    sec = 0.35
    s["ride/pop"] = normalize(tone(sec, 140, 70, 0.06) * 0.9 + click(sec, 0.002) * 0.6
                              + band_noise(sec, 1500, 6000) * envelope(n(sec), 0.005, 0.07) * 0.5, 0.8)
    sec = 0.45
    s["ride/land_soft"] = normalize(tone(sec, 90, 60, 0.08) * 0.6
                                    + band_noise(sec, 300, 2500, tilt=-3) * envelope(n(sec), 0.004, 0.10), 0.7)
    sec = 0.6
    s["ride/land_hard"] = normalize(tone(sec, 75, 45, 0.12) + band_noise(sec, 400, 7000, tilt=-1) * envelope(n(sec), 0.002, 0.16) * 0.9
                                    + click(sec, 0.003, brightness=5000) * 0.8, 0.9)
    sec = 0.7
    sweep = band_noise(sec, 400, 4000) * np.sin(np.linspace(0, np.pi, n(sec))) ** 2
    s["ride/spin_whoosh"] = normalize(sweep, 0.6)
    sec = 0.9
    s["ride/wipeout"] = normalize(tone(sec, 70, 40, 0.15) + band_noise(sec, 300, 6000, tilt=-1.5) * envelope(n(sec), 0.003, 0.25)
                                  + band_noise(sec, 200, 1200) * envelope(n(sec), 0.15, 0.25, hold=0.1) * 0.5, 0.95)
    sec = 0.3
    s["ride/pole_push"] = normalize(band_noise(sec, 600, 4000) * envelope(n(sec), 0.004, 0.05)
                                    + tone(sec, 900, 700, 0.03) * 0.15, 0.6)
    sec = 0.5
    s["gear/strap_in"] = normalize(click(sec, 0.02, brightness=6000) + click(sec, 0.16, brightness=6000) * 0.9
                                   + click(sec, 0.22, brightness=4000) * 0.6
                                   + band_noise(sec, 2000, 6000) * envelope(n(sec), 0.05, 0.05, hold=0.06) * 0.25, 0.7)
    sec = 0.35
    s["gear/strap_out"] = normalize(click(sec, 0.02, brightness=5000)
                                    + band_noise(sec, 1500, 5000) * envelope(n(sec), 0.03, 0.06) * 0.35, 0.65)
    sec = 0.8
    rub = band_noise(sec, 1000, 6000, tilt=-2) * (0.6 + 0.4 * np.sin(2 * np.pi * 6 * np.arange(n(sec)) / RATE) ** 2)
    s["gear/wax_apply"] = normalize(rub * np.sin(np.linspace(0, np.pi, n(sec))), 0.55)
    sec = 0.9
    ring = sum(tone(sec, f, None, 0.25) * a for f, a in ((2100, 0.5), (3300, 0.3), (5200, 0.2)))
    file_ = band_noise(sec, 3000, 9000) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9 * np.arange(n(sec)) / RATE))) * envelope(n(sec), 0.01, 0.4)
    s["gear/tune"] = normalize(ring * 0.4 + file_ * 0.6, 0.6)
    return s


def write_ogg(path, samples):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "3",
                    "-map_metadata", "-1", "-fflags", "+bitexact", path], check=True)
    os.unlink(wav_path)


# Sound event id -> (files, subtitle key). Loops are streamed=false so OpenAL loops them gaplessly.
EVENTS = {
    "ride.glide.snow": (["ride/glide_snow"], "ride.glide"),
    "ride.glide.ice": (["ride/glide_ice"], "ride.glide"),
    "ride.glide.powder": (["ride/glide_powder"], "ride.glide"),
    "ride.scrape": (["ride/scrape"], "ride.scrape"),
    "ride.carve": (["ride/carve"], "ride.carve"),
    "ride.brake": (["ride/brake"], "ride.brake"),
    "ride.pop": (["ride/pop"], "ride.pop"),
    "ride.land.soft": (["ride/land_soft"], "ride.land"),
    "ride.land.hard": (["ride/land_hard"], "ride.land"),
    "ride.spin_whoosh": (["ride/spin_whoosh"], "ride.spin"),
    "ride.wipeout": (["ride/wipeout"], "ride.wipeout"),
    "ride.pole_push": (["ride/pole_push"], "ride.pole_push"),
    "gear.strap_in": (["gear/strap_in"], "gear.strap_in"),
    "gear.strap_out": (["gear/strap_out"], "gear.strap_out"),
    "gear.wax_apply": (["gear/wax_apply"], "gear.wax_apply"),
    "gear.tune": (["gear/tune"], "gear.tune"),
}


def main():
    if not os.path.isdir(ROOT):
        sys.exit("run from the repository root")
    audio = {**loops(), **one_shots()}
    for name, samples in audio.items():
        write_ogg(os.path.join(SOUNDS, name + ".ogg"), samples)
    sounds_json = {}
    for event, (files, subtitle) in EVENTS.items():
        missing = [f for f in files if f not in audio]
        if missing:
            sys.exit(f"{event}: no audio for {missing}")
        sounds_json[event] = {
            "subtitle": "subtitles.alpineshred." + subtitle,
            "sounds": [{"name": "alpineshred:" + f} for f in files],
        }
    with open(os.path.join(ROOT, "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2)
        f.write("\n")
    print(f"wrote {len(audio)} sounds and {len(sounds_json)} events")


if __name__ == "__main__":
    main()
