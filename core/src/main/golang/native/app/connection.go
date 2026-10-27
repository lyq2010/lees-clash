package app

import (
	"github.com/metacubex/mihomo/component/process"
	C "github.com/metacubex/mihomo/constant"
)

// ResolvePackage preserves Android's UID even when a package is absent from the
// installed-app snapshot. Callers can resolve its label with PackageManager.
func ResolvePackage(metadata *C.Metadata) (string, error) {
	uid := int(metadata.Uid)
	if uid == 0 {
		if metadata.RawSrcAddr == nil || metadata.RawDstAddr == nil {
			return "", process.ErrInvalidNetwork
		}
		uid = QuerySocketUid(metadata.RawSrcAddr, metadata.RawDstAddr)
		if uid < 0 {
			return "", process.ErrNotFound
		}
		metadata.Uid = uint32(uid)
	}
	pkg := QueryAppByUid(uid)
	if pkg == "" {
		return "", process.ErrNotFound
	}
	return pkg, nil
}
