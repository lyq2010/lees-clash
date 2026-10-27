// Lee's Clash diagnostics. Added 2026-10-26; upstream licenses remain applicable.
package diagnostics

import (
	"context"
	"encoding/json"
	"fmt"
	"lees/native/geoasset"
	"lees/native/preview"
	LT "lees/native/tunnel"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
	"github.com/miekg/dns"
)

const maxPage = 50
const maxResponse = 192 * 1024

type Request struct {
	URL        string   `json:"url"`
	Direct     bool     `json:"direct"`
	Query      string   `json:"query"`
	Offset     int      `json:"offset"`
	Limit      int      `json:"limit"`
	ID         string   `json:"id"`
	Domain     string   `json:"domain"`
	RecordType string   `json:"recordType"`
	Path       string   `json:"path"`
	SecretKey  string   `json:"secretKey"`
	Overrides  []string `json:"overrides"`
}

type Page[T any] struct {
	Items      []T `json:"items"`
	Total      int `json:"total"`
	NextOffset int `json:"nextOffset"`
}

type Connection struct {
	ID          string   `json:"id"`
	Host        string   `json:"host"`
	Source      string   `json:"source"`
	Destination string   `json:"destination"`
	Network     string   `json:"network"`
	Process     string   `json:"process"`
	UID         uint32   `json:"uid"`
	Upload      int64    `json:"upload"`
	Download    int64    `json:"download"`
	Start       string   `json:"start"`
	Chains      []string `json:"chains"`
	Rule        string   `json:"rule"`
	RulePayload string   `json:"rulePayload"`
}

type Rule struct {
	Index   int    `json:"index"`
	Type    string `json:"type"`
	Payload string `json:"payload"`
	Proxy   string `json:"proxy"`
}

func matches(query string, fields ...string) bool {
	query = strings.ToLower(strings.TrimSpace(query))
	for _, field := range fields {
		if strings.Contains(strings.ToLower(field), query) {
			return true
		}
	}
	return query == ""
}

func paginate[T any](items []T, offset, limit int) Page[T] {
	if offset < 0 {
		offset = 0
	}
	if offset > len(items) {
		offset = len(items)
	}
	if limit <= 0 || limit > maxPage {
		limit = maxPage
	}
	end := offset + limit
	if end > len(items) {
		end = len(items)
	}
	next := -1
	if end < len(items) {
		next = end
	}
	return Page[T]{Items: items[offset:end], Total: len(items), NextOffset: next}
}

func connection(info *statistic.TrackerInfo) Connection {
	c := Connection{ID: info.UUID.String(), Upload: info.UploadTotal.Load(), Download: info.DownloadTotal.Load(), Start: info.Start.UTC().Format(time.RFC3339Nano), Chains: []string(info.Chain), Rule: info.Rule, RulePayload: info.RulePayload}
	if c.Chains == nil {
		c.Chains = []string{}
	}
	if m := info.Metadata; m != nil {
		c.Host, c.Network, c.Process, c.UID = m.Host, m.NetWork.String(), m.Process, m.Uid
		c.Source = fmt.Sprintf("%s:%d", m.SrcIP, m.SrcPort)
		c.Destination = fmt.Sprintf("%s:%d", m.DstIP, m.DstPort)
	}
	return c
}

func Query(operation, payload string) string {
	var request Request
	if len(payload) > 8192 {
		return failure("查询参数过长")
	}
	if err := json.Unmarshal([]byte(payload), &request); err != nil {
		return failure("无效的诊断参数")
	}
	var result any
	switch operation {
	case "groupSummary":
		value := LT.QueryProxyGroupSummary(request.ID)
		if value == nil {
			return failure("没有该策略组")
		}
		result = value
	case "networkCheck":
		value, err := probe(request)
		if err != nil {
			return failure(err.Error())
		}
		result = value
	case "runtime":
		var memory runtime.MemStats
		runtime.ReadMemStats(&memory)
		values := map[string]any{"heapAlloc": memory.HeapAlloc, "heapInuse": memory.HeapInuse, "heapReleased": memory.HeapReleased, "sys": memory.Sys, "goroutines": runtime.NumGoroutine()}
		if request.Query == "stacks" {
			buffer := make([]byte, 64*1024)
			values["stacks"] = string(buffer[:runtime.Stack(buffer, true)])
		}
		result = values
	case "geoStatus":
		result = geoasset.List()
	case "geoUpdate":
		if err := geoasset.Update(request.ID, request.Path, request.Overrides); err != nil {
			return failure(err.Error())
		}
		result = map[string]bool{"ok": true}
	case "geoInstall":
		if err := geoasset.Install(request.ID, request.Path); err != nil {
			return failure(err.Error())
		}
		result = map[string]bool{"ok": true}
	case "geoValidate":
		if err := geoasset.Validate(request.ID, request.Path); err != nil {
			return failure(err.Error())
		}
		result = map[string]bool{"ok": true}
	case "profileGroupNames", "profileGroup", "profileGroupSummary", "profileRules", "profileMode", "profileProviders":
		profile, err := preview.Read(request.Path, request.SecretKey)
		if err != nil {
			return failure(err.Error())
		}
		switch operation {
		case "profileProviders":
			result = profile.Providers
		case "profileMode":
			result = map[string]string{"mode": profile.Mode}
		case "profileGroupNames":
			names := []string{}
			for _, group := range profile.Groups {
				if request.Query != "select" || group.Group.Type == "select" {
					names = append(names, group.Name)
				}
			}
			result = names
		case "profileGroup", "profileGroupSummary":
			for _, group := range profile.Groups {
				if group.Name == request.ID {
					if operation == "profileGroupSummary" {
						copy := *group.Group
						if copy.Type == "select" {
							for _, proxy := range copy.Proxies {
								if proxy.Name == request.Query {
									copy.Now = request.Query
									break
								}
							}
						}
						copy.Proxies = []*LT.Proxy{}
						result = &copy
					} else {
						result = group.Group
					}
					break
				}
			}
			if result == nil {
				return failure("所选配置中没有该策略组")
			}
		case "profileRules":
			items := []preview.Rule{}
			for _, rule := range profile.Rules {
				if matches(request.Query, rule.Type, rule.Payload, rule.Proxy) {
					items = append(items, rule)
				}
			}
			result = paginate(items, request.Offset, request.Limit)
		}
	case "connections":
		items := make([]Connection, 0)
		statistic.DefaultManager.Range(func(tracker statistic.Tracker) bool {
			c := connection(tracker.Info())
			if matches(request.Query, c.Host, c.Destination, c.Process, c.Network, c.Rule, c.RulePayload, strings.Join(c.Chains, " ")) {
				items = append(items, c)
			}
			return true
		})
		sort.Slice(items, func(i, j int) bool {
			if items[i].Start == items[j].Start {
				return items[i].ID < items[j].ID
			}
			return items[i].Start > items[j].Start
		})
		result = paginate(items, request.Offset, request.Limit)
	case "connection":
		tracker := statistic.DefaultManager.Get(request.ID)
		if tracker == nil {
			return failure("连接已结束")
		}
		result = connection(tracker.Info())
	case "close":
		if tracker := statistic.DefaultManager.Get(request.ID); tracker != nil {
			if err := tracker.Close(); err != nil {
				return failure(err.Error())
			}
		}
		result = map[string]bool{"success": true}
	case "closeAll":
		var closeError error
		statistic.DefaultManager.Range(func(tracker statistic.Tracker) bool {
			if err := tracker.Close(); err != nil {
				closeError = err
			}
			return true
		})
		if closeError != nil {
			return failure(closeError.Error())
		}
		result = map[string]bool{"success": true}
	case "rules":
		items := make([]Rule, 0)
		for i, rule := range tunnel.Rules() {
			if matches(request.Query, rule.RuleType().String(), rule.Payload(), rule.Adapter()) {
				items = append(items, Rule{i, rule.RuleType().String(), rule.Payload(), rule.Adapter()})
			}
		}
		result = paginate(items, request.Offset, request.Limit)
	case "traffic":
		up, down := statistic.DefaultManager.Now()
		upTotal, downTotal := statistic.DefaultManager.Total()
		result = map[string]int64{"uploadRate": up, "downloadRate": down, "uploadTotal": upTotal, "downloadTotal": downTotal}
	case "dns":
		return queryDNS(request)
	default:
		return failure("未知诊断操作")
	}
	encoded, err := json.Marshal(result)
	if err != nil {
		return failure(err.Error())
	}
	if len(encoded) > maxResponse && operation != "profileGroup" {
		return failure("结果过大，请缩小查询范围或分页数量")
	}
	return string(encoded)
}

func failure(message string) string {
	encoded, _ := json.Marshal(map[string]string{"error": message})
	return string(encoded)
}

func queryDNS(request Request) string {
	if resolver.DefaultResolver == nil {
		return failure("当前配置未启用 DNS")
	}
	domain := strings.TrimSpace(request.Domain)
	if domain == "" || len(domain) > 253 {
		return failure("请输入有效域名")
	}
	if _, valid := dns.IsDomainName(dns.Fqdn(domain)); !valid {
		return failure("域名格式无效")
	}
	kind := strings.ToUpper(request.RecordType)
	if kind == "" {
		kind = "A"
	}
	qType, valid := dns.StringToType[kind]
	if !valid {
		return failure("不支持的记录类型")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	message := new(dns.Msg)
	message.SetQuestion(dns.Fqdn(domain), qType)
	started := time.Now()
	response, err := resolver.DefaultResolver.ExchangeContext(ctx, message)
	if err != nil {
		return failure(err.Error())
	}
	type Answer struct {
		Name string `json:"name"`
		Type string `json:"type"`
		TTL  uint32 `json:"ttl"`
		Data string `json:"data"`
	}
	answers := make([]Answer, 0, len(response.Answer))
	for _, rr := range response.Answer {
		header := rr.Header()
		answers = append(answers, Answer{header.Name, dns.TypeToString[header.Rrtype], header.Ttl, strings.TrimSpace(strings.TrimPrefix(rr.String(), header.String()))})
	}
	encoded, _ := json.Marshal(struct {
		Status  string   `json:"status"`
		Elapsed int64    `json:"elapsedMs"`
		Answers []Answer `json:"answers"`
	}{dns.RcodeToString[response.Rcode], time.Since(started).Milliseconds(), answers})
	if len(encoded) > maxResponse {
		return failure("DNS 响应过大")
	}
	return string(encoded)
}
