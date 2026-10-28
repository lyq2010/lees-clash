package tunnel

import (
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	T "github.com/metacubex/mihomo/tunnel"
)

type policyTestProxy struct {
	C.Proxy
	name    string
	history map[string]C.ProxyState
}

func (p *policyTestProxy) Name() string                                 { return p.name }
func (p *policyTestProxy) Adapter() C.ProxyAdapter                      { return nil }
func (p *policyTestProxy) Type() C.AdapterType                          { return C.Shadowsocks }
func (p *policyTestProxy) ExtraDelayHistories() map[string]C.ProxyState { return p.history }

type policyTestGroup struct {
	*outboundgroup.GroupBase
	now   string
	nodes []C.Proxy
}

func (g *policyTestGroup) Now() string                  { return g.now }
func (g *policyTestGroup) Proxies() []C.Proxy           { return g.nodes }
func (g *policyTestGroup) Providers() []P.ProxyProvider { return nil }

func TestPolicyDelayDistinguishesUntestedFailureAndLatestURL(t *testing.T) {
	node := &policyTestProxy{name: "Node"}
	if got := policyDelay(node); got != 0 {
		t.Fatalf("untested = %d", got)
	}
	first := time.Unix(1, 0)
	node.history = map[string]C.ProxyState{
		"https://first.test":  {History: []C.DelayHistory{{Time: first, Delay: 120}}},
		"https://second.test": {History: []C.DelayHistory{{Time: first.Add(time.Second), Delay: 0}}},
	}
	if got := policyDelay(node); got != 65535 {
		t.Fatalf("latest failed test = %d", got)
	}
	node.history["https://second.test"] = C.ProxyState{History: []C.DelayHistory{{Time: first.Add(2 * time.Second), Delay: 280}}}
	if got := policyDelay(node); got != 280 {
		t.Fatalf("recovered test = %d", got)
	}
	if got := convertProxies([]C.Proxy{node}, nil)[0].Delay; got != 280 {
		t.Fatalf("node payload = %d", got)
	}
}

func TestPolicySummaryFollowsSelectionAndStaysCompact(t *testing.T) {
	previous, providers := T.Proxies(), T.Providers()
	defer T.UpdateProxies(previous, providers)
	node := &policyTestProxy{name: "Node", history: map[string]C.ProxyState{
		"https://test": {History: []C.DelayHistory{{Time: time.Now(), Delay: 180}}},
	}}
	inner := &policyTestGroup{GroupBase: outboundgroup.NewGroupBase(outboundgroup.GroupBaseOption{Name: "Inner", Type: C.Selector}), now: "Node", nodes: []C.Proxy{node}}
	innerProxy := adapter.NewProxy(inner)
	outer := &policyTestGroup{GroupBase: outboundgroup.NewGroupBase(outboundgroup.GroupBaseOption{Name: "Outer", Type: C.Selector}), now: "Inner", nodes: []C.Proxy{innerProxy}}
	outerProxy := adapter.NewProxy(outer)
	T.UpdateProxies(map[string]C.Proxy{"Outer": outerProxy}, nil)
	for _, group := range []*ProxyGroup{QueryProxyGroupSummary("Outer"), QueryProxyGroup("Outer", Default, nil)} {
		if group.Delay != 180 || group.Now != "Inner" {
			t.Fatalf("nested selection = %+v", group)
		}
	}
	if len(QueryProxyGroupSummary("Outer").Proxies) != 0 {
		t.Fatal("summary expanded all nodes")
	}
	inner.now = "Missing"
	if got := policyDelay(outerProxy); got != 0 {
		t.Fatalf("missing node = %d", got)
	}
	inner.now, inner.nodes = "Outer", []C.Proxy{outerProxy}
	if got := policyDelay(outerProxy); got != 0 {
		t.Fatalf("cycle = %d", got)
	}
}
