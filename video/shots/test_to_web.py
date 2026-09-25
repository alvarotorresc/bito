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

    def test_copies_fourteen_files_with_brief_names(self):
        written = to_web.copy_all(self.public, self.web)
        names = sorted(path.name for path, _ in written)
        self.assertEqual(len(names), 14)
        self.assertIn("cover-es.png", names)
        self.assertIn("shot-06-revision-en.png", names)
        with Image.open(self.web / "shot-02-recordatorio-es.png") as img:
            self.assertEqual((img.size, img.mode), ((1080, 2340), "RGB"))

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


if __name__ == "__main__":
    unittest.main()
