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
SIZE_LIMIT = 400 * 1024
WEB_NAMES = {
    "cover": "hoy",
    "shot-01-habitos": "detalle",
    "shot-02-recordatorio": "notificacion",
    "shot-03-estadisticas": "stats",
    "shot-04-widget": "widget",
    "shot-05-insignias": "logros",
    "shot-06-revision": "repaso",
    "feat-tareas": "foco",
    "feat-respiracion": "respiracion",
}


def plan_copies(public: Path, web: Path) -> list[tuple[Path, Path]]:
    return [
        (public / lang / f"{screen}.png", web / f"{name}-{lang}.png")
        for lang in LANGS
        for name, screen in WEB_NAMES.items()
    ]


def save_under(img: Image.Image, dst: Path, max_bytes: int) -> int:
    img.save(dst, "PNG", optimize=True)
    if dst.stat().st_size <= max_bytes:
        return dst.stat().st_size
    img.quantize(colors=256, method=Image.Quantize.FASTOCTREE).save(dst, "PNG", optimize=True)
    size = dst.stat().st_size
    if size > max_bytes:
        raise ValueError(f"to_web: {dst} pesa {size} bytes incluso con paleta, el máximo es {max_bytes}")
    return size


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
            written.append((dst, save_under(img.convert("RGB"), dst, SIZE_LIMIT)))
    return written


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--public", default=str(PUBLIC))
    parser.add_argument("--web", default=str(WEB_ASSETS))
    args = parser.parse_args()
    for dst, size in copy_all(Path(args.public), Path(args.web)):
        print(f"{size // 1024:5d} KB  {dst}")


if __name__ == "__main__":
    main()
