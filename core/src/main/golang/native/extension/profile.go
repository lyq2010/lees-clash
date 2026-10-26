// Per-profile additions are stored separately from downloaded subscription YAML.
package extension

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/metacubex/mihomo/config"
)

const FileName = "extension.json"
const MaxSize = 192 * 1024

type Profile struct {
	Rules    []string         `json:"rules"`
	Proxies  []map[string]any `json:"proxies"`
	Groups   []map[string]any `json:"proxy-groups"`
	Override json.RawMessage  `json:"override"`
	Script   string           `json:"script"`
}

func merge(original, additions []map[string]any) ([]map[string]any, error) {
	result := append([]map[string]any{}, original...)
	seen := map[string]bool{}
	for _, item := range additions {
		name, _ := item["name"].(string)
		if name == "" || seen[name] {
			return nil, fmt.Errorf("自定义项目名称为空或重复")
		}
		seen[name] = true
		found := false
		for i, current := range result {
			if current["name"] == name {
				result[i] = item
				found = true
				break
			}
		}
		if !found {
			result = append(result, item)
		}
	}
	return result, nil
}

func Apply(raw *config.RawConfig, directory string) error {
	file := filepath.Join(directory, FileName)
	info, err := os.Stat(file)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	if info.Size() > MaxSize {
		return fmt.Errorf("配置扩展超过 192 KB")
	}
	data, err := os.ReadFile(file)
	if err != nil {
		return err
	}
	var additions Profile
	if err = json.Unmarshal(data, &additions); err != nil {
		return fmt.Errorf("配置扩展 JSON 无效: %w", err)
	}
	if len(additions.Rules) > 5000 || len(additions.Proxies) > 1000 || len(additions.Groups) > 500 {
		return fmt.Errorf("配置扩展项目数量过多")
	}
	if additions.Script != "" {
		if err := transform(raw, additions.Script); err != nil {
			return err
		}
	}
	if len(additions.Override) > 0 {
		var values map[string]json.RawMessage
		if err := json.Unmarshal(additions.Override, &values); err != nil {
			return fmt.Errorf("高级扩展必须是 JSON 对象")
		}
		for _, key := range []string{"rules", "proxies", "proxy-groups"} {
			if _, exists := values[key]; exists {
				return fmt.Errorf("规则、代理和分组请使用对应列表编辑")
			}
		}
		if err := json.Unmarshal(additions.Override, raw); err != nil {
			return err
		}
	}
	raw.Rule = append(append([]string{}, additions.Rules...), raw.Rule...)
	raw.Proxy, err = merge(raw.Proxy, additions.Proxies)
	if err != nil {
		return err
	}
	raw.ProxyGroup, err = merge(raw.ProxyGroup, additions.Groups)
	return err
}
