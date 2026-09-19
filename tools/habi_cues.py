# Habi cue generator — GPL-3.0-or-later, part of Bito.
"""Sintetiza los SEIS clips de la voz de Habi y los codifica a OGG Vorbis mono 44,1 kHz.

La familia entera es nueva (biblia §8): nada de la voz anterior se reutiliza, se recorta ni se
reprocesa. La regla es «criatura con peso · nunca pitido de interfaz · nunca madera hueca», y lo
que la hace sonar a UNA criatura es el TRATAMIENTO COMÚN, no cada clip por separado: mismo cuerpo
armónico (fundamental con dos parciales inarmónicos suaves), misma envolvente de ataque corto y
cola amortiguada, mismo paso de banda y misma normalización de pico.

Determinista: `random.Random(SEED)` por clip, así regenerar no ensucia el diff.

Uso:
    python3 tools/habi_cues.py app/src/main/res/raw

Requiere ffmpeg con libvorbis para el paso final (verificado: ffmpeg 8.1.2). Sin ffmpeg el script
para con un mensaje claro. En Fedora/Nobara:  sudo dnf install ffmpeg

Variantes (para que el arquitecto elija de oído, no de código — ninguna decisión de timbre es
mía): `--variant {a,b,c}` (por defecto "a") reescala el MISMO pipeline con un juego de
multiplicadores por variante — tono, duración, corte de filtro y cola — sin tocar la síntesis de
cada clip. `--suffix` añade "-<variante>" al nombre del fichero (usado para dejar las tres
variantes lado a lado en `.superpowers/apk/sonidos/`); sin `--suffix` el nombre sale limpio, que es
lo que se instala en `res/raw`:

    for v in a b c; do
        python3 tools/habi_cues.py .superpowers/apk/sonidos --variant "$v" --suffix
    done
    python3 tools/habi_cues.py app/src/main/res/raw --variant a
"""
from __future__ import annotations

import math
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import wave
from random import Random
from typing import List, Tuple

SAMPLE_RATE = 44100
SEED = 20260919

MAX_CLIP_BYTES = 12 * 1024
MAX_FAMILY_BYTES = 70 * 1024

# Calidad libvorbis (-q:a). Si algun clip se pasa de presupuesto, baja esto a 2 ANTES de tocar la
# sintesis (spec §10.2) — el script para con un mensaje claro si el presupuesto no se cumple.
VORBIS_QUALITY = 3

# Los dos parciales inarmonicos que dan cuerpo a BUMP, JINGLE y PURR: (multiplicador de
# frecuencia, amplitud relativa a la fundamental). El mismo par en los tres es justo lo que hace
# que "un golpecito" y "dos notas" suenen de la MISMA criatura y no de dos instrumentos distintos.
BODY_PARTIALS: List[Tuple[float, float]] = [(2.4, 0.35), (3.7, 0.18)]

# El techo de pico por clip: mismo PROCESO de normalizacion para los seis (_normalize), pero el
# tick es feedback de UI minimo (mas bajo que la voz), y sigh es un ruido de fondo, no un golpe —
# igual que el habi_sounds.py que este script sustituye tenia un pico por clip bajo un mismo
# tratamiento.
PEAK_BUMP = 0.90
PEAK_MEEH = 0.85
PEAK_TICK = 0.55
PEAK_SIGH = 0.70
PEAK_JINGLE = 0.85
PEAK_PURR = 0.80

# Tres personalidades del MISMO pipeline (ver docstring del modulo). "a" es la lectura literal del
# brief; "b" achica y aviva a Habi; "c" la agranda y sosiega. Nada de esto cambia la sintesis por
# clip, solo la reescala.
VARIANTS = {
    "a": {
        "pitch": 1.00,
        "dur": 1.00,
        "cutoff": 1.00,
        "tail": 1.00,
        "desc": "Baseline: el timbre y la duracion tal como los describe el brief, sin reescalar.",
    },
    "b": {
        "pitch": 1.09,
        "dur": 0.82,
        "cutoff": 1.30,
        "tail": 0.75,
        "desc": "Mas aguda y mas corta, filtro mas abierto: una criatura mas pequena y vivaracha.",
    },
    "c": {
        "pitch": 0.90,
        "dur": 1.22,
        "cutoff": 0.78,
        "tail": 1.35,
        "desc": "Mas grave y mas larga, filtro mas cerrado: una criatura mas grande y pausada.",
    },
}


# --- DSP compartido: el mismo cuerpo armonico, la misma envolvente, el mismo filtro y la misma
# normalizacion para los seis clips -----------------------------------------------------------


def _sine(phase: float) -> float:
    return math.sin(2.0 * math.pi * phase)


def _biquad(samples: List[float], kind: str, cutoff_hz: float, q: float = 0.7071) -> List[float]:
    """RBJ-cookbook paso-bajo ("lp") o paso-banda de ganancia constante ("bp"), forma directa I.
    El mismo filtro para toda la familia — solo cambian el corte y la Q por clip.
    """
    cutoff_hz = min(max(cutoff_hz, 1.0), SAMPLE_RATE * 0.49)
    w0 = 2.0 * math.pi * cutoff_hz / SAMPLE_RATE
    alpha = math.sin(w0) / (2.0 * q)
    cos_w0 = math.cos(w0)
    if kind == "lp":
        b0, b1, b2 = (1.0 - cos_w0) / 2.0, 1.0 - cos_w0, (1.0 - cos_w0) / 2.0
    elif kind == "bp":
        b0, b1, b2 = alpha, 0.0, -alpha
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


def _sweeping_bandpass(samples: List[float], hz_start: float, hz_end: float, q: float) -> List[float]:
    """Paso-banda con el centro deslizando LINEALMENTE de hz_start a hz_end: los coeficientes se
    recalculan cada muestra. El barrido es lento (450 ms), asi que no hay salto audible. Solo lo
    usa SIGH.
    """
    n = len(samples)
    out: List[float] = []
    x1 = x2 = y1 = y2 = 0.0
    for i, x in enumerate(samples):
        t = i / n if n else 0.0
        cutoff = min(max(hz_start + (hz_end - hz_start) * t, 1.0), SAMPLE_RATE * 0.49)
        w0 = 2.0 * math.pi * cutoff / SAMPLE_RATE
        alpha = math.sin(w0) / (2.0 * q)
        cos_w0 = math.cos(w0)
        b0, b1, b2 = alpha, 0.0, -alpha
        a0, a1, a2 = 1.0 + alpha, -2.0 * cos_w0, 1.0 - alpha
        b0n, b1n, b2n, a1n, a2n = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
        y = b0n * x + b1n * x1 + b2n * x2 - a1n * y1 - a2n * y2
        out.append(y)
        x2, x1 = x1, x
        y2, y1 = y1, y
    return out


def _envelope(n: int, attack_s: float, tail_s: float) -> List[float]:
    """LA envolvente compartida por los seis clips: ataque lineal corto, cola EXPONENCIAL
    amortiguada (constante de tiempo tail_s/5, ~99% apagada al final de tail_s). Ataque y cola son
    los unicos parametros que cambian de un clip a otro.
    """
    attack_n = max(1, int(SAMPLE_RATE * attack_s))
    tau = max(tail_s / 5.0, 1e-4)
    env: List[float] = []
    for i in range(n):
        if i < attack_n:
            env.append(i / attack_n)
        else:
            t = (i - attack_n) / SAMPLE_RATE
            env.append(math.exp(-t / tau))
    return env


def _peak(samples: List[float]) -> float:
    return max((abs(s) for s in samples), default=0.0)


def _normalize(samples: List[float], peak: float) -> List[float]:
    """LA normalizacion compartida por los seis clips: escala a un techo de pico comun."""
    current = _peak(samples)
    if current == 0.0:
        return list(samples)
    gain = peak / current
    return [s * gain for s in samples]


def _mix(*tracks: List[float]) -> List[float]:
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, s in enumerate(t):
            out[i] += s
    return out


def _harmonic_body(
    n: int,
    rng: Random,
    freq_start: float,
    freq_end: float,
    partials: List[Tuple[float, float]] = BODY_PARTIALS,
) -> List[float]:
    """Fundamental (con caida de tono lineal de freq_start a freq_end) mas los parciales
    inarmonicos compartidos — el cuerpo armonico comun a BUMP, JINGLE y PURR. Cada parcial se
    desafina una pizca (jitter deterministico via [rng]) para que no suene a suma de senos
    perfecta — una criatura, no un sintetizador.
    """
    jittered = [(mult * (1.0 + rng.uniform(-0.008, 0.008)), amp) for mult, amp in partials]
    out: List[float] = []
    phase = 0.0
    partial_phase = [0.0] * len(jittered)
    for i in range(n):
        t = i / n if n else 0.0
        freq = freq_start + (freq_end - freq_start) * t
        phase += freq / SAMPLE_RATE
        sample = _sine(phase)
        for idx, (mult, amp) in enumerate(jittered):
            partial_phase[idx] += (freq * mult) / SAMPLE_RATE
            sample += amp * _sine(partial_phase[idx])
        out.append(sample)
    return out


def _pink_noise(rng: Random, n: int) -> List[float]:
    """Ruido rosa (metodo economico de Paul Kellet, 3 polos) — usado por SIGH. Ruido blanco
    filtrado, no una grabacion: sigue sin dependencias mas alla de la libreria estandar."""
    b0 = b1 = b2 = 0.0
    out: List[float] = []
    for _ in range(n):
        white = rng.uniform(-1.0, 1.0)
        b0 = 0.99765 * b0 + white * 0.0990460
        b1 = 0.96300 * b1 + white * 0.2965164
        b2 = 0.57000 * b2 + white * 1.0526913
        out.append(b0 + b1 + b2 + white * 0.1848)
    return out


# --- Los seis clips -----------------------------------------------------------------------------


def build_bump(rng: Random, v: dict) -> List[float]:
    """Golpecito sordo (~90 ms): fundamental 150 Hz cayendo un semitono, ataque 4 ms, cola
    exponencial 80 ms, paso bajo a 1,2 kHz. Sordo con cuerpo — nunca un "tock" de madera."""
    n = max(1, int(SAMPLE_RATE * 0.090 * v["dur"]))
    f0 = 150.0 * v["pitch"]
    f1 = f0 * 2.0 ** (-1.0 / 12.0)
    body = _harmonic_body(n, rng, f0, f1)
    body = _biquad(body, "lp", 1200.0 * v["cutoff"])
    env = _envelope(n, 0.004, 0.080 * v["tail"])
    return _normalize([s * e for s, e in zip(body, env)], PEAK_BUMP)


def build_meeh(rng: Random, v: dict) -> List[float]:
    """El "meeh" al levantarse (~280 ms): dos formantes (520 y 1150 Hz) sobre una fundamental de
    230 Hz con vibrato de 6 Hz y una caida de tono de tres semitonos al final — el gesto de
    "meeh", no una nota."""
    n = max(1, int(SAMPLE_RATE * 0.280 * v["dur"]))
    f0 = 230.0 * v["pitch"]
    f_end = f0 * 2.0 ** (-3.0 / 12.0)
    vibrato_hz = 6.0
    vibrato_depth_semi = 0.4
    formants = [(520.0 * v["pitch"], 0.50), (1150.0 * v["pitch"], 0.28)]
    out: List[float] = []
    phase = 0.0
    formant_phase = [0.0, 0.0]
    for i in range(n):
        t = i / n
        freq = f0 + (f_end - f0) * t
        vib = vibrato_depth_semi * math.sin(2.0 * math.pi * vibrato_hz * i / SAMPLE_RATE)
        freq *= 2.0 ** (vib / 12.0)
        phase += freq / SAMPLE_RATE
        sample = _sine(phase)
        for idx, (fhz, amp) in enumerate(formants):
            formant_phase[idx] += fhz / SAMPLE_RATE
            sample += amp * _sine(formant_phase[idx])
        out.append(sample)
    env = _envelope(n, 0.015, 0.220 * v["tail"])
    out = [s * e for s, e in zip(out, env)]
    out = _biquad(out, "lp", 2200.0 * v["cutoff"])
    return _normalize(out, PEAK_MEEH)


def build_tick(rng: Random, v: dict) -> List[float]:
    """Chirrido minimo al registrar (~55 ms): ruido filtrado en banda estrecha alrededor de
    2,1 kHz con envolvente 3 ms/40 ms. Seco, sin tono definido — un chirrido, no un bip."""
    n = max(1, int(SAMPLE_RATE * 0.055 * v["dur"]))
    noise = [rng.uniform(-1.0, 1.0) for _ in range(n)]
    filtered = _biquad(noise, "bp", 2100.0 * v["pitch"], q=10.0)
    env = _envelope(n, 0.003, 0.040 * v["tail"])
    return _normalize([s * e for s, e in zip(filtered, env)], PEAK_TICK)


def build_sigh(rng: Random, v: dict) -> List[float]:
    """Exhalacion al dormirse (~450 ms): ruido rosa con un paso banda que baja de 900 a 380 Hz,
    envolvente lenta (80 ms ataque, 320 ms cola) y un halo tonal de 180 Hz al 20%."""
    n = max(1, int(SAMPLE_RATE * 0.450 * v["dur"]))
    noise = _pink_noise(rng, n)
    swept = _sweeping_bandpass(noise, 900.0 * v["pitch"], 380.0 * v["pitch"], q=1.2)
    env = _envelope(n, 0.080, 0.320 * v["tail"])
    breath = [s * e for s, e in zip(swept, env)]
    halo_freq = 180.0 * v["pitch"]
    halo = [0.20 * _sine(halo_freq * i / SAMPLE_RATE) for i in range(n)]
    halo = [s * e for s, e in zip(halo, env)]
    return _normalize(_mix(breath, halo), PEAK_SIGH)


def build_jingle(rng: Random, v: dict) -> List[float]:
    """Dia perfecto (~380 ms): DOS notas (no tres), 587 Hz -> 880 Hz, con el mismo cuerpo armonico
    del bump y solapamiento de 40 ms."""
    n_note = max(1, int(SAMPLE_RATE * 0.210 * v["dur"]))
    n_overlap = max(0, min(n_note - 1, int(SAMPLE_RATE * 0.040 * v["dur"])))
    f1 = 587.0 * v["pitch"]  # D5
    f2 = 880.0 * v["pitch"]  # A5
    env = _envelope(n_note, 0.004, 0.160 * v["tail"])
    note1 = _biquad(_harmonic_body(n_note, rng, f1, f1), "lp", 3200.0 * v["cutoff"])
    note1 = [s * e for s, e in zip(note1, env)]
    note2 = _biquad(_harmonic_body(n_note, rng, f2, f2), "lp", 3200.0 * v["cutoff"])
    note2 = [s * e for s, e in zip(note2, env)]
    total_n = n_note * 2 - n_overlap
    out = [0.0] * total_n
    for i, s in enumerate(note1):
        out[i] += s
    offset = n_note - n_overlap
    for i, s in enumerate(note2):
        out[offset + i] += s
    return _normalize(out, PEAK_JINGLE)


def build_purr(rng: Random, v: dict) -> List[float]:
    """La caricia (~520 ms): fundamental 95 Hz modulada en amplitud a 24 Hz con jitter, dos
    parciales, paso bajo a 800 Hz. Ronroneo, no motor."""
    n = max(1, int(SAMPLE_RATE * 0.520 * v["dur"]))
    f0 = 95.0 * v["pitch"]
    mod_hz = 24.0
    jittered = [(mult * (1.0 + rng.uniform(-0.008, 0.008)), amp) for mult, amp in BODY_PARTIALS]
    out: List[float] = []
    phase = 0.0
    partial_phase = [0.0] * len(jittered)
    mod_phase = 0.0
    for _i in range(n):
        # Jitter suave en la tasa de modulacion: un ronroneo real no es un tremolo perfecto.
        jitter = 1.0 + (rng.random() * 2.0 - 1.0) * 0.03
        mod_phase += (mod_hz * jitter) / SAMPLE_RATE
        mod = 0.65 + 0.35 * _sine(mod_phase)
        phase += f0 / SAMPLE_RATE
        sample = _sine(phase)
        for idx, (mult, amp) in enumerate(jittered):
            partial_phase[idx] += (f0 * mult) / SAMPLE_RATE
            sample += amp * _sine(partial_phase[idx])
        out.append(sample * mod)
    out = _biquad(out, "lp", 800.0 * v["cutoff"])
    env = _envelope(n, 0.030, 0.300 * v["tail"])
    return _normalize([s * e for s, e in zip(out, env)], PEAK_PURR)


BUILDERS = {
    "habi_bump": build_bump,
    "habi_meeh": build_meeh,
    "habi_tick": build_tick,
    "habi_sigh": build_sigh,
    "habi_jingle": build_jingle,
    "habi_purr": build_purr,
}


# --- WAV intermedio + codificacion a OGG ---------------------------------------------------------


def _write_wav(path: str, samples: List[float]) -> None:
    frames = b"".join(struct.pack("<h", max(-32768, min(32767, int(s * 32767)))) for s in samples)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        f.writeframes(frames)


def _encode_ogg(tmp_wav: str, out_ogg: str, quality: int) -> None:
    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg no esta instalado. En Fedora/Nobara:  sudo dnf install ffmpeg")
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-loglevel",
            "error",
            "-i",
            tmp_wav,
            "-ac",
            "1",
            "-ar",
            str(SAMPLE_RATE),
            "-c:a",
            "libvorbis",
            "-q:a",
            str(quality),
            out_ogg,
        ],
        check=True,
    )


def main() -> None:
    args = sys.argv[1:]
    variant = "a"
    if "--variant" in args:
        variant_idx = args.index("--variant")
        variant = args[variant_idx + 1]
        args = args[:variant_idx] + args[variant_idx + 2 :]
    positional = [a for a in args if not a.startswith("--")]
    if len(positional) != 1:
        print("uso: python3 tools/habi_cues.py <res_raw_dir> [--variant a|b|c] [--suffix]", file=sys.stderr)
        raise SystemExit(1)
    out_dir = positional[0]

    if variant not in VARIANTS:
        raise SystemExit(f"variante desconocida: {variant!r} (usa a, b o c)")
    v = VARIANTS[variant]
    suffix = f"-{variant}" if "--suffix" in args else ""

    os.makedirs(out_dir, exist_ok=True)
    tmp_dir = tempfile.mkdtemp(prefix="habi_cues_")
    try:
        total_bytes = 0
        for name, builder in BUILDERS.items():
            rng = Random(SEED)
            samples = builder(rng, v)
            tmp_wav = os.path.join(tmp_dir, f"{name}.wav")
            _write_wav(tmp_wav, samples)
            out_name = f"{name}{suffix}.ogg"
            out_path = os.path.join(out_dir, out_name)
            _encode_ogg(tmp_wav, out_path, VORBIS_QUALITY)
            size = os.path.getsize(out_path)
            total_bytes += size
            print(f"{out_name}: {len(samples) / SAMPLE_RATE:.3f}s, {size} bytes")
            if size > MAX_CLIP_BYTES:
                raise SystemExit(
                    f"{out_name} pesa {size} bytes, mas de los {MAX_CLIP_BYTES} permitidos "
                    f"(spec S10.2). Baja VORBIS_QUALITY a 2 antes de tocar la sintesis."
                )
        print(f"variante {variant!r} ({v['desc']}) — total: {total_bytes} bytes (limite {MAX_FAMILY_BYTES})")
        if total_bytes > MAX_FAMILY_BYTES:
            raise SystemExit(f"la familia pesa {total_bytes} bytes, mas de los {MAX_FAMILY_BYTES} permitidos.")
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
