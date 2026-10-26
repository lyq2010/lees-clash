//go:build cgo

#include "bridge.h"
#include "quickjs/quickjs.h"
#include <stdlib.h>
#include <string.h>
#include <time.h>

static int64_t milliseconds(void) {
    struct timespec value;
    clock_gettime(CLOCK_MONOTONIC, &value);
    return (int64_t)value.tv_sec * 1000 + value.tv_nsec / 1000000;
}

static int interrupted(JSRuntime *runtime, void *opaque) {
    return milliseconds() >= *(int64_t *)opaque;
}

char *lees_run_script(const char *source, size_t source_size, const char *input, size_t input_size, char **error) {
    char *output = NULL;
    *error = NULL;
    JSRuntime *runtime = JS_NewRuntime();
    if (!runtime) { *error = strdup("无法创建脚本运行时"); return NULL; }
    JS_SetMemoryLimit(runtime, 32 * 1024 * 1024);
    JS_SetMaxStackSize(runtime, 512 * 1024);
    int64_t deadline = milliseconds() + 500;
    JS_SetInterruptHandler(runtime, interrupted, &deadline);
    JSContext *ctx = JS_NewContext(runtime);
    if (!ctx) { *error = strdup("脚本内存不足"); JS_FreeRuntime(runtime); return NULL; }
    JSValue compiled = JS_Eval(ctx, source, source_size, "profile.js", JS_EVAL_TYPE_GLOBAL);
    JSValue global = JS_GetGlobalObject(ctx);
    JSValue function = JS_GetPropertyStr(ctx, global, "main");
    JSValue config = JS_UNDEFINED;
    JSValue result = JS_UNDEFINED;
    JSValue encoded = JS_UNDEFINED;
    if (JS_IsException(compiled)) goto failed;
    if (!JS_IsFunction(ctx, function)) { *error = strdup("脚本需要定义 main(config) 函数"); goto done; }
    config = JS_ParseJSON(ctx, input, input_size, "config.json");
    if (JS_IsException(config)) goto failed;
    result = JS_Call(ctx, function, global, 1, &config);
    if (JS_IsException(result)) goto failed;
    if (!JS_IsObject(result) || JS_IsArray(ctx, result) || (int)JS_PromiseState(ctx, result) >= 0) {
        *error = strdup("main(config) 必须同步返回配置对象"); goto done;
    }
    encoded = JS_JSONStringify(ctx, result, JS_UNDEFINED, JS_UNDEFINED);
    if (JS_IsException(encoded)) goto failed;
    size_t size;
    const char *text = JS_ToCStringLen(ctx, &size, encoded);
    if (!text) goto failed;
    if (size > 8 * 1024 * 1024) *error = strdup("脚本输出超过 8 MB");
    else output = strndup(text, size);
    JS_FreeCString(ctx, text);
    goto done;
failed: {
    JSValue exception = JS_GetException(ctx);
    const char *message = JS_ToCString(ctx, exception);
    *error = strndup(message ? message : "脚本执行失败", 512);
    if (message) JS_FreeCString(ctx, message);
    JS_FreeValue(ctx, exception);
}
done:
    JS_FreeValue(ctx, encoded);
    JS_FreeValue(ctx, result);
    JS_FreeValue(ctx, config);
    JS_FreeValue(ctx, function);
    JS_FreeValue(ctx, global);
    JS_FreeValue(ctx, compiled);
    JS_FreeContext(ctx);
    JS_FreeRuntime(runtime);
    return output;
}
