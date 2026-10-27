package diagnostics

// Stamped by the same build action that sets runtime.godebugDefault. Android
// c-shared has an empty environment, and -trimpath omits ldflags from build
// metadata. Device validation additionally checks /proc LazyFree after GC.
var runtimeMemoryAdvice string
var immediatePageRelease = runtimeMemoryAdvice == "madvdontneed=1"
