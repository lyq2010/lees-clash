package diagnostics

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestNetworkProbeValidationAndRealResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/large" {
			w.Write([]byte(strings.Repeat("x", 70*1024)))
			return
		}
		w.Write([]byte(`{"ip":"203.0.113.5","isp":{"asn":"AS64500","isp":"Fixture"},"location":{"country":"Test"},"risk":{"is_proxy":false,"risk_score":0}}`))
	}))
	defer server.Close()
	result, err := probe(Request{URL: server.URL, Direct: true, RecordType: "ip"})
	if err != nil || result.IP != "203.0.113.5" || result.Risk["risk_score"] != float64(0) {
		t.Fatalf("IP response: %+v %v", result, err)
	}
	for _, target := range []string{"file:///etc/passwd", "https://user:password@example.com", "not a URL"} {
		if _, err := probe(Request{URL: target}); err == nil {
			t.Fatal("accepted invalid URL")
		}
	}
	if _, err := probe(Request{URL: server.URL + "/large", Direct: true, RecordType: "ip"}); err == nil {
		t.Fatal("accepted oversized IP response")
	}
}
