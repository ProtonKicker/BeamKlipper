// Windows-compatible version of pyhelper.c
// Replaces Linux-specific calls with Windows equivalents

#include <errno.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#ifdef _WIN32
    #include <windows.h>
    // Thread naming via Windows API
    typedef HRESULT (WINAPI *SetThreadDescription_t)(HANDLE, PCWSTR);
#else
    #include <sys/prctl.h>
#endif

#include "compiler.h"
#include "pyhelper.h"

// Return the monotonic system time as a double
double __visible
get_monotonic(void)
{
#ifdef _WIN32
    // Use QueryPerformanceCounter for high-resolution monotonic time
    static LARGE_INTEGER frequency = {0};
    static LARGE_INTEGER start = {0};
    LARGE_INTEGER current;

    if (frequency.QuadPart == 0) {
        QueryPerformanceFrequency(&frequency);
        QueryPerformanceCounter(&start);
    }

    QueryPerformanceCounter(&current);
    double elapsed = (double)(current.QuadPart - start.QuadPart) / (double)frequency.QuadPart;
    return elapsed;
#else
    struct timespec ts;
    int ret = clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    if (ret) {
        report_errno("clock_gettime", ret);
        return 0.;
    }
    return (double)ts.tv_sec + (double)ts.tv_nsec * .000000001;
#endif
}

// Fill a 'struct timespec' with a system time stored in a double
struct timespec
fill_time(double time)
{
    time_t t = (time_t)time;
    struct timespec ts;
    ts.tv_sec = t;
    ts.tv_nsec = (long)((time - t) * 1000000000.);
    return ts;
}

static void
default_logger(const char *msg)
{
    fprintf(stderr, "%s\n", msg);
}

static void (*python_logging_callback)(const char *msg) = default_logger;

void __visible
set_python_logging_callback(void (*func)(const char *))
{
    python_logging_callback = func;
}

// Log an error message
void
errorf(const char *fmt, ...)
{
    char buf[512];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    buf[sizeof(buf)-1] = '\0';
    python_logging_callback(buf);
}

// Report 'errno' in a message written to stderr
void
report_errno(char *where, int rc)
{
    int e = errno;
    errorf("Got error %d in %s: (%d)%s", rc, where, e, strerror(e));
}

// Return a hex character for a given number
#define GETHEX(x) ((x) < 10 ? '0' + (x) : 'a' + (x) - 10)

// Translate a binary string into an ASCII string with escape sequences
char *
dump_string(char *outbuf, int outbuf_size, char *inbuf, int inbuf_size)
{
    char *outend = &outbuf[outbuf_size-5], *o = outbuf;
    uint8_t *inend = (void*)&inbuf[inbuf_size], *p = (void*)inbuf;
    while (p < inend && o < outend) {
        uint8_t c = *p++;
        if (c > 31 && c < 127 && c != '\\') {
            *o++ = c;
            continue;
        }
        *o++ = '\\';
        *o++ = 'x';
        *o++ = GETHEX(c >> 4);
        *o++ = GETHEX(c & 0x0f);
    }
    *o = '\0';
    return outbuf;
}

// Set custom thread names
int __visible
set_thread_name(char name[16])
{
#ifdef _WIN32
    // Windows 10 1607+ supports SetThreadDescription
    HMODULE kernel32 = GetModuleHandleA("kernel32.dll");
    if (kernel32) {
        SetThreadDescription_t SetThreadDescription =
            (SetThreadDescription_t)GetProcAddress(kernel32, "SetThreadDescription");
        if (SetThreadDescription) {
            wchar_t wname[16];
            mbstowcs(wname, name, 16);
            wname[15] = L'\0';
            SetThreadDescription(GetCurrentThread(), wname);
            return 0;
        }
    }
    // Fallback: older Windows doesn't support thread naming from user space easily
    return -1;
#else
    return prctl(PR_SET_NAME, name);
#endif
}
