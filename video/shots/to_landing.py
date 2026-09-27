"""Genera las capturas de la landing y de los README a partir de public/shots.

Uso: python3 to_landing.py [--public RUTA] [--out RUTA]
Escribe web/img/capturas/{es,en}/<pantalla>.png a 540x1170, cada una de 300 KB o menos.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

from to_web import save_under

HERE = Path(__file__).resolve().parent
PUBLIC = HERE.parent / "public" / "shots"
OUT = HERE.parent.parent / "web" / "img" / "capturas"
LANGS = ("es", "en")
SCREENS = ("hoy", "detalle", "habi", "stats", "records", "logros", "repaso", "ajustes", "tareas", "foco", "respiracion")
SIZE = (540, 1170)
MAX_BYTES = 300 * 1024


def plan(public: Path, out: Path) -> list[tuple[Path, Path]]:
    return [(public / lang / f"{screen}.png", out / lang / f"{screen}.png") for lang in LANGS for screen in SCREENS]


def build_all(public: Path, out: Path) -> list[tuple[Path, int]]:
    pairs = plan(public, out)
    missing = [str(src) for src, _ in pairs if not src.exists()]
    if missing:
        raise FileNotFoundError("to_landing: faltan capturas: " + ", ".join(missing))
    for src, _ in pairs:
        with Image.open(src) as img:
            if img.size != (1080, 2340):
                raise ValueError(f"to_landing: {src} mide {img.size[0]}x{img.size[1]}, esperaba 1080x2340")
    written: list[tuple[Path, int]] = []
    for src, dst in pairs:
        dst.parent.mkdir(parents=True, exist_ok=True)
        with Image.open(src) as img:
            small = img.convert("RGB").resize(SIZE, Image.Resampling.LANCZOS)
        written.append((dst, save_under(small, dst, MAX_BYTES)))
    return written


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--public", default=str(PUBLIC))
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()
    for dst, size in build_all(Path(args.public), Path(args.out)):
        print(f"{size // 1024:5d} KB  {dst}")


if __name__ == "__main__":
    main()
