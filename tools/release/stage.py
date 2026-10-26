"""Prepare verified private-release assets and the Music-compatible update feed."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"}


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def version_of(tag):
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag) or int(tag.rsplit(".", 1)[1]) >= 50:
        raise ValueError("Expected vMAJOR.MINOR.PATCH; patch carries at 50")
    return tag[1:]


def stage(root, tag, output=None):
    version = version_of(tag)
    folder = root / "app/build/outputs/apk/alpha/release"
    metadata = json.loads((folder / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata["applicationId"] != "io.github.leenban.clash":
        raise ValueError("Refusing to publish a verification package")
    destination = output or root / "artifacts/release"
    if destination.exists() and any(destination.iterdir()):
        raise ValueError("Release staging directory must be empty")
    entries = {}
    for item in metadata["elements"]:
        if item["versionName"] != version or item["versionCode"] <= 0:
            raise ValueError("Tag does not match release version")
        filters = item.get("filters", [])
        abi = next((f["value"] for f in filters if f["filterType"] == "ABI"), "universal")
        if abi in entries or abi not in ABIS | {"universal"}:
            raise ValueError("Unexpected or duplicate ABI")
        source = folder / item["outputFile"]
        if source.parent != folder or not source.is_file():
            raise ValueError("Invalid APK source path")
        entries[abi] = (source, item["versionCode"])
    if set(entries) != ABIS | {"universal"} or len({item[1] for item in entries.values()}) != 1:
        raise ValueError("Four ABI packages and a consistent universal APK are required")
    destination.mkdir(parents=True, exist_ok=True)
    files = []
    for abi, (source, _) in sorted(entries.items()):
        name = f"lees-clash-{version}" + ("" if abi == "universal" else f"-{abi}") + ".apk"
        target = destination / name
        shutil.copyfile(source, target)
        files.append(target)
    universal = destination / f"lees-clash-{version}.apk"
    manifest = dict(version=version, versionCode=entries["universal"][1], packageName=metadata["applicationId"],
                    apk=universal.name, sha256=digest(universal), size=universal.stat().st_size, minSdk=26,
                    notes=(root / "docs/RELEASE_NOTES.md").read_text(encoding="utf-8").strip())
    if len(manifest["notes"]) > 10000 or manifest["size"] > 512 * 1024 * 1024:
        raise ValueError("Release feed exceeds app limits")
    (destination / "latest-lees-clash.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (destination / f"lees-clash-{version}-SHA256SUMS.txt").write_text("".join(f"{digest(p)}  {p.name}\n" for p in files), encoding="utf-8")
    return manifest


if __name__ == "__main__":
    stage(Path(__file__).resolve().parents[2], sys.argv[1])
