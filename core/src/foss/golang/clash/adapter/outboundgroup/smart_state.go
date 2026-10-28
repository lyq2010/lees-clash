package outboundgroup

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"sync"
	"sync/atomic"
	"time"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

const smartStateMaxBytes = 2 * 1024 * 1024
const smartStateInterval = 5 * time.Minute

// Only registration and write bookkeeping stay resident, never a second cache.
var smartState = struct {
	sync.Mutex
	path, scope string
	groups      map[uint64]string
	saved       uint64
	revision    atomic.Uint64
	next        atomic.Int64
	worker      atomic.Bool
}{}

type smartStateHeader struct {
	Version int    `json:"version"`
	Scope   string `json:"scope"`
	Saved   int64  `json:"saved"`
}

type smartStateRecord struct {
	Kind        string  `json:"k"`
	Group       string  `json:"g"`
	Name        string  `json:"n"`
	UDP         bool    `json:"u,omitempty"`
	Expires     int64   `json:"e"`
	Delay       float64 `json:"d,omitempty"`
	Updated     int64   `json:"t,omitempty"`
	Blocked     int64   `json:"b,omitempty"`
	Fails       uint8   `json:"f,omitempty"`
	Good        string  `json:"a,omitempty"`
	Bad         string  `json:"z,omitempty"`
	GoodUpdated int64   `json:"r,omitempty"`
	BadUntil    int64   `json:"l,omitempty"`
	GoodDelay   uint32  `json:"v,omitempty"`
}

// Call after replacing the runtime proxies. Profile fingerprints prevent
// applying observations to a different configuration with reused node names.
func ConfigureSmartPersistence(path, scope string, proxies map[string]C.Proxy) error {
	smartState.Lock()
	defer smartState.Unlock()
	if err := saveSmartStateLocked(); err != nil {
		log.Warnln("Save Smart state: %v", err)
	}
	smartState.path, smartState.scope = path, scope
	smartState.groups = make(map[uint64]string)
	groups := make(map[string]*Smart)
	for name, p := range proxies {
		if s, ok := p.Adapter().(*Smart); ok && !s.closed.Load() && len(name) <= 1024 {
			groups[name] = s
			smartState.groups[s.id] = name
		}
	}
	smartState.revision.Store(0)
	smartState.saved = 0
	smartState.next.Store(time.Now().Add(smartStateInterval).Unix())
	if path == "" || scope == "" || len(groups) == 0 {
		smartState.path = ""
		smartState.groups = nil
		return nil
	}
	err := restoreSmartState(path, scope, groups)
	for _, s := range groups {
		s.persist.Store(true)
	}
	return err // The caller may log a bad cache; routing must still start.
}

func (s *Smart) queueStateSave(now time.Time) {
	if !s.persist.Load() || s.closed.Load() {
		return
	}
	if now.Unix() < smartState.next.Load() || !smartState.worker.CompareAndSwap(false, true) {
		return
	}
	smartState.next.Store(now.Add(smartStateInterval).Unix())
	go func() {
		defer smartState.worker.Store(false)
		FlushSmartState()
	}()
}

// Normal stop/reload flushes pending changes once, even inside the write interval.
func FlushSmartState() {
	smartState.Lock()
	defer smartState.Unlock()
	if err := saveSmartStateLocked(); err != nil {
		log.Warnln("Save Smart state: %v", err)
	}
}

func smartStateRecordFor(key smartKey, site bool, now time.Time) (smartStateRecord, bool) {
	smartMemory.Lock()
	defer smartMemory.Unlock()
	group, registered := smartState.groups[key.group]
	if !registered || len(key.name) > 1024 {
		return smartStateRecord{}, false
	}
	if !site {
		state, expires, ok := smartMemory.nodes.GetWithExpire(key)
		if !ok || !expires.After(now) {
			return smartStateRecord{}, false
		}
		blocked := int64(0)
		if !state.blocked.IsZero() {
			blocked = state.blocked.Unix()
		}
		return smartStateRecord{Kind: "n", Group: group, Name: key.name, UDP: key.udp,
			Expires: expires.Unix(), Delay: state.delay, Updated: state.updated.Unix(), Blocked: blocked, Fails: state.fails}, true
	} else {
		if len(key.name) > 253 {
			return smartStateRecord{}, false
		}
		state, expires, ok := smartMemory.sites.GetWithExpire(key)
		if !ok || !expires.After(now) || len(state.good) > 1024 || len(state.bad) > 1024 {
			return smartStateRecord{}, false
		}
		updated := state.goodUpdated
		if state.badUntil-600 > updated {
			updated = state.badUntil - 600
		}
		return smartStateRecord{Kind: "s", Group: group, Name: key.name, UDP: key.udp,
			Expires: expires.Unix(), Updated: updated, Good: state.good, Bad: state.bad,
			GoodUpdated: state.goodUpdated, BadUntil: state.badUntil, GoodDelay: state.goodDelay}, true
	}
}

var errSmartStateFull = errors.New("Smart snapshot budget reached")

type smartStateWriter struct {
	io.Writer
	remaining int
}

func (w *smartStateWriter) Write(p []byte) (int, error) {
	if len(p) > w.remaining {
		return 0, errSmartStateFull
	}
	n, err := w.Writer.Write(p)
	w.remaining -= n
	return n, err
}

func saveSmartStateLocked() error {
	revision := smartState.revision.Load()
	if smartState.path == "" || revision == smartState.saved {
		return nil
	}
	now := time.Now()
	// Copy only bounded key lists (~160 KiB at full capacity), never a second
	// cache or a whole encoded file. Each record is read outside disk I/O.
	nodes, sites := smartMemory.nodes.Keys(), smartMemory.sites.Keys()
	f, err := os.OpenFile(smartState.path+".tmp", os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	defer os.Remove(f.Name())
	defer f.Close()
	buffer := bufio.NewWriterSize(f, 4096)
	encoder := json.NewEncoder(&smartStateWriter{Writer: buffer, remaining: smartStateMaxBytes})
	if err = encoder.Encode(smartStateHeader{Version: 1, Scope: smartState.scope, Saved: now.Unix()}); err != nil {
		return err
	}
	for n, s := 0, 0; n < len(nodes) || s < len(sites); {
		// Interleave recent node/site records so a full file retains both kinds.
		site := s < len(sites) && (n >= len(nodes) || n >= 4*(s+1))
		var key smartKey
		if site {
			key = sites[s]
			s++
		} else {
			key = nodes[n]
			n++
		}
		record, ok := smartStateRecordFor(key, site, now)
		if !ok {
			continue
		}
		if err = encoder.Encode(record); errors.Is(err, errSmartStateFull) {
			break
		}
		if err != nil {
			return err
		}
	}
	if err = buffer.Flush(); err != nil {
		return err
	}
	if err = f.Sync(); err != nil {
		return err
	}
	if err = f.Close(); err != nil {
		return err
	}
	if err = os.Rename(f.Name(), smartState.path); err != nil {
		return err
	}
	smartState.saved = revision
	return nil
}

func restoreSmartState(path, scope string, groups map[string]*Smart) error {
	f, err := os.Open(path)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return err
	}
	if info.Size() > smartStateMaxBytes {
		return fmt.Errorf("Smart snapshot exceeds size budget")
	}
	now := time.Now()
	// Validate once before restoring, then stream again. Do not retain a second
	// copy of every observation or let a truncated cache partially restore.
	if err = readSmartState(f, scope, now, nil); err != nil {
		return err
	}
	if _, err = f.Seek(0, io.SeekStart); err != nil {
		return err
	}
	members := make(map[*Smart][]C.Proxy, len(groups))
	for _, s := range groups {
		members[s] = s.GetProxies(false)
	}
	return readSmartState(f, scope, now, func(r smartStateRecord) {
		s := groups[r.Group]
		if s == nil || s.closed.Load() || r.Expires <= now.Unix() || r.Updated > now.Unix()+60 {
			return
		}
		member := func(name string) string {
			for _, p := range members[s] {
				if p.Name() == name && smartEligible(p, r.UDP) {
					return p.Name() // Reuse configured names instead of retaining decoded copies.
				}
			}
			return ""
		}
		smartMemory.Lock()
		defer smartMemory.Unlock()
		key := smartKey{s.id, r.Name, r.UDP}
		if r.Kind == "n" && r.Updated > now.Unix()-1800 {
			key.name = member(r.Name)
			if key.name == "" {
				return
			}
			state := smartNode{delay: r.Delay, updated: time.Unix(r.Updated, 0), fails: r.Fails}
			if r.Blocked > now.Unix() {
				state.blocked = time.Unix(smartStateEarlier(r.Blocked, now.Unix()+64), 0)
			}
			smartMemory.nodes.SetWithExpire(key, state, time.Unix(smartStateEarlier(r.Expires, now.Unix()+1800), 0))
		} else if r.Kind == "s" && len(r.Name) <= 253 && r.Updated > now.Unix()-3600 {
			r.Good, r.Bad = member(r.Good), member(r.Bad)
			if r.BadUntil <= now.Unix() {
				r.Bad = ""
			}
			if r.Good == "" && r.Bad == "" {
				return
			}
			state := smartSite{good: r.Good, bad: r.Bad, goodUpdated: smartStateEarlier(r.GoodUpdated, now.Unix()), badUntil: smartStateEarlier(r.BadUntil, now.Unix()+600), goodDelay: r.GoodDelay}
			smartMemory.sites.SetWithExpire(key, state, time.Unix(smartStateEarlier(r.Expires, now.Unix()+3600), 0))
		}
	})
}

func readSmartState(reader io.Reader, scope string, now time.Time, apply func(smartStateRecord)) error {
	scanner := bufio.NewScanner(io.LimitReader(reader, smartStateMaxBytes+1))
	scanner.Buffer(make([]byte, 4096), 32*1024)
	if !scanner.Scan() {
		return fmt.Errorf("Invalid Smart snapshot header")
	}
	var header smartStateHeader
	if err := json.Unmarshal(scanner.Bytes(), &header); err != nil {
		return err
	}
	if header.Version != 1 || header.Scope != scope || header.Saved > now.Unix()+60 {
		return nil
	}
	count := 0
	for scanner.Scan() {
		count++
		if count > smartNodeLimit+smartSiteLimit {
			return fmt.Errorf("Smart snapshot exceeds record budget")
		}
		var r smartStateRecord
		if err := json.Unmarshal(scanner.Bytes(), &r); err != nil {
			return err
		}
		if (r.Kind != "n" && r.Kind != "s") || len(r.Group) > 1024 || len(r.Name) > 1024 || len(r.Good) > 1024 || len(r.Bad) > 1024 ||
			math.IsNaN(r.Delay) || math.IsInf(r.Delay, 0) || r.Delay < 0 || r.Delay > 10000 || r.GoodDelay > 10000 || r.Fails > 6 {
			return fmt.Errorf("Invalid Smart snapshot record")
		}
		if apply != nil {
			apply(r)
		}
	}
	return scanner.Err()
}

// Caller presents confirmation and supplies only the active profile's path.
func ClearSmartState(path string) error {
	smartState.Lock()
	defer smartState.Unlock()
	for _, file := range []string{path, path + ".tmp"} {
		if err := os.Remove(file); err != nil && !os.IsNotExist(err) {
			return err
		}
	}
	if path == smartState.path {
		smartMemory.Lock()
		defer smartMemory.Unlock()
		for _, key := range smartMemory.nodes.Keys() {
			if _, ok := smartState.groups[key.group]; ok {
				smartMemory.nodes.Delete(key)
			}
		}
		for _, key := range smartMemory.sites.Keys() {
			if _, ok := smartState.groups[key.group]; ok {
				smartMemory.sites.Delete(key)
			}
		}
		smartState.revision.Store(0)
		smartState.saved = 0
		smartState.next.Store(time.Now().Add(smartStateInterval).Unix())
	}
	return nil
}

func smartStateEarlier(a, b int64) int64 {
	if a < b {
		return a
	}
	return b
}
