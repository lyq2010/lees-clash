// Per-profile selection modes; downloaded YAML is never rewritten.
package smart

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"

	"github.com/metacubex/mihomo/config"
)

const FileName = "smart.json"
const maxSize = 64 * 1024

var mutex sync.Mutex

func Read(directory string) (map[string]bool, error) {
	f, err := os.Open(filepath.Join(directory, FileName))
	if os.IsNotExist(err) {
		return map[string]bool{}, nil
	}
	if err != nil {
		return nil, err
	}
	defer f.Close()
	data, err := io.ReadAll(io.LimitReader(f, maxSize+1))
	if err != nil {
		return nil, err
	}
	if len(data) > maxSize {
		return nil, fmt.Errorf("策略覆盖文件过大")
	}
	var values map[string]bool
	if err := json.Unmarshal(data, &values); err != nil {
		return nil, fmt.Errorf("策略覆盖无效: %w", err)
	}
	if values == nil {
		values = map[string]bool{}
	}
	return values, nil
}

func Apply(raw *config.RawConfig, directory string) error {
	values, err := Read(directory)
	if err != nil {
		return err
	}
	for _, group := range raw.ProxyGroup {
		name, _ := group["name"].(string)
		if values[name] && group["type"] == "url-test" {
			group["type"] = "smart"
		}
	}
	return nil
}

func Set(directory, name string, enabled bool) error {
	return SetBatch(directory, []string{name}, enabled)
}

// Persist a bulk change once, so failures cannot leave a partially toggled profile.
func SetBatch(directory string, names []string, enabled bool) error {
	for _, name := range names {
		if name == "" || len(name) > 1024 {
			return fmt.Errorf("策略组名称无效")
		}
	}
	mutex.Lock()
	defer mutex.Unlock()
	values, err := Read(directory)
	if err != nil {
		return err
	}
	for _, name := range names {
		if enabled {
			values[name] = true
		} else {
			delete(values, name)
		}
	}
	if len(values) > 1024 {
		return fmt.Errorf("策略覆盖数量过多")
	}
	data, err := json.Marshal(values)
	if err != nil {
		return err
	}
	if len(data) > maxSize {
		return fmt.Errorf("策略覆盖文件过大")
	}
	f, err := os.CreateTemp(directory, ".smart-*")
	if err != nil {
		return err
	}
	defer os.Remove(f.Name())
	if _, err = f.Write(data); err != nil {
		f.Close()
		return err
	}
	if err = f.Close(); err != nil {
		return err
	}
	return os.Rename(f.Name(), filepath.Join(directory, FileName))
}
