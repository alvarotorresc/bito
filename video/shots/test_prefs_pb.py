import unittest

import prefs_pb


class PrefsPbTest(unittest.TestCase):
    def test_single_string_entry_bytes(self):
        data = prefs_pb.encode({"a": ("string", "b")})
        self.assertEqual(data, bytes([0x0A, 0x08, 0x0A, 0x01, 0x61, 0x12, 0x03, 0x2A, 0x01, 0x62]))

    def test_round_trip_every_kind(self):
        prefs = {
            "user_name": ("string", "Lucía"),
            "onboarding_done": ("bool", True),
            "habi_sounds_enabled": ("bool", False),
            "perfect_day_celebrated_day": ("int", -1),
            "review_time_minutes": ("int", 1290),
            "badges_seen_until_millis": ("long", 4102444800000),
            "ratio": ("float", 0.5),
            "precise": ("double", 0.1),
            "tags": ("string_set", ["a", "b"]),
            "blob": ("bytes", b"\x00\x01"),
            "empty": ("string", ""),
        }
        self.assertEqual(prefs_pb.decode(prefs_pb.encode(prefs)), prefs)

    def test_bytes_round_trip_keeps_order(self):
        data = prefs_pb.encode({"z": ("int", 3), "a": ("bool", True)})
        self.assertEqual(prefs_pb.encode(prefs_pb.decode(data)), data)
        self.assertEqual(list(prefs_pb.decode(data)), ["z", "a"])


if __name__ == "__main__":
    unittest.main()
