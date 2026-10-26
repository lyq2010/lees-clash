package extension

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/metacubex/mihomo/config"
)

func TestProfileAdditionsReplaceNamesPrependRulesAndKeepSource(t *testing.T) {
	dir := t.TempDir()
	data := `{"rules":["DOMAIN,example.com,custom"],"proxies":[{"name":"node","type":"socks5","server":"127.0.0.1","port":1081}],"proxy-groups":[{"name":"custom","type":"select","proxies":["node"]}],"override":{"global-ua":"Lees-Test"}}`
	if err := os.WriteFile(filepath.Join(dir, FileName), []byte(data), 0600); err != nil {
		t.Fatal(err)
	}
	raw, err := config.UnmarshalRawConfig([]byte("proxies:\n - {name: node, type: socks5, server: 127.0.0.1, port: 1080}\nrules:\n - MATCH,DIRECT\n"))
	if err != nil {
		t.Fatal(err)
	}
	if err := Apply(raw, dir); err != nil {
		t.Fatal(err)
	}
	if len(raw.Proxy) != 1 || len(raw.ProxyGroup) != 1 || raw.Rule[0] != "DOMAIN,example.com,custom" || raw.Rule[1] != "MATCH,DIRECT" {
		t.Fatal("incorrect merge")
	}
	if raw.Proxy[0]["port"] != float64(1081) {
		t.Fatal("same-name proxy was not replaced")
	}
	for _, invalid := range []string{`{"proxies":[{"name":"dup"},{"name":"dup"}]}`, `{"override":{"rules":[]}}`, `invalid`} {
		os.WriteFile(filepath.Join(dir, FileName), []byte(invalid), 0600)
		if err := Apply(raw, dir); err == nil {
			t.Fatal("accepted invalid extension")
		}
	}
}
