package diagnostics

import (
	"encoding/json"
	"testing"

	"github.com/metacubex/mihomo/tunnel/statistic"
)

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
	}
	if err := json.Unmarshal([]byte(Query("traffic", "{}")), &response); err != nil {
		t.Fatal(err)
	}
	if response.Upload != upload || response.Download != download {
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
