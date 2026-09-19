# Habi grain generator — GPL-3.0-or-later, part of Bito.
"""Genera el tile de grano de Habi: 64x64 px, gris, sin costuras, determinista.

Ruido de valor (value noise) con interpolacion suave sobre una rejilla toroidal, asi el tile
repite sin junta visible. Determinista por `random.Random(SEED)`: dos ejecuciones dan el mismo
PNG byte a byte, y regenerarlo no ensucia el diff.

Uso:
    python3 tools/habi_grain.py app/src/main/res/drawable-nodpi/habi_grain.png

Requiere Pillow (verificado en el equipo: 12.3.0). Si faltara:  pip install --user Pillow
"""
from __future__ import annotations

import sys
from random import Random

TILE_PX = 64
SEED = 20260919

# Dos octavas: la rejilla gruesa da forma, la fina da detalle. Pesos 0.7/0.3 (70/30).
OCTAVE1_GRID = 8
OCTAVE1_WEIGHT = 0.7
OCTAVE2_GRID = 16
OCTAVE2_WEIGHT = 0.3

# Luminancia de salida: 0.78 a 1.00 — el grano solo oscurece (BlendMode.Multiply), nunca aclara.
OUTPUT_LO = 0.78
OUTPUT_HI = 1.00


def _smoothstep(t: float) -> float:
    """Interpolacion suave (Perlin-style): 3t^2 - 2t^3, cero derivada en los extremos."""
    return t * t * (3.0 - 2.0 * t)


def _value_grid(rng: Random, grid_size: int) -> list[list[float]]:
    """Rejilla grid_size x grid_size de valores aleatorios en [0,1)."""
    return [[rng.random() for _ in range(grid_size)] for _ in range(grid_size)]


def _sample_toroidal(grid: list[list[float]], grid_size: int, u: float, v: float) -> float:
    """Muestrea la rejilla en (u, v) in [0,1)x[0,1) con interpolacion bilineal suave y
    envoltura toroidal (los bordes se leen del lado opuesto), asi el tile repite sin junta.
    """
    fx = u * grid_size
    fy = v * grid_size
    x0 = int(fx) % grid_size
    y0 = int(fy) % grid_size
    x1 = (x0 + 1) % grid_size
    y1 = (y0 + 1) % grid_size
    tx = _smoothstep(fx - int(fx))
    ty = _smoothstep(fy - int(fy))

    top = grid[y0][x0] * (1.0 - tx) + grid[y0][x1] * tx
    bottom = grid[y1][x0] * (1.0 - tx) + grid[y1][x1] * tx
    return top * (1.0 - ty) + bottom * ty


def _generate_pixels(size: int) -> list[int]:
    """Devuelve `size*size` valores de luminancia (0-255), fila a fila."""
    rng = Random(SEED)
    grid1 = _value_grid(rng, OCTAVE1_GRID)
    grid2 = _value_grid(rng, OCTAVE2_GRID)

    raw: list[float] = []
    for y in range(size):
        for x in range(size):
            u = x / size
            v = y / size
            n1 = _sample_toroidal(grid1, OCTAVE1_GRID, u, v)
            n2 = _sample_toroidal(grid2, OCTAVE2_GRID, u, v)
            raw.append(n1 * OCTAVE1_WEIGHT + n2 * OCTAVE2_WEIGHT)

    lo = min(raw)
    hi = max(raw)
    span = (hi - lo) or 1.0

    pixels: list[int] = []
    for n in raw:
        normalized = (n - lo) / span  # 0..1, contraste completo
        luminance = OUTPUT_LO + normalized * (OUTPUT_HI - OUTPUT_LO)
        pixels.append(round(luminance * 255.0))
    return pixels


def build_tile(size: int = TILE_PX):
    from PIL import Image

    pixels = _generate_pixels(size)
    image = Image.new("L", (size, size))
    image.putdata(pixels)
    return image


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Uso: python3 {argv[0]} <ruta_salida.png>", file=sys.stderr)
        return 1

    output_path = argv[1]
    image = build_tile()
    image.save(output_path, optimize=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
