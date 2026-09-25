import datetime as dt
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

import seed

SCHEMA = Path(__file__).resolve().parents[2] / "app/schemas/com.alvarotc.bito.data.db.BitoDatabase/2.json"
TODAY = 20721  # 2026-09-25, viernes


def empty_db(path: Path) -> None:
    entities = json.loads(SCHEMA.read_text(encoding="utf-8"))["database"]["entities"]
    conn = sqlite3.connect(path)
    for entity in entities:
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    conn.commit()
    conn.close()


class SeedTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = Path(self.tmp.name) / "bito.db"
        empty_db(self.db)

    def tearDown(self):
        self.tmp.cleanup()

    def query(self, sql, *args):
        conn = sqlite3.connect(self.db)
        try:
            return conn.execute(sql, args).fetchall()
        finally:
            conn.close()

    def test_habits_are_localized(self):
        seed.seed(str(self.db), "es", TODAY)
        names = {row[0] for row in self.query("select name from habits")}
        self.assertEqual(len(names), 6)
        self.assertIn("Beber agua", names)
        seed.seed(str(self.db), "en", TODAY)
        names = {row[0] for row in self.query("select name from habits")}
        self.assertIn("Drink water", names)
        self.assertNotIn("Beber agua", names)

    def test_water_has_a_130_day_run_and_is_pending_today(self):
        seed.seed(str(self.db), "es", TODAY)
        habit_id = self.query("select id from habits where name = 'Beber agua'")[0][0]
        values = dict(self.query("select logicalDay, value from entries where habitId = ?", habit_id))
        for day in range(TODAY - 130, TODAY):
            self.assertGreaterEqual(values.get(day, 0), 8, day)
        self.assertEqual(values[TODAY], 5)
        self.assertNotIn(TODAY - 131, values)

    def test_today_is_partial_and_unsealed(self):
        seed.seed(str(self.db), "es", TODAY)
        self.assertEqual(self.query("select count(*) from day_seals where logicalDay = ?", TODAY)[0][0], 0)
        self.assertEqual(self.query("select count(*) from day_seals")[0][0], 130)
        touched = self.query("select count(distinct habitId) from entries where logicalDay = ?", TODAY)[0][0]
        self.assertLess(touched, 6)

    def test_perfect_week_has_every_daily_habit(self):
        seed.seed(str(self.db), "es", TODAY)
        monday = seed.perfect_monday(TODAY - 130)
        self.assertEqual(seed.weekday(monday), 0)
        daily = [row[0] for row in self.query("select id from habits where period = 'DAY' and direction = 'AT_LEAST'")]
        self.assertEqual(len(daily), 4)
        for day in range(monday, monday + 7):
            for habit_id in daily:
                self.assertTrue(self.query("select 1 from entries where habitId = ? and logicalDay = ?", habit_id, day))

    def test_four_open_tasks_with_different_deadlines(self):
        seed.seed(str(self.db), "es", TODAY)
        rows = self.query("select dueKind, dueDay, status from tasks")
        self.assertEqual(len(rows), 4)
        self.assertEqual({row[2] for row in rows}, {"OPEN"})
        self.assertEqual(sorted(row[0] for row in rows), ["DATE", "DATE", "NONE", "WEEK"])
        self.assertEqual(len({(row[0], row[1]) for row in rows}), 4)
        self.assertIn(("WEEK", TODAY + 2), [(row[0], row[1]) for row in rows])

    def test_badges_and_equipped_crown(self):
        seed.seed(str(self.db), "es", TODAY)
        badges = {row[0] for row in self.query("select badgeId from badges")}
        self.assertTrue({"streak-30", "perfect-week", "streak-100"} <= badges)
        self.assertEqual(self.query("select equipped from customization_items where itemId = 'upper-corona'"), [(1,)])

    def test_seed_is_idempotent(self):
        first = seed.seed(str(self.db), "es", TODAY)
        second = seed.seed(str(self.db), "es", TODAY)
        self.assertEqual(first, second)
        self.assertEqual(self.query("select count(*) from entries")[0][0], first["entries"])

    def test_logical_day_uses_madrid_time(self):
        instant = int(dt.datetime(2026, 9, 25, 22, 30, tzinfo=dt.timezone.utc).timestamp())
        self.assertEqual(seed.logical_day(instant), TODAY + 1)
        self.assertEqual(seed.local_minutes(instant), 30)


if __name__ == "__main__":
    unittest.main()
