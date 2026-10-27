package diagnostics

import (
	"fmt"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	mihomohttp "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/component/proxydialer"
	"github.com/metacubex/mihomo/tunnel"
)

func nodeExit(request Request) (*NetworkProbe, error) {
	p := tunnel.Proxies()[request.Query]
	if p == nil {
		return nil, fmt.Errorf("没有该策略组")
	}
	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return nil, fmt.Errorf("没有该策略组")
	}
	for _, node := range g.Proxies() {
		if node.Name() != request.ID {
			continue
		}
		if _, isGroup := node.Adapter().(outboundgroup.ProxyGroup); isGroup {
			return nil, fmt.Errorf("请选择具体节点检测落地信息")
		}
		if request.URL == "" {
			request.URL = "https://api.ipquery.io/?format=json"
		}
		request.RecordType, request.Direct = "ip", false
		// Dial the actual member (including provider nodes). Never switch a selector,
		// apply routing rules, or fall back to a direct connection on failure.
		return probe(request, mihomohttp.WithDialer(proxydialer.New(node.Adapter(), false)))
	}
	return nil, fmt.Errorf("该节点已不在策略组中，请刷新后重试")
}
