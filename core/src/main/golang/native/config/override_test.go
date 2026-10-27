package config

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	upstream "github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
)

// Shared with androidTest: these identities, endpoints and values are synthetic.
//
//go:embed testdata/override-full.json
var fullOverride string

const sourceProfile = `mixed-port: 17890
allow-lan: true
bind-address: 127.0.0.1
external-controller: 0.0.0.0:9999
interface-name: unused-interface
routing-mark: 123
hosts: {source.test: 127.0.0.2}
dns:
  enable: true
  nameserver: [tcp://127.0.0.1:13053]
  default-nameserver: [tcp://127.0.0.1:13053]
  fallback-filter: {geoip: false}
proxies: [{name: Local, type: socks5, server: 127.0.0.1, port: 18081}]
proxy-groups: [{name: Local-Test, type: select, proxies: [Local]}]
rules: ["MATCH,DIRECT"]
`

func overrideEnvironment(t *testing.T) string {
	t.Helper()
	previous, session := C.Path.HomeDir(), ReadOverride(OverrideSlotSession)
	root := t.TempDir()
	C.SetHomeDir(root)
	WriteOverride(OverrideSlotSession, "{}")
	t.Cleanup(func() { C.SetHomeDir(previous); WriteOverride(OverrideSlotSession, session) })
	if err := os.WriteFile(filepath.Join(root, "config.yaml"), []byte(sourceProfile), 0600); err != nil {
		t.Fatal(err)
	}
	return root
}

func checkFields(t *testing.T, path string, expected, actual any) {
	t.Helper()
	if fields, ok := expected.(map[string]any); ok {
		object, ok := actual.(map[string]any)
		if !ok {
			t.Fatalf("%s is not an object", path)
		}
		for key, value := range fields {
			checkFields(t, path+"/"+key, value, object[key])
		}
	} else if !reflect.DeepEqual(expected, actual) {
		t.Fatalf("%s: want %v, got %v", path, expected, actual)
	}
}

func TestEveryOverrideFieldReachesPatchedConfig(t *testing.T) {
	root := overrideEnvironment(t)
	WriteOverride(OverrideSlotPersist, fullOverride)
	raw, err := UnmarshalAndPatch(root)
	if err != nil {
		t.Fatal(err)
	}
	data, err := json.Marshal(raw)
	if err != nil {
		t.Fatal(err)
	}
	var expected, actual map[string]any
	if err := json.Unmarshal([]byte(fullOverride), &expected); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(data, &actual); err != nil {
		t.Fatal(err)
	}
	checkFields(t, "", expected, actual)
	if _, err := Parse(raw); err != nil {
		t.Fatal(err)
	}
}

func TestOverridePrecedenceEmptyCollectionsAndPlatformGuards(t *testing.T) {
	root := overrideEnvironment(t)
	WriteOverride(OverrideSlotPersist, fullOverride)
	WriteOverride(OverrideSlotSession, `{"mode":"direct","authentication":[],"hosts":{"override.test":"127.0.0.3"},"dns":{"ipv6":false,"fallback":[]},"sniffer":{"sniff":{"HTTP":{"override-destination":true}}}}`)
	raw, err := UnmarshalAndPatch(root)
	if err != nil {
		t.Fatal(err)
	}
	if raw.Mode.String() != "direct" || len(raw.Authentication) != 0 || raw.DNS.IPv6 || len(raw.DNS.Fallback) != 0 {
		t.Fatal("session scalar/list override did not win")
	}
	if raw.Hosts["override.test"] != "127.0.0.3" || raw.Hosts["source.test"] != "127.0.0.2" {
		t.Fatal("host map merge precedence")
	}
	http := raw.Sniffer.Sniff["HTTP"]
	if http.OverrideDest == nil || !*http.OverrideDest {
		t.Fatal("nested sniffer session override")
	}
	if raw.Interface != "" || raw.RoutingMark != 0 || raw.Tun.Enable || raw.Tun.AutoRoute {
		t.Fatal("Android safety guards were overridden")
	}
	if _, err := Parse(raw); err != nil {
		t.Fatal(err)
	}
	WriteOverride(OverrideSlotSession, "{}")
	WriteOverride(OverrideSlotPersist, "{}")
	raw, err = UnmarshalAndPatch(root)
	if err != nil {
		t.Fatal(err)
	}
	if raw.MixedPort != 17890 || !raw.AllowLan || raw.ExternalController != "" {
		t.Fatal("unset fields or controller guard")
	}
}

func TestRareOverrideCombinationsParse(t *testing.T) {
	root := overrideEnvironment(t)
	for _, mode := range []string{"rule", "global", "direct"} {
		for _, dnsMode := range []string{"normal", "redir-host", "fake-ip"} {
			for _, filter := range []string{"blacklist", "whitelist", "rule"} {
				for _, processMode := range []string{"off", "strict", "always"} {
					for flags := 0; flags < 16; flags++ {
						name := fmt.Sprintf("%s/%s/%s/%s/%d", mode, dnsMode, filter, processMode, flags)
						t.Run(name, func(t *testing.T) {
							var value map[string]any
							if err := json.Unmarshal([]byte(fullOverride), &value); err != nil {
								t.Fatal(err)
							}
							value["mode"] = mode
							value["find-process-mode"] = processMode
							value["global-client-fingerprint"] = ""
							value["disable-keep-alive"] = flags&1 != 0
							dns := value["dns"].(map[string]any)
							dns["enhanced-mode"], dns["fake-ip-filter-mode"] = dnsMode, filter
							if filter == "rule" {
								dns["fake-ip-filter"] = []string{"DOMAIN,local.test,real-ip", "MATCH,fake-ip"}
							}
							dns["respect-rules"] = flags&2 != 0
							value["sniffer"].(map[string]any)["enable"] = flags&4 != 0
							value["ntp"].(map[string]any)["enable"] = flags&8 != 0
							value["ntp"].(map[string]any)["write-to-system"] = flags&1 != 0
							data, _ := json.Marshal(value)
							WriteOverride(OverrideSlotPersist, string(data))
							raw, err := UnmarshalAndPatch(root)
							if err != nil {
								t.Fatal(err)
							}
							parsed, err := Parse(raw)
							if err != nil {
								t.Fatal(err)
							}
							if parsed.NTP.Enable != (flags&8 != 0) || parsed.NTP.WriteToSystem != (flags&1 != 0) || parsed.Sniffer.Enable != (flags&4 != 0) || parsed.DNS.EnhancedMode.String() != dnsMode || raw.FindProcessMode.String() != processMode {
								t.Fatal("parsed flags lost")
							}
						})
					}
				}
			}
		}
	}
}

func TestAndroidDnsFallbackAndSystemDnsAppend(t *testing.T) {
	root := overrideEnvironment(t)
	WriteOverride(OverrideSlotPersist, `{"dns":{"enable":false},"clash-for-android":{"append-system-dns":false}}`)
	raw, err := UnmarshalAndPatch(root)
	if err != nil {
		t.Fatal(err)
	}
	if !raw.DNS.Enable || !raw.ClashForAndroid.AppendSystemDNS || raw.DNS.NameServer[len(raw.DNS.NameServer)-1] != "system://" {
		t.Fatal("Android DNS fallback policy changed")
	}
	WriteOverride(OverrideSlotPersist, `{"clash-for-android":{"append-system-dns":true}}`)
	raw, err = UnmarshalAndPatch(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(raw.DNS.NameServer) != 2 || raw.DNS.NameServer[0] != "tcp://127.0.0.1:13053" || raw.DNS.NameServer[1] != "system://" {
		t.Fatal("system DNS append lost profile resolver")
	}
}

func TestInvalidNestedOverrideValuesAreRejectedByCore(t *testing.T) {
	root := overrideEnvironment(t)
	for _, patch := range []string{
		`{"dns":{"respect-rules":true,"proxy-server-nameserver":[]}}`,
		`{"dns":{"nameserver":[]}}`,
		`{"sniffer":{"sniff":{"HTTP":{"ports":["invalid-range"]}}}}`,
		`{"sniffer":{"skip-src-address":["not-a-network"]}}`,
	} {
		WriteOverride(OverrideSlotPersist, patch)
		raw, err := UnmarshalAndPatch(root)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := upstream.ParseRawConfig(raw); err == nil {
			t.Fatalf("invalid nested override accepted: %s", patch)
		}
	}
}
