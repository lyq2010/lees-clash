package preview

import (
	"os"
	"path/filepath"
	"testing"
)

func TestStoppedProfileGroupsProvidersAndLogicalRules(t *testing.T) {
	root := t.TempDir()
	config := `proxies:
  - {name: Local-A, type: socks5, server: 127.0.0.1, port: 9}
proxy-providers:
  Cached: {type: http, url: "https://invalid.example/nodes", path: cache.yaml}
proxy-groups:
  - {name: Manual, type: select, proxies: [Local-A], use: [Cached], exclude-filter: "Excluded"}
  - {name: Auto, type: url-test, proxies: [Local-A]}
rules:
  - DOMAIN,example.test,Manual
  - "AND,((DOMAIN,a.test),(NETWORK,TCP)),Manual"
  - IP-CIDR,127.0.0.0/8,DIRECT,no-resolve
  - MATCH,Manual
`
	if err := os.WriteFile(filepath.Join(root, "config.yaml"), []byte(config), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(filepath.Join(root, "providers"), 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "providers", "cache.yaml"), []byte("proxies:\n  - {name: Cached-A, type: socks5}\n  - {name: Excluded, type: ss}\n"), 0600); err != nil {
		t.Fatal(err)
	}
	p, err := Read(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(p.Groups) != 3 || len(p.Groups[0].Group.Proxies) != 2 || p.Groups[0].Group.Now != "Local-A" || p.Groups[1].Group.Now != "" {
		t.Fatalf("invalid groups: %+v", p.Groups)
	}
	if len(p.Rules) != 4 || p.Rules[1].Proxy != "Manual" || p.Rules[2].Proxy != "DIRECT" || p.Rules[3].Payload != "" {
		t.Fatalf("invalid rules: %+v", p.Rules)
	}
	// Removing the cache must not trigger a download or remove the configured group.
	if err := os.Rename(filepath.Join(root, "providers", "cache.yaml"), filepath.Join(root, "providers", "cache.saved")); err != nil {
		t.Fatal(err)
	}
	p, err = Read(root)
	if err != nil || len(p.Groups[0].Group.Proxies) != 1 {
		t.Fatalf("cache-independent preview: %v", err)
	}
}
