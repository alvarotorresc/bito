import os
import tempfile
import unittest
from pathlib import Path

from PIL import Image

import to_landing


class ToLandingTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.public = Path(self.tmp.name) / "public"
        self.out = Path(self.tmp.name) / "capturas"
        for lang in ("es", "en"):
            (self.public / lang).mkdir(parents=True)
            for screen in to_landing.SCREENS:
                Image.new("RGB", (1080, 2340), (242, 236, 225)).save(self.public / lang / f"{screen}.png")

    def tearDown(self):
        self.tmp.cleanup()

    def noisy(self, size=(1080, 2340), tile=(108, 234)):
        small = Image.frombytes("RGB", tile, os.urandom(tile[0] * tile[1] * 3))
        return small.resize(size, Image.Resampling.NEAREST)

    def test_eleven_screens_per_language_at_half_size(self):
        written = to_landing.build_all(self.public, self.out)
        self.assertEqual(len(written), 22)
        for lang in ("es", "en"):
            self.assertEqual(sorted(p.name for p in (self.out / lang).iterdir()), sorted(f"{s}.png" for s in to_landing.SCREENS))
        with Image.open(self.out / "en" / "respiracion.png") as img:
            self.assertEqual(img.size, (540, 1170))

    def test_every_file_is_300_kb_or_less(self):
        for lang in ("es", "en"):
            for screen in to_landing.SCREENS:
                self.noisy().save(self.public / lang / f"{screen}.png")
        for path, size in to_landing.build_all(self.public, self.out):
            self.assertLessEqual(size, 300 * 1024, path)

    def test_the_screens_the_landing_and_readmes_use(self):
        self.assertEqual(to_landing.SCREENS, ("hoy", "detalle", "habi", "stats", "records", "logros", "repaso", "ajustes", "tareas", "foco", "respiracion"))

    def test_missing_source_fails_before_writing(self):
        (self.public / "es" / "foco.png").unlink()
        with self.assertRaises(FileNotFoundError):
            to_landing.build_all(self.public, self.out)
        self.assertFalse(self.out.exists())

    def test_wrong_size_is_rejected(self):
        Image.new("RGB", (1080, 1920)).save(self.public / "en" / "hoy.png")
        with self.assertRaises(ValueError):
            to_landing.build_all(self.public, self.out)


if __name__ == "__main__":
    unittest.main()
