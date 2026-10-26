package diagnostics

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"time"

	mihomohttp "github.com/metacubex/mihomo/component/http"
)

type NetworkProbe struct {
	At      int64          `json:"at"`
	Host    string         `json:"host"`
	Direct  bool           `json:"direct"`
	Status  int            `json:"status"`
	Elapsed int64          `json:"elapsedMs"`
	Bytes   int            `json:"sampleBytes"`
	IP      string         `json:"ip,omitempty"`
	Country string         `json:"country,omitempty"`
	City    string         `json:"city,omitempty"`
	ISP     string         `json:"isp,omitempty"`
	ASN     string         `json:"asn,omitempty"`
	Risk    map[string]any `json:"risk,omitempty"`
}

func probe(request Request) (*NetworkProbe, error) {
	target, err := url.Parse(request.URL)
	if err != nil || len(request.URL) > 2048 || target.User != nil || target.Hostname() == "" || (target.Scheme != "https" && target.Scheme != "http") {
		return nil, fmt.Errorf("请输入有效 HTTP 或 HTTPS 地址")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	options := []mihomohttp.Option{}
	if request.Direct {
		options = append(options, mihomohttp.WithSpecialProxy("DIRECT"))
	}
	start := time.Now()
	response, err := mihomohttp.HttpRequest(ctx, request.URL, http.MethodGet, http.Header{"User-Agent": {"Lees-Clash/0.1"}}, nil, options...)
	if err != nil {
		return nil, fmt.Errorf("网络请求失败: %T", err)
	}
	defer response.Body.Close()
	data, err := io.ReadAll(io.LimitReader(response.Body, 64*1024+1))
	if err != nil {
		return nil, fmt.Errorf("读取网络响应失败")
	}
	result := &NetworkProbe{At: time.Now().UnixMilli(), Host: target.Hostname(), Direct: request.Direct, Status: response.StatusCode, Elapsed: time.Since(start).Milliseconds(), Bytes: len(data)}
	if request.RecordType == "ip" {
		if response.StatusCode < 200 || response.StatusCode >= 300 {
			return nil, fmt.Errorf("IP 服务返回 HTTP %d", response.StatusCode)
		}
		if len(data) > 64*1024 {
			return nil, fmt.Errorf("IP 服务响应过大")
		}
		var info struct {
			IP  string `json:"ip"`
			ISP struct {
				ISP string `json:"isp"`
				ASN string `json:"asn"`
			} `json:"isp"`
			Location struct {
				Country string `json:"country"`
				City    string `json:"city"`
			} `json:"location"`
			Risk map[string]any `json:"risk"`
		}
		if json.Unmarshal(data, &info) != nil || net.ParseIP(info.IP) == nil {
			return nil, fmt.Errorf("IP 服务响应格式无效")
		}
		result.IP, result.Country, result.City, result.ISP, result.ASN = info.IP, info.Location.Country, info.Location.City, info.ISP.ISP, info.ISP.ASN
		result.Risk = map[string]any{}
		for _, key := range []string{"is_mobile", "is_vpn", "is_tor", "is_proxy", "is_datacenter"} {
			if value, ok := info.Risk[key].(bool); ok {
				result.Risk[key] = value
			}
		}
		if score, ok := info.Risk["risk_score"].(float64); ok && score >= 0 && score <= 100 {
			result.Risk["risk_score"] = score
		}
	}
	return result, nil
}
