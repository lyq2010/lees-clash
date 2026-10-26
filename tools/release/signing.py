"""Restore only independent Clash signing material on an ephemeral runner."""
import base64
import os
from pathlib import Path


def restore():
    required = ["LEES_CLASH_KEYSTORE_BASE64", "LEES_CLASH_STORE_PASSWORD", "LEES_CLASH_KEY_PASSWORD"]
    if any(not os.environ.get(name) for name in required):
        raise ValueError("Missing independent Clash signing secrets")
    key = Path(os.environ["RUNNER_TEMP"]) / "lees-clash-release.p12"
    key.write_bytes(base64.b64decode(os.environ[required[0]], validate=True))
    key.chmod(0o600)
    values = [str(key), os.environ[required[1]], "lees-clash", os.environ[required[2]]]
    if any("\n" in value or "\r" in value or "\\" in value for value in values):
        raise ValueError("Unsupported signing property encoding")
    properties = Path("signing.properties")
    properties.write_text("".join(f"{name}={value}\n" for name, value in zip(["keystore.path", "keystore.password", "key.alias", "key.password"], values)), encoding="utf-8")
    properties.chmod(0o600)


if __name__ == "__main__":
    restore()
