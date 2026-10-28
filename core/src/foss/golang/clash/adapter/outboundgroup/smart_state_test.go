package outboundgroup

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	C "github.com/metacubex/mihomo/constant"
	"github.com/stretchr/testify/require"
)

func enableSmartState(t *testing.T, s *Smart, path, scope string) {
	t.Helper()
	require.NoError(t, ConfigureSmartPersistence(path, scope, map[string]C.Proxy{s.Name(): adapter.NewProxy(s)}))
	t.Cleanup(func() { s.Close(); _ = ConfigureSmartPersistence("", "", nil) })
}

func TestSmartStateRestoresNewGroupIdentityAndTrafficQuality(t *testing.T) {
	path := filepath.Join(t.TempDir(), "smart-state.jsonl")
	s, _ := smartFixture(t, 2)
	enableSmartState(t, s, path, "profile-a")
	s.observe("Node-0", "slow.example", false, 2*time.Second, true)
	s.observe("Node-1", "", false, 350*time.Millisecond, true)
	for i := 0; i < 40; i++ {
		s.observe("Node-0", "fast.example", false, 20*time.Millisecond, true)
	}
	s.observe("Node-0", "udp.example", true, time.Millisecond, false)
	s.Close() // Stop flushes even before the five-minute traffic checkpoint.
	info, err := os.Stat(path)
	require.NoError(t, err)
	require.Equal(t, os.FileMode(0600), info.Mode().Perm())
	require.LessOrEqual(t, info.Size(), int64(smartStateMaxBytes))
	resumed, _ := smartFixture(t, 2)
	require.NotEqual(t, s.id, resumed.id)
	enableSmartState(t, resumed, path, "profile-a")
	require.Equal(t, "Node-1", resumed.candidates(resumed.Proxies(), "slow.example", false)[0].Name())
	state, ok := smartMemory.nodes.Get(smartKey{resumed.id, "Node-0", true})
	require.True(t, ok)
	require.EqualValues(t, 1, state.fails)
	require.True(t, state.blocked.After(time.Now()))
	entry, expires, ok := smartMemory.sites.GetWithExpire(smartKey{resumed.id, "slow.example", false})
	require.True(t, ok)
	require.EqualValues(t, 2000, entry.goodDelay)
	require.Less(t, time.Until(expires), time.Hour)
}

func TestSmartStateCoalescesTrafficAndFlushesOnlyDirtyChanges(t *testing.T) {
	path := filepath.Join(t.TempDir(), "smart-state.jsonl")
	s, _ := smartFixture(t, 1)
	enableSmartState(t, s, path, "profile")
	for i := 0; i < 100; i++ {
		s.observe("Node-0", "example.com", false, time.Millisecond, true)
	}
	_, err := os.Stat(path)
	require.True(t, os.IsNotExist(err), "ordinary samples must not each cause a write")
	smartState.next.Store(0)
	s.observe("Node-0", "example.com", false, 2*time.Millisecond, true)
	require.Eventually(t, func() bool {
		smartState.Lock()
		defer smartState.Unlock()
		return smartState.saved == 101 && !smartState.worker.Load()
	}, time.Second, time.Millisecond)
	for i := 0; i < 100; i++ {
		s.observe("Node-0", "example.com", false, 3*time.Millisecond, true)
	}
	smartState.Lock()
	saved := smartState.saved
	smartState.Unlock()
	require.EqualValues(t, 101, saved)
	FlushSmartState()
	smartState.Lock()
	saved = smartState.saved
	smartState.Unlock()
	require.EqualValues(t, 201, saved)
	info, err := os.Stat(path)
	require.NoError(t, err)
	FlushSmartState()
	after, err := os.Stat(path)
	require.NoError(t, err)
	require.Equal(t, info.ModTime(), after.ModTime())
	proxies := s.Proxies()
	allocations := testing.AllocsPerRun(100, func() { s.candidates(proxies, "example.com", false) })
	require.Zero(t, allocations, "enabled persistence must not allocate during selection")
	require.Zero(t, testing.AllocsPerRun(100, func() {
		s.observe("Node-0", "example.com", false, time.Millisecond, true)
	}), "coalescing normal samples must not allocate")
}

func TestSmartStateSizeLimitKeepsCompleteRecords(t *testing.T) {
	path := filepath.Join(t.TempDir(), "smart-state.jsonl")
	s, _ := smartFixture(t, 1)
	enableSmartState(t, s, path, "profile")
	for i := 0; i < smartNodeLimit+100; i++ {
		s.observe(fmt.Sprintf("%04d-%s", i, strings.Repeat("x", 900)), "example.com", false, time.Millisecond, true)
	}
	runtime.GC()
	var before, after runtime.MemStats
	runtime.ReadMemStats(&before)
	FlushSmartState()
	runtime.GC()
	runtime.ReadMemStats(&after)
	require.Less(t, int64(after.HeapAlloc)-int64(before.HeapAlloc), int64(256*1024), "snapshot must not retain a file-sized memory copy")
	info, err := os.Stat(path)
	require.NoError(t, err)
	require.LessOrEqual(t, info.Size(), int64(smartStateMaxBytes))
	require.Greater(t, info.Size(), int64(smartStateMaxBytes-4096))
	f, err := os.Open(path)
	require.NoError(t, err)
	defer f.Close()
	decoder := json.NewDecoder(f)
	var header smartStateHeader
	require.NoError(t, decoder.Decode(&header))
	count := 0
	for decoder.More() {
		var record smartStateRecord
		require.NoError(t, decoder.Decode(&record))
		count++
	}
	require.Greater(t, count, 0)
	require.Less(t, count, smartNodeLimit)
}

func TestSmartStateRejectsChangedProfileCorruptionAndExpiredRecords(t *testing.T) {
	path := filepath.Join(t.TempDir(), "smart-state.jsonl")
	s, _ := smartFixture(t, 1)
	enableSmartState(t, s, path, "profile-a")
	s.observe("Node-0", "example.com", false, time.Millisecond, true)
	s.Close()
	valid, err := os.ReadFile(path)
	require.NoError(t, err)
	other, _ := smartFixture(t, 1)
	enableSmartState(t, other, path, "profile-b")
	_, ok := smartMemory.nodes.Get(smartKey{other.id, "Node-0", false})
	require.False(t, ok)
	other.Close()
	for _, data := range [][]byte{append(append([]byte{}, valid...), []byte("{broken")...), []byte(strings.Repeat("x", smartStateMaxBytes+1))} {
		require.NoError(t, os.WriteFile(path, data, 0600))
		s, _ := smartFixture(t, 1)
		err := ConfigureSmartPersistence(path, "profile-a", map[string]C.Proxy{s.Name(): adapter.NewProxy(s)})
		require.Error(t, err)
		_, ok := smartMemory.nodes.Get(smartKey{s.id, "Node-0", false})
		require.False(t, ok, "a truncated cache must not partially restore observations")
		s.Close()
	}
	now := time.Now().Unix()
	header, _ := json.Marshal(smartStateHeader{Version: 1, Scope: "profile-a", Saved: now - 4000})
	record, _ := json.Marshal(smartStateRecord{Kind: "n", Group: "Smart", Name: "Node-0", Expires: now - 1, Updated: now - 1900, Delay: 1})
	require.NoError(t, os.WriteFile(path, append(append(header, '\n'), append(record, '\n')...), 0600))
	last, _ := smartFixture(t, 1)
	enableSmartState(t, last, path, "profile-a")
	_, ok = smartMemory.nodes.Get(smartKey{last.id, "Node-0", false})
	require.False(t, ok)
}

func TestSmartStateClearRemovesMemoryAndDiskWithoutDisablingLearning(t *testing.T) {
	path := filepath.Join(t.TempDir(), "smart-state.jsonl")
	s, _ := smartFixture(t, 1)
	enableSmartState(t, s, path, "profile")
	s.observe("Node-0", "example.com", false, time.Millisecond, true)
	FlushSmartState()
	require.NoError(t, ClearSmartState(path))
	_, err := os.Stat(path)
	require.True(t, os.IsNotExist(err))
	_, ok := smartMemory.nodes.Get(smartKey{s.id, "Node-0", false})
	require.False(t, ok)
	_, ok = smartMemory.sites.Get(smartKey{s.id, "example.com", false})
	require.False(t, ok)
	require.True(t, s.persist.Load())
	FlushSmartState()
	_, err = os.Stat(path)
	require.True(t, os.IsNotExist(err), "old queued writes must not recreate cleared results")
	s.observe("Node-0", "fresh.example", false, 2*time.Millisecond, true)
	FlushSmartState()
	data, err := os.ReadFile(path)
	require.NoError(t, err)
	require.Contains(t, string(data), "fresh.example")
	require.NotContains(t, string(data), "example.com")
}

func TestSmartStateWriteFailureDoesNotBlockSelection(t *testing.T) {
	path := filepath.Join(t.TempDir(), "missing", "smart-state.jsonl")
	s, _ := smartFixture(t, 1)
	enableSmartState(t, s, path, "profile")
	s.observe("Node-0", "example.com", false, time.Millisecond, true)
	FlushSmartState()
	require.Equal(t, "Node-0", s.candidates(s.Proxies(), "example.com", false)[0].Name())
	smartState.Lock()
	saved := smartState.saved
	smartState.Unlock()
	require.Zero(t, saved, "failed snapshots remain dirty for a later retry")
}
