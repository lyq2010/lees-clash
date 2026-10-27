"""Exercise the installed R8 APK through Android UI and actual proxy forwarding."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


def bounds(node):
    values = list(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
    return values if len(values) == 4 else [0, 0, 0, 0]


def tap_control(read_ui, run, text=None, description=None, wait=time.sleep):
    for attempt in range(8):
        document = read_ui()
        nodes = list(document.iter("node"))
        width = max((bounds(node)[2] for node in nodes), default=0)
        height = max((bounds(node)[3] for node in nodes), default=0)
        parents = {child: parent for parent in document.iter() for child in parent}
        navigation_top = height
        for node in nodes:
            if node.get("text") not in ("首页", "工具", "更多") or bounds(node)[1] < height // 2:
                continue
            container = node
            while container in parents and container.get("clickable") != "true":
                container = parents[container]
            top = bounds(container)[1]
            if top >= height // 2:
                navigation_top = min(navigation_top, top)
        clipped = False
        for node in nodes:
            if not ((text is not None and node.get("text") == text)
                    or (description is not None and node.get("content-desc") == description)):
                continue
            if node.get("enabled") == "false":
                continue
            left, top, right, bottom = bounds(node)
            limit = height if text in ("首页", "工具", "更多") else navigation_top
            if 0 <= left < right <= width and 0 <= top < bottom <= limit:
                run("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return
            clipped = True
        if width and height and (clipped or attempt >= 2):
            run("shell", "input", "swipe", str(width // 2), str(int(height * .72)),
                str(width // 2), str(int(height * .38)), "300")
        wait(.4)
    raise ValueError("Release UI entry missing or obscured: " + str(text or description))


def wait_for_labels(read_ui, ready, failure, wait=time.sleep):
    labels = set()
    for _ in range(30):
        labels = {node.get("text") for node in read_ui().iter("node")}
        if ready(labels):
            return
        wait(.5)
    raise ValueError(failure + ": " + str(sorted(filter(None, labels))))


def wait_for_editor_closed(read_ui, wait=time.sleep):
    wait_for_labels(read_ui, lambda labels: "编辑配置" not in labels and "启动" in labels,
                    "Release configuration save did not complete", wait)


def wait_for_profile(read_ui, name, wait=time.sleep):
    wait_for_labels(read_ui, lambda labels: "编辑配置" not in labels and name in labels and "启动" in labels,
                    "Release configuration save and selection did not complete", wait)


def verify(adb, serial, package):
    def run(*arguments):
        return subprocess.check_output([adb, "-s", serial, *arguments], timeout=30)

    def ui():
        for _ in range(4):
            try:
                output = run("shell", "uiautomator", "dump", "--compressed", "/sdcard/lees-clash-release-smoke.xml")
                if b"dumped" in output:
                    document = ET.fromstring(run("shell", "cat", "/sdcard/lees-clash-release-smoke.xml"))
                    if any(True for _ in document.iter("node")):
                        return document
            except (subprocess.CalledProcessError, ET.ParseError):
                pass
            time.sleep(.5)
        raise ValueError("Android did not provide a fresh UI hierarchy")

    def tap(text=None, description=None):
        tap_control(ui, run, text, description)

    def require_text(text):
        for _ in range(5):
            if any(node.get("text") == text for node in ui().iter("node")):
                return
            time.sleep(1)
        raise ValueError("Release UI result missing: " + text)
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
        document = ui()
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
    # Saving commits and selects the profile before the editor closes; a fresh home task then reads it.
    wait_for_editor_closed(ui)
    run("shell", "am", "start", "-W", "-f", "0x10008000", "-n", component + "MainActivity")
    wait_for_profile(ui, "Release-Verification")
    tap(text="启动")
    run("forward", "tcp:18891", "tcp:17890")
    client = urllib.request.build_opener(urllib.request.ProxyHandler({"http": "http://127.0.0.1:18891"}))
    try:
        success = False
        last_failure = "No forwarding response"
        for _ in range(30):
            try:
                with client.open("http://lees-clash.test:18080/echo", timeout=2) as response:
                    success = response.status == 200 and response.read() == b"lees-clash-local-fixture"
                    if not success:
                        last_failure = f"Unexpected fixture response: HTTP {response.status}"
                if success:
                    break
            except OSError as error:
                last_failure = repr(error)
            time.sleep(1)
        if not success:
            raise ValueError("Signed Release / JNI / VPN proxy forwarding failed: " + last_failure)
        # Start a fresh UI task without stopping the running service.
        run("shell", "am", "start", "-W", "-f", "0x10008000", "-n", component + "MainActivity")
        tap(text="网络")
        tap(text="局域网信息与共享")
        require_text("HTTP 代理端口")
        require_text("17890")
        tap(description="返回")
        tap(text="工具")
        tap(text="内存详情")
        require_text("后台进程 PSS")
        require_text("Go 协程数")
        if any(node.get("text") == "正在读取" for node in ui().iter("node")):
            raise ValueError("R8 memory diagnostics did not complete")
        tap(description="返回")
        tap(text="清理 DNS 缓存")
        tap(text="确认")
        require_text("DNS 缓存已清理")
        print("Signed R8 APK: import / VPN / local forwarding / LAN ports / memory / DNS cache passed")
    except Exception:
        for name, arguments in (
            ("UI", ("shell", "cat", "/sdcard/lees-clash-release-smoke.xml")),
            ("services", ("shell", "dumpsys", "activity", "services", package)),
            ("logcat", ("shell", "logcat", "-d", "-t", "300")),
        ):
            try:
                print(f"Release failure {name}:\n" + run(*arguments).decode(errors="replace"))
            except subprocess.SubprocessError as error:
                print(f"Release failure diagnostics unavailable: {error}")
        raise
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
