package preview

import (
	"crypto/sha256"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sync"
)

var cache struct {
	sync.Mutex
	path    string
	stamp   string
	profile *Profile
}

// Running configurations own their data; do not retain a second stopped preview.
func ClearCache() {
	cache.Lock()
	defer cache.Unlock()
	cache.path, cache.stamp, cache.profile = "", "", nil
}

// Reuse immutable parsed data across group queries. Local file updates invalidate it.
func Read(path, secretKey string) (*Profile, error) {
	hash := sha256.New()
	// A key change must invalidate decrypted cached data even if the file is unchanged.
	fmt.Fprintf(hash, "%s\x00", secretKey)
	err := filepath.WalkDir(path, func(name string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		fmt.Fprintf(hash, "%s\x00%d\x00%d\n", name, info.Size(), info.ModTime().UnixNano())
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("无法读取所选配置")
	}
	stamp := fmt.Sprintf("%x", hash.Sum(nil))
	cache.Lock()
	defer cache.Unlock()
	if cache.profile != nil && cache.path == path && cache.stamp == stamp {
		return cache.profile, nil
	}
	if _, err := os.Stat(filepath.Join(path, "config.yaml")); err != nil {
		return nil, fmt.Errorf("无法读取所选配置")
	}
	profile, err := readProfile(path, secretKey)
	if err != nil {
		return nil, err
	}
	cache.path, cache.stamp, cache.profile = path, stamp, profile
	return profile, nil
}
