package tunnel

import (
	"context"
	"sync/atomic"
	"testing"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	T "github.com/metacubex/mihomo/tunnel"
)

type measuredSmart struct {
	*outboundgroup.GroupBase
	calls atomic.Int32
}

func (g *measuredSmart) Now() string                  { return "" }
func (g *measuredSmart) Proxies() []C.Proxy           { return nil }
func (g *measuredSmart) Providers() []P.ProxyProvider { return nil }
func (g *measuredSmart) URLTest(ctx context.Context, _ string, _ utils.IntRanges[uint16]) (map[string]uint16, error) {
	if _, bounded := ctx.Deadline(); !bounded {
		panic("manual probe requires an overall timeout")
	}
	g.calls.Add(1)
	return map[string]uint16{"Node": 10}, nil
}

func TestSmartManualCheckUsesGroupBudget(t *testing.T) {
	previous, providers := T.Proxies(), T.Providers()
	group := &measuredSmart{GroupBase: outboundgroup.NewGroupBase(outboundgroup.GroupBaseOption{Name: "Smart", Type: C.Smart})}
	T.UpdateProxies(map[string]C.Proxy{"Smart": adapter.NewProxy(group)}, nil)
	defer T.UpdateProxies(previous, providers)
	HealthCheck("Smart")
	if group.calls.Load() != 1 {
		t.Fatal("Smart was bypassed by the provider-level manual test")
	}
}
