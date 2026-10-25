package all

import (
	_ "lees/native/app"
	_ "lees/native/common"
	_ "lees/native/config"
	_ "lees/native/delegate"
	_ "lees/native/platform"
	_ "lees/native/proxy"
	_ "lees/native/tun"
	_ "lees/native/tunnel"

	_ "golang.org/x/sync/semaphore"

	_ "github.com/metacubex/mihomo/log"
)
