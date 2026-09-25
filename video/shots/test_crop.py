import tempfile
import unittest
from pathlib import Path

from PIL import Image

import crop

TOP, BOTTOM = 100, 60


def synthetic_raw(path: Path) -> None:
    img = Image.new("RGB", (crop.WIDTH, crop.HEIGHT + TOP + BOTTOM), crop.PAPEL)
    img.paste((200, 0, 0), (0, 0, crop.WIDTH, TOP))
    img.paste((0, 0, 200), (0, TOP + crop.HEIGHT, crop.WIDTH, TOP + crop.HEIGHT + BOTTOM))
    img.save(path)


class CropTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_crop_removes_both_bars(self):
        raw, out = self.dir / "raw.png", self.dir / "out.png"
        synthetic_raw(raw)
        crop.crop_raw(raw, out, TOP, BOTTOM)
        with Image.open(out) as img:
            self.assertEqual(img.size, (1080, 2340))
            self.assertEqual(img.mode, "RGB")
            self.assertEqual(img.getpixel((10, 0)), crop.PAPEL)
            self.assertEqual(img.getpixel((10, 2339)), crop.PAPEL)
        self.assertTrue(crop.top_row_is_papel(out))

    def test_wrong_raw_size_is_rejected(self):
        raw = self.dir / "raw.png"
        Image.new("RGB", (1080, 2340), crop.PAPEL).save(raw)
        with self.assertRaises(ValueError):
            crop.crop_raw(raw, self.dir / "out.png", TOP, BOTTOM)

    def test_dark_top_row_is_not_papel(self):
        path = self.dir / "dark.png"
        img = Image.new("RGB", (1080, 2340), crop.PAPEL)
        img.paste((20, 20, 20), (0, 0, 1080, 1))
        img.save(path)
        self.assertFalse(crop.top_row_is_papel(path))

    def test_check_final_rejects_alpha(self):
        path = self.dir / "alpha.png"
        Image.new("RGBA", (1080, 2340), (0, 0, 0, 0)).save(path)
        with self.assertRaises(ValueError):
            crop.check_final(path)

    def test_load_geometry(self):
        env = self.dir / "avd.env"
        env.write_text("TOP=100\nBOTTOM=60\nHEIGHT=2500\n")
        self.assertEqual(crop.load_geometry(env), (100, 60))
        env.write_text("TOP=100\nBOTTOM=60\nHEIGHT=2340\n")
        with self.assertRaises(ValueError):
            crop.load_geometry(env)


if __name__ == "__main__":
    unittest.main()
