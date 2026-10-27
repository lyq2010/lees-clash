package app

import (
	"strconv"
	"strings"
	"sync"
	"time"
)

var appVersionName string
var platformVersion int
var installedAppsUid = map[int]string{}
var installedAppsMu sync.RWMutex

func ApplyVersionName(versionName string) {
	appVersionName = versionName
}

func ApplyPlatformVersion(version int) {
	platformVersion = version
}

func VersionName() string {
	return appVersionName
}

func PlatformVersion() int {
	return platformVersion
}

func NotifyInstallAppsChanged(uidList string) {
	uids := map[int]string{}

	for _, item := range strings.Split(uidList, ",") {
		kv := strings.Split(item, ":")
		if len(kv) == 2 {
			uid, err := strconv.Atoi(kv[0])
			if err != nil {
				continue
			}

			uids[uid] = kv[1]
		}
	}

	installedAppsMu.Lock()
	installedAppsUid = uids
	installedAppsMu.Unlock()
}

func QueryAppByUid(uid int) string {
	installedAppsMu.RLock()
	defer installedAppsMu.RUnlock()
	return installedAppsUid[uid]
}

func NotifyTimeZoneChanged(name string, offset int) {
	time.Local = time.FixedZone(name, offset)
}
