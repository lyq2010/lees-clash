// Read-only configuration browsing. Never applies a configuration or opens listeners.
package preview

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/dlclark/regexp2"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
	RC "github.com/metacubex/mihomo/rules/common"
	"lees/native/common"
	"lees/native/tunnel"
)

type Group struct {
	Name  string             `json:"name"`
	Group *tunnel.ProxyGroup `json:"group"`
}
type Rule struct {
	Index   int    `json:"index"`
	Type    string `json:"type"`
	Payload string `json:"payload"`
	Proxy   string `json:"proxy"`
}
type Profile struct {
	Groups    []Group
	Rules     []Rule
	Mode      string
	Providers []tunnel.Provider
}

func text(m map[string]any, key string) string { s, _ := m[key].(string); return s }
func names(value any) []string {
	var result []string
	switch v := value.(type) {
	case []any:
		for _, item := range v {
			if s, ok := item.(string); ok {
				result = append(result, s)
			}
		}
	case []string:
		result = v
	}
	return result
}
func enabled(m map[string]any, key string) bool { value, _ := m[key].(bool); return value }

func providerNodes(path string, mapping map[string]any) []map[string]any {
	var data []byte
	if payload, ok := mapping["payload"]; ok {
		data, _ = yaml.Marshal(map[string]any{"proxies": payload})
	} else {
		relative := common.ResolveAsRoot(text(mapping, "path"))
		if relative == "" && text(mapping, "url") != "" {
			relative = "proxies/" + utils.MakeHash([]byte(text(mapping, "url"))).String()
		}
		if relative == "" {
			return nil
		}
		data, _ = os.ReadFile(filepath.Join(path, "providers", relative))
	}
	var content struct {
		Proxies []map[string]any `yaml:"proxies"`
	}
	_ = yaml.Unmarshal(data, &content)
	return content.Proxies
}

func readProfile(path string) (*Profile, error) {
	data, err := os.ReadFile(filepath.Join(path, "config.yaml"))
	if err != nil {
		return nil, fmt.Errorf("无法读取所选配置")
	}
	raw, err := config.UnmarshalRawConfig(data)
	if err != nil {
		return nil, fmt.Errorf("所选配置无法解析: %w", err)
	}
	proxies := map[string]*tunnel.Proxy{}
	order := []string{"DIRECT", "REJECT"}
	for _, name := range order {
		proxies[name] = &tunnel.Proxy{Name: name, Title: name, Type: name}
	}
	add := func(m map[string]any, isGroup bool) {
		name := text(m, "name")
		if name == "" {
			return
		}
		proxies[name] = &tunnel.Proxy{Name: name, Title: name, Subtitle: text(m, "type"), Type: text(m, "type"), IsGroup: isGroup}
	}
	for _, node := range raw.Proxy {
		add(node, false)
		order = append(order, text(node, "name"))
	}
	providerNames := make([]string, 0, len(raw.ProxyProvider))
	providers := map[string][]string{}
	for name, mapping := range raw.ProxyProvider {
		providerNames = append(providerNames, name)
		for _, node := range providerNodes(path, mapping) {
			add(node, false)
			providers[name] = append(providers[name], text(node, "name"))
		}
	}
	sort.Strings(providerNames)
	for _, group := range raw.ProxyGroup {
		add(group, true)
		order = append(order, text(group, "name"))
	}
	result := &Profile{Groups: []Group{}, Rules: []Rule{}, Mode: raw.Mode.String()}
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
	groups := raw.ProxyGroup
	if _, exists := proxies["GLOBAL"]; !exists {
		groups = append(append([]map[string]any{}, groups...), map[string]any{"name": "GLOBAL", "type": "select", "proxies": order})
	}
	for _, group := range groups {
		if enabled(group, "hidden") {
			continue
		}
		members := names(group["proxies"])
		if enabled(group, "include-all") || enabled(group, "include-all-proxies") {
			for _, node := range raw.Proxy {
				members = append(members, text(node, "name"))
			}
		}
		use := names(group["use"])
		if enabled(group, "include-all") || enabled(group, "include-all-providers") {
			use = providerNames
		}
		for _, name := range use {
			members = append(members, providers[name]...)
		}
		var include, exclude *regexp2.Regexp
		if pattern := text(group, "filter"); pattern != "" {
			include, err = regexp2.Compile(pattern, 0)
			if err != nil {
				return nil, err
			}
			include.MatchTimeout = 50 * time.Millisecond
		}
		if pattern := text(group, "exclude-filter"); pattern != "" {
			exclude, err = regexp2.Compile(pattern, 0)
			if err != nil {
				return nil, err
			}
			exclude.MatchTimeout = 50 * time.Millisecond
		}
		items := []*tunnel.Proxy{}
		seen := map[string]bool{}
		for _, name := range members {
			p := proxies[name]
			if p == nil || seen[name] || name == text(group, "name") {
				continue
			}
			if include != nil {
				match, e := include.MatchString(name)
				if e != nil {
					return nil, e
				}
				if !match {
					continue
				}
			}
			if exclude != nil {
				match, e := exclude.MatchString(name)
				if e != nil {
					return nil, e
				}
				if match {
					continue
				}
			}
			seen[name] = true
			items = append(items, p)
		}
		now := ""
		if text(group, "type") == "select" && len(items) > 0 {
			now = items[0].Name
		}
		result.Groups = append(result.Groups, Group{text(group, "name"), &tunnel.ProxyGroup{Type: text(group, "type"), Now: now, Proxies: items}})
	}
	for i, line := range raw.Rule {
		kind, payload, target, _ := RC.ParseRulePayload(strings.TrimSpace(line), true)
		result.Rules = append(result.Rules, Rule{i, kind, payload, target})
	}
	return result, nil
}
