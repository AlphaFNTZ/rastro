"""Install and test chat/custody on the three attached emulators, preserving app data.
Temporarily disable animations and system notification banners for Espresso, then restore settings.
Map/navigation tests need a compatible GPU and are intentionally not included here.
"""
import os
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get("ADB", r"C:\SDK\platform-tools\adb.exe")
DEVICES = ("emulator-5554", "emulator-5556", "emulator-5558")
CLASSES = ",".join("com.example.rastro." + name for name in (
    "ChatPersistenceTest", "CustodyPersistenceTest", "ForwardDeviceTest",
    "ChatActivityTest", "CustodyActivityTest"))
SETTINGS = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale", "heads_up_notifications_enabled")

def adb(serial, *args, timeout=240):
    return subprocess.check_output([ADB, "-s", serial, *args], text=True,
                                   encoding="utf-8", errors="replace", timeout=timeout)

def run(serial):
    report = ROOT / "app/build/reports" / ("chat-" + serial + ".txt")
    report.parent.mkdir(parents=True, exist_ok=True)
    original = {}
    try:
        for apk in ("app/build/outputs/apk/debug/app-debug.apk",
                    "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
            result = adb(serial, "install", "-r", str(ROOT / apk))
            if "Success" not in result:
                raise RuntimeError(result)
        for setting in SETTINGS:
            original[setting] = adb(serial, "shell", "settings", "get", "global", setting).strip()
            adb(serial, "shell", "settings", "put", "global", setting, "0")
        adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb(serial, "shell", "wm", "dismiss-keyguard")
        result = adb(serial, "shell", "am", "instrument", "-w", "-e", "class", CLASSES,
                     "com.example.rastro.test/androidx.test.runner.AndroidJUnitRunner")
        report.write_text(result, encoding="utf-8")
        success = "OK (24 tests)" in result
        print(serial + (": PASS (24 tests)" if success else ": FAILED; see " + str(report)), flush=True)
        return success
    finally:
        for setting, value in original.items():
            if value == "null":
                adb(serial, "shell", "settings", "delete", "global", setting)
            else:
                adb(serial, "shell", "settings", "put", "global", setting, value)

if __name__ == "__main__":
    with ThreadPoolExecutor(max_workers=3) as executor:
        results = list(executor.map(run, DEVICES))
    raise SystemExit(0 if all(results) else 1)
