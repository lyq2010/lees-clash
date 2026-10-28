package config

import (
	"context"
	"net"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
)

func TestSmartLearningUsesProfileFingerprintAcrossReload(t *testing.T) {
	root := overrideEnvironment(t)
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			conn.Close()
		}
	}()
	raw, err := UnmarshalAndPatch(root)
	if err != nil {
		t.Fatal(err)
	}
	_, port, _ := net.SplitHostPort(listener.Addr().String())
	raw.Proxy[0]["port"] = port
	raw.ProxyGroup[0]["type"] = "smart"
	cfg, err := Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	defer outboundgroup.ConfigureSmartPersistence("", "", nil)
	if err := restoreSmartLearning(root, cfg); err != nil {
		t.Fatal(err)
	}
	s := cfg.Proxies["Local-Test"].Adapter().(*outboundgroup.Smart)
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	_, _ = s.DialContext(ctx, &C.Metadata{Host: "learn.invalid", DstPort: 443, NetWork: C.TCP})
	s.Close()
	if _, err := os.Stat(filepath.Join(root, "smart-state.jsonl")); err != nil {
		t.Fatal(err)
	}
	reloaded, err := Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	s2 := reloaded.Proxies["Local-Test"].Adapter().(*outboundgroup.Smart)
	if err := restoreSmartLearning(root, reloaded); err != nil {
		t.Fatal(err)
	}
	if outboundgroup.SmartMemoryStats()["nodeRecords"] == 0 {
		t.Fatal("profile reload lost saved observations")
	}
	s2.Close()
	f, err := os.OpenFile(filepath.Join(root, "config.yaml"), os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		t.Fatal(err)
	}
	f.WriteString("\n# changed profile\n")
	f.Close()
	changed, err := Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	defer changed.Proxies["Local-Test"].Close()
	if err := restoreSmartLearning(root, changed); err != nil {
		t.Fatal(err)
	}
	if outboundgroup.SmartMemoryStats()["nodeRecords"] != 0 {
		t.Fatal("changed profile inherited stale learning")
	}
}
