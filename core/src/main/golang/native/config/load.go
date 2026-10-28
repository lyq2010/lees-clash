package config

import (
	"crypto/sha256"
	"fmt"
	"io"
	"os"
	P "path"
	"runtime/debug"
	"strings"

	"lees/native/app"
	"lees/native/preview"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/log"
)

func logDns(cfg *config.RawConfig) {
	bytes, err := yaml.Marshal(&cfg.DNS)
	if err != nil {
		log.Warnln("Marshal dns: %s", err.Error())

		return
	}

	log.Infoln("dns:")

	for _, line := range strings.Split(string(bytes), "\n") {
		log.Infoln("  %s", line)
	}
}

func UnmarshalAndPatch(profilePath string) (*config.RawConfig, error) {
	configPath := P.Join(profilePath, "config.yaml")

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

func Parse(rawConfig *config.RawConfig) (*config.Config, error) {
	cfg, err := config.ParseRawConfig(rawConfig)
	if err != nil {
		return nil, err
	}

	return cfg, nil
}

func Load(path string) error {
	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	logDns(rawCfg)

	cfg, err := Parse(rawCfg)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	// like hub.Parse()
	hub.ApplyConfig(cfg)
	if err := restoreSmartLearning(path, cfg); err != nil {
		log.Warnln("Restore Smart state: %v", err)
	}

	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

	preview.ClearCache()
	debug.FreeOSMemory()

	return nil
}

func LoadDefault() {
	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	hub.ApplyConfig(cfg)
	_ = outboundgroup.ConfigureSmartPersistence("", "", nil)
}

func restoreSmartLearning(directory string, cfg *config.Config) error {
	f, err := os.Open(P.Join(directory, "config.yaml"))
	if err != nil {
		_ = outboundgroup.ConfigureSmartPersistence("", "", nil)
		return err
	}
	defer f.Close()
	hash := sha256.New()
	if _, err = io.Copy(hash, f); err != nil {
		_ = outboundgroup.ConfigureSmartPersistence("", "", nil)
		return err
	}
	return outboundgroup.ConfigureSmartPersistence(P.Join(directory, "smart-state.jsonl"), fmt.Sprintf("%x", hash.Sum(nil)), cfg.Proxies)
}
