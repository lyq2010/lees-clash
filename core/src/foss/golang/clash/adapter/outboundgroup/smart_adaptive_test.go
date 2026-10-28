package outboundgroup

import (
	"fmt"
	"testing"
	"time"
	"unsafe"

	C "github.com/metacubex/mihomo/constant"
	"github.com/stretchr/testify/require"
)

func TestSmartSiteDelaySurvivesFastSamplesFromOtherSites(t *testing.T) {
	s, _ := smartFixture(t, 2)
	proxies := s.Proxies()
	s.observe("Node-0", "slow.example", false, 2*time.Second, true)
	s.observe("Node-1", "", false, 350*time.Millisecond, true)
	for i := 0; i < 40; i++ {
		s.observe("Node-0", "fast.example", false, 20*time.Millisecond, true)
	}
	require.Equal(t, "Node-0", s.candidates(proxies, "fast.example", false)[0].Name())
	require.Equal(t, "Node-1", s.candidates(proxies, "slow.example", false)[0].Name(),
		"a fast global average must not hide a slow response for this site")

	s.observe("Node-1", "slow.example", false, 300*time.Millisecond, true)
	for i := 0; i < 40; i++ {
		s.observe("Node-0", "fast.example", false, 20*time.Millisecond, true)
	}
	for i := 0; i < 20; i++ {
		require.Equal(t, "Node-1", s.candidates(proxies, "slow.example", false)[0].Name(),
			"a successful faster alternative must not immediately bounce to the old slow node")
	}

	key := smartKey{s.id, "slow.example", false}
	smartMemory.Lock()
	entry, ok := smartMemory.sites.Get(key)
	smartMemory.sites.SetWithExpire(key, entry, time.Unix(1, 0))
	smartMemory.Unlock()
	require.True(t, ok)
	require.Equal(t, "Node-0", s.candidates(proxies, "slow.example", false)[0].Name(),
		"expired site evidence must allow the current global scores to take over")
}

func TestSmartSlowerAlternativeDoesNotReplaceKnownSiteRoute(t *testing.T) {
	s, _ := smartFixture(t, 2)
	proxies := s.Proxies()
	s.observe("Node-0", "target.example", false, 100*time.Millisecond, true)
	for i := 0; i < 40; i++ {
		s.observe("Node-0", "other.example", false, 2*time.Second, true)
	}
	s.observe("Node-1", "elsewhere.example", false, 20*time.Millisecond, true)
	// An alternative connection can finish after the known faster route. Its
	// success alone must not overwrite that destination's better observation.
	s.observe("Node-1", "target.example", false, 2*time.Second, true)
	for i := 0; i < 40; i++ {
		s.observe("Node-1", "elsewhere.example", false, 20*time.Millisecond, true)
	}
	for i := 0; i < 20; i++ {
		require.Equal(t, "Node-0", s.candidates(proxies, "target.example", false)[0].Name(),
			"a slower alternative must not replace a destination's known faster route because of unrelated traffic")
	}
}

type smartHealthTestProxy struct {
	C.Proxy
	delay uint16
	alive bool
}

func (p *smartHealthTestProxy) LastDelayForTestUrl(string) uint16 { return p.delay }
func (p *smartHealthTestProxy) AliveForTestUrl(string) bool       { return p.alive }

func TestSmartURLHealthDistinguishesUnknownFailureAndRecovery(t *testing.T) {
	s, _ := smartFixture(t, 2)
	nodes := s.Proxies()
	unknown := &smartHealthTestProxy{Proxy: nodes[0], delay: 65535, alive: true}
	healthy := &smartHealthTestProxy{Proxy: nodes[1], delay: 900, alive: true}
	proxies := []C.Proxy{unknown, healthy}
	require.Equal(t, unknown.Name(), s.candidates(proxies, "example.com", false)[0].Name(),
		"an untested node keeps the 500ms reference rather than a failure penalty")
	healthy.delay = 400
	require.Equal(t, healthy.Name(), s.candidates(proxies, "example.com", false)[0].Name(),
		"the unknown reference must not beat a healthy 400ms node")

	healthy.delay = 900
	unknown.alive = false
	ranked := s.candidates(proxies, "example.com", false)
	require.Equal(t, healthy.Name(), ranked[0].Name(),
		"a known failed test must not outrank a healthy 900ms node")
	require.Equal(t, unknown.Name(), ranked[1].Name(),
		"a failed node remains a bounded fallback when all preferred candidates fail")

	unknown.delay, unknown.alive = 120, true
	require.Equal(t, unknown.Name(), s.candidates(proxies, "example.com", false)[0].Name(),
		"a recovered node must become eligible for preference again")
}

func TestSmartSiteRecordDoesNotGrow(t *testing.T) {
	previous := struct {
		good, bad string
		badUntil  time.Time
	}{}
	require.LessOrEqual(t, unsafe.Sizeof(smartSite{}), unsafe.Sizeof(previous),
		"per-site learning must fit the previous record size without growing the global cache budget")
}

func TestSmartSelectionDoesNotAllocate(t *testing.T) {
	s, _ := smartFixture(t, 500)
	proxies := s.Proxies()
	for i, p := range proxies {
		s.observe(p.Name(), "example.com", false, time.Duration(50+i)*time.Millisecond, true)
	}
	require.Zero(t, testing.AllocsPerRun(100, func() {
		s.candidates(proxies, "example.com", false)
	}), "site-aware selection must not allocate on the connection path")
}

func BenchmarkSmartSelection500NodesWithMemory(b *testing.B) {
	s, _ := smartFixture(b, 500)
	proxies := s.Proxies()
	for i, p := range proxies {
		s.observe(p.Name(), "", false, time.Duration(50+i)*time.Millisecond, true)
	}
	for i := 0; i < smartSiteLimit; i++ {
		s.observe("Node-0", fmt.Sprintf("site-%d.example", i), false, 50*time.Millisecond, true)
	}
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		s.candidates(proxies, "site-0.example", false)
	}
	b.StopTimer() // Exclude fixture cleanup and its cache-key snapshots.
}
