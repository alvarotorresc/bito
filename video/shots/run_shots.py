"""Pasada de capturas de Bito.

Uso: python3 run_shots.py --lang es,en --screens all
     python3 run_shots.py --lang es --screens hoy,detalle
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

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

SCREENS = [
    "hoy", "detalle", "stats", "records", "logros", "habi", "tienda",
    "repaso", "ajustes", "tareas", "widget", "notificacion", "foco",
]
DEEP_LINKS = {"repaso": "review", "tareas": "tasks", "foco": "tasks"}
BEST_EFFORT = {"widget", "notificacion"}
CROP_FROM_TOP = {"notificacion"}
PAPEL_SCREENS = set(SCREENS) - {"widget", "notificacion", "foco", "tienda"}
ATTEMPTS = 2
NOTIFICATION_WAIT_S = 240


def labels_for(lang: str) -> dict[str, str]:
    return json.loads((HERE / "labels.json").read_text(encoding="utf-8"))[lang]


def emulator(*args: str) -> None:
    subprocess.run(["bash", str(EMULATOR), *args], check=True)


def run_maestro(screen: str, labels: dict[str, str]) -> bool:
    cmd = [str(MAESTRO), "--device", d.SERIAL, "test"]
    for key, value in labels.items():
        cmd += ["-e", f"{key}={value}"]
    cmd.append(str(FLOWS / f"{screen}.yaml"))
    env = {**os.environ, "JAVA_HOME": str(JAVA_HOME), "MAESTRO_CLI_NO_ANALYTICS": "1"}
    return subprocess.run(cmd, env=env).returncode == 0


def notification_posted() -> bool:
    dump = d.shell("dumpsys notification --noredact", check=False)
    return any("NotificationRecord(" in line and f"pkg={d.PKG}" in line for line in dump.splitlines())


def before(screen: str, lang: str) -> bool:
    d.shell("cmd statusbar collapse", check=False)
    if screen == "notificacion":
        d.shell("settings put global adb_notify 0", check=False)
        d.shell("svc wifi disable", check=False)
        subprocess.run([sys.executable, str(HERE / "prepare.py"), "--lang", lang, "--arm-reminder", "2"], check=True)
        deadline = time.monotonic() + NOTIFICATION_WAIT_S
        while not notification_posted():
            if time.monotonic() > deadline:
                return False
            time.sleep(5)
        emulator("demo")
        d.shell("cmd statusbar expand-notifications")
        time.sleep(2)
        return True
    d.force_stop()
    if screen == "widget":
        emulator("demo")
    if screen in DEEP_LINKS:
        d.deep_link(DEEP_LINKS[screen])
    return True


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
        if before(screen, lang) and run_maestro(screen, labels):
            raw.write_bytes(d.screencap())
            if screen in CROP_FROM_TOP:
                crop.crop_raw(raw, out, 0, top + bottom)
            else:
                crop.crop_raw(raw, out, top, bottom)
            if screen not in PAPEL_SCREENS or crop.top_row_is_papel(out):
                after(screen)
                return "ok"
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
