package geoasset

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/metacubex/mihomo/component/geodata/router"
	C "github.com/metacubex/mihomo/constant"
	"google.golang.org/protobuf/proto"
)

func TestBundledFormats(t *testing.T) {
	root := filepath.Join("..", "..", "..", "..", "..", "..", "resources", "geodata")
	for id, file := range map[string]string{"mmdb": "geoip.metadb", "asn": "ASN.mmdb", "geosite": "geosite.dat", "mrs": "BundleMRS.7z"} {
		if err := Validate(id, filepath.Join(root, file)); err != nil {
			t.Fatalf("%s: %v", id, err)
		}
	}
}

func TestInvalidImportPreservesDatabase(t *testing.T) {
	root := t.TempDir()
	previous := C.Path.HomeDir()
	C.SetHomeDir(root)
	defer C.SetHomeDir(previous)
	old := []byte("previous database")
	if err := os.WriteFile(filepath.Join(root, "GeoSite.dat"), old, 0600); err != nil {
		t.Fatal(err)
	}
	staged := filepath.Join(root, "incoming")
	if err := os.WriteFile(staged, []byte("<html>failed download</html>"), 0600); err != nil {
		t.Fatal(err)
	}
	for _, id := range []string{"mmdb", "asn", "geosite", "geoip", "mrs", "../other"} {
		if err := Install(id, staged); err == nil {
			t.Fatalf("accepted invalid %s", id)
		}
	}
	current, _ := os.ReadFile(filepath.Join(root, "GeoSite.dat"))
	if !bytes.Equal(old, current) {
		t.Fatal("previous database modified")
	}
}

func TestValidatedReplacementAndDownloadFailure(t *testing.T) {
	root := t.TempDir()
	previous := C.Path.HomeDir()
	C.SetHomeDir(root)
	defer C.SetHomeDir(previous)
	data, err := proto.Marshal(&router.GeoSiteList{Entry: []*router.GeoSite{{CountryCode: "test", Domain: []*router.Domain{{Value: "example.test"}}}}})
	if err != nil {
		t.Fatal(err)
	}
	staged := filepath.Join(root, "incoming")
	if err := os.WriteFile(staged, data, 0600); err != nil {
		t.Fatal(err)
	}
	if err := Install("geosite", staged); err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(503)
		_, _ = w.Write([]byte("unavailable"))
	}))
	defer server.Close()
	override := `{"geox-url":{"geosite":"` + server.URL + `"}}`
	if err := Update("geosite", "", []string{override}); err == nil {
		t.Fatal("accepted failed HTTP download")
	}
	current, _ := os.ReadFile(C.Path.GeoSite())
	if !bytes.Equal(data, current) {
		t.Fatal("failed download replaced database")
	}
}
