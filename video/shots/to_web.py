"""Copia a alvarotc-web las capturas de Bito que pide docs/MEDIA_PROYECTOS.md.

Uso: python3 to_web.py [--public RUTA] [--web RUTA]
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

PUBLIC = Path(__file__).resolve().parent.parent / "public" / "shots"
WEB_ASSETS = Path("/home/alvarotc/Documents/apps/alvarotc-web/src/assets/projects/bito")
LANGS = ("es", "en")
SIZE_WARNING = 400 * 1024
WEB_NAMES = {
    "cover": "hoy",
    "shot-01-habitos": "detalle",
    "shot-02-recordatorio": "notificacion",
    "shot-03-estadisticas": "stats",
    "shot-04-widget": "widget",
    "shot-05-insignias": "logros",
    "shot-06-revision": "repaso",
}


def plan_copies(public: Path, web: Path) -> list[tuple[Path, Path]]:
    return [
        (public / lang / f"{screen}.png", web / f"{name}-{lang}.png")
        for lang in LANGS
        for name, screen in WEB_NAMES.items()
    ]


def copy_all(public: Path, web: Path) -> list[tuple[Path, int]]:
    pairs = plan_copies(public, web)
    missing = [str(src) for src, _ in pairs if not src.exists()]
    if missing:
        raise FileNotFoundError("to_web: faltan capturas: " + ", ".join(missing))
    for src, _ in pairs:
        with Image.open(src) as img:
            if img.size != (1080, 2340):
                raise ValueError(f"to_web: {src} mide {img.size[0]}x{img.size[1]}, esperaba 1080x2340")
    web.mkdir(parents=True, exist_ok=True)
    written: list[tuple[Path, int]] = []
    for src, dst in pairs:
        with Image.open(src) as img:
            img.convert("RGB").save(dst, "PNG", optimize=True)
        written.append((dst, dst.stat().st_size))
    return written


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--public", default=str(PUBLIC))
    parser.add_argument("--web", default=str(WEB_ASSETS))
    args = parser.parse_args()
    for dst, size in copy_all(Path(args.public), Path(args.web)):
        warning = "  AVISO: más de 400 KB, lo resuelve el plan de la ficha web" if size > SIZE_WARNING else ""
        print(f"{size // 1024:5d} KB  {dst}{warning}")


if __name__ == "__main__":
    main()
