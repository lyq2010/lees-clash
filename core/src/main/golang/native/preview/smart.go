package preview

import (
	"fmt"
	"lees/native/smart"
	"strings"
)

func SmartMode(path, secretKey, name, mode string) (map[string]string, error) {
	raw, err := readProfileSource(path, secretKey)
	if err != nil {
		return nil, err
	}
	found := false
	supported := false
	for _, group := range raw.ProxyGroup {
		if text(group, "name") == name {
			found = true
			supported = text(group, "type") == "url-test"
			break
		}
	}
	if !found {
		return nil, fmt.Errorf("所选配置中没有该策略组")
	}
	if mode != "" {
		if !supported {
			return nil, fmt.Errorf("仅 URLTest 策略组支持转换为 Smart")
		}
		if mode != "smart" && mode != "config" {
			return nil, fmt.Errorf("策略模式无效")
		}
		if mode == "smart" {
			profile, err := Read(path, secretKey)
			if err != nil {
				return nil, err
			}
			if !smartUsable(profile, name) {
				return nil, fmt.Errorf("轻量 Smart 需要组内包含代理节点")
			}
		}
		if err := smart.Set(path, name, mode == "smart"); err != nil {
			return nil, err
		}
		ClearCache()
	}
	values, err := smart.Read(path)
	if err != nil {
		return nil, err
	}
	value := "config"
	if supported && values[name] {
		value = "smart"
	}
	return map[string]string{"mode": value, "supported": fmt.Sprint(supported)}, nil
}

func smartUsable(profile *Profile, name string) bool {
	for _, group := range profile.Groups {
		if group.Name != name {
			continue
		}
		for _, p := range group.Group.Proxies {
			if p.IsGroup {
				continue
			}
			switch strings.ToLower(p.Type) {
			case "direct", "compatible", "reject", "rejectdrop", "pass", "passrule", "rematch", "dns":
				continue
			}
			return true
		}
	}
	return false
}

// Counts use only source metadata; expand nodes only when enabling overrides.
func SmartAll(path, secretKey, mode string) (map[string]int, error) {
	raw, err := readProfileSource(path, secretKey)
	if err != nil {
		return nil, err
	}
	names := []string{}
	for _, group := range raw.ProxyGroup {
		if text(group, "type") == "url-test" {
			names = append(names, text(group, "name"))
		}
	}
	if mode != "" {
		if mode != "smart" && mode != "config" {
			return nil, fmt.Errorf("策略模式无效")
		}
		if mode == "smart" && len(names) > 0 {
			profile, err := Read(path, secretKey)
			if err != nil {
				return nil, err
			}
			for _, name := range names {
				if !smartUsable(profile, name) {
					return nil, fmt.Errorf("URLTest 组 %s 需包含实际代理节点", name)
				}
			}
		}
		if len(names) > 0 {
			if err := smart.SetBatch(path, names, mode == "smart"); err != nil {
				return nil, err
			}
			ClearCache()
		}
	}
	values, err := smart.Read(path)
	if err != nil {
		return nil, err
	}
	enabled := 0
	for _, name := range names {
		if values[name] {
			enabled++
		}
	}
	return map[string]int{"total": len(names), "enabled": enabled}, nil
}
