"""Controlled ciphertext handoff across three Android emulators (not a Nearby radio test).
Each step launches fresh instrumentation; test databases/keys are isolated from the user's app data.
Usage: python scripts/test_forward_emulators.py
"""
import json
import os
import subprocess
from pathlib import Path

ADB = os.environ.get("ADB", r"C:\SDK\platform-tools\adb.exe")
DEVICES = {"A": "emulator-5554", "B": "emulator-5556", "D": "emulator-5558", "C": "emulator-5554"}
ROOT = Path(__file__).resolve().parents[1]
events = []

def adb(serial, *args):
    return subprocess.check_output([ADB, "-s", serial, *args], text=True, encoding="utf-8", errors="replace", timeout=90)

def step(role, op, **values):
    args = ["shell", "am", "instrument", "-w", "-e", "class",
            "com.example.rastro.ForwardExchangeTest", "-e", "role", role, "-e", "op", op]
    for key, value in values.items():
        args += ["-e", key, str(value)]
    args += ["com.example.rastro.test/androidx.test.runner.AndroidJUnitRunner"]
    output = adb(DEVICES[role], *args)
    if "OK (1 test)" not in output:
        raise RuntimeError(f"{role}/{op}: {output}")
    result = json.loads(adb(DEVICES[role], "shell", "run-as", "com.example.rastro", "cat", "files/forward-lab-result.json"))
    events.append({"role": role, "device": DEVICES[role], "op": op,
                   **{k:v for k,v in result.items() if k not in ("bundle", "qr")}})
    print(f"{role} {op}: OK", flush=True)
    return result

def main():
    for role in DEVICES:
        step(role, "cleanup")
    try:
        identities = {role: step(role, "identity") for role in DEVICES}
        own = step("A", "send", contact=identities["C"]["qr"])
        ab = step("A", "delegate", lane=own["lane"], contact=identities["B"]["qr"])
        accepted = step("B", "accept", bundle=ab["bundle"])
        assert accepted["contacts"] == 0
        # Deliberately lose B's storage response; A must remain frozen after another process restart.
        pending = step("A", "snapshot", lane=ab["lane"])
        assert pending["state"] == "PENDING"
        assert pending["identity"] == identities["A"]["identity"]
        repeat = step("B", "accept", bundle=ab["bundle"])
        assert repeat["state"] == "ACTIVE"
        assert step("A", "stored", bundle=ab["bundle"])["state"] == "TRANSFERRED"

        bd = step("B", "delegate", lane=ab["lane"], contact=identities["D"]["qr"])
        assert step("D", "accept", bundle=bd["bundle"])["contacts"] == 0
        step("B", "stored", bundle=bd["bundle"])
        receipt = step("C", "deliver", bundle=bd["bundle"], contact=identities["A"]["qr"])
        duplicate = step("C", "deliver", bundle=bd["bundle"], contact=identities["A"]["qr"])
        assert receipt["bundle"] == duplicate["bundle"] and duplicate["history"] == 1

        # Different return route: C -> B -> D -> A; no personal contacts in B or D.
        cb = step("C", "delegate", lane=receipt["lane"], contact=identities["B"]["qr"])
        assert step("B", "accept", bundle=cb["bundle"])["contacts"] == 0
        step("C", "stored", bundle=cb["bundle"])
        bd_ack = step("B", "delegate", lane=cb["lane"], contact=identities["D"]["qr"])
        assert step("D", "accept", bundle=bd_ack["bundle"])["contacts"] == 0
        step("B", "stored", bundle=bd_ack["bundle"])
        final = step("A", "deliver", bundle=bd_ack["bundle"], contact=identities["C"]["qr"])
        assert final["delivered"] and final["history"] == 1
        print("PASS: A -> B -> D -> C; receipt C -> B -> D -> A; restart after every step.", flush=True)
    finally:
        for role in DEVICES:
            step(role, "cleanup")
        report = ROOT / "app" / "build" / "reports" / "forward-emulators.json"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(events, ensure_ascii=False, indent=2), encoding="utf-8")

if __name__ == "__main__":
    main()
