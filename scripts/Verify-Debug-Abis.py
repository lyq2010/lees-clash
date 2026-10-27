"""Audit all five Debug APKs. Source mac-env.sh and build without an injected ABI first."""
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

project = Path(__file__).resolve().parents[1]
packages = project / "app/build/outputs/apk/alpha/debug"
cache = Path(os.environ["LEES_CLASH_CACHE"]) / "debug-abi-audit"
cache.mkdir(parents=True, exist_ok=True)
tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
readelf = Path(os.environ["ANDROID_NDK_HOME"]) / "toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
architectures = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86": (1, 3), "x86_64": (2, 62)}
metadata = json.loads((packages / "output-metadata.json").read_text())
assert len(metadata["elements"]) == 5, "Build all four ABIs and universal Debug first"
reports = []
for entry in metadata["elements"]:
    apk = packages / entry["outputFile"]
    expected = set(architectures) if entry["type"] == "UNIVERSAL" else {entry["filters"][0]["value"]}
    subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk)], check=True, stdout=subprocess.DEVNULL)
    signer = subprocess.check_output([str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    assert "package: name='io.github.leenban.clash'" in badging
    assert re.search(r"^(?:minSdkVersion|sdkVersion):'26'$", badging, re.M)
    assert entry["versionName"].endswith(".debug") and f"versionName='{entry['versionName']}'" in badging
    libraries = []
    with zipfile.ZipFile(apk) as archive:
        for item in archive.infolist():
            if not item.filename.startswith("lib/") or not item.filename.endswith(".so"):
                continue
            abi = item.filename.split("/")[1]
            data = archive.read(item)
            assert data[:4] == b"\x7fELF" and data[5] == 1, item.filename
            assert (data[4], struct.unpack_from("<H", data, 18)[0]) == architectures[abi], item.filename
            target = cache / item.filename
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            headers = subprocess.check_output([str(readelf), "-lW", str(target)], text=True)
            alignments = [int(line.split()[-1], 16) for line in headers.splitlines() if line.strip().startswith("LOAD ")]
            minimum = 16384 if abi in ("arm64-v8a", "x86_64") else 4096
            assert alignments and all(value >= minimum for value in alignments), (item.filename, alignments)
            libraries.append({"name": item.filename, "alignments": alignments, "machine": architectures[abi]})
    abis = {item["name"].split("/")[1] for item in libraries}
    assert abis == expected, apk.name
    for name in ("libclash.so", "libbridge.so"):
        assert {item["name"].split("/")[1] for item in libraries if item["name"].endswith("/" + name)} == expected
    reports.append({
        "name": apk.name, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "abis": sorted(abis), "zipalign_16kb": True, "libraries": libraries,
        "signer_sha256": re.search(r"Signer #1 certificate SHA-256 digest: (.+)", signer).group(1),
    })
    print(apk.name, "/".join(sorted(abis)), "identity / signature / ELF / zip alignment PASS", flush=True)
assert len({item["signer_sha256"] for item in reports}) == 1
release_certificate = (project / "tools/release/signing-certificate.sha256").read_text().strip()
assert reports[0]["signer_sha256"] != release_certificate, "Debug must not stand in for independent Release signing"
(cache / "report.json").write_text(json.dumps(reports, indent=2) + "\n")
print("Five Debug APKs verified; this does not verify Release signing or R8", flush=True)
