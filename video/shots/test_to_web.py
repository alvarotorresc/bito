import os
import tempfile
import unittest
from pathlib import Path

from PIL import Image

import to_web


class ToWebTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.public = Path(self.tmp.name) / "public"
        self.web = Path(self.tmp.name) / "web"
        for lang in ("es", "en"):
            (self.public / lang).mkdir(parents=True)
            for screen in set(to_web.WEB_NAMES.values()):
                Image.new("RGB", (1080, 2340), (242, 236, 225)).save(self.public / lang / f"{screen}.png")

    def tearDown(self):
        self.tmp.cleanup()

    def test_copies_eighteen_files_with_brief_names(self):
        written = to_web.copy_all(self.public, self.web)
        names = sorted(path.name for path, _ in written)
        self.assertEqual(len(names), 18)
        self.assertIn("cover-es.png", names)
        self.assertIn("shot-06-revision-en.png", names)
        self.assertIn("feat-tareas-es.png", names)
        self.assertIn("feat-respiracion-en.png", names)
        with Image.open(self.web / "shot-02-recordatorio-es.png") as img:
            self.assertEqual((img.size, img.mode), ((1080, 2340), "RGB"))

    def test_feature_images_come_from_focus_and_breathing(self):
        pairs = dict((dst.name, src.name) for src, dst in to_web.plan_copies(self.public, self.web))
        self.assertEqual(pairs["feat-tareas-es.png"], "foco.png")
        self.assertEqual(pairs["feat-respiracion-en.png"], "respiracion.png")

    def test_cover_is_today(self):
        pairs = dict((dst.name, src.name) for src, dst in to_web.plan_copies(self.public, self.web))
        self.assertEqual(pairs["cover-en.png"], "hoy.png")

    def test_missing_source_fails_before_writing(self):
        (self.public / "en" / "widget.png").unlink()
        with self.assertRaises(FileNotFoundError):
            to_web.copy_all(self.public, self.web)
        self.assertFalse(self.web.exists())

    def test_wrong_size_is_rejected(self):
        Image.new("RGB", (1080, 1920)).save(self.public / "es" / "stats.png")
        with self.assertRaises(ValueError):
            to_web.copy_all(self.public, self.web)

    def noisy(self, size=(300, 300)):
        return Image.frombytes("RGB", size, os.urandom(size[0] * size[1] * 3))

    def test_save_under_keeps_rgb_when_it_fits(self):
        dst = Path(self.tmp.name) / "fits.png"
        size = to_web.save_under(Image.new("RGB", (300, 300), (242, 236, 225)), dst, 400 * 1024)
        self.assertEqual(size, dst.stat().st_size)
        with Image.open(dst) as img:
            self.assertEqual(img.mode, "RGB")

    def test_save_under_falls_back_to_a_palette(self):
        img = self.noisy()
        rgb = Path(self.tmp.name) / "rgb.png"
        img.save(rgb, "PNG", optimize=True)
        limit = rgb.stat().st_size - 1
        dst = Path(self.tmp.name) / "palette.png"
        size = to_web.save_under(img, dst, limit)
        self.assertLessEqual(size, limit)
        with Image.open(dst) as saved:
            self.assertEqual(saved.mode, "P")

    def test_save_under_fails_when_even_the_palette_is_too_big(self):
        with self.assertRaises(ValueError):
            to_web.save_under(self.noisy(), Path(self.tmp.name) / "big.png", 1024)


if __name__ == "__main__":
    unittest.main()
