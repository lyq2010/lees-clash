"""Reject packages with the wrong identity, signing certificate or native ABI set."""
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile


def audit(path):
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    tools = sdk / "build-tools/35.0.0"
    suffix = ".bat" if os.name == "nt" else ""
    output = subprocess.check_output([str(tools / ("apksigner" + suffix)), "verify", "--print-certs", str(path)], text=True)
    expected = (Path(__file__).parent / "signing-certificate.sha256").read_text().strip()
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", output)
    if certificates != [expected]:
        raise ValueError("APK certificate differs from the independent Clash signing certificate")
    badging = subprocess.check_output([str(tools / ("aapt2.exe" if os.name == "nt" else "aapt2")), "dump", "badging", str(path)], text=True)
    if not re.search(r"^package: name='io.github.leenban.clash'", badging, re.M) or not re.search(r"^(?:minSdkVersion|sdkVersion):'26'$", badging, re.M):
        raise ValueError("Wrong package identity or minimum SDK")
    with zipfile.ZipFile(path) as archive:
        core = {name.split('/')[1] for name in archive.namelist() if re.fullmatch(r"lib/[^/]+/libclash\.so", name)}
    if not core or not core <= {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}:
        raise ValueError("Missing or unexpected native core")
    print(f"Verified {path.name}: signer and native core")


if __name__ == "__main__":
    for item in sys.argv[1:]:
        audit(Path(item))
