# Habi sound generator — GPL-3.0-or-later, part of Bito.
"""Generates Habi's four short WAV cues — mono, 16-bit, 22050 Hz, every one under a
second — using nothing but the standard library (`wave` for the container, `math` for
the waveforms, `struct` to pack samples). No external assets, no network calls, no
third-party dependencies, so nothing here carries any license of its own beyond Bito's.

Usage:
    python3 tools/habi_sounds.py <output_dir>

Writes habi_meeh.wav, habi_cheer.wav, habi_sad.wav and habi_pop.wav into <output_dir>,
creating it first if it doesn't exist yet (e.g. app/src/main/res/raw/).
"""
from __future__ import annotations

import math
import os
import struct
import sys
import wave
from typing import Callable, List

SAMPLE_RATE = 22050
PEAK = 0.5  # soft ceiling across every cue — cozy, not arcade

Wave = Callable[[float], float]


def _triangle(phase: float) -> float:
    """A triangle wave sample at `phase` (in wave-cycles), range [-1, 1]."""
    p = phase % 1.0
    return 4.0 * abs(p - 0.5) - 1.0


def _sine(phase: float) -> float:
    return math.sin(2.0 * math.pi * phase)


def _envelope(i: int, n: int, attack: int, decay: int, sustain_level: float, release: int) -> float:
    """A short attack/decay/sustain/release envelope over `n` samples, 0..1.

    Sustain fills whatever is left between the decay's end and the release's start —
    callers keep attack + decay + release comfortably under `n` so that gap is never
    negative.
    """
    if i < attack:
        return i / attack if attack else 1.0
    if i < attack + decay:
        d = i - attack
        return 1.0 - (1.0 - sustain_level) * (d / decay if decay else 1.0)
    release_start = n - release
    if release and i >= release_start:
        r = i - release_start
        return sustain_level * max(0.0, 1.0 - r / release)
    return sustain_level


def _tone(
    duration_s: float,
    freq_start: float,
    freq_end: float,
    wave_fn: Wave,
    attack_s: float,
    decay_s: float,
    sustain_level: float,
    release_s: float,
    peak: float = PEAK,
) -> List[float]:
    """Renders one pitch-swept, enveloped tone as a list of samples in [-1, 1]."""
    n = max(1, int(SAMPLE_RATE * duration_s))
    attack = int(SAMPLE_RATE * attack_s)
    decay = int(SAMPLE_RATE * decay_s)
    release = int(SAMPLE_RATE * release_s)
    samples: List[float] = []
    phase = 0.0
    for i in range(n):
        t = i / n
        freq = freq_start + (freq_end - freq_start) * t
        phase += freq / SAMPLE_RATE
        env = _envelope(i, n, attack, decay, sustain_level, release)
        samples.append(peak * env * wave_fn(phase))
    return samples


def _silence(duration_s: float) -> List[float]:
    return [0.0] * int(SAMPLE_RATE * duration_s)


def build_meeh() -> List[float]:
    """Two short triangle bleats, each sliding 330->262 Hz with a quick ADSR — a soft,
    goat-ish "meeh-meeh" for Habi's greeting."""
    bleat = _tone(
        duration_s=0.15,
        freq_start=330.0,
        freq_end=262.0,
        wave_fn=_triangle,
        attack_s=0.01,
        decay_s=0.03,
        sustain_level=0.6,
        release_s=0.05,
    )
    return bleat + _silence(0.06) + bleat


def build_cheer() -> List[float]:
    """A rising C5-E5-G5 sine arpeggio, three notes of 120ms each, for a purchase/celebration cue."""
    notes = (523.25, 659.25, 783.99)  # C5, E5, G5
    out: List[float] = []
    for freq in notes:
        out += _tone(
            duration_s=0.12,
            freq_start=freq,
            freq_end=freq,
            wave_fn=_sine,
            attack_s=0.008,
            decay_s=0.02,
            sustain_level=0.7,
            release_s=0.03,
        )
    return out


def build_sad() -> List[float]:
    """A slow descending slide, 294->196 Hz, with a long gentle decay for a logged relapse."""
    return _tone(
        duration_s=0.65,
        freq_start=294.0,
        freq_end=196.0,
        wave_fn=_triangle,
        attack_s=0.02,
        decay_s=0.05,
        sustain_level=0.4,
        release_s=0.5,
        peak=0.45,
    )


def build_pop() -> List[float]:
    """A single 880 Hz blip with an instant attack and a short decay, under 100ms, for a purchase."""
    return _tone(
        duration_s=0.09,
        freq_start=880.0,
        freq_end=880.0,
        wave_fn=_sine,
        attack_s=0.0005,
        decay_s=0.01,
        sustain_level=0.3,
        release_s=0.05,
    )


def _write_wav(path: str, samples: List[float]) -> None:
    frames = b"".join(struct.pack("<h", max(-32768, min(32767, int(s * 32767)))) for s in samples)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        f.writeframes(frames)


def main() -> None:
    if len(sys.argv) != 2:
        print("usage: habi_sounds.py <output_dir>", file=sys.stderr)
        raise SystemExit(1)
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    sounds = {
        "habi_meeh.wav": build_meeh(),
        "habi_cheer.wav": build_cheer(),
        "habi_sad.wav": build_sad(),
        "habi_pop.wav": build_pop(),
    }
    for name, samples in sounds.items():
        path = os.path.join(out_dir, name)
        _write_wav(path, samples)
        print(f"{name}: {len(samples) / SAMPLE_RATE:.3f}s ({len(samples)} samples)")


if __name__ == "__main__":
    main()
