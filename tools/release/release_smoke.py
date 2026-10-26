"""Exercise the installed R8 APK through Android UI and actual proxy forwarding."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


def verify(adb, serial, package):
    def run(*arguments):
        return subprocess.check_output([adb, "-s", serial, *arguments], timeout=30)
    component = f"{package}/io.github.leenban.clash."
    run("shell", "appops", "set", package, "ACTIVATE_VPN", "allow")
    run("shell", "pm", "grant", package, "android.permission.POST_NOTIFICATIONS")
    for port in (18080, 18081, 13053):
        run("reverse", f"tcp:{port}", f"tcp:{port}")
    run("shell", "am", "start", "-W", "-n", component + "MainActivity")
    link = "clash://install-config?url=" + urllib.parse.quote("http://127.0.0.1:18080/config", safe="") + "&name=Release-Verification"
    run("shell", "am", "start", "-W", "-n", component + "ExternalControlActivity", "-a", "android.intent.action.VIEW", "-d", "'" + link + "'")
    clicked = False
    for _ in range(30):
        run("shell", "uiautomator", "dump", "/sdcard/lees-clash-release-smoke.xml")
        document = ET.fromstring(run("shell", "cat", "/sdcard/lees-clash-release-smoke.xml"))
        for node in document.iter("node"):
            if node.get("text") == "完成" and node.get("enabled") == "true":
                bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                if len(bounds) == 4:
                    run("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
                    clicked = True
                    break
        if clicked:
            break
        time.sleep(1)
    if not clicked:
        raise ValueError("Release configuration editor did not become ready")
    time.sleep(3)
    run("shell", "am", "start", "-W", "-n", component + "MainActivity")
    run("shell", "am", "start", "-W", "-n", component + "ExternalControlActivity", "-a", package + ".action.START_CLASH")
    run("forward", "tcp:18891", "tcp:17890")
    client = urllib.request.build_opener(urllib.request.ProxyHandler({"http": "http://127.0.0.1:18891"}))
    try:
        success = False
        for _ in range(30):
            try:
                with client.open("http://lees-clash.test:18080/echo", timeout=2) as response:
                    success = response.status == 200 and response.read() == b"lees-clash-local-fixture"
                if success:
                    break
            except OSError:
                pass
            time.sleep(1)
        if not success:
            raise ValueError("Signed Release / JNI / VPN proxy forwarding failed")
        print("Signed R8 APK: configuration import, VPN and real local forwarding passed")
    finally:
        run("shell", "am", "start", "-W", "-n", component + "ExternalControlActivity", "-a", package + ".action.STOP_CLASH")
        run("forward", "--remove", "tcp:18891")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="io.github.leenban.clash")
    args = parser.parse_args()
    verify(args.adb, args.serial, args.package)
