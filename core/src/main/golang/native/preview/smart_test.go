package preview

import (
	"os"
	"path/filepath"
	"testing"
)

func TestSmartModePreviewAndValidation(t *testing.T) {
	defer ClearCache()
	root := t.TempDir()
	yaml := []byte("proxies: [{name: A, type: socks5, server: 127.0.0.1, port: 9}]\nproxy-groups: [{name: Auto, type: url-test, proxies: [A]}, {name: Builtin, type: select, proxies: [DIRECT]}, {name: Native, type: smart, proxies: [A]}]\n")
	if err := os.WriteFile(filepath.Join(root, "config.yaml"), yaml, 0600); err != nil {
		t.Fatal(err)
	}
	value, err := SmartMode(root, "", "Auto", "")
	if err != nil || value["mode"] != "config" {
		t.Fatalf("default: %v %v", value, err)
	}
	if _, err := SmartMode(root, "", "Auto", "smart"); err != nil {
		t.Fatal(err)
	}
	profile, err := Read(root, "")
	if err != nil || profile.Groups[0].Group.Type != "smart" {
		t.Fatalf("preview: %v %v", profile, err)
	}
	if profile.Groups[2].Group.Type != "smart" {
		t.Fatal("YAML-native smart lost")
	}
	if _, err := SmartMode(root, "", "Auto", "config"); err != nil {
		t.Fatal(err)
	}
	profile, _ = Read(root, "")
	if profile.Groups[0].Group.Type != "url-test" {
		t.Fatal("preview stale after reset")
	}
	for _, name := range []string{"GLOBAL", "Missing", "Builtin"} {
		if _, err := SmartMode(root, "", name, "smart"); err == nil {
			t.Fatalf("accepted unusable group %s", name)
		}
	}
	if data, _ := os.ReadFile(filepath.Join(root, "config.yaml")); string(data) != string(yaml) {
		t.Fatal("YAML changed")
	}
}

func TestSmartBulkOnlyURLTestAndIndependentReset(t *testing.T) {
	defer ClearCache()
	root := t.TempDir()
	data := []byte("proxies: [{name: A, type: socks5, server: 127.0.0.1, port: 9}]\nproxy-groups: [{name: Auto, type: url-test, proxies: [A]}, {name: OtherAuto, type: url-test, proxies: [A]}, {name: Manual, type: select, proxies: [A]}, {name: Failover, type: fallback, proxies: [A]}, {name: Balance, type: load-balance, proxies: [A]}, {name: Native, type: smart, proxies: [A]}]\n")
	if err := os.WriteFile(filepath.Join(root, "config.yaml"), data, 0600); err != nil {
		t.Fatal(err)
	}
	summary, err := SmartAll(root, "", "")
	if err != nil || summary["total"] != 2 || summary["enabled"] != 0 {
		t.Fatalf("default: %v %v", summary, err)
	}
	for _, name := range []string{"Manual", "Failover", "Balance", "Native"} {
		value, err := SmartMode(root, "", name, "")
		if err != nil || value["supported"] != "false" {
			t.Fatalf("unexpected control: %s %v %v", name, value, err)
		}
		if _, err := SmartMode(root, "", name, "smart"); err == nil {
			t.Fatalf("converted %s", name)
		}
	}
	summary, err = SmartAll(root, "", "smart")
	if err != nil || summary["enabled"] != 2 {
		t.Fatalf("bulk: %v %v", summary, err)
	}
	if _, err := SmartMode(root, "", "Auto", "config"); err != nil {
		t.Fatal(err)
	}
	summary, _ = SmartAll(root, "", "")
	if summary["enabled"] != 1 {
		t.Fatal("independent override lost")
	}
	if _, err := SmartAll(root, "", "config"); err != nil {
		t.Fatal(err)
	}
	p, err := Read(root, "")
	if err != nil || p.Groups[0].Group.Type != "url-test" || p.Groups[2].Group.Type != "select" || p.Groups[5].Group.Type != "smart" {
		t.Fatalf("restored types: %v %v", p, err)
	}
	if after, _ := os.ReadFile(filepath.Join(root, "config.yaml")); string(after) != string(data) {
		t.Fatal("YAML changed")
	}
}

func TestSmartBulkValidationDoesNotPartiallySave(t *testing.T) {
	defer ClearCache()
	root := t.TempDir()
	data := []byte("proxies: [{name: A, type: socks5, server: 127.0.0.1, port: 9}]\nproxy-groups: [{name: Auto, type: url-test, proxies: [A]}, {name: Empty, type: url-test, proxies: [DIRECT]}]\n")
	if err := os.WriteFile(filepath.Join(root, "config.yaml"), data, 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := SmartAll(root, "", "smart"); err == nil {
		t.Fatal("accepted unusable URLTest group")
	}
	value, err := SmartAll(root, "", "")
	if err != nil || value["enabled"] != 0 {
		t.Fatalf("partially saved: %v %v", value, err)
	}
}
