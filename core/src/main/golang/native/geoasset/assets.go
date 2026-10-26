package geoasset

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/metacubex/mihomo/component/geodata/router"
	mihomohttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/sevenzip"
	"github.com/oschwald/maxminddb-golang"
	"google.golang.org/protobuf/proto"
)

const MaxSize = 128 << 20

var updateLock sync.Mutex

type Asset struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	File      string `json:"file"`
	Size      int64  `json:"size"`
	UpdatedAt int64  `json:"updatedAt"`
}

func target(id string) (string, string, error) {
	switch id {
	case "mmdb":
		return "GeoIP 数据库", C.Path.MMDB(), nil
	case "geoip":
		return "GeoIP DAT", C.Path.GeoIP(), nil
	case "geosite":
		return "GeoSite 数据", C.Path.GeoSite(), nil
	case "asn":
		return "ASN 数据", C.Path.ASN(), nil
	case "mrs":
		return "MRS 规则集", C.Path.BundleMRS(), nil
	}
	return "", "", fmt.Errorf("不支持的 Geo 数据类型")
}

func List() []Asset {
	result := []Asset{}
	for _, id := range []string{"mmdb", "geosite", "asn", "geoip", "mrs"} {
		name, file, _ := target(id)
		item := Asset{ID: id, Name: name, File: filepath.Base(file)}
		if info, err := os.Stat(file); err == nil {
			item.Size, item.UpdatedAt = info.Size(), info.ModTime().UnixMilli()
		}
		result = append(result, item)
	}
	return result
}

func source(id, profile string, overrides []string) (string, error) {
	raw := config.DefaultRawConfig()
	if profile != "" {
		data, err := os.ReadFile(filepath.Join(profile, "config.yaml"))
		if err != nil {
			return "", fmt.Errorf("无法读取配置中的 Geo 来源")
		}
		raw, err = config.UnmarshalRawConfig(data)
		if err != nil {
			return "", fmt.Errorf("配置中的 Geo 来源无效")
		}
	}
	for _, override := range overrides {
		if err := json.Unmarshal([]byte(override), raw); err != nil {
			return "", err
		}
	}
	switch id {
	case "mmdb":
		return raw.GeoXUrl.Mmdb, nil
	case "geoip":
		return raw.GeoXUrl.GeoIp, nil
	case "geosite":
		return raw.GeoXUrl.GeoSite, nil
	case "asn":
		return raw.GeoXUrl.ASN, nil
	case "mrs":
		return "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z", nil
	}
	return "", fmt.Errorf("不支持的 Geo 数据类型")
}

// Validate uses the same file formats as the embedded kernel, without loading global caches.
func Validate(id, file string) error {
	info, err := os.Stat(file)
	if err != nil {
		return err
	}
	if info.Size() == 0 || info.Size() > MaxSize {
		return fmt.Errorf("Geo 文件为空或超过 128 MB")
	}
	switch id {
	case "mmdb", "asn":
		r, err := maxminddb.Open(file)
		if err != nil {
			return fmt.Errorf("不是有效的 MMDB 数据库")
		}
		defer r.Close()
		return nil
	case "geoip", "geosite":
		data, err := os.ReadFile(file)
		if err != nil {
			return err
		}
		if id == "geoip" {
			var list router.GeoIPList
			if proto.Unmarshal(data, &list) != nil || len(list.Entry) == 0 {
				return fmt.Errorf("不是有效的 GeoIP DAT")
			}
		} else {
			var list router.GeoSiteList
			if proto.Unmarshal(data, &list) != nil || len(list.Entry) == 0 {
				return fmt.Errorf("不是有效的 GeoSite DAT")
			}
		}
		return nil
	case "mrs":
		r, err := sevenzip.OpenReader(file)
		if err != nil {
			return fmt.Errorf("不是有效的 MRS 规则归档")
		}
		defer r.Close()
		if len(r.File) == 0 {
			return fmt.Errorf("MRS 规则归档为空")
		}
		return nil
	}
	return fmt.Errorf("不支持的 Geo 数据类型")
}

func install(id, staged string) error {
	_, destination, err := target(id)
	if err != nil {
		return err
	}
	if err = Validate(id, staged); err != nil {
		return err
	}
	// Atomic rename preserves the previous database on failure and any existing mmap reader.
	return os.Rename(staged, destination)
}

func Install(id, staged string) error {
	updateLock.Lock()
	defer updateLock.Unlock()
	return install(id, staged)
}

func Update(id, profile string, overrides []string) error {
	updateLock.Lock()
	defer updateLock.Unlock()
	_, destination, err := target(id)
	if err != nil {
		return err
	}
	url, err := source(id, profile, overrides)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 120*time.Second)
	defer cancel()
	response, err := mihomohttp.HttpRequest(ctx, url, "GET", nil, nil)
	if err != nil {
		return fmt.Errorf("Geo 下载失败，请检查网络或配置的更新来源")
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("Geo 下载失败（HTTP %d）", response.StatusCode)
	}
	f, err := os.CreateTemp(filepath.Dir(destination), ".geo-download-")
	if err != nil {
		return err
	}
	defer os.Remove(f.Name())
	n, copyErr := io.Copy(f, io.LimitReader(response.Body, MaxSize+1))
	if copyErr == nil {
		copyErr = f.Sync()
	}
	closeErr := f.Close()
	if copyErr != nil {
		return fmt.Errorf("Geo 下载未完成，原文件已保留")
	}
	if closeErr != nil {
		return closeErr
	}
	if n > MaxSize {
		return fmt.Errorf("Geo 文件超过 128 MB，原文件已保留")
	}
	return install(id, f.Name())
}
