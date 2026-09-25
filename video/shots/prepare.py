"""Deja Bito instalada y sembrada para un idioma.

Uso:
  python3 prepare.py --lang es                     reinstala, siembra base y ajustes
  python3 prepare.py --lang es --arm-reminder 2    solo programa un recordatorio global para dentro de 2 min
"""

from __future__ import annotations

import argparse
import sqlite3
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Callable

import device as d
import prefs_pb
import seed as seeder

APK = Path(__file__).resolve().parents[2] / "app/build/outputs/apk/debug/app-debug.apk"
DB = "databases/bito.db"
SETTINGS = "files/settings.preferences_pb"
FAR_FUTURE_MS = 4102444800000
FIRST_RUN_REMINDERS = "720,1140"
LAUNCH_ATTEMPTS = 10
LAUNCH_DELAY_S = 3.0


def settings_for(current: dict, lang: str, today: int, now_min: int) -> dict:
    prefs = dict(current)
    prefs.update(
        {
            "user_name": ("string", "Lucía"),
            "onboarding_done": ("bool", True),
            "language_tag": ("string", lang),
            "personality": ("string", "NEUTRA"),
            "badges_seen_until_millis": ("long", FAR_FUTURE_MS),
            "perfect_day_celebrated_day": ("int", today),
            "notification_prompt_claimed": ("bool", True),
            "default_reminders_seeded": ("bool", True),
            "global_reminder_minutes": ("string", ""),
            "review_time_minutes": ("int", (now_min + 720) % 1440),
            "task_notices_enabled": ("bool", False),
        }
    )
    return prefs


def reminder_at(now_min: int, minutes: int) -> int:
    return (now_min + minutes) % 1440


def install_clean() -> None:
    if not APK.exists():
        raise SystemExit(f"prepare: falta {APK}; ejecuta build_apk.sh")
    d.adb("uninstall", d.PKG, check=False)
    d.adb("install", "-g", str(APK))
    d.shell(f"appops set {d.PKG} SCHEDULE_EXACT_ALARM allow")


def launch_with_retry(start: Callable[[], str], attempts: int = LAUNCH_ATTEMPTS,
                      delay_s: float = LAUNCH_DELAY_S, sleep: Callable[[float], None] = time.sleep) -> None:
    last = ""
    for attempt in range(1, attempts + 1):
        try:
            last = start()
            if "Error" not in last:
                return
        except subprocess.CalledProcessError as error:
            last = (error.stdout or b"").decode("utf-8", errors="replace")
        if attempt < attempts:
            sleep(delay_s)
    raise SystemExit(f"prepare: la app no arrancó tras {attempts} intentos: {last.strip()}")


def first_launch() -> None:
    launch_with_retry(lambda: d.shell(f"am start -W -n {d.ACTIVITY}"))
    d.wait_for(lambda: d.run_as_exists(DB) and d.run_as_exists(SETTINGS), 60,
               "prepare: la app no creó bito.db ni settings.preferences_pb")
    time.sleep(2)
    d.force_stop()


def pull_db(workdir: Path) -> Path:
    local = workdir / "bito.db"
    for suffix in ("", "-wal", "-shm"):
        if d.run_as_exists(DB + suffix):
            (workdir / f"bito.db{suffix}").write_bytes(d.run_as_read(DB + suffix))
    conn = sqlite3.connect(local)
    try:
        conn.execute("pragma wal_checkpoint(TRUNCATE)")
        if not conn.execute("select count(*) from room_master_table").fetchone()[0]:
            raise SystemExit("prepare: bito.db sin room_master_table")
    finally:
        conn.close()
    return local


def read_settings() -> dict:
    raw = d.run_as_read(SETTINGS)
    current = prefs_pb.decode(raw)
    if prefs_pb.encode(current) != raw:
        raise SystemExit("prepare: prefs_pb no reproduce settings.preferences_pb byte a byte")
    return current


def prepare(lang: str) -> None:
    install_clean()
    first_launch()
    now = d.device_epoch_seconds()
    today = seeder.logical_day(now)
    current = read_settings()
    if current.get("global_reminder_minutes") != ("string", FIRST_RUN_REMINDERS):
        raise SystemExit(f"prepare: ajustes del primer arranque inesperados: {current}")
    with tempfile.TemporaryDirectory() as tmp:
        db = pull_db(Path(tmp))
        summary = seeder.seed(str(db), lang, today)
        d.run_as_write(DB, db.read_bytes())
    d.shell(f"run-as {d.PKG} rm -f {DB}-wal {DB}-shm")
    d.run_as_write(SETTINGS, prefs_pb.encode(settings_for(current, lang, today, seeder.local_minutes(now))))
    print(f"prepare: {lang} hoy={today} {summary}")


def arm_reminder(minutes: int) -> None:
    d.force_stop()
    at = reminder_at(seeder.local_minutes(d.device_epoch_seconds()), minutes)
    prefs = read_settings()
    prefs["global_reminder_minutes"] = ("string", str(at))
    d.run_as_write(SETTINGS, prefs_pb.encode(prefs))
    d.launch()
    time.sleep(3)
    d.shell("input keyevent KEYCODE_HOME")
    print(f"prepare: recordatorio global a las {at // 60:02d}:{at % 60:02d}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", choices=["es", "en"], required=True)
    parser.add_argument("--arm-reminder", type=int, default=None)
    args = parser.parse_args()
    if args.arm_reminder is None:
        prepare(args.lang)
    else:
        arm_reminder(args.arm_reminder)


if __name__ == "__main__":
    main()
