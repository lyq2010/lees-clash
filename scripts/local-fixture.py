"""Loopback-only network fixture for Android integration tests. No external servers."""
import http.server
import json
from pathlib import Path
import select
import socket
import socketserver
import struct
import threading
import time

CONFIG = """mixed-port: 17890
allow-lan: false
mode: rule
log-level: info
dns:
  enable: true
  enhanced-mode: redir-host
  nameserver:
    - tcp://127.0.0.1:13053
  default-nameserver:
    - tcp://127.0.0.1:13053
proxies:
  - name: Local-A
    type: socks5
    server: 127.0.0.1
    port: 18081
  - name: Local-B
    type: socks5
    server: 127.0.0.1
    port: 18081
proxy-groups:
  - name: Local-Test
    type: select
    proxies: [Local-A, Local-B]
rules:
  - DOMAIN-SUFFIX,lees-clash.test,Local-Test
  - MATCH,DIRECT
"""

dns_cache_queries = {}
dns_cache_lock = threading.Lock()

def read_exact(sock, count):
    data = b""
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise EOFError()
        data += chunk
    return data

class HTTP(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        if self.path == "/config-error":
            self.send_response(500)
            self.end_headers()
            self.wfile.write(b"fixture server error")
            return
        elif self.path.partition("?")[0] == "/ip-info":
            data = b'{"ip":"203.0.113.5","isp":{"asn":"AS64500","isp":"Local fixture"},"location":{"country":"Test","city":"Loopback"},"risk":{"is_vpn":false,"is_proxy":false,"is_tor":false,"is_mobile":false,"is_datacenter":true,"risk_score":0}}'
        elif self.path.startswith("/config-slow"):
            time.sleep(1.5)
            data = CONFIG.encode()
        elif self.path.startswith("/config-invalid"):
            data = b"proxies: [invalid: yaml"
        elif self.path in ("/encrypted-config/x25519", "/encrypted-config/hybrid"):
            fixtures = json.loads((Path(__file__).resolve().parents[1] /
                "app/src/androidTest/assets/encrypted-profiles.json").read_text())
            kind = self.path.rsplit("/", 1)[1]
            data = next(item["encrypted"] for item in fixtures if item["kind"] == kind).encode()
        elif self.path.startswith("/config"):
            data = CONFIG.encode()
        elif self.path == "/generate_204":
            self.send_response(204)
            self.end_headers()
            return
        elif self.path in ("/slow", "/hold"):
            self.send_response(200)
            self.send_header("Content-Length", "65536")
            self.end_headers()
            try:
                # Instrumented UI tests can take up to 180s; clients close these sockets in finally.
                chunks = 1200 if self.path == "/hold" else 16
                for _ in range(chunks):
                    self.wfile.write(b"x" * (1 if self.path == "/hold" else 4096))
                    self.wfile.flush()
                    time.sleep(.25)
            except (ConnectionError, OSError):
                pass
            return
        else:
            data = b"lees-clash-local-fixture"
        self.send_response(200)
        if self.path.startswith("/config"):
            if self.path == "/config-subscription/over-quota":
                self.send_header("subscription-userinfo", "upload=9223372036854775807; download=9223372036854775807; total=1; expire=1")
            elif self.path != "/config-subscription/no-info":
                self.send_header("subscription-userinfo", "upload=1024; download=2048; total=1048576; expire=1893456000")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

class SOCKS(socketserver.BaseRequestHandler):
    def handle(self):
        client = self.request
        try:
            version, count = read_exact(client, 2)
            if version != 5:
                return
            read_exact(client, count)
            client.sendall(b"\x05\x00")
            version, command, _, kind = read_exact(client, 4)
            if kind == 3:
                host = read_exact(client, read_exact(client, 1)[0]).decode()
            elif kind == 1:
                host = socket.inet_ntoa(read_exact(client, 4))
            else:
                return
            port = struct.unpack("!H", read_exact(client, 2))[0]
            if command != 1 or not (host in ("lees-clash.test", "127.0.0.1") or host.endswith(".lees-clash.test")) or port != 18080:
                client.sendall(b"\x05\x02\x00\x01" + b"\x00" * 6)
                return
            with socket.create_connection(("127.0.0.1", 18080), timeout=5) as target:
                client.sendall(b"\x05\x00\x00\x01" + b"\x00" * 6)
                while True:
                    readable, _, _ = select.select([client, target], [], [], 10)
                    if not readable:
                        return
                    for source in readable:
                        data = source.recv(65536)
                        if not data:
                            return
                        (target if source is client else client).sendall(data)
        except (OSError, EOFError):
            pass

class DNS(socketserver.BaseRequestHandler):
    def handle(self):
        try:
            packet = read_exact(self.request, struct.unpack("!H", read_exact(self.request, 2))[0])
            offset = 12
            labels = []
            while packet[offset]:
                labels.append(packet[offset+1:offset+1+packet[offset]])
                offset += packet[offset] + 1
            offset += 1
            kind = struct.unpack("!H", packet[offset:offset+2])[0]
            question = packet[12:offset+4]
            domain = b".".join(labels).lower()
            if domain == b"timeout-verification.test":
                time.sleep(12)
                return
            code = 3 if domain == b"nxdomain-verification.test" else 2 if domain == b"servfail-verification.test" else 0
            value = (socket.inet_aton("127.0.0.1") if kind == 1 else
                     socket.inet_pton(socket.AF_INET6, "::1") if kind == 28 else
                     b"\x0dlocal-fixture" if kind == 16 else b"")
            if kind == 1 and domain.startswith(b"cache-verification-"):
                with dns_cache_lock:
                    count = dns_cache_queries.get(domain, 0) + 1
                    dns_cache_queries[domain] = count
                value = socket.inet_aton(f"127.0.0.{count % 250 + 1}")
            answer = b"\xc0\x0c" + struct.pack("!HHIH", kind, 1, 60, len(value)) + value if value and not code else b""
            response = packet[:2] + struct.pack("!HHHHH", 0x8180 | code, 1, int(bool(answer)), 0, 0) + question + answer
            self.request.sendall(struct.pack("!H", len(response)) + response)
        except (OSError, EOFError, IndexError):
            pass

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

if __name__ == "__main__":
    servers = [http.server.ThreadingHTTPServer(("127.0.0.1", 18080), HTTP), Server(("127.0.0.1", 18081), SOCKS), Server(("127.0.0.1", 13053), DNS)]
    for server in servers:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    print("Loopback HTTP:18080 SOCKS:18081 DNS:13053 ready", flush=True)
    threading.Event().wait()
