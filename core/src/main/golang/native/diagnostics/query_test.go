package diagnostics

import (
	"encoding/json"
	"fmt"
	"testing"

	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/component/resolver"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

func TestConnectionApplicationLookupForStrictModePreservesRoutingAndOff(t *testing.T) {
	connectionOwners.Clear()
	defer connectionOwners.Clear()
	oldResolver, oldMode := PackageResolver, tunnel.FindProcessMode()
	defer func() { PackageResolver = oldResolver; tunnel.SetFindProcessMode(oldMode) }()
	calls := 0
	PackageResolver = func(m *C.Metadata) (string, error) {
		calls++
		m.Uid = 10123
		return "example.browser", nil
	}
	tunnel.SetFindProcessMode(process.FindProcessStrict)
	m := &C.Metadata{}
	info := &statistic.TrackerInfo{Metadata: m}
	result := connection(info)
	if result.Process != "example.browser" || result.UID != 10123 || m.Uid != 0 || m.Process != "" {
		t.Fatalf("missing identity or modified routing metadata: %+v %+v", result, m)
	}
	connection(info)
	if calls != 1 {
		t.Fatal("repeated Binder lookup for unchanged connection")
	}
	tunnel.SetFindProcessMode(process.FindProcessOff)
	result = connection(info)
	if calls != 1 || result.Process != "" || result.UID != -1 {
		t.Fatal("ignored off mode or invented UID")
	}
	tunnel.SetFindProcessMode(process.FindProcessStrict)
	connectionOwners.Clear()
	PackageResolver = func(m *C.Metadata) (string, error) { m.Uid = 10124; return "", fmt.Errorf("package absent") }
	if got := connection(info); got.UID != 10124 || got.Process != "" {
		t.Fatal("lost valid UID when package absent")
	}
}

type cacheResolver struct {
	resolver.Resolver
	cleared bool
}

func (r *cacheResolver) ClearCache() { r.cleared = true }

func TestDnsCacheClearCompletesForCoreAndSystem(t *testing.T) {
	previous, system := resolver.DefaultResolver, resolver.SystemResolver
	defer func() { resolver.DefaultResolver, resolver.SystemResolver = previous, system }()
	coreCache, systemCache := &cacheResolver{}, &cacheResolver{}
	resolver.DefaultResolver, resolver.SystemResolver = coreCache, systemCache
	var response map[string]bool
	if err := json.Unmarshal([]byte(Query("clearDnsCache", "{}")), &response); err != nil || !response["success"] {
		t.Fatalf("cache clear failed: %v", err)
	}
	if !coreCache.cleared || !systemCache.cleared {
		t.Fatal("reported success before both resolvers completed")
	}
}

func TestPaginationBoundaries(t *testing.T) {
	items := []int{1, 2, 3}
	for _, tc := range []struct{ offset, limit, count, next int }{{-1, 2, 2, 2}, {2, 50, 1, -1}, {99, 50, 0, -1}, {0, 0, 3, -1}} {
		page := paginate(items, tc.offset, tc.limit)
		if len(page.Items) != tc.count || page.NextOffset != tc.next || page.Total != 3 {
			t.Fatalf("unexpected page: %+v", page)
		}
	}
	large := make([]int, 500)
	if len(paginate(large, 0, 10000).Items) != maxPage {
		t.Fatal("unbounded page")
	}
}

func TestTrafficKeepsFull64BitTotals(t *testing.T) {
	statistic.DefaultManager.ResetStatistic()
	defer statistic.DefaultManager.ResetStatistic()
	const upload int64 = 1 << 45
	const download int64 = (1 << 46) + 9
	statistic.DefaultManager.PushUploaded(upload)
	statistic.DefaultManager.PushDownloaded(download)
	var response struct {
		Upload   int64 `json:"uploadTotal"`
		Download int64 `json:"downloadTotal"`
		Active   int64 `json:"activeConnections"`
	}
	if err := json.Unmarshal([]byte(Query("traffic", "{}")), &response); err != nil {
		t.Fatal(err)
	}
	if response.Upload != upload || response.Download != download || response.Active < 0 {
		t.Fatalf("traffic was truncated: %+v", response)
	}
}

func TestClosedConnectionIsIdempotent(t *testing.T) {
	var response map[string]bool
	if err := json.Unmarshal([]byte(Query("close", `{"id":"not-a-live-connection"}`)), &response); err != nil || !response["success"] {
		t.Fatalf("close must be idempotent: %v", err)
	}
}

func TestFilteringAndMalformedRequest(t *testing.T) {
	if !matches(" TrOjAn ", "trojan") || matches("hongkong", "tokyo", "singapore") {
		t.Fatal("filter mismatch")
	}
	for _, payload := range []string{"{", "null"} {
		var response map[string]any
		if err := json.Unmarshal([]byte(Query("unknown", payload)), &response); err != nil || response["error"] == nil {
			t.Fatal("invalid request did not return error")
		}
	}
}
