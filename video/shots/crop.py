"""Recorte de capturas crudas del AVD bito-shots a 1080x2340 sin barras, y sus comprobaciones."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

WIDTH, HEIGHT = 1080, 2340
PAPEL = (0xF2, 0xEC, 0xE1)


def load_geometry(env_path: Path) -> tuple[int, int]:
    values = dict(line.split("=", 1) for line in Path(env_path).read_text().split() if "=" in line)
    top, bottom, height = int(values["TOP"]), int(values["BOTTOM"]), int(values["HEIGHT"])
    if height != HEIGHT + top + bottom:
        raise ValueError(f"avd.env incoherente: HEIGHT={height}, esperaba {HEIGHT + top + bottom}")
    return top, bottom


def check_final(path: Path) -> None:
    with Image.open(path) as img:
        if img.size != (WIDTH, HEIGHT) or img.mode != "RGB":
            raise ValueError(f"{path}: {img.size[0]}x{img.size[1]} {img.mode}, esperaba {WIDTH}x{HEIGHT} RGB")


def crop_raw(raw: Path, out: Path, top: int, bottom: int) -> None:
    expected = (WIDTH, HEIGHT + top + bottom)
    with Image.open(raw) as img:
        if img.size != expected:
            raise ValueError(f"{raw}: mide {img.size[0]}x{img.size[1]}, esperaba {expected[0]}x{expected[1]}")
        img.convert("RGB").crop((0, top, WIDTH, top + HEIGHT)).save(out, "PNG", optimize=True)
    check_final(out)


def top_row_is_papel(path: Path, tolerance: int = 10) -> bool:
    with Image.open(path) as img:
        rgb = img.convert("RGB")
        samples = [rgb.getpixel((x, 0)) for x in range(0, WIDTH, 20)]
    close = sum(all(abs(c - p) <= tolerance for c, p in zip(px, PAPEL)) for px in samples)
    return close >= 0.9 * len(samples)
