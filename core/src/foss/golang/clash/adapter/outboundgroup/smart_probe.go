package outboundgroup

import (
	"context"
	"fmt"
	"time"

	"github.com/metacubex/mihomo/common/utils"
	C "github.com/metacubex/mihomo/constant"
)

// At most two probe workers globally, four sequential tests per traffic-driven
// round. There is no ticker or goroutine per group while idle.
func (s *Smart) maybeProbe(proxies []C.Proxy) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed.Load() || time.Since(s.probeAt) < s.interval {
		return
	}
	select {
	case smartProbeSlots <- struct{}{}:
	default:
		return
	}
	var batch [4]C.Proxy
	n := 0
	for checked := 0; checked < len(proxies) && n < len(batch); checked++ {
		p := proxies[s.cursor%len(proxies)]
		s.cursor++
		if smartEligible(p, false) {
			batch[n], n = p, n+1
		}
	}
	s.probeAt = time.Now()
	if n == 0 {
		<-smartProbeSlots
		return
	}
	go func() {
		defer func() { <-smartProbeSlots }()
		for _, p := range batch[:n] {
			if s.ctx.Err() != nil {
				return
			}
			ctx, cancel := context.WithTimeout(s.ctx, s.timeout)
			delay, err := p.URLTest(ctx, s.url, s.expected)
			cancel()
			if s.ctx.Err() == nil {
				s.observe(p.Name(), "", false, time.Duration(delay)*time.Millisecond, err == nil && p.AliveForTestUrl(s.url))
			}
		}
	}()
}

// Manual tests cover all leaf members sequentially and share the same global
// concurrency budget, instead of spawning one goroutine per node.
func (s *Smart) URLTest(ctx context.Context, _ string, expected utils.IntRanges[uint16]) (map[string]uint16, error) {
	if len(expected) == 0 {
		expected = s.expected
	}
	result := make(map[string]uint16)
	select {
	case smartProbeSlots <- struct{}{}:
	case <-ctx.Done():
		return result, ctx.Err()
	case <-s.ctx.Done():
		return result, s.ctx.Err()
	}
	defer func() { <-smartProbeSlots }()
	for _, p := range s.GetProxies(false) {
		if !smartEligible(p, false) {
			continue
		}
		if s.ctx.Err() != nil {
			return result, s.ctx.Err()
		}
		attempt, cancel := context.WithTimeout(ctx, s.timeout)
		stop := context.AfterFunc(s.ctx, cancel)
		delay, err := p.URLTest(attempt, s.url, expected)
		stop()
		cancel()
		if s.ctx.Err() != nil {
			return result, s.ctx.Err()
		}
		if ctx.Err() != nil {
			return result, ctx.Err()
		}
		// URLTest can return nil error for an unexpected HTTP status; the
		// per-URL alive flag carries that verdict in the existing proxy API.
		success := err == nil && p.AliveForTestUrl(s.url)
		s.observe(p.Name(), "", false, time.Duration(delay)*time.Millisecond, success)
		if success {
			result[p.Name()] = delay
		}
	}
	if len(result) == 0 {
		return result, fmt.Errorf("get delay: all smart nodes timeout")
	}
	return result, nil
}
