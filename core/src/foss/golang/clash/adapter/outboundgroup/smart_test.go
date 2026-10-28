package outboundgroup

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outbound"
	AP "github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/common/buf"
	"github.com/metacubex/mihomo/common/utils"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/stretchr/testify/require"
)

type smartTestNode struct {
	*outbound.Base
	dial   func(context.Context) (C.Conn, error)
	packet func(context.Context) (C.PacketConn, error)
	calls  atomic.Int32
}

func (p *smartTestNode) DialContext(ctx context.Context, _ *C.Metadata) (C.Conn, error) {
	p.calls.Add(1)
	return p.dial(ctx)
}
func (p *smartTestNode) ListenPacketContext(ctx context.Context, _ *C.Metadata) (C.PacketConn, error) {
	p.calls.Add(1)
	return p.packet(ctx)
}

func smartFixture(t testing.TB, count int) (*Smart, []*smartTestNode) {
	t.Helper()
	var proxies []C.Proxy
	var nodes []*smartTestNode
	for i := 0; i < count; i++ {
		n := &smartTestNode{Base: outbound.NewBase(outbound.BaseOption{Name: fmt.Sprintf("Node-%d", i), Type: C.Socks5, UDP: true})}
		nodes = append(nodes, n)
		proxies = append(proxies, adapter.NewProxy(n))
	}
	hc := AP.NewHealthCheck(proxies, C.DefaultTestURL, 0, 0, true, nil)
	provider, err := AP.NewCompatibleProvider("Smart", proxies, hc)
	require.NoError(t, err)
	s, err := NewSmart(GroupCommonOption{Name: "Smart"}, adapter.NewProxy(outbound.NewDirect()), []P.ProxyProvider{provider})
	require.NoError(t, err)
	s.probeAt = time.Now() // Unit tests explicitly control probes.
	t.Cleanup(func() { s.Close() })
	return s, nodes
}

func TestSmartScoresStickinessFailureAndRecovery(t *testing.T) {
	s, _ := smartFixture(t, 3)
	proxies := s.Proxies()
	s.observe("Node-0", "", false, 30*time.Millisecond, true)
	s.observe("Node-1", "", false, 45*time.Millisecond, true)
	s.observe("Node-2", "", false, time.Second, true)
	s.observe("Node-1", "example.com", false, 45*time.Millisecond, true)
	for i := 0; i < 20; i++ {
		require.Equal(t, "Node-1", s.candidates(proxies, "example.com", false)[0].Name())
	}
	s.observe("Node-1", "example.com", false, 0, false)
	require.Equal(t, "Node-0", s.candidates(proxies, "example.com", false)[0].Name())
	s.observe("Node-1", "example.com", false, time.Millisecond, true)
	require.Equal(t, "Node-1", s.candidates(proxies, "example.com", false)[0].Name())
	// Transport-specific evidence: TCP failure must not mark UDP failed.
	s.observe("Node-1", "example.com", false, 0, false)
	s.observe("Node-1", "example.com", true, time.Millisecond, true)
	require.Equal(t, "Node-1", s.candidates(proxies, "example.com", true)[0].Name())
}

func TestSmartDialFailoverTimeoutAndCancellation(t *testing.T) {
	s, nodes := smartFixture(t, 5)
	s.timeout = 20 * time.Millisecond
	for _, n := range nodes {
		n.dial = func(ctx context.Context) (C.Conn, error) { <-ctx.Done(); return nil, ctx.Err() }
	}
	start := time.Now()
	_, err := s.DialContext(context.Background(), request("", "example.com"))
	require.Error(t, err)
	require.Less(t, time.Since(start), 250*time.Millisecond)
	calls := int32(0)
	for _, n := range nodes {
		calls += n.calls.Load()
	}
	require.EqualValues(t, 3, calls, "Retries are bounded even with hundreds of nodes")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err = s.DialContext(ctx, request("", "example.com"))
	require.ErrorIs(t, err, context.Canceled)
	for _, n := range nodes {
		n.calls.Store(0)
	}
	ranked := s.candidates(s.Proxies(), "example.org", false)
	ranked[0].Adapter().(*smartTestNode).dial = func(context.Context) (C.Conn, error) { return nil, errors.New("down") }
	client, server := net.Pipe()
	defer server.Close()
	ranked[1].Adapter().(*smartTestNode).dial = func(context.Context) (C.Conn, error) { return outbound.NewConn(client, nodes[0]), nil }
	c, err := s.DialContext(context.Background(), request("", "example.org"))
	require.NoError(t, err)
	defer c.Close()
	require.Contains(t, c.Chains(), s.Name())
}

func TestSmartObservesBothCopyPathsAndRestoresDeadline(t *testing.T) {
	for _, buffered := range []bool{false, true} {
		t.Run(fmt.Sprint(buffered), func(t *testing.T) {
			s, nodes := smartFixture(t, 1)
			left, right := net.Pipe()
			defer right.Close()
			c := newSmartConn(outbound.NewConn(left, nodes[0]), s, "Node-0", "example.com", time.Now()).(*smartConn)
			defer c.Close()
			deadline := time.Now().Add(time.Minute)
			require.NoError(t, c.SetReadDeadline(deadline))
			go func() { data := make([]byte, 1); right.Read(data); right.Write([]byte("x")) }()
			if buffered {
				require.NoError(t, c.WriteBuffer(buf.With([]byte("q"))))
				buffer := buf.New()
				defer buffer.Release()
				require.NoError(t, c.ReadBuffer(buffer))
			} else {
				_, err := c.Write([]byte("q"))
				require.NoError(t, err)
				_, err = c.Read(make([]byte, 1))
				require.NoError(t, err)
			}
			require.True(t, c.sampled.Load())
			require.False(t, c.armed)
			require.Equal(t, deadline, c.readDeadline)
			require.True(t, c.ReaderReplaceable())
			smartMemory.Lock()
			state, ok := smartMemory.nodes.Get(smartKey{s.id, "Node-0", false})
			smartMemory.Unlock()
			require.True(t, ok)
			require.Zero(t, state.fails)
		})
	}
}

func TestSmartFirstResponseTimeoutDemotesWithoutReplay(t *testing.T) {
	s, nodes := smartFixture(t, 1)
	s.timeout = 25 * time.Millisecond
	left, right := net.Pipe()
	defer right.Close()
	c := newSmartConn(outbound.NewConn(left, nodes[0]), s, "Node-0", "example.com", time.Now()).(*smartConn)
	defer c.Close()
	go func() { _, _ = right.Read(make([]byte, 64)) }()
	_, err := c.Write([]byte("POST /charge"))
	require.NoError(t, err)
	_, err = c.Read(make([]byte, 64))
	require.Error(t, err)
	smartMemory.Lock()
	state, ok := smartMemory.nodes.Get(smartKey{s.id, "Node-0", false})
	smartMemory.Unlock()
	require.True(t, ok)
	require.EqualValues(t, 1, state.fails)
	require.Zero(t, nodes[0].calls.Load(), "Never redial/replay after accepting business data")
}

func TestSmartGlobalBoundsIdleAndClose(t *testing.T) {
	before := runtime.NumGoroutine()
	s, _ := smartFixture(t, 500)
	for i := 0; i < 20000; i++ {
		s.observe(fmt.Sprintf("node-%d", i), fmt.Sprintf("site-%d.example", i), false, time.Millisecond, true)
	}
	require.LessOrEqual(t, len(smartMemory.nodes.Keys()), smartNodeLimit)
	require.LessOrEqual(t, len(smartMemory.sites.Keys()), smartSiteLimit)
	require.LessOrEqual(t, runtime.NumGoroutine(), before+1, "Creating groups and storing samples must not start workers")
	s.Close()
	for _, key := range smartMemory.nodes.Keys() {
		require.NotEqual(t, s.id, key.group)
	}
	for _, key := range smartMemory.sites.Keys() {
		require.NotEqual(t, s.id, key.group)
	}
}

func TestSmartConcurrentSelectionAndObservation(t *testing.T) {
	s, _ := smartFixture(t, 20)
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 500; j++ {
				s.observe("Node-0", "example.com", false, time.Millisecond, true)
				_ = s.candidates(s.Proxies(), "example.com", false)
			}
		}()
	}
	wg.Wait()
}

func BenchmarkSmartSelection500Nodes(b *testing.B) {
	s, _ := smartFixture(b, 500)
	proxies := s.Proxies()
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		s.candidates(proxies, "example.com", false)
	}
}

func TestSmartUDPResponseAndSilentFailure(t *testing.T) {
	for _, respond := range []bool{true, false} {
		t.Run(fmt.Sprint(respond), func(t *testing.T) {
			s, nodes := smartFixture(t, 2)
			s.timeout = 25 * time.Millisecond
			server, err := net.ListenPacket("udp", "127.0.0.1:0")
			require.NoError(t, err)
			defer server.Close()
			client, err := net.ListenPacket("udp", "127.0.0.1:0")
			require.NoError(t, err)
			c := &smartPacketConn{PacketConn: outbound.NewPacketConn(client, nodes[0]), group: s, node: "Node-0", site: "example.com"}
			defer c.Close()
			if respond {
				go func() {
					b := make([]byte, 64)
					n, addr, e := server.ReadFrom(b)
					if e == nil {
						server.WriteTo(b[:n], addr)
					}
				}()
			}
			_, err = c.WriteTo([]byte("hello"), server.LocalAddr())
			require.NoError(t, err)
			data, put, _, err := c.WaitReadFrom()
			if put != nil {
				defer put()
			}
			if respond {
				require.NoError(t, err)
				require.Equal(t, "hello", string(data))
			} else {
				require.Error(t, err)
			}
			smartMemory.Lock()
			state, ok := smartMemory.nodes.Get(smartKey{s.id, "Node-0", true})
			smartMemory.Unlock()
			require.True(t, ok)
			if respond {
				require.Zero(t, state.fails)
			} else {
				require.EqualValues(t, 1, state.fails)
				require.Equal(t, "Node-1", s.candidates(s.Proxies(), "example.com", true)[0].Name())
			}
		})
	}
}

func TestSmartProbeBudgetAndCancellation(t *testing.T) {
	var groups []*Smart
	for i := 0; i < 8; i++ {
		s, nodes := smartFixture(t, 8)
		groups = append(groups, s)
		for _, n := range nodes {
			n.dial = func(ctx context.Context) (C.Conn, error) { <-ctx.Done(); return nil, ctx.Err() }
		}
		s.probeAt = time.Time{}
		s.maybeProbe(s.Proxies())
	}
	require.LessOrEqual(t, len(smartProbeSlots), 2)
	for _, s := range groups {
		s.Close()
	}
	require.Eventually(t, func() bool { return len(smartProbeSlots) == 0 }, time.Second, time.Millisecond)
}

func TestSmartRejectsBuiltinsAndParsesYAMLGroup(t *testing.T) {
	proxies := map[string]C.Proxy{"COMPATIBLE": adapter.NewProxy(outbound.NewDirect()), "DIRECT": adapter.NewProxy(outbound.NewDirect())}
	g, err := ParseProxyGroup(map[string]any{"name": "Native", "type": "smart", "proxies": []string{"DIRECT"}}, proxies, map[string]P.ProxyProvider{}, nil, nil)
	require.NoError(t, err)
	s := g.(*Smart)
	defer s.Close()
	require.Equal(t, C.Smart, s.Type())
	require.Equal(t, "Smart", s.Type().String())
	_, err = s.DialContext(context.Background(), request("", "example.com"))
	require.ErrorIs(t, err, errSmartEmpty, "must never silently fall back to DIRECT")
}

func TestSmartSaturatedMemoryBudget(t *testing.T) {
	runtime.GC()
	var before, after runtime.MemStats
	runtime.ReadMemStats(&before)
	s, _ := smartFixture(t, 500)
	for i := 0; i < 50000; i++ {
		s.observe(fmt.Sprintf("node-%d", i), fmt.Sprintf("site-%d.example", i), false, time.Millisecond, true)
	}
	runtime.GC()
	runtime.ReadMemStats(&after)
	delta := int64(after.HeapAlloc) - int64(before.HeapAlloc)
	t.Logf("500 nodes, 50000 samples, saturated global caches: retained heap delta=%d bytes", delta)
	require.Less(t, delta, int64(4*1024*1024), "Smart bounded state including 500 fixture proxies must remain small")
	s.Close()
	runtime.GC()
}

func TestSmartManualTestIsCancelledOnReload(t *testing.T) {
	s, nodes := smartFixture(t, 1)
	started := make(chan struct{})
	nodes[0].dial = func(ctx context.Context) (C.Conn, error) { close(started); <-ctx.Done(); return nil, ctx.Err() }
	done := make(chan error, 1)
	go func() { _, err := s.URLTest(context.Background(), "", nil); done <- err }()
	<-started
	s.Close()
	select {
	case err := <-done:
		require.ErrorIs(t, err, context.Canceled)
	case <-time.After(250 * time.Millisecond):
		t.Fatal("reloading must cancel an active manual probe immediately")
	}
}

func TestSmartRetryCandidatesAreDistinct(t *testing.T) {
	s, nodes := smartFixture(t, 3)
	for _, node := range nodes {
		node.dial = func(context.Context) (C.Conn, error) { return nil, errors.New("offline") }
	}
	proxies := s.Proxies()
	duplicates := []C.Proxy{proxies[0], proxies[0], proxies[1], proxies[1], proxies[2]}
	ranked := s.candidates(duplicates, "example.com", false)
	seen := map[string]bool{}
	for _, p := range ranked {
		require.NotNil(t, p)
		require.False(t, seen[p.Name()])
		seen[p.Name()] = true
	}
}

func TestSmartManualProbeHonorsExpectedStatus(t *testing.T) {
	s, nodes := smartFixture(t, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(200) }))
	defer server.Close()
	s.url = server.URL
	nodes[0].dial = func(ctx context.Context) (C.Conn, error) {
		conn, err := (&net.Dialer{}).DialContext(ctx, "tcp", server.Listener.Addr().String())
		if err != nil {
			return nil, err
		}
		return outbound.NewConn(conn, nodes[0]), nil
	}
	s.expected, _ = utils.NewUnsignedRanges[uint16]("204")
	_, err := s.URLTest(context.Background(), "", nil)
	require.Error(t, err, "configured expected status must not be ignored by the UI test")
	s.expected, _ = utils.NewUnsignedRanges[uint16]("200")
	_, err = s.URLTest(context.Background(), "", nil)
	require.NoError(t, err)
}

func TestSmartProbesSkipRecentTrafficAndPreserveItsBackoff(t *testing.T) {
	s, nodes := smartFixture(t, 2)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(204) }))
	defer server.Close()
	s.url = server.URL
	for _, node := range nodes {
		node := node
		node.dial = func(ctx context.Context) (C.Conn, error) {
			conn, err := (&net.Dialer{}).DialContext(ctx, "tcp", server.Listener.Addr().String())
			if err != nil {
				return nil, err
			}
			return outbound.NewConn(conn, node), nil
		}
	}
	s.observe("Node-0", "example.com", false, 20*time.Millisecond, true)
	s.observe("Node-1", "example.com", false, 0, false)
	key := smartKey{s.id, "Node-1", false}
	before, _ := smartMemory.nodes.Get(key)
	s.probeAt = time.Time{}
	s.maybeProbe(s.Proxies())
	require.Eventually(t, func() bool { return nodes[1].calls.Load() == 1 && len(smartProbeSlots) == 0 }, time.Second, time.Millisecond)
	require.Zero(t, nodes[0].calls.Load(), "recent real success needs no extra automatic request")
	after, _ := smartMemory.nodes.Get(key)
	require.Equal(t, before, after, "a successful test URL must not erase business failure backoff")

	_, err := s.URLTest(context.Background(), "", nil)
	require.NoError(t, err)
	require.EqualValues(t, 1, nodes[0].calls.Load(), "manual tests still cover recent healthy nodes")
	require.EqualValues(t, 2, nodes[1].calls.Load())
	after, _ = smartMemory.nodes.Get(key)
	require.Equal(t, before, after)

	healthyKey := smartKey{s.id, "Node-0", false}
	state, _ := smartMemory.nodes.Get(healthyKey)
	state.updated = time.Now().Add(-2 * s.interval)
	smartMemory.nodes.Set(healthyKey, state)
	s.probeAt = time.Time{}
	s.maybeProbe(s.Proxies())
	require.Eventually(t, func() bool {
		return nodes[0].calls.Load() == 2 && nodes[1].calls.Load() == 3 && len(smartProbeSlots) == 0
	}, time.Second, time.Millisecond)
}
