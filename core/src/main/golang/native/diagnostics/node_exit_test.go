package diagnostics

import (
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"sync/atomic"
	"testing"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	P "github.com/metacubex/mihomo/adapter/provider"
	C "github.com/metacubex/mihomo/constant"
	CP "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/tunnel"
	LT "lees/native/tunnel"
)

func TestNodeExitUsesMemberWithoutChangingSelectionOrDirectFallback(t *testing.T) {
	var targetHits, proxyHits atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		targetHits.Add(1)
		io.WriteString(w, `{"ip":"203.0.113.8","isp":{"isp":"Fixture ISP","asn":"AS64500"}}`)
	}))
	defer target.Close()
	// A real HTTP CONNECT server proves the probe traverses the requested node.
	proxyServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		proxyHits.Add(1)
		if r.Method != http.MethodConnect {
			t.Errorf("expected CONNECT, got %s", r.Method)
			return
		}
		upstream, err := net.Dial("tcp", r.Host)
		if err != nil {
			t.Error(err)
			return
		}
		defer upstream.Close()
		client, rw, err := w.(http.Hijacker).Hijack()
		if err != nil {
			t.Error(err)
			return
		}
		defer client.Close()
		fmt.Fprint(rw, "HTTP/1.1 200 Connection established\r\n\r\n")
		rw.Flush()
		go io.Copy(upstream, rw)
		io.Copy(client, upstream)
	}))
	defer proxyServer.Close()
	host, port, _ := net.SplitHostPort(proxyServer.Listener.Addr().String())
	number, _ := strconv.Atoi(port)
	member, err := outbound.NewHttp(outbound.HttpOption{Name: "Member", Server: host, Port: number})
	if err != nil {
		t.Fatal(err)
	}
	direct := adapter.NewProxy(outbound.NewDirect())
	nodes := []C.Proxy{direct, adapter.NewProxy(member)}
	provider, err := P.NewCompatibleProvider("Fixture", nodes, P.NewHealthCheck(nodes, "", 0, 0, true, nil))
	if err != nil {
		t.Fatal(err)
	}
	defer provider.Close()
	group, err := outboundgroup.NewSelector(outboundgroup.GroupCommonOption{Name: "Group", Icon: "https://example.test/group.svg"}, outboundgroup.SelectorOption{DefaultSelected: "DIRECT"}, direct, []CP.ProxyProvider{provider})
	if err != nil {
		t.Fatal(err)
	}
	oldProxies, oldProviders := tunnel.Proxies(), tunnel.Providers()
	tunnel.UpdateProxies(map[string]C.Proxy{"Group": adapter.NewProxy(group)}, nil)
	defer tunnel.UpdateProxies(oldProxies, oldProviders)
	for _, summary := range []*LT.ProxyGroup{LT.QueryProxyGroupSummary("Group"), LT.QueryProxyGroup("Group", LT.Default, nil)} {
		if summary.Icon != "https://example.test/group.svg" {
			t.Fatal("running group lost icon")
		}
	}
	result, err := nodeExit(Request{Query: "Group", ID: "Member", URL: target.URL})
	if err != nil || result.IP != "203.0.113.8" || result.ISP != "Fixture ISP" || result.ASN != "AS64500" {
		t.Fatalf("exit result: %+v %v", result, err)
	}
	if group.Now() != "DIRECT" || proxyHits.Load() != 1 || targetHits.Load() != 1 {
		t.Fatal("probe changed selector or bypassed member")
	}
	proxyServer.Close()
	if _, err := nodeExit(Request{Query: "Group", ID: "Member", URL: target.URL}); err == nil {
		t.Fatal("unavailable node reported success")
	}
	if targetHits.Load() != 1 {
		t.Fatal("failed node fell back to direct")
	}
	for _, req := range []Request{{Query: "Missing", ID: "Member"}, {Query: "Group", ID: "Missing"}} {
		if _, err := nodeExit(req); err == nil {
			t.Fatal("accepted missing group/member")
		}
	}
}
