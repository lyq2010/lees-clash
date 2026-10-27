"""Publish verified packages before the feed; COS never downloads object bodies."""
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from prune import prune_cos, prune_r2, cloudflare_request
from stage import ABIS, digest, version_of


def load_assets(folder):
    manifest = json.loads((folder / "latest-lees-clash.json").read_text(encoding="utf-8"))
    version = version_of("v" + manifest["version"])
    names = {f"lees-clash-{version}.apk"} | {f"lees-clash-{version}-{abi}.apk" for abi in ABIS}
    sums = f"lees-clash-{version}-SHA256SUMS.txt"
    if {p.name for p in folder.iterdir() if p.is_file()} != names | {sums, "latest-lees-clash.json"}:
        raise ValueError("Unexpected or incomplete release assets")
    if manifest["packageName"] != "io.github.leenban.clash" or manifest["apk"] != f"lees-clash-{version}.apk":
        raise ValueError("Wrong release identity")
    if manifest["versionCode"] <= 0 or manifest["minSdk"] != 26:
        raise ValueError("Invalid release version code or SDK")
    apk = folder / manifest["apk"]
    if apk.stat().st_size != manifest["size"] or digest(apk) != manifest["sha256"]:
        raise ValueError("Feed APK checksum or size differs")
    checked = set()
    for line in (folder / sums).read_text().splitlines():
        expected, name = line.split("  ", 1)
        if name not in names or name in checked or digest(folder / name) != expected:
            raise ValueError("Invalid APK checksums")
        checked.add(name)
    if checked != names:
        raise ValueError("Incomplete APK checksums")
    return manifest, sorted([folder / name for name in names | {sums}])


def publish(folder, base, upload, cleanup, fetch=None):
    manifest, files = load_assets(folder)
    def remote(name):
        request = urllib.request.Request(f"{base.rstrip('/')}/{name}", headers={"User-Agent": "Mozilla/5.0 LeesClashRelease", "Cache-Control": "no-cache"})
        return urllib.request.urlopen(request, timeout=60)
    fetch = fetch or remote
    try:
        with fetch("latest-lees-clash.json") as response:
            previous = json.load(response)
        if previous["versionCode"] > manifest["versionCode"] or (previous["versionCode"] == manifest["versionCode"] and previous != manifest):
            raise ValueError("Refusing rollback or replacement of an immutable release")
    except urllib.error.HTTPError as error:
        error.close()
        if error.code != 404:
            raise
    for file in files:
        upload(file)
    for file in files:
        import hashlib
        checksum, size = hashlib.sha256(), 0
        with fetch(file.name) as response:
            while chunk := response.read(1024 * 1024):
                size += len(chunk)
                if size > file.stat().st_size:
                    raise ValueError("Remote package too large; feed not published")
                checksum.update(chunk)
        if size != file.stat().st_size or checksum.hexdigest() != digest(file):
            raise ValueError("Remote package failed verification; feed not published")
    upload(folder / "latest-lees-clash.json")
    with fetch("latest-lees-clash.json") as response:
        if json.load(response) != manifest:
            raise ValueError("Published feed differs; old packages retained")
    cleanup(manifest["version"])
    print("Verified release and completed Clash-only cleanup:", manifest["version"])


def publish_cos(folder, client, bucket):
    manifest, files = load_assets(folder)
    feed = folder / "latest-lees-clash.json"
    def head(name):
        return {key.lower(): value for key, value in client.head_object(Bucket=bucket, Key=name).items()}
    try:
        previous = head(feed.name)
    except Exception as error:
        if not hasattr(error, "get_status_code") or error.get_status_code() != 404:
            raise
    else:
        code = int(previous.get("x-cos-meta-version-code", "0"))
        if code <= 0:
            raise ValueError("Existing COS feed lacks version metadata; no object downloaded or replaced")
        if code > manifest["versionCode"] or (code == manifest["versionCode"] and previous.get("x-cos-meta-sha256") != digest(feed)):
            raise ValueError("Refusing COS rollback or replacement of an immutable release")
    for path in [*files, feed]:
        checksum = digest(path)
        with path.open("rb") as source:
            response = client.put_object(Bucket=bucket, Body=source, Key=path.name, EnableMD5=True,
                Metadata={"x-cos-meta-sha256": checksum, "x-cos-meta-version-code": str(manifest["versionCode"])},
                ContentType="application/json" if path.suffix == ".json" else "application/octet-stream",
                CacheControl="no-cache" if path.suffix == ".json" else "public, max-age=31536000, immutable")
        uploaded = {key.lower(): value for key, value in response.items()}
        stored = head(path.name)
        if (not uploaded.get("etag") or stored.get("etag") != uploaded["etag"]
                or int(stored.get("content-length", "-1")) != path.stat().st_size
                or stored.get("x-cos-meta-sha256") != checksum
                or stored.get("x-cos-meta-version-code") != str(manifest["versionCode"])):
            raise ValueError("COS metadata verification failed; old packages retained")
    prune_cos(client, bucket, manifest["version"])
    print("Verified COS Content-MD5 upload and HEAD metadata; no object bodies downloaded:", manifest["version"])


def main(channel, folder):
    load_assets(folder)  # Reject unexpected input before opening cloud credentials.
    if channel == "cos":
        from qcloud_cos import CosConfig, CosS3Client
        bucket = os.environ["TENCENT_COS_BUCKET"]
        client = CosS3Client(CosConfig(Region=os.environ["TENCENT_COS_REGION"], SecretId=os.environ["TENCENT_COS_SECRET_ID"], SecretKey=os.environ["TENCENT_COS_SECRET_KEY"]))
        publish_cos(folder, client, bucket)
    elif channel == "r2":
        token = os.environ["CLOUDFLARE_API_TOKEN"]
        account = os.environ.get("CLOUDFLARE_ACCOUNT_ID")
        if not account:
            accounts = cloudflare_request(token, "GET", "/accounts").get("result", [])
            if len(accounts) != 1:
                raise ValueError("Set CLOUDFLARE_ACCOUNT_ID explicitly; account is ambiguous")
            account = accounts[0]["id"]
        toolchain = json.loads((Path(__file__).parent / "toolchain.json").read_text())
        def upload(path):
            subprocess.run(["npx", "--yes", "wrangler@" + toolchain["wrangler"], "r2", "object", "put", f"packing-list-releases/{path.name}", "--file", str(path), "--remote", "--content-type", "application/json" if path.suffix == ".json" else "application/octet-stream"], check=True)
        endpoint = f"/accounts/{urllib.parse.quote(account, safe='')}/r2/buckets/packing-list-releases/objects"
        publish(folder, "https://plt-releases.leenbsl.workers.dev", upload,
                lambda version: prune_r2(lambda method, path, params=None: cloudflare_request(token, method, path, params), endpoint, version))
    else:
        raise ValueError("Expected cos or r2")


if __name__ == "__main__":
    main(sys.argv[1], Path(sys.argv[2]) if len(sys.argv) > 2 else Path("assets"))
