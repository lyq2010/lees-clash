"""Prepare verified private-release assets and the Music-compatible update feed."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def version_of(tag):
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag) or int(tag.rsplit(".", 1)[1]) >= 50:
        raise ValueError("Expected vMAJOR.MINOR.PATCH; patch carries at 50")
    return tag[1:]


def stage(root, tag, output=None):
    version = version_of(tag)
    changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    notes = re.search(r"^## " + re.escape(version) + r" \([^\n]+\)\s*\n(.*?)(?=^## |\Z)", changelog, re.M | re.S)
    if notes is None or not notes.group(1).strip():
        raise ValueError("Release version is missing from CHANGELOG.md")
    folder = root / "app/build/outputs/apk/alpha/release"
    metadata = json.loads((folder / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata["applicationId"] != "io.github.leenban.clash":
        raise ValueError("Refusing to publish a verification package")
    destination = output or root / "artifacts/release"
    if destination.exists() and any(destination.iterdir()):
        raise ValueError("Release staging directory must be empty")
    if len(metadata["elements"]) != 1 or metadata["elements"][0].get("filters"):
        raise ValueError("Exactly one universal APK is required; ABI splits are not published")
    item = metadata["elements"][0]
    if item["versionName"] != version or item["versionCode"] <= 0:
        raise ValueError("Tag does not match release version")
    source = folder / item["outputFile"]
    if source.parent != folder or not source.is_file():
        raise ValueError("Invalid APK source path")
    destination.mkdir(parents=True, exist_ok=True)
    universal = destination / f"lees-clash-{version}.apk"
    shutil.copyfile(source, universal)
    manifest = dict(version=version, versionCode=item["versionCode"], packageName=metadata["applicationId"],
                    apk=universal.name, sha256=digest(universal), size=universal.stat().st_size, minSdk=26,
                    notes=notes.group(1).strip())
    if len(manifest["notes"]) > 10000 or manifest["size"] > 512 * 1024 * 1024:
        raise ValueError("Release feed exceeds app limits")
    (destination / "latest-lees-clash.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (destination / f"lees-clash-{version}-SHA256SUMS.txt").write_text(f"{digest(universal)}  {universal.name}\n", encoding="utf-8")
    return manifest


if __name__ == "__main__":
    stage(Path(__file__).resolve().parents[2], sys.argv[1])
