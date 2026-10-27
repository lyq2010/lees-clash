"""Recognize a complete immutable release before resuming its mirrors."""
import json
import os
import re
import urllib.error
import urllib.request


def is_published(repository, tag, token):
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag) or tuple(map(int, tag[1:].split("."))) < (0, 0, 1):
        raise ValueError("Expected a new closed-release version tag")
    if int(tag.rsplit(".", 1)[1]) >= 50:
        raise ValueError("Patch version must carry at 50")
    public_tag = tag
    request = urllib.request.Request(
        f"https://api.github.com/repos/{repository}/releases/tags/{public_tag}",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            release = json.load(response)
    except urllib.error.HTTPError as error:
        error.close()
        if error.code == 404:
            return False
        raise
    version = tag[1:]
    expected = {"latest-lees-clash.json", f"lees-clash-{version}.apk", f"lees-clash-{version}-SHA256SUMS.txt"}
    expected |= {f"lees-clash-{version}-{abi}.apk" for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")}
    assets = release.get("assets", [])
    if (release.get("draft") or release.get("prerelease") or release.get("tag_name") != public_tag
            or {asset["name"] for asset in assets} != expected
            or any(asset.get("state") != "uploaded" or asset.get("size", 0) <= 0 for asset in assets)):
        raise ValueError("Existing release is incomplete; refusing to overwrite it")
    return True


if __name__ == "__main__":
    published = is_published(os.environ["RELEASE_REPOSITORY"], os.environ["TAG"], os.environ["GH_TOKEN"])
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"published={str(published).lower()}\n")
    print("Release already published; resume mirrors" if published else "Build a new release")
