package extension

import (
	"encoding/json"
	"fmt"
	"lees/native/script"

	"github.com/metacubex/mihomo/config"
)

func transform(raw *config.RawConfig, source string) error {
	input, err := json.Marshal(raw)
	if err != nil {
		return err
	}
	var values map[string]json.RawMessage
	if err := json.Unmarshal(input, &values); err != nil {
		return err
	}
	// The upstream Go JSON tag is singular; scripts use the public YAML key.
	values["rules"] = values["rule"]
	delete(values, "rule")
	input, err = json.Marshal(values)
	if err != nil {
		return err
	}
	output, err := script.Run(source, input)
	if err != nil {
		return err
	}
	values = nil
	if err := json.Unmarshal(output, &values); err != nil {
		return err
	}
	delete(values, "rule")
	if rules, exists := values["rules"]; exists {
		values["rule"] = rules
	}
	delete(values, "rules")
	output, err = json.Marshal(values)
	if err != nil {
		return err
	}
	transformed := config.DefaultRawConfig()
	if err := json.Unmarshal(output, transformed); err != nil {
		return fmt.Errorf("脚本返回了无效配置: %w", err)
	}
	*raw = *transformed
	return nil
}
