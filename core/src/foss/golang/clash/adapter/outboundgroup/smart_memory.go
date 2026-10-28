package outboundgroup

import (
	"math"
	"sync"
	"sync/atomic"
	"time"

	"github.com/metacubex/mihomo/common/lru"
)

const smartNodeLimit = 4096
const smartSiteLimit = 1024

type smartKey struct {
	group uint64
	name  string
	udp   bool
}

type smartNode struct {
	delay   float64
	updated time.Time
	blocked time.Time
	fails   uint8
}

type smartSite struct {
	good, bad             string
	goodUpdated, badUntil int64
	goodDelay             uint32 // Capped milliseconds; fits the original record on 32- and 64-bit builds.
}

// Shared across ALL groups. No timers, target-by-node matrices, models or
// references to group/proxy objects are retained here.
var smartMemory = struct {
	sync.Mutex
	nodes *lru.LruCache[smartKey, smartNode]
	sites *lru.LruCache[smartKey, smartSite]
}{
	nodes: lru.New[smartKey, smartNode](lru.WithSize[smartKey, smartNode](smartNodeLimit), lru.WithAge[smartKey, smartNode](1800)),
	sites: lru.New[smartKey, smartSite](lru.WithSize[smartKey, smartSite](smartSiteLimit), lru.WithAge[smartKey, smartSite](3600)),
}

var smartSequence atomic.Uint64
var smartProbeSlots = make(chan struct{}, 2)

// Counts only; never disclose remembered domains or proxy names.
func SmartMemoryStats() map[string]int {
	return map[string]int{"nodeRecords": smartMemory.nodes.Len(), "siteRecords": smartMemory.sites.Len(),
		"nodeLimit": smartNodeLimit, "siteLimit": smartSiteLimit, "probeWorkers": len(smartProbeSlots)}
}

func (s *Smart) observe(node, site string, udp bool, elapsed time.Duration, success bool) {
	if s.closed.Load() {
		return
	}
	now := time.Now()
	smartMemory.Lock()
	defer func() {
		smartMemory.Unlock()
		s.queueStateSave(now)
	}()
	if s.closed.Load() {
		return
	}
	key := smartKey{s.id, node, udp}
	state, _ := smartMemory.nodes.Get(key)
	delay := math.Min(float64(elapsed.Milliseconds()), 10000)
	if success {
		if state.updated.IsZero() {
			state.delay = delay
		} else {
			// New evidence gets more weight after a long silence/network change.
			weight := math.Max(.2, 1-math.Exp(-now.Sub(state.updated).Seconds()/300))
			state.delay += weight * (delay - state.delay)
		}
		state.fails, state.blocked = 0, time.Time{}
	} else {
		if state.updated.IsZero() {
			state.delay = 500
		}
		if state.fails < 6 {
			state.fails++
		}
		state.blocked = now.Add(time.Second * time.Duration(1<<state.fails))
	}
	state.updated = now
	smartMemory.nodes.Set(key, state)
	if site != "" {
		key.name = site
		entry, _ := smartMemory.sites.Get(key)
		if success {
			recent := entry.good != "" && entry.good != node && now.Unix()-entry.goodUpdated < 600
			if recent && delay > float64(entry.goodDelay)*1.25+50 {
				// A slow, late success must not replace a faster known route.
				entry.bad, entry.badUntil = node, now.Add(10*time.Minute).Unix()
			} else {
				// Reuse the single demotion slot to keep a faster replacement
				// stable even if the old node is fast for other destinations.
				if recent && float64(entry.goodDelay) > delay*1.25+50 {
					entry.bad, entry.badUntil = entry.good, now.Add(10*time.Minute).Unix()
				}
				entry.good, entry.goodDelay, entry.goodUpdated = node, uint32(delay), now.Unix()
				if entry.bad == node {
					entry.bad = ""
				}
			}
		} else {
			entry.bad, entry.badUntil = node, now.Add(10*time.Minute).Unix()
			if entry.good == node {
				entry.good = ""
			}
		}
		smartMemory.sites.Set(key, entry)
	}
	if s.persist.Load() {
		smartState.revision.Add(1)
	}
}

func (s *Smart) clearMemory() {
	smartMemory.Lock()
	defer smartMemory.Unlock()
	for _, key := range smartMemory.nodes.Keys() {
		if key.group == s.id {
			smartMemory.nodes.Delete(key)
		}
	}
	for _, key := range smartMemory.sites.Keys() {
		if key.group == s.id {
			smartMemory.sites.Delete(key)
		}
	}
}
