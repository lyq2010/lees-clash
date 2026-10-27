"""Reject packages with the wrong identity, signing certificate or native ABI set."""
import os
from pathlib import Path
import re
import json
import struct
import subprocess
import sys
import zipfile

ARCHITECTURES = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86": (1, 3), "x86_64": (2, 62)}


def check_native(data, abi):
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("Invalid native ELF header")
    if (data[4], struct.unpack_from("<H", data, 18)[0]) != ARCHITECTURES.get(abi):
        raise ValueError("Native CPU differs from its APK ABI directory")
    wide = data[4] == 2
    offset = struct.unpack_from("<Q" if wide else "<I", data, 32 if wide else 28)[0]
    size, count = struct.unpack_from("<HH", data, 54 if wide else 42)
    if size < (56 if wide else 32) or offset + size * count > len(data):
        raise ValueError("Invalid native program headers")
    alignments = []
    for index in range(count):
        header = offset + index * size
        if struct.unpack_from("<I", data, header)[0] == 1:
            alignments.append(struct.unpack_from("<Q" if wide else "<I", data, header + (48 if wide else 28))[0])
    if not alignments or min(alignments) < (16384 if wide else 4096):
        raise ValueError("Native LOAD alignment is below the supported page size")


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
    if "application-debuggable" in badging or ".debug'" in badging:
        raise ValueError("Debug APK cannot be published as Release")
    metadata = json.loads((path.parent / "output-metadata.json").read_text())
    entries = [item for item in metadata["elements"] if item["outputFile"] == path.name]
    if len(entries) != 1:
        raise ValueError("APK is absent or duplicated in output metadata")
    entry = entries[0]
    if f"versionName='{entry['versionName']}'" not in badging or f"versionCode='{entry['versionCode']}'" not in badging:
        raise ValueError("APK version differs from output metadata")
    expected_abis = {item["value"] for item in entry.get("filters", []) if item["filterType"] == "ABI"} or set(ARCHITECTURES)
    subprocess.run([str(tools / ("zipalign.exe" if os.name == "nt" else "zipalign")), "-c", "-P", "16", "4", str(path)],
                   check=True, stdout=subprocess.DEVNULL)
    with zipfile.ZipFile(path) as archive:
        libraries = [item for item in archive.infolist() if re.fullmatch(r"lib/[^/]+/[^/]+\.so", item.filename)]
        if {item.filename.split('/')[1] for item in libraries} != expected_abis:
            raise ValueError("Native ABI set differs from output metadata")
        for item in libraries:
            check_native(archive.read(item), item.filename.split('/')[1])
            if item.compress_type != zipfile.ZIP_DEFLATED:
                raise ValueError("Release native libraries must retain compressed APK packaging")
        for name in ("libclash.so", "libbridge.so"):
            if {item.filename.split('/')[1] for item in libraries if item.filename.endswith('/' + name)} != expected_abis:
                raise ValueError("Missing native core or bridge")
    print(f"Verified {path.name}: signer / version / CPU / compressed native libraries / 16KB alignment")


if __name__ == "__main__":
    for item in sys.argv[1:]:
        audit(Path(item))
