package preview

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"

	C "github.com/metacubex/mihomo/constant"
	RP "github.com/metacubex/mihomo/rules/provider"
	T "github.com/metacubex/mihomo/tunnel"
)

func TestStoppedResourceUpdateDirectValidationAndAtomicCache(t *testing.T) {
	// Android core initialization binds the rule-update dispatcher before any RPC.
	RP.SetTunnel(T.Tunnel)
	root := t.TempDir()
	previous := C.Path.HomeDir()
	C.SetHomeDir(root)
	defer C.SetHomeDir(previous)
	var bad atomic.Bool
	var hits atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		if r.Header.Get("X-Resource") != "fixture" {
			t.Error("configured resource header was lost")
		}
		if bad.Load() {
			fmt.Fprint(w, "invalid: [")
			return
		}
		if r.URL.Path == "/rules" {
			fmt.Fprint(w, "payload:\n  - example.test\n")
			return
		}
		fmt.Fprint(w, "proxies: [{name: Updated, type: direct}]")
	}))
	defer server.Close()
	config := fmt.Sprintf(`proxies: [{name: Base, type: direct}]
proxy-providers:
  External: {type: http, url: "%s/nodes", path: external.yaml, proxy: REJECT, interval: 86400, header: {X-Resource: [fixture]}}
rule-providers:
  Rules: {type: http, behavior: domain, url: "%s/rules", path: rules.yaml, proxy: REJECT, interval: 86400, header: {X-Resource: [fixture]}}
proxy-groups: [{name: Test, type: select, proxies: [Base], use: [External]}]
rules: ["MATCH,DIRECT"]
`, server.URL, server.URL)
	if err := os.WriteFile(filepath.Join(root, "config.yaml"), []byte(config), 0600); err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct{ kind, name, path, content string }{{"Proxy", "External", "external.yaml", "Updated"}, {"Rule", "Rules", "rules.yaml", "example.test"}} {
		if err := UpdateProvider(root, "", tc.kind, tc.name); err != nil {
			t.Fatal(err)
		}
		target := filepath.Join(root, "providers", tc.path)
		valid, err := os.ReadFile(target)
		if err != nil || !strings.Contains(string(valid), tc.content) {
			t.Fatalf("cache: %s %v", valid, err)
		}
		bad.Store(true)
		if err = UpdateProvider(root, "", tc.kind, tc.name); err == nil {
			t.Fatal("invalid response replaced cache")
		}
		after, _ := os.ReadFile(target)
		if string(valid) != string(after) {
			t.Fatal("failed update damaged old cache")
		}
		bad.Store(false)
	}
	if hits.Load() != 4 {
		t.Fatalf("unexpected requests: %d", hits.Load())
	}
	data, _ := os.ReadFile(filepath.Join(root, "config.yaml"))
	if string(data) != config {
		t.Fatal("resource update rewrote YAML policy or interval")
	}
	files, _ := filepath.Glob(filepath.Join(root, "providers", ".resource-update-*"))
	if len(files) != 0 {
		t.Fatal("temporary cache retained")
	}
	if err := UpdateProvider(root, "", "Proxy", "Missing"); err == nil {
		t.Fatal("accepted missing resource")
	}
}
