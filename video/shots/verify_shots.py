"""Comprueba public/shots/{es,en}: 16 pantallas por idioma, 1080x2340 RGB, fila superior y pares distintos.

Uso: python3 verify_shots.py
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageChops

import crop
from run_shots import PAPEL_SCREENS, PUBLIC, SCREENS


def problems(public: Path = PUBLIC) -> list[str]:
    errors: list[str] = []
    for screen in SCREENS:
        paths = {lang: public / lang / f"{screen}.png" for lang in ("es", "en")}
        for lang, path in paths.items():
            if not path.exists():
                errors.append(f"falta {lang}/{screen}.png")
                continue
            try:
                crop.check_final(path)
            except ValueError as exc:
                errors.append(str(exc))
                continue
            if screen in PAPEL_SCREENS and not crop.top_row_is_papel(path):
                errors.append(f"{lang}/{screen}.png: la fila superior no es Papel (barra o diálogo encima)")
        if all(path.exists() for path in paths.values()):
            with Image.open(paths["es"]) as es_img, Image.open(paths["en"]) as en_img:
                if ImageChops.difference(es_img.convert("RGB"), en_img.convert("RGB")).getbbox() is None:
                    errors.append(f"{screen}: es y en son idénticas (el idioma no cambió)")
    return errors


def main() -> int:
    errors = problems()
    for error in errors:
        print(error)
    if errors:
        print(f"verify_shots: {len(errors)} problemas")
        return 1
    print(f"verify_shots: OK, {2 * len(SCREENS)} capturas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
