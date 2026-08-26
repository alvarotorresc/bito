# Habi sound generator — GPL-3.0-or-later, part of Bito.
"""Builds Habi's WAV cues — mono, 16-bit, 22050 Hz — using nothing but the standard
library (`wave` for the container, `math` for the DSP, `struct` to pack samples). No
external assets beyond the checked-in meow recording, no network calls, no third-party
dependencies, so nothing here carries any license of its own beyond Bito's.

Family design (art pass 2026-08-25): every Habi cue is now derived from the SAME curated
cat-meow recording, so the whole set speaks with one small organic voice — the previous
mix of raw sine/triangle synthesis next to a recording read as beeps from a machine
standing beside a real animal, and their loudness was wildly mismatched (the meow peaked
at 0.08 while the synth cheer hit 0.50). Derivations are plain, audible-on-a-phone-speaker
processing: resampling for pitch/tempo (a small creature MAY chipmunk a little — that is
the charm), gentle biquad EQ, raised-cosine fades and peak normalization to one family
level. Only the registro tick stays synthetic: it is deliberately neutral UI feedback,
not Habi's voice, and it is NOT rewritten by this script.

Usage:
    python3 tools/habi_sounds.py <res_raw_dir> [--master-meow]

Default run: reads <res_raw_dir>/habi_meeh.wav (the mastered greeting) and regenerates
habi_happy.wav, habi_cheer.wav, habi_sad.wav and habi_pop.wav from it — deterministic and
idempotent, safe to re-run. `--master-meow` first re-masters habi_meeh.wav IN PLACE
(high-pass, low-pass, leading-silence trim, fades, normalize); run that exactly once per
NEW source recording — the filters are gentle but they are not a no-op, so don't re-apply
them to an already-mastered file for fun.
"""
from __future__ import annotations

import math
import os
import struct
import sys
import wave
from typing import List

SAMPLE_RATE = 22050

# One family loudness: the greeting sets the reference peak, the celebratory cues sit just
# under it and the sad cue under that — contained, cozy, no cue ever jumps out of the set.
MEEH_PEAK = 0.5
HAPPY_PEAK = 0.5
CHEER_PEAK = 0.45
SAD_PEAK = 0.4
POP_PEAK = 0.45

# Mastering EQ: the high-pass strips handling rumble a phone speaker turns into mud, the
# low-pass rounds off the hiss and edge that read as harsh. Sad gets a darker second pass.
HP_CUTOFF_HZ = 170.0
LP_CUTOFF_HZ = 4200.0
SAD_LP_CUTOFF_HZ = 2800.0
SAD_HP_CUTOFF_HZ = 260.0

# The old greeting opened with ~340 ms of dead air, so the cue answered a pet a beat late —
# trim to the onset, keeping a short natural pre-roll.
TRIM_THRESHOLD_RATIO = 0.06
TRIM_PREROLL_S = 0.02
TRIM_TAIL_S = 0.15


def _read_wav(path: str) -> List[float]:
    with wave.open(path, "rb") as f:
        if f.getnchannels() != 1 or f.getsampwidth() != 2 or f.getframerate() != SAMPLE_RATE:
            raise SystemExit(f"{path}: expected mono/16-bit/{SAMPLE_RATE} Hz")
        raw = f.readframes(f.getnframes())
    return [s / 32768.0 for (s,) in struct.iter_unpack("<h", raw)]


def _write_wav(path: str, samples: List[float]) -> None:
    frames = b"".join(struct.pack("<h", max(-32768, min(32767, int(s * 32767)))) for s in samples)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        f.writeframes(frames)


def _silence(duration_s: float) -> List[float]:
    return [0.0] * int(SAMPLE_RATE * duration_s)


def _biquad(samples: List[float], kind: str, cutoff_hz: float, q: float = 0.7071) -> List[float]:
    """RBJ-cookbook low-pass ("lp") or high-pass ("hp"), direct form I."""
    w0 = 2.0 * math.pi * cutoff_hz / SAMPLE_RATE
    alpha = math.sin(w0) / (2.0 * q)
    cos_w0 = math.cos(w0)
    if kind == "lp":
        b0, b1, b2 = (1.0 - cos_w0) / 2.0, 1.0 - cos_w0, (1.0 - cos_w0) / 2.0
    elif kind == "hp":
        b0, b1, b2 = (1.0 + cos_w0) / 2.0, -(1.0 + cos_w0), (1.0 + cos_w0) / 2.0
    else:
        raise ValueError(kind)
    a0, a1, a2 = 1.0 + alpha, -2.0 * cos_w0, 1.0 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    out: List[float] = []
    x1 = x2 = y1 = y2 = 0.0
    for x in samples:
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        out.append(y)
        x2, x1 = x1, x
        y2, y1 = y1, y
    return out


def _peak(samples: List[float]) -> float:
    return max((abs(s) for s in samples), default=0.0)


def _normalize(samples: List[float], peak: float) -> List[float]:
    current = _peak(samples)
    if current == 0.0:
        return list(samples)
    gain = peak / current
    return [s * gain for s in samples]


def _fade(samples: List[float], fade_in_s: float, fade_out_s: float) -> List[float]:
    """Raised-cosine fades at both ends — clickless, softer than linear ramps."""
    out = list(samples)
    n = len(out)
    fade_in = min(n, int(SAMPLE_RATE * fade_in_s))
    fade_out = min(n, int(SAMPLE_RATE * fade_out_s))
    for i in range(fade_in):
        out[i] *= 0.5 - 0.5 * math.cos(math.pi * i / fade_in)
    for i in range(fade_out):
        out[n - 1 - i] *= 0.5 - 0.5 * math.cos(math.pi * i / fade_out)
    return out


def _trim_silence(samples: List[float]) -> List[float]:
    """Cuts leading/trailing quiet so the cue answers the touch NOW, keeping a natural pre-roll."""
    threshold = TRIM_THRESHOLD_RATIO * _peak(samples)
    first = next((i for i, s in enumerate(samples) if abs(s) >= threshold), 0)
    last = next((len(samples) - i for i, s in enumerate(reversed(samples)) if abs(s) >= threshold), len(samples))
    start = max(0, first - int(SAMPLE_RATE * TRIM_PREROLL_S))
    end = min(len(samples), last + int(SAMPLE_RATE * TRIM_TAIL_S))
    return samples[start:end]


def _resample(samples: List[float], rate: float) -> List[float]:
    """Linear-interpolation resample: rate > 1 plays faster AND higher — the little-creature
    pitch treatment used across the family (formants shift with it; on a tiny pet that reads
    as intended, not as an artifact)."""
    n = int(len(samples) / rate)
    out: List[float] = []
    for i in range(n):
        pos = i * rate
        j = int(pos)
        frac = pos - j
        a = samples[j] if j < len(samples) else 0.0
        b = samples[j + 1] if j + 1 < len(samples) else a
        out.append(a + (b - a) * frac)
    return out


def master_meow(path: str) -> None:
    """One-shot mastering for a (new) greeting recording, in place: EQ, onset trim, fades,
    family level. Fixes the three faults the shipped file had — ~340 ms of dead air before
    the meow, a 0.08 peak (six times quieter than the synth cues it lived beside) and an
    unfiltered top end that read as harsh next to everything else."""
    samples = _read_wav(path)
    samples = _biquad(samples, "hp", HP_CUTOFF_HZ)
    samples = _biquad(samples, "lp", LP_CUTOFF_HZ)
    samples = _trim_silence(samples)
    samples = _fade(samples, 0.006, 0.07)
    samples = _normalize(samples, MEEH_PEAK)
    _write_wav(path, samples)


def build_happy(meeh: List[float]) -> List[float]:
    """The pet-streak double meow: the greeting twice, second time smaller and more eager
    (higher, quicker, a touch louder) — a creature that liked it and says so again. The
    previous file kept the source's dead air and left its first meow nearly inaudible."""
    first = [s * 0.92 for s in _resample(meeh, 1.28)]
    second = _resample(meeh, 1.45)
    return _normalize(first + _silence(0.07) + second, HAPPY_PEAK)


def _chirp(meeh: List[float], slice_s: float, rate: float) -> List[float]:
    """A short chirp cut from the meow's onset (attack plus the start of the vowel) and pitched
    up — the creature's own voice at trill length."""
    core = _fade(meeh[: int(SAMPLE_RATE * slice_s)], 0.004, 0.08)
    return _resample(core, rate)


def build_cheer(meeh: List[float]) -> List[float]:
    """Celebration: a rising three-chirp trill ("mi-mi-miii!") — same rising-triad idea as the
    old C5-E5-G5 arpeggio, but sung by the meow instead of bare sine waves. The last chirp is
    longer and rings out."""
    chirps = [
        _chirp(meeh, 0.26, 1.5),
        _chirp(meeh, 0.26, 1.7),
        _chirp(meeh, 0.34, 1.9),
    ]
    gap = _silence(0.03)
    return _normalize(chirps[0] + gap + chirps[1] + gap + chirps[2], CHEER_PEAK)


def build_sad(meeh: List[float]) -> List[float]:
    """A logged relapse: the meow slowed and dropped ("mrooow"), darkened with a second
    low-pass and left to fade long. Replaces the old 294->196 Hz triangle slide, which put
    ~99% of its energy under 300 Hz — below what a phone speaker reproduces, so it played
    as a faint buzz rather than a sad little voice. The drop is kept moderate (0.78) for
    the same reason: any lower and the voice itself sinks under the speaker's floor. A
    second high-pass rebalances the slowed fundamental toward the harmonics that actually
    carry on a speaker (doubles the >300 Hz share, measured)."""
    slow = _resample(meeh, 0.78)
    dark = _biquad(_biquad(slow, "hp", SAD_HP_CUTOFF_HZ), "lp", SAD_LP_CUTOFF_HZ)
    return _normalize(_fade(dark, 0.01, 0.15), SAD_PEAK)


def build_pop(meeh: List[float]) -> List[float]:
    """Purchase: a tiny high "mip!" — the meow's onset pitched far up, under 100 ms. Vocal
    where the old 880 Hz sine blip was mechanical."""
    return _normalize(_chirp(meeh, 0.18, 2.2), POP_PEAK)


# --- Registro tick (NOT Habi's voice) -------------------------------------------------------
# The log tick is deliberately neutral UI feedback with its own Ajustes toggle, so it stays
# synthetic and untouched by the family rework above. Kept verbatim so the recipe for the
# shipped log_tick.wav lives in the repo; main() re-writes it byte-identically.


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


def build_tick() -> List[float]:
    """A tiny, droplet-like blip for logging a habit — quiet, short, deliberately neutral so it
    never competes with Habi's own voice. Gated by its own Ajustes toggle, not the Habi one."""
    duration_s, freq_start, freq_end, peak = 0.05, 880.0, 990.0, 0.3
    n = max(1, int(SAMPLE_RATE * duration_s))
    attack = int(SAMPLE_RATE * 0.002)
    decay = int(SAMPLE_RATE * 0.012)
    release = int(SAMPLE_RATE * 0.025)
    samples: List[float] = []
    phase = 0.0
    for i in range(n):
        t = i / n
        freq = freq_start + (freq_end - freq_start) * t
        phase += freq / SAMPLE_RATE
        env = _envelope(i, n, attack, decay, 0.35, release)
        samples.append(peak * env * _sine(phase))
    return samples


def main() -> None:
    args = [a for a in sys.argv[1:] if a != "--master-meow"]
    if len(args) != 1:
        print("usage: habi_sounds.py <res_raw_dir> [--master-meow]", file=sys.stderr)
        raise SystemExit(1)
    out_dir = args[0]
    os.makedirs(out_dir, exist_ok=True)
    meeh_path = os.path.join(out_dir, "habi_meeh.wav")
    if "--master-meow" in sys.argv[1:]:
        master_meow(meeh_path)
        print("habi_meeh.wav: mastered in place")
    meeh = _read_wav(meeh_path)
    sounds = {
        "habi_happy.wav": build_happy(meeh),
        "habi_cheer.wav": build_cheer(meeh),
        "habi_sad.wav": build_sad(meeh),
        "habi_pop.wav": build_pop(meeh),
        "log_tick.wav": build_tick(),
    }
    for name, samples in sounds.items():
        path = os.path.join(out_dir, name)
        _write_wav(path, samples)
        print(f"{name}: {len(samples) / SAMPLE_RATE:.3f}s ({len(samples)} samples)")


if __name__ == "__main__":
    main()
