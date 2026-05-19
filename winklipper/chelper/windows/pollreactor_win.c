// Windows-compatible version of pollreactor.c
// Replaces POSIX poll() with Windows WSAPoll() and fcntl() with Windows equivalents

#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #include <windows.h>
    #pragma comment(lib, "ws2_32.lib")
#else
    #include <fcntl.h>
    #include <poll.h>
#endif

#include "pollreactor.h"
#include "pyhelper.h"

struct pollreactor_timer {
    double waketime;
    double (*callback)(void *data, double eventtime);
};

struct pollreactor {
    int num_fds, num_timers, must_exit;
    void *callback_data;
    double next_timer;
#ifdef _WIN32
    struct pollfd *fds;
    HANDLE *handles;  // Windows handles for WaitForMultipleObjects
#else
    struct pollfd *fds;
#endif
    void (**fd_callbacks)(void *data, double eventtime);
    struct pollreactor_timer *timers;
};

// Allocate a new 'struct pollreactor' object
struct pollreactor *
pollreactor_alloc(int num_fds, int num_timers, void *callback_data)
{
    struct pollreactor *pr = malloc(sizeof(*pr));
    memset(pr, 0, sizeof(*pr));
    pr->num_fds = num_fds;
    pr->num_timers = num_timers;
    pr->must_exit = 0;
    pr->callback_data = callback_data;
    pr->next_timer = PR_NEVER;

    pr->fds = malloc(num_fds * sizeof(*pr->fds));
    memset(pr->fds, 0, num_fds * sizeof(*pr->fds));

#ifdef _WIN32
    pr->handles = malloc(num_fds * sizeof(*pr->handles));
    memset(pr->handles, 0, num_fds * sizeof(*pr->handles));
#endif

    pr->fd_callbacks = malloc(num_fds * sizeof(*pr->fd_callbacks));
    memset(pr->fd_callbacks, 0, num_fds * sizeof(*pr->fd_callbacks));
    pr->timers = malloc(num_timers * sizeof(*pr->timers));
    memset(pr->timers, 0, num_timers * sizeof(*pr->timers));

    int i;
    for (i = 0; i < num_timers; i++)
        pr->timers[i].waketime = PR_NEVER;

    return pr;
}

// Free resources associated with a 'struct pollreactor' object
void
pollreactor_free(struct pollreactor *pr)
{
    free(pr->fds);
    pr->fds = NULL;
#ifdef _WIN32
    free(pr->handles);
    pr->handles = NULL;
#endif
    free(pr->fd_callbacks);
    pr->fd_callbacks = NULL;
    free(pr->timers);
    pr->timers = NULL;
    free(pr);
}

// Add a callback for when a file descriptor (fd) becomes readable
void
pollreactor_add_fd(struct pollreactor *pr, int pos, int fd, void *callback, int write_only)
{
    pr->fds[pos].fd = fd;
    pr->fds[pos].events = write_only ? POLLOUT : POLLIN;
    pr->fds[pos].revents = 0;
    pr->fd_callbacks[pos] = callback;

#ifdef _WIN32
    // On Windows, we need to track handles separately
    // For now, store the fd as-is (works for sockets)
    pr->handles[pos] = (HANDLE)_get_osfhandle(fd);
#endif
}

// Add a timer callback
void
pollreactor_add_timer(struct pollreactor *pr, int pos, void *callback)
{
    pr->timers[pos].callback = callback;
    pr->timers[pos].waketime = PR_NEVER;
}

// Return the last schedule wake-up time for a timer
double
pollreactor_get_timer(struct pollreactor *pr, int pos)
{
    return pr->timers[pos].waketime;
}

// Set the wake-up time for a given timer
void
pollreactor_update_timer(struct pollreactor *pr, int pos, double waketime)
{
    pr->timers[pos].waketime = waketime;
    if (waketime < pr->next_timer)
        pr->next_timer = waketime;
}

// Internal code to invoke timer callbacks
static int
pollreactor_check_timers(struct pollreactor *pr, double eventtime, int busy)
{
    if (eventtime >= pr->next_timer) {
        pr->next_timer = PR_NEVER;
        int i;
        for (i = 0; i < pr->num_timers; i++) {
            struct pollreactor_timer *timer = &pr->timers[i];
            double t = timer->waketime;
            if (eventtime >= t) {
                busy = 1;
                t = timer->callback(pr->callback_data, eventtime);
                timer->waketime = t;
            }
            if (t < pr->next_timer)
                pr->next_timer = t;
        }
    }
    if (busy)
        return 0;

    double timeout = ceil((pr->next_timer - eventtime) * 1000.);
    return timeout < 1. ? 1 : (timeout > 1000. ? 1000 : (int)timeout);
}

// Repeatedly check for timer and fd events and invoke their callbacks
void
pollreactor_run(struct pollreactor *pr)
{
    double eventtime = get_monotonic();
    int busy = 1;

    while (!pr->must_exit) {
        int timeout = pollreactor_check_timers(pr, eventtime, busy);
        busy = 0;

#ifdef _WIN32
        // Use WSAPoll on Windows (available on Vista+)
        int ret = WSAPoll(pr->fds, pr->num_fds, timeout);
#else
        int ret = poll(pr->fds, pr->num_fds, timeout);
#endif

        eventtime = get_monotonic();

        if (ret > 0) {
            busy = 1;
            int i;
            for (i = 0; i < pr->num_fds; i++)
                if (pr->fds[i].revents)
                    pr->fd_callbacks[i](pr->callback_data, eventtime);
        } else if (ret < 0) {
            report_errno("poll", ret);
            pr->must_exit = 1;
        }
    }
}

// Request that a currently running pollreactor_run() loop exit
void
pollreactor_do_exit(struct pollreactor *pr)
{
    pr->must_exit = 1;
}

// Check if a pollreactor_run() loop has been requested to exit
int
pollreactor_is_exit(struct pollreactor *pr)
{
    return pr->must_exit;
}

// Set a file descriptor as non-blocking
int
fd_set_non_blocking(int fd)
{
#ifdef _WIN32
    u_long mode = 1;
    int ret = ioctlsocket((SOCKET)_get_osfhandle(fd), FIONBIO, &mode);
    if (ret != 0) {
        report_errno("ioctlsocket", ret);
        return -1;
    }
    return 0;
#else
    int flags = fcntl(fd, F_GETFL);
    if (flags < 0) {
        report_errno("fcntl getfl", flags);
        return -1;
    }
    int ret = fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    if (ret < 0) {
        report_errno("fcntl setfl", flags);
        return -1;
    }
    return 0;
#endif
}
