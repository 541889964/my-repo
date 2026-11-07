
        #include <stdio.h>
        #include <stdlib.h>
        #include <string.h>
        #include <dlfcn.h>
        #include <stdint.h>
        int main(int argc, char** argv) {
            if (argc != 4) return -1;
            char* base = argv[1]; char* challenge = argv[2]; long long difficulty = atoll(argv[3]);
            void* handle = dlopen("/data/data/com.termux/files/home/ds_audit/apktool/lib/arm64-v8a/librscrypto.so", RTLD_NOW | RTLD_LOCAL);
            if (!handle) return 1;
            void* export_func = dlsym(handle, "Java_com_deepseek_crypto_PowCalculator_nativeCalculateDeepSeekHashV1Pow");
            if (!export_func) return 2;
            Dl_info info; dladdr(export_func, &info);
            uintptr_t target_addr = (uintptr_t)info.dli_fbase + 0x19bc0;
            typedef long long (*core_hash_t)(const char*, int, const char*, int, long long);
            core_hash_t core_hash = (core_hash_t)target_addr;
            long long result = core_hash(challenge, strlen(challenge), base, strlen(base), difficulty);
            printf("%lld\n", result); return 0;
        }
        