"""Siembra la base de Room de Bito con los datos de demo de Lucia.

Uso: python3 seed.py RUTA_BITO_DB --lang es|en --today DIA_LOGICO
"""

from __future__ import annotations

import argparse
import datetime as dt
import random
import sqlite3
import uuid
import zoneinfo

TZ = zoneinfo.ZoneInfo("Europe/Madrid")
EPOCH = dt.date(1970, 1, 1)
NS = uuid.UUID("5b1d0c1e-7a8f-4c1b-9d6e-b17000000000")
HISTORY_DAYS = 130

# clave, nombre es, nombre en, metric, period, direction, target, unidad es, unidad en, step
HABITS = [
    ("agua", "Beber agua", "Drink water", "COUNT", "DAY", "AT_LEAST", 8, "vasos", "glasses", 1),
    ("lectura", "Leer", "Read", "DURATION", "DAY", "AT_LEAST", 20, None, None, 5),
    ("correr", "Correr", "Run", "CHECK", "WEEK", "AT_LEAST", 3, None, None, 1),
    ("fumar", "No fumar", "No smoking", "CHECK", "DAY", "ZERO", 0, None, None, 1),
    ("meditar", "Meditar", "Meditate", "DURATION", "DAY", "AT_LEAST", 10, None, None, 5),
    ("estudiar", "Estudiar", "Study", "DURATION", "DAY", "AT_LEAST", 30, None, None, 5),
]

# clave: titulo es, titulo en, primer paso es, primer paso en
TASKS = {
    "dentista": ("Llamar al dentista", "Call the dentist", "Buscar el numero", "Find the number"),
    "presentacion": ("Preparar la presentacion", "Prepare the slides", "Abrir el documento", "Open the file"),
    "dni": ("Renovar el DNI", "Renew my ID card", None, None),
    "armario": ("Ordenar el armario", "Tidy the wardrobe", None, None),
}

TABLES = (
    "task_events", "tasks", "entries", "freezer_uses", "pause_intervals", "target_changes",
    "day_seals", "points_ledger", "badges", "customization_items", "habits",
)


def logical_day(epoch_seconds: int) -> int:
    return (dt.datetime.fromtimestamp(epoch_seconds, TZ).date() - EPOCH).days


def local_minutes(epoch_seconds: int) -> int:
    moment = dt.datetime.fromtimestamp(epoch_seconds, TZ)
    return moment.hour * 60 + moment.minute


def millis(day: int, hour: int = 12, minute: int = 0) -> int:
    date = EPOCH + dt.timedelta(days=day)
    return int(dt.datetime(date.year, date.month, date.day, hour, minute, tzinfo=TZ).timestamp() * 1000)


def weekday(day: int) -> int:
    return (day + 3) % 7


def perfect_monday(start: int) -> int:
    return next(day for day in range(start + 56, start + 63) if weekday(day) == 0)


def _id(kind: str, key: str) -> str:
    return str(uuid.uuid5(NS, f"{kind}:{key}"))


def seed(db_path: str, lang: str, today: int) -> dict[str, int]:
    if lang not in ("es", "en"):
        raise ValueError(f"seed: idioma desconocido {lang}")
    es = lang == "es"
    rng = random.Random(7)
    start = today - HISTORY_DAYS
    monday = perfect_monday(start)
    perfect_week = range(monday, monday + 7)
    relapses = {start + 20, start + 48}
    conn = sqlite3.connect(db_path)
    try:
        with conn:
            for table in TABLES:
                conn.execute(f"delete from {table}")
            ids: dict[str, str] = {}
            for order, (key, name_es, name_en, metric, period, direction, target, unit_es, unit_en, step) in enumerate(HABITS):
                habit_id = _id("habit", key)
                ids[key] = habit_id
                conn.execute(
                    "insert into habits (id, name, metric, period, direction, target, unit, logMode, step,"
                    " timeBucket, timeOfDayMinutes, reminderMinutes, status, createdAtMillis, createdOnDay,"
                    " archivedAtMillis, archivedOnDay, sortOrder)"
                    " values (?, ?, ?, ?, ?, ?, ?, 'COUNTER', ?, null, null, null, 'ACTIVE', ?, ?, null, null, ?)",
                    (habit_id, name_es if es else name_en, metric, period, direction, target,
                     unit_es if es else unit_en, step, millis(start, 9), start, order),
                )
                conn.execute(
                    "insert into target_changes (habitId, effectiveFromDay, target) values (?, ?, ?)",
                    (habit_id, start, target),
                )

            entries: list[tuple[str, str, int, int, int]] = []

            def entry(key: str, day: int, value: int, hour: int) -> None:
                entries.append((_id("entry", f"{key}:{day}"), ids[key], day, value, millis(day, hour)))

            for day in range(start, today):
                perfect = day in perfect_week
                entry("agua", day, rng.choice([8, 8, 9, 10]), 21)
                if perfect or rng.random() < 0.88:
                    entry("lectura", day, rng.choice([20, 25, 30]), 23)
                if weekday(day) in (0, 2) or (weekday(day) == 5 and (perfect or rng.random() < 0.85)):
                    entry("correr", day, 1, 8)
                if day in relapses:
                    entry("fumar", day, 1, 19)
                if perfect or rng.random() < 0.9:
                    entry("meditar", day, rng.choice([10, 10, 15]), 7)
                if perfect or rng.random() < 0.8:
                    entry("estudiar", day, rng.choice([30, 35, 45]), 18)
                conn.execute(
                    "insert into day_seals (logicalDay, sealedAtMillis) values (?, ?)",
                    (day, millis(day, 22, 30)),
                )
            entry("agua", today, 5, 12)
            entry("lectura", today, 20, 8)
            entry("meditar", today, 10, 7)
            conn.executemany(
                "insert into entries (id, habitId, logicalDay, value, createdAtMillis) values (?, ?, ?, ?, ?)",
                entries,
            )

            badges = {
                "first-habit": start,
                "streak-7": start + 6,
                "first-week": start + 6,
                "streak-30": start + 29,
                "perfect-day-1": monday,
                "perfect-week": monday + 6,
                "perfect-days-10": monday + 20,
                "streak-100": start + 99,
            }
            conn.executemany(
                "insert into badges (badgeId, unlockedAtMillis) values (?, ?)",
                [(badge, millis(day, 21, 5)) for badge, day in badges.items()],
            )
            conn.executemany(
                "insert into customization_items (itemId, category, acquiredAtMillis, equipped) values (?, ?, ?, ?)",
                [
                    ("pattern-chispas", "PATTERN", millis(start + 6, 21, 5), 0),
                    ("upper-corona", "UPPER", millis(start + 99, 21, 5), 1),
                ],
            )

            sunday = today + (6 - weekday(today))
            due = {
                "dentista": ("DATE", today),
                "presentacion": ("WEEK", sunday),
                "dni": ("DATE", today + 9),
                "armario": ("NONE", None),
            }
            for age, (key, (title_es, title_en, step_es, step_en)) in enumerate(TASKS.items()):
                kind, due_day = due[key]
                created = today - 1 - age
                conn.execute(
                    "insert into tasks (id, title, firstStep, dueKind, dueDay, status, createdAtMillis,"
                    " createdOnDay, doneAtMillis, doneOnDay) values (?, ?, ?, ?, ?, 'OPEN', ?, ?, null, null)",
                    (_id("task", key), title_es if es else title_en, step_es if es else step_en,
                     kind, due_day, millis(created, 10), created),
                )
        return {"habits": len(HABITS), "entries": len(entries), "tasks": len(TASKS), "badges": len(badges)}
    finally:
        conn.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("db")
    parser.add_argument("--lang", choices=["es", "en"], required=True)
    parser.add_argument("--today", type=int, required=True)
    args = parser.parse_args()
    print(seed(args.db, args.lang, args.today))


if __name__ == "__main__":
    main()
