package smart

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/metacubex/mihomo/config"
)

func TestOverridesDefaultIsolationPersistenceAndReset(t *testing.T) {
	root, other := t.TempDir(), t.TempDir()
	content := []byte("proxy-groups: [{name: Auto, type: url-test, proxies: [A], interval: 600, filter: HK}]\n")
	path := filepath.Join(root, "config.yaml")
	if err := os.WriteFile(path, content, 0600); err != nil {
		t.Fatal(err)
	}
	raw, err := config.UnmarshalRawConfig(content)
	if err != nil {
		t.Fatal(err)
	}
	if err := Apply(raw, root); err != nil {
		t.Fatal(err)
	}
	if raw.ProxyGroup[0]["type"] != "url-test" {
		t.Fatal("default must follow YAML")
	}
	if err := Set(root, "Auto", true); err != nil {
		t.Fatal(err)
	}
	if err := Apply(raw, root); err != nil {
		t.Fatal(err)
	}
	if raw.ProxyGroup[0]["type"] != "smart" || raw.ProxyGroup[0]["filter"] != "HK" {
		t.Fatal("override must only change group type")
	}
	values, _ := Read(other)
	if values["Auto"] {
		t.Fatal("profile override leaked")
	}
	if data, _ := os.ReadFile(path); string(data) != string(content) {
		t.Fatal("subscription modified")
	}
	// A subscription refresh updates YAML while preserving the separate mode.
	newContent := []byte("proxy-groups: [{name: Auto, type: fallback, proxies: [A,B]}]\n")
	if err := os.WriteFile(path, newContent, 0600); err != nil {
		t.Fatal(err)
	}
	raw, _ = config.UnmarshalRawConfig(newContent)
	if err := Apply(raw, root); err != nil {
		t.Fatal(err)
	}
	if raw.ProxyGroup[0]["type"] != "fallback" {
		t.Fatal("a refreshed non-URLTest group must ignore the old override")
	}
	if err := Set(root, "Auto", false); err != nil {
		t.Fatal(err)
	}
	raw, _ = config.UnmarshalRawConfig(newContent)
	if err := Apply(raw, root); err != nil {
		t.Fatal(err)
	}
	if raw.ProxyGroup[0]["type"] != "fallback" {
		t.Fatal("reset must restore current YAML, not saved old YAML")
	}
	if err := Set(root, "Auto", true); err != nil {
		t.Fatal(err)
	}
	raw, _ = config.UnmarshalRawConfig([]byte("proxy-groups: [{name: Renamed, type: select, proxies: [A]}]\n"))
	if err := Apply(raw, root); err != nil {
		t.Fatal(err)
	}
	if raw.ProxyGroup[0]["type"] != "select" {
		t.Fatal("rename matched stale override")
	}
}

func TestNonURLTestOverridesAreIgnored(t *testing.T) {
	root := t.TempDir()
	for _, name := range []string{"Auto", "Manual", "Backup", "Native"} {
		if err := Set(root, name, true); err != nil {
			t.Fatal(err)
		}
	}
	raw, _ := config.UnmarshalRawConfig([]byte("proxy-groups: [{name: Auto, type: url-test}, {name: Manual, type: select}, {name: Backup, type: fallback}, {name: Native, type: smart}]\n"))
	if err := Apply(raw, root); err != nil {
		t.Fatal(err)
	}
	for i, expected := range []string{"smart", "select", "fallback", "smart"} {
		if raw.ProxyGroup[i]["type"] != expected {
			t.Fatalf("wrong group %d: %v", i, raw.ProxyGroup[i])
		}
	}
}
