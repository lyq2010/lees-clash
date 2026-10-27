package outboundgroup

import (
	"context"
	"encoding/json"
	"errors"
	"hash/maphash"
	"math"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/metacubex/mihomo/common/utils"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
)

var errSmartEmpty = errors.New("smart group has no proxy nodes (nested groups and built-in policies are excluded)")

type Smart struct {
	*GroupBase
	id       uint64
	seed     maphash.Seed
	url      string
	expected utils.IntRanges[uint16]
	interval time.Duration
	timeout  time.Duration
	noUDP    bool
	ctx      context.Context
	cancel   context.CancelFunc
	closed   atomic.Bool
	mu       sync.Mutex
	last     string
	probeAt  time.Time
	cursor   int
}

func NewSmart(option GroupCommonOption, fallback C.Proxy, providers []P.ProxyProvider) (*Smart, error) {
	status, err := utils.NewUnsignedRanges[uint16](option.ExpectedStatus)
	if err != nil {
		return nil, err
	}
	if option.URL == "" {
		option.URL = C.DefaultTestURL
	}
	interval := option.Interval
	if interval <= 0 {
		interval = 300
	}
	timeout := option.TestTimeout
	if timeout <= 0 {
		timeout = 3000
	}
	ctx, cancel := context.WithCancel(context.Background())
	return &Smart{
		GroupBase: NewGroupBase(GroupBaseOption{Name: option.Name, Type: C.Smart,
			Hidden: option.Hidden, Icon: option.Icon, Filter: option.Filter,
			ExcludeFilter: option.ExcludeFilter, ExcludeType: option.ExcludeType,
			EmptyFallback: fallback, Providers: providers}),
		id: smartSequence.Add(1), seed: maphash.MakeSeed(), url: option.URL, expected: status,
		interval: time.Duration(smartClamp(interval, 60, 86400)) * time.Second,
		timeout:  time.Duration(smartClamp(timeout, 500, 10000)) * time.Millisecond,
		noUDP:    option.DisableUDP, ctx: ctx, cancel: cancel,
	}, nil
}

func smartClamp(value, low, high int) int {
	if value < low {
		return low
	}
	if value > high {
		return high
	}
	return value
}

func smartEligible(p C.Proxy, udp bool) bool {
	if _, group := p.Adapter().(ProxyGroup); group {
		return false
	}
	switch p.Type() {
	case C.Direct, C.Compatible, C.Reject, C.RejectDrop, C.Pass, C.PassRule, C.Rematch, C.Dns:
		return false
	}
	return !udp || p.SupportUDP()
}

func smartSiteKey(m *C.Metadata) string {
	if m == nil {
		return ""
	}
	if key := getKey(m); key != "" && len(key) <= 253 {
		return key
	}
	if len(m.Host) <= 253 {
		return strings.ToLower(m.Host)
	}
	return ""
}

// Keep only three candidates on the stack rather than sorting/allocating a
// complete ranked node list for every connection.
func (s *Smart) candidates(proxies []C.Proxy, site string, udp bool) [3]C.Proxy {
	var result [3]C.Proxy
	scores := [3]float64{math.Inf(1), math.Inf(1), math.Inf(1)}
	now := time.Now()
	smartMemory.Lock()
	defer smartMemory.Unlock()
	entry, _ := smartMemory.sites.Get(smartKey{s.id, site, udp})
	var preferred C.Proxy
	preferredScore := math.Inf(1)
	for _, p := range proxies {
		if !smartEligible(p, udp) {
			continue
		}
		duplicate := false
		for _, previous := range result {
			if previous != nil && previous.Name() == p.Name() {
				duplicate = true
				break
			}
		}
		if duplicate {
			continue
		}
		base := float64(p.LastDelayForTestUrl(s.url))
		if base == 0 || base >= 65535 {
			base = 500
		}
		node, ok := smartMemory.nodes.Get(smartKey{s.id, p.Name(), udp})
		score := base
		if ok {
			weight := math.Exp(-now.Sub(node.updated).Seconds() / 600)
			score = base + weight*(node.delay-base) + float64(node.fails)*500*weight
			if now.Before(node.blocked) {
				score += 100000
			}
		}
		if entry.bad == p.Name() && now.Before(entry.badUntil) {
			score += 10000
		}
		// Stable site-specific tie breaking spreads sites across similar nodes.
		var hash maphash.Hash
		hash.SetSeed(s.seed)
		hash.WriteString(site)
		hash.WriteByte(0)
		hash.WriteString(p.Name())
		score += float64(hash.Sum64() % 25)
		if entry.good == p.Name() {
			preferred, preferredScore = p, score
		}
		for i := range result {
			if score < scores[i] {
				for j := len(result) - 1; j > i; j-- {
					result[j], scores[j] = result[j-1], scores[j-1]
				}
				result[i], scores[i] = p, score
				break
			}
		}
	}
	if preferred != nil && preferredScore <= scores[0]*1.25+50 {
		found := false
		for i := range result {
			if result[i] == preferred {
				copy(result[1:i+1], result[:i])
				found = true
				break
			}
		}
		if !found {
			copy(result[1:], result[:len(result)-1])
		}
		result[0] = preferred
	}
	return result
}

func (s *Smart) DialContext(ctx context.Context, m *C.Metadata) (C.Conn, error) {
	if s.closed.Load() {
		return nil, net.ErrClosed
	}
	proxies, site := s.GetProxies(true), smartSiteKey(m)
	candidates := s.candidates(proxies, site, false)
	s.maybeProbe(proxies)
	err := errSmartEmpty
	for _, p := range candidates {
		if p == nil || ctx.Err() != nil {
			break
		}
		start := time.Now()
		attempt, cancel := context.WithTimeout(ctx, s.timeout)
		c, dialErr := p.DialContext(attempt, m)
		cancel()
		if dialErr == nil {
			c.AppendToChains(s)
			s.mu.Lock()
			s.last = p.Name()
			s.mu.Unlock()
			return newSmartConn(c, s, p.Name(), site, start), nil
		}
		err = dialErr
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		s.observe(p.Name(), site, false, time.Since(start), false)
	}
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	return nil, err
}

func (s *Smart) Unwrap(m *C.Metadata, touch bool) C.Proxy {
	return s.candidates(s.GetProxies(touch), smartSiteKey(m), m != nil && m.NetWork == C.UDP)[0]
}

func (s *Smart) Now() string {
	s.mu.Lock()
	last := s.last
	s.mu.Unlock()
	proxies := s.GetProxies(false)
	for _, p := range proxies {
		if p.Name() == last && smartEligible(p, false) {
			return last
		}
	}
	if p := s.candidates(proxies, "", false)[0]; p != nil {
		return p.Name()
	}
	return ""
}

func (s *Smart) SupportUDP() bool {
	return !s.noUDP && s.candidates(s.GetProxies(false), "", true)[0] != nil
}

func (s *Smart) IsL3Protocol(m *C.Metadata) bool {
	if p := s.Unwrap(m, false); p != nil {
		return p.IsL3Protocol(m)
	}
	return false
}

func (s *Smart) Proxies() []C.Proxy           { return s.GetProxies(false) }
func (s *Smart) Providers() []P.ProxyProvider { return s.providers }

func (s *Smart) MarshalJSON() ([]byte, error) {
	all := make([]string, 0)
	for _, p := range s.Proxies() {
		all = append(all, p.Name())
	}
	return json.Marshal(map[string]any{"type": "Smart", "now": s.Now(), "all": all, "hidden": s.Hidden(), "icon": s.Icon()})
}

func (s *Smart) Close() error {
	s.closed.Store(true)
	s.cancel()
	s.clearMemory()
	return nil
}

var _ ProxyGroup = (*Smart)(nil)
