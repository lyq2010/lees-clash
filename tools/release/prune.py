"""Delete only older Lee's Clash versions after the new mirror has been verified."""
import json
import re
import urllib.parse
import urllib.request

ASSET = re.compile(r"lees-clash-(\d+)\.(\d+)\.(\d+)(?:-(?:arm64-v8a|armeabi-v7a|x86_64|x86))?(?:\.apk|-SHA256SUMS\.txt)")


def stale_keys(keys, version):
    current = tuple(map(int, version.split(".")))
    if len(current) != 3:
        raise ValueError("Invalid current version")
    return sorted({key for key in keys if (match := ASSET.fullmatch(key))
                   and tuple(map(int, match.groups())) < current})


def prune_cos(client, bucket, version):
    keys, marker, seen = [], "", set()
    while True:
        params = dict(Bucket=bucket, Prefix="lees-clash-", MaxKeys=1000)
        if marker:
            params["Marker"] = marker
        page = client.list_objects(**params)
        truncated = page.get("IsTruncated")
        if type(truncated) is not bool and truncated not in ("true", "false"):
            raise ValueError("Missing COS pagination status; nothing deleted")
        keys.extend(item["Key"] for item in page.get("Contents", []) or [])
        if truncated in (False, "false"):
            break
        marker = page.get("NextMarker")
        if not marker or marker in seen:
            raise ValueError("Invalid COS pagination; nothing deleted")
        seen.add(marker)
    for key in stale_keys(keys, version):
        client.delete_object(Bucket=bucket, Key=key)
        print(f"Deleted old Lee's Clash mirror: {key}")


def cloudflare_request(token, method, path, params=None):
    url = "https://api.cloudflare.com/client/v4" + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, method=method, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.load(response)
    if not data.get("success"):
        raise RuntimeError("Cloudflare API request failed")
    return data


def prune_r2(request, bucket_path, version):
    keys, cursor, seen = [], "", set()
    while True:
        params = {"prefix": "lees-clash-", "per_page": "1000"}
        if cursor:
            params["cursor"] = cursor
        page = request("GET", bucket_path, params)
        keys.extend(item["key"] for item in page.get("result", []) or [])
        info = page.get("result_info") or {}
        if type(info.get("is_truncated")) is not bool:
            shape = {key: type(value).__name__ for key, value in page.items()}
            raise ValueError(f"Missing R2 pagination status; nothing deleted; response fields={shape}, result_info={info}")
        if not info["is_truncated"]:
            break
        cursor = info.get("cursor")
        if not cursor or cursor in seen:
            raise ValueError("Invalid R2 pagination; nothing deleted")
        seen.add(cursor)
    for key in stale_keys(keys, version):
        request("DELETE", bucket_path + "/" + urllib.parse.quote(key, safe=""))
        print(f"Deleted old Lee's Clash mirror: {key}")
