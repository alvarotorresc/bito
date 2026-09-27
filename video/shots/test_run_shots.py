import json
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
