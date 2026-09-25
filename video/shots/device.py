"""Ayudas de adb para la pasada de capturas de Bito."""

from __future__ import annotations

import hashlib
import os
import subprocess
import time
from pathlib import Path
from typing import Callable

SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android" / "Sdk")))
ADB = str(SDK / "platform-tools" / "adb")
SERIAL = "emulator-5554"
PKG = "com.alvarotc.bito"
ACTIVITY = f"{PKG}/.MainActivity"


def adb(*args: str, check: bool = True) -> str:
    result = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, check=check)
    return result.stdout.decode("utf-8", errors="replace")


def shell(cmd: str, check: bool = True) -> str:
    return adb("shell", cmd, check=check)


def run_as_exists(path: str) -> bool:
    result = subprocess.run([ADB, "-s", SERIAL, "shell", f"run-as {PKG} ls {path}"], capture_output=True)
    return result.returncode == 0


def run_as_read(path: str) -> bytes:
    return subprocess.run([ADB, "-s", SERIAL, "exec-out", "run-as", PKG, "cat", path], capture_output=True, check=True).stdout


def run_as_write(path: str, data: bytes) -> None:
    subprocess.run([ADB, "-s", SERIAL, "shell", f"run-as {PKG} sh -c 'cat > {path}'"], input=data, check=True)
    remote = shell(f"run-as {PKG} sha256sum {path}").split()[0]
    if remote != hashlib.sha256(data).hexdigest():
        raise RuntimeError(f"device: {path} no llegó íntegro al dispositivo")


def force_stop() -> None:
    shell(f"am force-stop {PKG}")


def launch() -> None:
    shell(f"am start -W -n {ACTIVITY}")


def deep_link(route: str) -> None:
    shell(f"am start -W -n {ACTIVITY} --es openRoute {route}")


def screencap() -> bytes:
    return subprocess.run([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout


def device_epoch_seconds() -> int:
    return int(shell("date +%s").strip())


def wait_for(predicate: Callable[[], bool], timeout_s: float, message: str) -> None:
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(1)
    raise TimeoutError(message)
