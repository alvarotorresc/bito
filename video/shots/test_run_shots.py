import json
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

import crop
import run_shots

HERE = Path(__file__).resolve().parent
RES = HERE.parent.parent / "app" / "src" / "main" / "res"


def app_strings(folder: str) -> dict[str, str]:
    root = ET.parse(RES / folder / "strings_breathing.xml").getroot()
    return {e.get("name"): e.text for e in root.findall("string")}


class RunShotsTest(unittest.TestCase):
    def test_fourteen_screens_ending_in_respiracion(self):
        self.assertEqual(len(run_shots.SCREENS), 14)
        self.assertEqual(run_shots.SCREENS[-1], "respiracion")

    def test_respiracion_opens_by_deep_link_on_papel(self):
        self.assertEqual(run_shots.DEEP_LINKS["respiracion"], "breathing")
        self.assertIn("respiracion", run_shots.PAPEL_SCREENS)
        self.assertNotIn("respiracion", run_shots.BEST_EFFORT)

    def test_breath_labels_are_the_app_strings(self):
        labels = json.loads((HERE / "labels.json").read_text(encoding="utf-8"))
        for lang, folder in (("es", "values-es"), ("en", "values")):
            strings = app_strings(folder)
            self.assertEqual(labels[lang]["L_BREATH_CALM"], strings["breathing_mode_calm"])
            self.assertEqual(labels[lang]["L_BREATH_START"], strings["breathing_start"])
            self.assertEqual(labels[lang]["L_BREATH_EXHALE"], strings["breathing_phase_exhale"])

    def test_the_flow_waits_for_the_exhale_and_never_for_animations(self):
        flow = (HERE / "flows" / "respiracion.yaml").read_text(encoding="utf-8")
        self.assertIn("${L_BREATH_EXHALE}", flow)
        self.assertIn("1:5[0-9]", flow)
        self.assertNotIn("waitForAnimationToEnd", flow)

    def test_the_flow_screenshots_itself_right_after_the_remaining_time_assertion(self):
        # Un adb screencap tras salir Maestro llega 8-12 s tarde y se sale de los 6 s del
        # "Exhala"; el flujo toma su propia captura con takeScreenshot justo tras comprobarlo.
        flow = (HERE / "flows" / "respiracion.yaml").read_text(encoding="utf-8")
        self.assertIn("takeScreenshot: ${MAESTRO_SHOT}", flow)
        self.assertGreater(flow.index("- takeScreenshot"), flow.index("1:5[0-9]"))

    def test_run_maestro_passes_the_screenshot_path_only_for_respiracion(self):
        self.assertIn("respiracion-maestro.png", str(run_shots.MAESTRO_SHOT_PATH))
        for screen in run_shots.SCREENS:
            if screen == "respiracion":
                continue
            self.assertNotIn(screen, str(run_shots.MAESTRO_SHOT_PATH))

    def test_screencap_for_respiracion_reads_the_maestro_screenshot_not_adb(self):
        top, _bottom = crop.load_geometry(HERE / "avd.env")
        original = run_shots.MAESTRO_SHOT_PATH
        try:
            with tempfile.TemporaryDirectory() as tmp:
                fake = Path(tmp) / "respiracion-maestro.png"
                # Mismo tamano que produce tanto adb screencap como maestro takeScreenshot en el
                # AVD bito-shots: WIDTH x (HEIGHT + top + bottom), sin recortar todavia.
                Image.new("RGB", (crop.WIDTH, crop.HEIGHT + top + _bottom), "white").save(fake)
                run_shots.MAESTRO_SHOT_PATH = fake

                png = run_shots.screencap_for("respiracion", top)

                raw = Path(tmp) / "raw.png"
                out = Path(tmp) / "out.png"
                raw.write_bytes(png)
                with Image.open(raw) as img:
                    self.assertEqual(img.size, (crop.WIDTH, crop.HEIGHT + top + _bottom))
                # El recorte de siempre tiene que seguir valiendo con esta fuente.
                crop.crop_raw(raw, out, top, _bottom)
                with Image.open(out) as img:
                    self.assertEqual(img.size, (crop.WIDTH, crop.HEIGHT))
        finally:
            run_shots.MAESTRO_SHOT_PATH = original

    def test_screencap_for_respiracion_returns_none_if_maestro_never_wrote_it(self):
        original = run_shots.MAESTRO_SHOT_PATH
        try:
            run_shots.MAESTRO_SHOT_PATH = Path(tempfile.gettempdir()) / "no-existe-respiracion.png"
            self.assertIsNone(run_shots.screencap_for("respiracion", 66))
        finally:
            run_shots.MAESTRO_SHOT_PATH = original


if __name__ == "__main__":
    unittest.main()
