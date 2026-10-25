package main

//#include "bridge.h"
import "C"

import "cfa/native/diagnostics"

//export queryDiagnostics
func queryDiagnostics(operation, payload C.c_string) *C.char {
	return C.CString(diagnostics.Query(C.GoString(operation), C.GoString(payload)))
}
