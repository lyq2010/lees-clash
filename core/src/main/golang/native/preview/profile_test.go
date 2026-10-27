package preview

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/metacubex/mihomo/component/age"
)

func TestStoppedProfileGroupsProvidersAndLogicalRules(t *testing.T) {
	root := t.TempDir()
	config := `proxies:
  - {name: Local-A, type: socks5, server: 127.0.0.1, port: 9}
proxy-providers:
  Cached: {type: http, url: "https://invalid.example/nodes", path: cache.yaml}
proxy-groups:
  - {name: Manual, type: select, proxies: [Local-A], use: [Cached], exclude-filter: "Excluded", icon: "https://example.test/icon.svg"}
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
	p, err := Read(root, "")
	if err != nil {
		t.Fatal(err)
	}
	if len(p.Groups) != 3 || len(p.Groups[0].Group.Proxies) != 2 || p.Groups[0].Group.Now != "Local-A" || p.Groups[1].Group.Now != "" {
		t.Fatalf("invalid groups: %+v", p.Groups)
	}
	if p.Groups[0].Group.Icon != "https://example.test/icon.svg" || p.Groups[1].Group.Icon != "" {
		t.Fatal("preview lost configured/default group icon")
	}
	ClearCache()
	cache.Lock()
	retained := cache.profile != nil || cache.path != "" || cache.stamp != ""
	cache.Unlock()
	if retained {
		t.Fatal("preview retained after cache release")
	}
	if refreshed, err := Read(root, ""); err != nil || len(refreshed.Groups) != len(p.Groups) {
		t.Fatalf("preview cannot reload after release: %v", err)
	}
	if len(p.Rules) != 4 || p.Rules[1].Proxy != "Manual" || p.Rules[2].Proxy != "DIRECT" || p.Rules[3].Payload != "" {
		t.Fatalf("invalid rules: %+v", p.Rules)
	}
	// Removing the cache must not trigger a download or remove the configured group.
	if err := os.Rename(filepath.Join(root, "providers", "cache.yaml"), filepath.Join(root, "providers", "cache.saved")); err != nil {
		t.Fatal(err)
	}
	p, err = Read(root, "")
	if err != nil || len(p.Groups[0].Group.Proxies) != 1 {
		t.Fatalf("cache-independent preview: %v", err)
	}
}

func TestEncryptedPreviewUsesSelectedKeyAndInvalidatesCache(t *testing.T) {
	for _, generate := range []func() (string, string, error){age.GenX25519KeyPair, age.GenHybridKeyPair} {
		secret, public, err := generate()
		if err != nil {
			t.Fatal(err)
		}
		wrong, _, err := generate()
		if err != nil {
			t.Fatal(err)
		}
		data, err := age.EncryptBytes([]byte("proxy-groups:\n  - {name: Private, type: select, proxies: [DIRECT]}\nrules: [\"MATCH,DIRECT\"]\n"), public)
		if err != nil {
			t.Fatal(err)
		}
		root := t.TempDir()
		if err := os.WriteFile(filepath.Join(root, "config.yaml"), data, 0600); err != nil {
			t.Fatal(err)
		}
		// A valid global key from another operation must not unlock a preview with no key.
		age.SetGlobalSecretKeys(secret)
		t.Cleanup(func() { age.SetGlobalSecretKeys() })
		if _, err := Read(root, ""); err == nil {
			t.Fatal("preview used a global identity")
		}
		age.SetGlobalSecretKeys(wrong)
		if _, err := Read(root, secret); err != nil {
			t.Fatal(err)
		}
		for _, key := range []string{"", wrong, "invalid"} {
			if _, err := Read(root, key); err == nil {
				t.Fatal("cached preview bypassed identity validation")
			}
		}
		if _, err := Read(root, secret); err != nil {
			t.Fatal(err)
		}
		if _, err := ReadMetadata(root, wrong); err == nil {
			t.Fatal("metadata bypassed the selected decryption key")
		}
	}
}

func TestMetadataDoesNotRetainFullPolicyPreview(t *testing.T) {
	ClearCache()
	defer ClearCache()
	root := t.TempDir()
	data := "mode: global\nproxy-providers:\n  External: {type: http, url: https://invalid.example/nodes, path: nodes.yaml}\n"
	if err := os.WriteFile(filepath.Join(root, "config.yaml"), []byte(data), 0600); err != nil {
		t.Fatal(err)
	}
	metadata, err := ReadMetadata(root, "")
	if err != nil || metadata.Mode != "global" || len(metadata.Providers) != 1 {
		t.Fatalf("metadata: %+v %v", metadata, err)
	}
	if metadata.Groups != nil || metadata.Rules != nil {
		t.Fatal("metadata loaded policy payload")
	}
	cache.Lock()
	retained := cache.profile != nil
	cache.Unlock()
	if retained {
		t.Fatal("metadata populated full preview cache")
	}
}
