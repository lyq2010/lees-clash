"""Position this project's dedicated virtualscene camera in front of its local QR poster.

Requires the external Python venv (grpcio-tools) and emulator on localhost:8556
started with -grpc-use-token. Never prints or saves the emulator auth token.
"""
import os
from pathlib import Path
import subprocess
import sys

cache = Path(os.environ["LEES_CLASH_CACHE"]) / "grpc-client"
cache.mkdir(parents=True, exist_ok=True)
protocol = Path(os.environ["ANDROID_HOME"]) / "emulator/lib/emulator_controller.proto"
subprocess.run([
    sys.executable, "-m", "grpc_tools.protoc", "-I", str(protocol.parent),
    f"--python_out={cache}", f"--grpc_python_out={cache}", str(protocol),
], check=True)
sys.path.insert(0, str(cache))
import grpc
import emulator_controller_pb2 as messages
import emulator_controller_pb2_grpc as service

discovery = Path.home() / "Library/Caches/TemporaryItems/avd/running"
settings = None
for entry in discovery.glob("pid_*.ini"):
    values = dict(line.strip().split("=", 1) for line in entry.read_text().splitlines() if "=" in line)
    if values.get("grpc.port") == "8556":
        settings = values
        break
if settings is None or "grpc.token" not in settings:
    raise RuntimeError("请先以 -grpc 8556 -grpc-use-token 启动专用模拟器")

with grpc.insecure_channel("127.0.0.1:8556") as channel:
    stub = service.EmulatorControllerStub(channel)
    # Toren1BD.posters: wall center (-0.807, 0.320, 5.316), rotation (0, -150, 0).
    for target, values in [
        (messages.PhysicalModelValue.POSITION, [-1.807, .320, 3.584]),
        (messages.PhysicalModelValue.ROTATION, [0, -150, 0]),
    ]:
        stub.setPhysicalModel(messages.PhysicalModelValue(
            target=target, value=messages.ParameterValue(data=values),
            interpolation=messages.PhysicalModelValue.STEP,
        ), metadata=[("authorization", "Bearer " + settings["grpc.token"])], timeout=5)
print("本地二维码虚拟相机姿态已就绪")
