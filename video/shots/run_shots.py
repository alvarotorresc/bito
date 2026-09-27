"""Pasada de capturas de Bito.

Uso: python3 run_shots.py --lang es,en --screens all
     python3 run_shots.py --lang es --screens hoy,detalle
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from io import BytesIO
from pathlib import Path

from PIL import Image

import crop
import device as d

HERE = Path(__file__).resolve().parent
PUBLIC = HERE.parent / "public" / "shots"
RAW = HERE / "raw"
FALLBACK = HERE / "fallback"
FLOWS = HERE / "flows"
EMULATOR = HERE / "emulator.sh"
MAESTRO = Path.home() / ".maestro" / "bin" / "maestro"
JAVA_HOME = Path.home() / ".jdks" / "jdk-21.0.12+8"
# respiracion anima sin parar: un adb screencap posterior a que Maestro termine de salir (8-12 s)
# se sale de la ventana de 6 s del "Exhala". En su lugar, el propio flujo toma la captura con
# takeScreenshot justo tras comprobar la fase y el restante, mientras Maestro sigue vivo.
MAESTRO_SHOT_PATH = RAW / "respiracion-maestro.png"

SCREENS = [
    "hoy", "detalle", "stats", "records", "logros", "habi", "tienda",
    "repaso", "ajustes", "tareas", "widget", "notificacion", "foco", "respiracion",
    "hoy-tareas", "stats-logros",
]
DEEP_LINKS = {"repaso": "review", "tareas": "tasks", "foco": "tasks", "respiracion": "breathing"}
BEST_EFFORT = {"widget", "notificacion"}
CROP_FROM_TOP = {"notificacion"}
# hoy-tareas y stats-logros son pantallas solo del video (Tarea 8b): Hoy y Estadisticas
# desplazados con scrollUntilVisible hasta que su objetivo de toque quede visible. No entran
# en to_web.py, to_landing.py ni en la tienda; hoy.png y stats.png (sin desplazar) siguen
# siendo las que usan esas superficies.
PAPEL_SCREENS = set(SCREENS) - {"widget", "notificacion", "foco", "tienda", "hoy-tareas", "stats-logros"}
ATTEMPTS = 2
NOTIFICATION_WAIT_S = 240
# Ojos de Habi en foco (coordenadas del PNG recortado): si parpadea no hay pixeles oscuros.
HABI_EYES = (440, 400, 640, 475)
HABI_EYES_MIN_DARK = 300
BLINK_RETRIES = 5
SNOOZE_MS = 3600000
NOTIFICATION_RECORD_RE = re.compile(r"NotificationRecord\(\S+ pkg=(\S+) .*? key=(\S+): Notification\(")
# Boton de comprar de la tienda: en la columna x=300 es la ultima franja verde alta (>=100 px);
# debajo tiene que verse el margen de su tarjeta (>=20 px), si no el boton sale cortado.
BUY_COLUMN_X = 300
BUY_MIN_HEIGHT = 100
BUY_MIN_MARGIN = 20
CARD = (0xFB, 0xF8, 0xF2)


def labels_for(lang: str) -> dict[str, str]:
    return json.loads((HERE / "labels.json").read_text(encoding="utf-8"))[lang]


def emulator(*args: str) -> None:
    subprocess.run(["bash", str(EMULATOR), *args], check=True)


def run_maestro(screen: str, labels: dict[str, str]) -> bool:
    cmd = [str(MAESTRO), "--device", d.SERIAL, "test"]
    for key, value in labels.items():
        cmd += ["-e", f"{key}={value}"]
    if screen == "respiracion":
        MAESTRO_SHOT_PATH.parent.mkdir(parents=True, exist_ok=True)
        MAESTRO_SHOT_PATH.unlink(missing_ok=True)
        cmd += ["-e", f"MAESTRO_SHOT={MAESTRO_SHOT_PATH.with_suffix('')}"]
    cmd.append(str(FLOWS / f"{screen}.yaml"))
    env = {**os.environ, "JAVA_HOME": str(JAVA_HOME), "MAESTRO_CLI_NO_ANALYTICS": "1"}
    return subprocess.run(cmd, env=env).returncode == 0


def notification_posted() -> bool:
    dump = d.shell("dumpsys notification --noredact", check=False)
    return any("NotificationRecord(" in line and f"pkg={d.PKG}" in line for line in dump.splitlines())


def notification_records(dump: str) -> list[tuple[str, str]]:
    """(pkg, key) de cada NotificationRecord activo en un volcado de `dumpsys notification`."""
    return NOTIFICATION_RECORD_RE.findall(dump)


def snooze_system_notifications() -> None:
    dump = d.shell("dumpsys notification --noredact", check=False)
    for pkg, key in notification_records(dump):
        if pkg != d.PKG:
            d.shell(f"cmd notification snooze --for {SNOOZE_MS} '{key}'", check=False)


def shade_is_clean() -> bool:
    """True si en la persiana no hay ninguna notificacion activa que no sea de Bito."""
    dump = d.shell("dumpsys notification --noredact", check=False)
    return all(pkg == d.PKG for pkg, _key in notification_records(dump))


def before(screen: str, lang: str) -> bool:
    d.shell("cmd statusbar collapse", check=False)
    if screen == "notificacion":
        d.shell("settings put global adb_notify 0", check=False)
        d.shell("svc wifi disable", check=False)
        # Notificaciones del sistema como "Serial console enabled" o "AT Translated Set 2
        # keyboard configured" (pkg=android) pueden estar en la persiana cuando se dispara la
        # de Bito. Se posponen aqui, para partir de una persiana limpia, y otra vez justo antes
        # de la foto por si sale alguna entre medias; capture() ademas comprueba la persiana
        # tras la foto y descarta el intento si queda alguna, para no depender solo del aviso.
        snooze_system_notifications()
        subprocess.run([sys.executable, str(HERE / "prepare.py"), "--lang", lang, "--arm-reminder", "2"], check=True)
        deadline = time.monotonic() + NOTIFICATION_WAIT_S
        while not notification_posted():
            if time.monotonic() > deadline:
                return False
            time.sleep(5)
        snooze_system_notifications()
        emulator("demo")
        d.shell("cmd statusbar expand-notifications")
        time.sleep(2)
        snooze_system_notifications()
        time.sleep(1)
        return True
    d.force_stop()
    if screen == "widget":
        emulator("demo")
    if screen in DEEP_LINKS:
        d.deep_link(DEEP_LINKS[screen])
    return True


def habi_eyes_open(png: bytes, top: int) -> bool:
    x0, y0, x1, y1 = HABI_EYES
    with Image.open(BytesIO(png)) as img:
        eyes = img.convert("L").crop((x0, y0 + top, x1, y1 + top))
        return sum(eyes.histogram()[:90]) >= HABI_EYES_MIN_DARK


def screencap_for(screen: str, top: int) -> bytes | None:
    if screen == "respiracion":
        return MAESTRO_SHOT_PATH.read_bytes() if MAESTRO_SHOT_PATH.exists() else None
    png = d.screencap()
    if screen != "foco":
        return png
    for _ in range(BLINK_RETRIES):
        if habi_eyes_open(png, top):
            return png
        time.sleep(0.4)
        png = d.screencap()
    return png if habi_eyes_open(png, top) else None


def is_green(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return g - r > 40 and g - b > 30


def buy_button_whole(path: Path) -> bool:
    with Image.open(path) as img:
        rgb = img.convert("RGB")
        column = [rgb.getpixel((BUY_COLUMN_X, y)) for y in range(rgb.height)]
    runs: list[tuple[int, int]] = []
    start = None
    for y, px in enumerate(column + [(0, 0, 0)]):
        if is_green(px) and start is None:
            start = y
        elif not is_green(px) and start is not None:
            runs.append((start, y))
            start = None
    tall = [run for run in runs if run[1] - run[0] >= BUY_MIN_HEIGHT]
    if not tall:
        return False
    end = tall[-1][1]
    margin = column[end:end + BUY_MIN_MARGIN]
    return len(margin) == BUY_MIN_MARGIN and all(
        all(abs(c - k) <= 6 for c, k in zip(px, CARD)) for px in margin
    )


def after(screen: str) -> None:
    d.shell("cmd statusbar collapse", check=False)
    if screen == "notificacion":
        d.shell("svc wifi enable", check=False)
        d.shell("settings put global adb_notify 1", check=False)


def capture(screen: str, lang: str, labels: dict[str, str], top: int, bottom: int) -> str:
    out = PUBLIC / lang / f"{screen}.png"
    raw = RAW / lang / f"{screen}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    raw.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, ATTEMPTS + 1):
        png = screencap_for(screen, top) if before(screen, lang) and run_maestro(screen, labels) else None
        if png:
            raw.write_bytes(png)
            if screen in CROP_FROM_TOP:
                crop.crop_raw(raw, out, 0, top + bottom)
            else:
                crop.crop_raw(raw, out, top, bottom)
            if screen == "tienda" and not buy_button_whole(out):
                print(f"run_shots: {lang}/{screen} intento {attempt}: el boton de comprar sale cortado", file=sys.stderr)
            elif screen == "notificacion" and not shade_is_clean():
                print(f"run_shots: {lang}/{screen} intento {attempt}: la persiana tiene notificaciones del sistema", file=sys.stderr)
                # Sin esto, si el intento siguiente tambien sale sucio y no hay fallback,
                # capture() devuelve "missing" pero deja en disco el PNG sucio de este
                # intento: verify_shots.py lo daria por bueno sin comprobar la persiana.
                out.unlink(missing_ok=True)
            elif screen not in PAPEL_SCREENS or crop.top_row_is_papel(out):
                after(screen)
                return "ok"
            else:
                print(f"run_shots: {lang}/{screen} intento {attempt}: la fila superior no es Papel", file=sys.stderr)
        else:
            print(f"run_shots: {lang}/{screen} intento {attempt} fallido", file=sys.stderr)
    after(screen)
    fallback = FALLBACK / lang / f"{screen}.png"
    if screen in BEST_EFFORT and fallback.exists():
        crop.check_final(fallback)
        shutil.copyfile(fallback, out)
        return "fallback"
    if screen in BEST_EFFORT:
        return "missing"
    raise SystemExit(f"run_shots: {lang}/{screen} no sale tras {ATTEMPTS} intentos")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", default="es,en")
    parser.add_argument("--screens", default="all")
    args = parser.parse_args()
    langs = args.lang.split(",")
    wanted = SCREENS if args.screens == "all" else args.screens.split(",")
    unknown = [s for s in wanted if s not in SCREENS] + [lang for lang in langs if lang not in ("es", "en")]
    if unknown:
        raise SystemExit(f"run_shots: desconocido: {unknown}")
    screens = [s for s in SCREENS if s in wanted]
    top, bottom = crop.load_geometry(HERE / "avd.env")
    emulator("up")
    results: dict[str, str] = {}
    for lang in langs:
        emulator("locale", lang)
        subprocess.run([sys.executable, str(HERE / "prepare.py"), "--lang", lang], check=True)
        labels = labels_for(lang)
        for screen in screens:
            results[f"{lang}/{screen}"] = capture(screen, lang, labels, top, bottom)
    for key, status in results.items():
        print(f"{status:9} {key}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
