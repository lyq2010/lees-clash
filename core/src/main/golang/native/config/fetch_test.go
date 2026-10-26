package config

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestSubscriptionHTTPStatusBeforeParsing(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/failure" {
			w.WriteHeader(http.StatusInternalServerError)
			io.WriteString(w, "private error body")
			return
		}
		w.Header().Set("subscription-userinfo", "upload=1; download=2; total=3")
		io.WriteString(w, "proxies: []")
	}))
	defer server.Close()
	reader, _, err := openUrl(context.Background(), server.URL+"/failure")
	if reader != nil || err == nil || !strings.Contains(err.Error(), "HTTP 500") || strings.Contains(err.Error(), "private") {
		t.Fatalf("server error was not classified: %v", err)
	}
	reader, header, err := openUrl(context.Background(), server.URL+"/success")
	if err != nil {
		t.Fatal(err)
	}
	defer reader.Close()
	data, _ := io.ReadAll(reader)
	if string(data) != "proxies: []" || header.SubscriptionUserInfo == "" {
		t.Fatal("successful configuration or metadata lost")
	}
}
