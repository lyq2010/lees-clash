//go:build cgo

package script

import (
	"bytes"
	"testing"
	"time"
)

func TestConfigScriptBoundaries(t *testing.T) {
	result, err := Run(`function main(config) { config.mode = "direct"; return config; }`, []byte(`{"mode":"rule"}`))
	if err != nil || !bytes.Contains(result, []byte(`"direct"`)) {
		t.Fatalf("transform: %s %v", result, err)
	}
	for _, source := range []string{
		`function main(c) { throw new Error("invalid"); }`,
		`function main(c) { return undefined; }`,
		`function main(c) { return Promise.resolve(c); }`,
		`function main(c) { return require("fs"); }`,
		`function main(c) { while(true){} }`,
		`function main(c) { c.x = new Uint8Array(128 * 1024 * 1024); return c; }`,
	} {
		start := time.Now()
		if _, err := Run(source, []byte(`{}`)); err == nil {
			t.Fatal("accepted invalid or unbounded script")
		}
		if time.Since(start) > 2*time.Second {
			t.Fatal("script did not stop promptly")
		}
	}
}
