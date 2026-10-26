//go:build !cgo

// Host-only pure-Go checks do not compile the Android C runtime.
package script

import "fmt"

func Run(source string, input []byte) ([]byte, error) {
	return nil, fmt.Errorf("此构建未启用配置脚本引擎，需要 Android CGo 运行时")
}
