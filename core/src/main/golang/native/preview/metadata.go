package preview

import (
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/config"
	"lees/native/common"
	"lees/native/tunnel"
	"os"
	"path/filepath"
	"sort"
)

// The home page and resource list need metadata, not every cached node and rule.
func ReadMetadata(path, secretKey string) (*Profile, error) {
	raw, err := readRawProfile(path, secretKey)
	if err != nil {
		return nil, err
	}
	return profileMetadata(raw, path), nil
}

func profileMetadata(raw *config.RawConfig, path string) *Profile {
	result := &Profile{Mode: raw.Mode.String()}
	for _, entry := range []struct {
		items        map[string]map[string]any
		kind, prefix string
	}{{raw.ProxyProvider, "Proxy", "proxies"}, {raw.RuleProvider, "Rule", "rules"}} {
		for name, mapping := range entry.items {
			vehicle := "File"
			switch text(mapping, "type") {
			case "http":
				vehicle = "HTTP"
			case "inline":
				vehicle = "Inline"
			}
			relative := common.ResolveAsRoot(text(mapping, "path"))
			if relative == "" && text(mapping, "url") != "" {
				relative = entry.prefix + "/" + utils.MakeHash([]byte(text(mapping, "url"))).String()
			}
			var updated int64
			if relative != "" {
				if info, e := os.Stat(filepath.Join(path, "providers", relative)); e == nil {
					updated = info.ModTime().UnixMilli()
				}
			}
			result.Providers = append(result.Providers, tunnel.Provider{Name: name, Type: entry.kind, VehicleType: vehicle, UpdatedAt: updated})
		}
	}
	sort.Slice(result.Providers, func(i, j int) bool { return result.Providers[i].Name < result.Providers[j].Name })
	if result.Providers == nil {
		result.Providers = []tunnel.Provider{}
	}
	return result
}
