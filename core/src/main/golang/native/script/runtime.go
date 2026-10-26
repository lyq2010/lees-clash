//go:build cgo

package script

/*
#cgo CFLAGS: -D_GNU_SOURCE -Wno-implicit-const-int-float-conversion
#cgo LDFLAGS: -lm
#include <stdlib.h>
#include "bridge.h"
*/
import "C"

import (
	"fmt"
	"unsafe"
)

// Run creates a short-lived VM. No filesystem, network or host functions are exposed.
func Run(source string, input []byte) ([]byte, error) {
	if len(source) > 128*1024 || len(input) > 8*1024*1024 {
		return nil, fmt.Errorf("脚本或配置超过大小限制")
	}
	code := C.CString(source)
	data := C.CString(string(input))
	defer C.free(unsafe.Pointer(code))
	defer C.free(unsafe.Pointer(data))
	var message *C.char
	output := C.lees_run_script(code, C.size_t(len(source)), data, C.size_t(len(input)), &message)
	if message != nil {
		defer C.free(unsafe.Pointer(message))
		return nil, fmt.Errorf("配置脚本失败: %s", C.GoString(message))
	}
	if output == nil {
		return nil, fmt.Errorf("脚本没有返回配置")
	}
	defer C.free(unsafe.Pointer(output))
	return []byte(C.GoString(output)), nil
}
