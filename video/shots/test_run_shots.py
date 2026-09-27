import json
import tempfile
import unittest
import xml.etree.ElementTree as ET
from io import BytesIO
from pathlib import Path
from unittest import mock

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
        self.assertEqual(len(run_shots.SCREENS), 16)
        self.assertIn("respiracion", run_shots.SCREENS)

    def test_video_only_screens_scroll_past_the_fold(self):
        # hoy-tareas y stats-logros son solo para el video (Tarea 8b): no las usan to_web.py,
        # to_landing.py ni la tienda, asi que no llevan deep link propio.
        self.assertIn("hoy-tareas", run_shots.SCREENS)
        self.assertIn("stats-logros", run_shots.SCREENS)
        self.assertNotIn("hoy-tareas", run_shots.DEEP_LINKS)
        self.assertNotIn("stats-logros", run_shots.DEEP_LINKS)
        self.assertNotIn("hoy-tareas", run_shots.BEST_EFFORT)
        self.assertNotIn("stats-logros", run_shots.BEST_EFFORT)

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

    def test_notificacion_snoozes_system_notifications_before_arming_and_before_the_shot(self):
        # Notificaciones del sistema (pkg=android: consola serie, teclado) pueden estar en la
        # persiana en cualquier momento antes de la foto. before() las pospone al principio, para
        # partir de una persiana limpia antes de armar el aviso de Bito, y otra vez justo antes
        # de disparar, por si sale alguna entre medias.
        calls: list[str] = []

        def fake_shell(cmd, check=True):
            if "expand-notifications" in cmd:
                calls.append("expand")
            return ""

        with (
            mock.patch.object(run_shots.d, "shell", side_effect=fake_shell),
            mock.patch.object(run_shots, "snooze_system_notifications", side_effect=lambda: calls.append("snooze")),
            mock.patch.object(run_shots, "notification_posted", return_value=True),
            mock.patch.object(run_shots, "emulator"),
            mock.patch.object(run_shots.subprocess, "run"),
            mock.patch.object(run_shots.time, "sleep"),
        ):
            self.assertTrue(run_shots.before("notificacion", "en"))

        self.assertEqual(calls, ["snooze", "snooze", "expand", "snooze"])


class ShadeCleanTest(unittest.TestCase):
    # Extracto real de `dumpsys notification --noredact` tras arrancar en frio el AVD
    # bito-shots con -wipe-data: "Serial console enabled" (id=55) y "AT Translated Set 2
    # keyboard configured" (id=19), las dos notificaciones de la regresion. Las dos son
    # pkg=android, no de Bito.
    DIRTY_DUMP = (
        "Current Notification Manager state:\n"
        "  Notification List:\n"
        "    NotificationRecord(0x02ebc189: pkg=android user=UserHandle{-1} id=55 tag=null"
        " importance=2 key=-1|android|55|null|1000: Notification(channel=DEVELOPER"
        " shortcut=null contentView=null vibrate=null sound=null tick defaults=0"
        " flags=ONGOING_EVENT|CAN_COLORIZE color=0x00000000 vis=PUBLIC))\n"
        "    NotificationRecord(0x0c4f558e: pkg=android user=UserHandle{-1} id=19 tag=null"
        " importance=2 key=-1|android|19|null|1000: Notification(channel=PHYSICAL_KEYBOARD"
        " shortcut=null contentView=null vibrate=null sound=null defaults=0"
        " flags=AUTO_CANCEL|CAN_COLORIZE color=0x00000000 vis=PRIVATE))\n"
    )
    CLEAN_DUMP = (
        "Current Notification Manager state:\n"
        "  Notification List:\n"
        "    NotificationRecord(0x1a2b3c4d: pkg=com.alvarotc.bito user=UserHandle{0} id=1"
        " tag=null importance=3 key=0|com.alvarotc.bito|1|null|10234:"
        " Notification(channel=reminders shortcut=null contentView=null vibrate=null"
        " sound=null defaults=0 flags=AUTO_CANCEL color=0xffab4433 vis=PRIVATE))\n"
    )
    EMPTY_DUMP = "Current Notification Manager state:\n  Notification List:\n"

    def test_notification_records_parses_the_real_system_notifications(self):
        self.assertEqual(
            run_shots.notification_records(self.DIRTY_DUMP),
            [("android", "-1|android|55|null|1000"), ("android", "-1|android|19|null|1000")],
        )

    def test_shade_is_clean_is_false_with_system_notifications_present(self):
        with mock.patch.object(run_shots.d, "shell", return_value=self.DIRTY_DUMP):
            self.assertFalse(run_shots.shade_is_clean())

    def test_shade_is_clean_is_true_with_only_bitos_own_notification(self):
        with mock.patch.object(run_shots.d, "shell", return_value=self.CLEAN_DUMP):
            self.assertTrue(run_shots.shade_is_clean())

    def test_shade_is_clean_is_true_with_no_notifications(self):
        with mock.patch.object(run_shots.d, "shell", return_value=self.EMPTY_DUMP):
            self.assertTrue(run_shots.shade_is_clean())


class CaptureNotificacionShadeTest(unittest.TestCase):
    def test_capture_retries_notificacion_when_the_shade_is_not_clean(self):
        # No basta con posponer y confiar: si la persiana sigue sucia en la foto, capture()
        # tiene que descartar el intento y repetir antes de aceptarla.
        top, bottom = crop.load_geometry(HERE / "avd.env")
        buf = BytesIO()
        Image.new("RGB", (crop.WIDTH, crop.HEIGHT + top + bottom), (0xF2, 0xEC, 0xE1)).save(buf, "PNG")
        raw_png = buf.getvalue()

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            with (
                mock.patch.object(run_shots, "PUBLIC", tmp_path / "public"),
                mock.patch.object(run_shots, "RAW", tmp_path / "raw"),
                mock.patch.object(run_shots, "before", return_value=True),
                mock.patch.object(run_shots, "run_maestro", return_value=True),
                mock.patch.object(run_shots, "after"),
                mock.patch.object(run_shots.d, "screencap", return_value=raw_png),
                mock.patch.object(run_shots, "shade_is_clean") as shade_mock,
            ):
                shade_mock.side_effect = [False, True]
                status = run_shots.capture("notificacion", "en", {}, top, bottom)
                self.assertEqual(shade_mock.call_count, 2)

        self.assertEqual(status, "ok")


if __name__ == "__main__":
    unittest.main()
