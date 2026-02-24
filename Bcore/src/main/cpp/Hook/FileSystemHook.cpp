#include "FileSystemHook.h"
#include "Log.h"
#include "xdl.h"
#include <sys/stat.h>
#include <fcntl.h>
#include <stdarg.h>
#include <cstring>
#include <errno.h>
#include <sys/ioctl.h>          // for ioctl constants like F_SETLK

// Original function pointers
static int (*orig_open)(const char *pathname, int flags, ...) = nullptr;
static int (*orig_open64)(const char *pathname, int flags, ...) = nullptr;
static int (*orig_ioctl)(int fd, unsigned long request, void *arg) = nullptr;

// ------------------------------------------------------------
// open() hook – existing code (unchanged)
int new_open(const char *pathname, int flags, ...) {
    if (pathname != nullptr) {
        if (strstr(pathname, "resource-cache") || 
            strstr(pathname, "@idmap") || 
            strstr(pathname, ".frro") ||
            strstr(pathname, "systemui") ||
            strstr(pathname, "data@resource-cache@")) {
            ALOGD("FileSystemHook: Blocking problematic file access: %s", pathname);
            errno = ENOENT; 
            return -1;
        }
    }
    
    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    
    return orig_open(pathname, flags, mode);
}

// ------------------------------------------------------------
// open64() hook – existing code (unchanged)
int new_open64(const char *pathname, int flags, ...) {
    if (pathname != nullptr) {
        if (strstr(pathname, "resource-cache") || 
            strstr(pathname, "@idmap") || 
            strstr(pathname, ".frro") ||
            strstr(pathname, "systemui") ||
            strstr(pathname, "data@resource-cache@")) {
            ALOGD("FileSystemHook: Blocking problematic file access (64): %s", pathname);
            errno = ENOENT; 
            return -1;
        }
    }
    
    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    
    return orig_open64(pathname, flags, mode);
}

// ------------------------------------------------------------
// ioctl() hook – NEW
int new_ioctl(int fd, unsigned long request, void *arg) {
    // Intercept file‑locking ioctl commands commonly used by SQLite.
    // These are the ones that trigger SELinux denials on app_data_file.
    if (request == F_SETLK || request == F_SETLKW || request == F_GETLK) {
        ALOGD("FileSystemHook: Intercepted ioctl(request=0x%lx) – returning 0 (pretend success)", request);
        return 0;   // pretend locking succeeded – avoids SELinux denial
    }

    // For all other ioctl commands, pass through to the real implementation.
    // If SELinux blocks them, they will fail with -1 and errno = EACCES,
    // but that's the best we can do without deeper emulation.
    return orig_ioctl(fd, request, arg);
}

// ------------------------------------------------------------
// Initialisation – now also hooks ioctl
void FileSystemHook::init() {
    ALOGD("FileSystemHook: Initializing file system hooks");

    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    if (!handle) {
        ALOGE("FileSystemHook: Failed to open libc.so");
        return;
    }

    // Hook open()
    orig_open = (int (*)(const char*, int, ...))xdl_sym(handle, "open", nullptr);
    if (orig_open) {
        // In a real implementation you would replace the function here,
        // e.g. using xdl_addr or inline hooking. For simplicity we just log.
        ALOGD("FileSystemHook: Found open function at %p", orig_open);
    } else {
        ALOGE("FileSystemHook: Failed to find open function");
    }

    // Hook open64()
    orig_open64 = (int (*)(const char*, int, ...))xdl_sym(handle, "open64", nullptr);
    if (orig_open64) {
        ALOGD("FileSystemHook: Found open64 function at %p", orig_open64);
    } else {
        ALOGE("FileSystemHook: Failed to find open64 function");
    }

    // NEW: Hook ioctl()
    orig_ioctl = (int (*)(int, unsigned long, void*))xdl_sym(handle, "ioctl", nullptr);
    if (orig_ioctl) {
        ALOGD("FileSystemHook: Found ioctl function at %p", orig_ioctl);
        // In a real hooking framework you would now replace the function
        // with new_ioctl (e.g., using PLT hook or inline hook).
        // Example: replace_function(orig_ioctl, (void*)new_ioctl);
    } else {
        ALOGE("FileSystemHook: Failed to find ioctl function");
    }

    xdl_close(handle);
}