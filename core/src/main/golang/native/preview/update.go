package preview

import (
	"fmt"
	"io"
	"os"
	"path/filepath"

	AP "github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/resource"
	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/rules"
	RP "github.com/metacubex/mihomo/rules/provider"
	T "github.com/metacubex/mihomo/tunnel"
	"lees/native/common"
)

// Update one stopped profile's cache without applying it or opening listeners.
// A temporary provider reuses the core's full format/override validation. Initial
// is deliberately never called, so no interval, watcher or health check starts.
func UpdateProvider(path, secretKey, kind, name string) error {
	raw, err := readRawProfile(path, secretKey)
	if err != nil {
		return err
	}
	var original map[string]any
	prefix := "proxies"
	switch kind {
	case "Proxy":
		original = raw.ProxyProvider[name]
	case "Rule":
		original = raw.RuleProvider[name]
		prefix = "rules"
	default:
		return fmt.Errorf("未知资源类型")
	}
	if original == nil {
		return fmt.Errorf("没有该外部资源")
	}
	vehicle := text(original, "type")
	if vehicle != "http" && vehicle != "file" {
		return fmt.Errorf("该资源不需要更新")
	}
	mapping := make(map[string]any, len(original)+3)
	for key, value := range original {
		mapping[key] = value
	}
	relative := common.ResolveAsRoot(text(original, "path"))
	if relative == "" && text(original, "url") != "" {
		relative = prefix + "/" + utils.MakeHash([]byte(text(original, "url"))).String()
	}
	if relative == "" {
		return fmt.Errorf("资源路径为空")
	}
	target := filepath.Join(path, "providers", relative)
	mapping["path"], mapping["proxy"], mapping["interval"] = target, "DIRECT", 0
	mapping["health-check"] = map[string]any{"enable": false, "url": ""}
	if vehicle == "http" {
		if err = os.MkdirAll(filepath.Dir(target), 0700); err != nil {
			return err
		}
		file, e := os.CreateTemp(filepath.Dir(target), ".resource-update-*")
		if e != nil {
			return e
		}
		temporary := file.Name()
		if e = file.Close(); e != nil {
			_ = os.Remove(temporary)
			return e
		}
		defer os.Remove(temporary)
		mapping["path"] = temporary
	}
	var provider P.Provider
	if kind == "Proxy" {
		provider, err = AP.ParseProxyProvider(name, mapping, T.Tunnel)
	} else {
		provider, err = RP.ParseRuleProvider(name, mapping, rules.ParseRule, func(string) resource.BundleFile { return nil })
	}
	if err != nil {
		return err
	}
	if closer, ok := provider.(io.Closer); ok {
		defer closer.Close()
	}
	if err = provider.Update(); err != nil {
		return err
	}
	if vehicle == "http" {
		if err = os.Rename(mapping["path"].(string), target); err != nil {
			return err
		}
	}
	ClearCache()
	return nil
}
