"""Install the locked Android Go SDK on Linux Actions runners; never resolve floating latest."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import urllib.request


if __name__ == "__main__":
    spec = json.loads((Path(__file__).parent / "toolchain.json").read_text())
    temporary = Path(os.environ["RUNNER_TEMP"])
    archive = temporary / "lees-clash-go.tar.gz"
    with urllib.request.urlopen(spec["goLinuxUrl"], timeout=60) as response, archive.open("wb") as output:
        while data := response.read(1024 * 1024):
            output.write(data)
    with archive.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != spec["goLinuxSha256"]:
            raise ValueError("Go SDK digest changed; explicit toolchain upgrade required")
    with tarfile.open(archive) as package:
        package.extractall(temporary / "lees-clash-sdk", filter="data")
    goroot = temporary / "lees-clash-sdk/go"
    if (goroot / "VERSION").read_text().splitlines()[0] != spec["goVersion"]:
        raise ValueError("Unexpected Go version")
    for patch in sorted(Path(".github/patch").glob("*.patch")):
        contents = patch.read_bytes()
        command = ["patch", "--silent", "-d", str(goroot), "-p1"]
        if subprocess.run(command + ["--dry-run", "--forward"], input=contents).returncode == 0:
            subprocess.run(command + ["--forward"], input=contents, check=True)
        elif subprocess.run(command + ["--dry-run", "--reverse"], input=contents).returncode != 0:
            raise ValueError("Required Android Go patch is incompatible")
    with open(os.environ["GITHUB_PATH"], "a") as output:
        sdk = Path(os.environ["ANDROID_HOME"])
        for directory in [goroot / "bin", sdk / "platform-tools", sdk / "emulator", sdk / "cmdline-tools/latest/bin"]:
            output.write(str(directory) + "\n")
    with open(os.environ["GITHUB_ENV"], "a") as output:
        output.write(f"GOROOT={goroot}\n")
    sdk = Path(os.environ["ANDROID_HOME"])
    Path("local.properties").write_text(f"sdk.dir={sdk}\nndk.dir={sdk / 'ndk' / spec['ndk']}\n")
