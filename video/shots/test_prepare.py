import subprocess
import unittest

import prepare


class PrepareTest(unittest.TestCase):
    def test_settings_for_sets_demo_keys_and_keeps_unknown_ones(self):
        current = {"default_reminders_seeded": ("bool", True), "global_reminder_minutes": ("string", "720,1140"), "x": ("int", 1)}
        prefs = prepare.settings_for(current, "en", 20721, 600)
        self.assertEqual(prefs["user_name"], ("string", "Lucía"))
        self.assertEqual(prefs["onboarding_done"], ("bool", True))
        self.assertEqual(prefs["language_tag"], ("string", "en"))
        self.assertEqual(prefs["badges_seen_until_millis"], ("long", prepare.FAR_FUTURE_MS))
        self.assertEqual(prefs["perfect_day_celebrated_day"], ("int", 20721))
        self.assertEqual(prefs["global_reminder_minutes"], ("string", ""))
        self.assertEqual(prefs["review_time_minutes"], ("int", 1320))
        self.assertEqual(prefs["task_notices_enabled"], ("bool", False))
        self.assertEqual(prefs["x"], ("int", 1))
        self.assertEqual(current["global_reminder_minutes"], ("string", "720,1140"))

    def test_review_time_wraps_past_midnight(self):
        prefs = prepare.settings_for({}, "es", 1, 1000)
        self.assertEqual(prefs["review_time_minutes"], ("int", 280))

    def test_reminder_at_wraps_past_midnight(self):
        self.assertEqual(prepare.reminder_at(1439, 2), 1)
        self.assertEqual(prepare.reminder_at(600, 2), 602)

    def test_launch_retries_until_activity_manager_is_ready(self):
        outcomes = [
            subprocess.CalledProcessError(1, "am", output=b"Error: activity manager not ready"),
            "Error: Activity not started",
            "Status: ok\nComplete",
        ]
        sleeps = []

        def start():
            outcome = outcomes.pop(0)
            if isinstance(outcome, Exception):
                raise outcome
            return outcome

        prepare.launch_with_retry(start, attempts=10, delay_s=3.0, sleep=sleeps.append)
        self.assertEqual(sleeps, [3.0, 3.0])

    def test_launch_gives_up_after_last_attempt(self):
        sleeps = []

        def start():
            raise subprocess.CalledProcessError(1, "am", output=b"Error: not ready")

        with self.assertRaises(SystemExit) as ctx:
            prepare.launch_with_retry(start, attempts=10, delay_s=3.0, sleep=sleeps.append)
        self.assertEqual(len(sleeps), 9)
        self.assertIn("10 intentos", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
