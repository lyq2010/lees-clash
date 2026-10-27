package outboundgroup

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"io"
	"net"
	"runtime"
	"runtime/debug"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter/outbound"
	N "github.com/metacubex/mihomo/common/net"
)

func relaySmartPayload(s *Smart, node *smartTestNode, payload []byte, expected [32]byte, pressure bool) error {
	client, inbound := net.Pipe()
	remote, server := net.Pipe()
	done := make(chan struct{})
	serverError := make(chan error, 1)
	conn := newSmartConn(outbound.NewConn(remote, node), s, node.Name(), "relay.example", time.Now())
	go func() { N.Relay(inbound, conn); close(done) }()
	defer func() { client.Close(); server.Close(); <-done }()
	go func() {
		defer server.Close()
		hash := sha256.New()
		if _, err := io.CopyN(hash, server, int64(len(payload))); err != nil {
			serverError <- err
			return
		}
		result := hash.Sum(nil)
		if _, err := server.Write(result[:16]); err != nil {
			serverError <- err
			return
		}
		if pressure {
			runtime.GC()
			debug.FreeOSMemory()
			time.Sleep(100 * time.Millisecond) // Longer than the first-response timeout.
		}
		_, err := server.Write(result[16:])
		serverError <- err
	}()
	if _, err := client.Write(payload); err != nil {
		return err
	}
	var actual [32]byte
	if _, err := io.ReadFull(client, actual[:]); err != nil {
		return err
	}
	if actual != expected {
		return fmt.Errorf("relayed payload checksum mismatch")
	}
	return <-serverError
}

func TestSmartRelayPreservesBulkPayloadAndLongResponse(t *testing.T) {
	s, nodes := smartFixture(t, 1)
	s.timeout = 40 * time.Millisecond
	payload := bytes.Repeat([]byte("smart-stream-data"), 65536)
	if err := relaySmartPayload(s, nodes[0], payload, sha256.Sum256(payload), true); err != nil {
		t.Fatal(err)
	}
}

func BenchmarkSmartRelay8MiB(b *testing.B) {
	s, nodes := smartFixture(b, 1)
	payload := bytes.Repeat([]byte("0123456789abcdef"), 524288)
	expected := sha256.Sum256(payload)
	b.SetBytes(int64(len(payload)))
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if err := relaySmartPayload(s, nodes[0], payload, expected, false); err != nil {
			b.Fatal(err)
		}
	}
}
