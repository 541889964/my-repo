#include <jni.h>
#include <dlfcn.h>
#include <cstring>
#include <cstdint>

using PoWFunc = long long (*)(const char*, int, const char*, int, long long);
static PoWFunc g_pow = nullptr;

extern "C" JNIEXPORT jlong JNICALL
Java_com_superrouter_app_NativeLib_nativeCalculate(
    JNIEnv* env, jclass,
    jstring so_path, jstring challenge_, jstring base_, jlong difficulty
) {
    if (!g_pow) {
        const char* path = env->GetStringUTFChars(so_path, nullptr);
        void* h = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        env->ReleaseStringUTFChars(so_path, path);
        if (!h) return -1;

        void* anchor = dlsym(h,
            "Java_com_deepseek_crypto_PowCalculator_nativeCalculateDeepSeekHashV1Pow");
        if (!anchor) return -2;

        Dl_info info;
        dladdr(anchor, &info);
        g_pow = (PoWFunc)((uintptr_t)info.dli_fbase + 0x19bc0);
    }

    const char* c = env->GetStringUTFChars(challenge_, 0);
    const char* b = env->GetStringUTFChars(base_, 0);
    long long r = g_pow(c, (int)strlen(c), b, (int)strlen(b), (long long)difficulty);
    env->ReleaseStringUTFChars(challenge_, c);
    env->ReleaseStringUTFChars(base_, b);
    return r;
}
