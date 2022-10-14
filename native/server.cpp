#define LOG_TAG "AndroidPerf"

#include "server.h"

#include <errno.h>
#include <fcntl.h>
#include <getopt.h>
#include <log/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/sendfile.h>
#include <unistd.h>
#include <netinet/in.h>
#include <inttypes.h>

#include <android-base/file.h>
#include <android-base/unique_fd.h>
#include <android-base/stringprintf.h>
#include <cutils/sockets.h>
#include <utils/String8.h>
#include <utils/String16.h>

#include <chrono>
#include <thread>

using namespace android;

using ::android::base::unique_fd;
using ::android::base::WriteFully;

#define MAX_EVENTS 64
#define SEND_SIZE 1024
#define MSG_END "PERF_MSG_END\n"
#define PADDING "PADDING"

static int nonBlockingSocket(int sfd)
{
    int flags;

    flags = fcntl(sfd, F_GETFL, 0);
    if (flags == -1)
    {
        ALOGE("fcntl failed");
        return -1;
    }

    flags |= O_NONBLOCK;
    if (fcntl(sfd, F_SETFL, flags) == -1)
    {
        ALOGE("fcntl nonblocking failed");
        return -1;
    }

    return 0;
}

int AndroidPerf::main()
{
    pid_t pid = fork();
    if (pid == 0)
    {
        execl("/data/local/tmp/AndroidPerfServerFW", (char *)NULL);
    }

    else
    {
        struct epoll_event event;
        struct epoll_event *events;
        int socketFd;
        if ((socketFd = createSocket()) < 0)
        {
            ALOGE("create socket failed");
            exit(EXIT_FAILURE);
        }

        if (listen(socketFd, SOMAXCONN) < 0)
        {
            ALOGE("failed to listen socket");
            exit(EXIT_FAILURE);
        }

        epollFd = epoll_create1(0);
        if (epollFd == -1)
        {
            ALOGE("epoll_create failed");
            close(socketFd);
            exit(EXIT_FAILURE);
        }
        event.events = EPOLLIN | EPOLLPRI | EPOLLERR | EPOLLHUP;
        event.data.fd = socketFd;
        if (epoll_ctl(epollFd, EPOLL_CTL_ADD, socketFd, &event) == -1)
        {
            ALOGE("epoll_ctl failed");
            close(socketFd);
            exit(EXIT_FAILURE);
        }

        /* Buffer where events are returned */
        events = (struct epoll_event *)calloc(MAX_EVENTS, sizeof event);

        /* The event loop */
        while (1)
        {
            int n, i;

            n = epoll_wait(epollFd, events, MAX_EVENTS, -1);
            for (i = 0; i < n; i++)
            {
                if (!(events[i].events & EPOLLIN))
                {
                    continue;
                }

                else if (socketFd == events[i].data.fd)
                {
                    while (1)
                    {
                        struct sockaddr in_addr;
                        socklen_t in_len;
                        int clientFd;

                        in_len = sizeof in_addr;
                        clientFd = accept(socketFd, &in_addr, &in_len);
                        if (clientFd == -1)
                        {
                            if ((errno == EAGAIN) || (errno == EWOULDBLOCK))
                            {
                                break;
                            }
                            else
                            {
                                ALOGE("accept connection failed");
                                break;
                            }
                        }

                        if (addEpollFd(clientFd) < 0)
                        {
                            ALOGE("failed to add client fd to epoll");
                            break;
                        }
                    }
                    continue;
                }
                else
                {
                    ssize_t count;
                    char *res = readMSG(events[i].data.fd, &count);
                    if (count > (long)sizeof(MSG_END) - 1)
                    {
                        String8 data(res, count - sizeof(MSG_END) + 1);
                        ALOGD("data received: %s", data.string());
                        handleData(events[i].data.fd, data);
                    }
                    free(res);
                }
            }
        }

        free(events);
        close(socketFd);
    }

    return 0;
}

void AndroidPerf::dumpLayerListData(int fd)
{
    Vector<String16> argList;
    argList.add(String16("--list"));
    surfaceFlingerService->dump(fd, argList);
    write(fd, MSG_END, sizeof(MSG_END) - 1);
}

void AndroidPerf::dumpLayerLatency(int fd, String16 layerName)
{
    Vector<String16> argLatency;
    argLatency.add(String16("--latency"));
    argLatency.add(layerName);
    surfaceFlingerService->dump(fd, argLatency);
    nsecs_t now = systemTime(CLOCK_MONOTONIC);
    appendPadding(fd, now);
    write(fd, MSG_END, sizeof(MSG_END) - 1);
}

void AndroidPerf::dumpNetworkStats(int fd, String8 data)
{
    if (FILE *file = fopen("/proc/net/xt_qtaguid/stats", "r"))
    {
        const char *uid = data.string() + sizeof("network");
        char *line = (char *)malloc(1024);
        size_t len = 0;
        ssize_t read;
        long netStats[4] = {0};
        while ((read = getline(&line, &len, file)) != -1)
        {
            char *token = strtok(line, " ");
            int count = 0;
            while (token)
            {
                if (count == 3 && strcmp(token, uid) != 0)
                    break;
                if (count >= 5 && count <= 8)
                {
                    netStats[count - 5] += atol(token);
                    if (count == 8)
                    {
                        break;
                    }
                }
                token = strtok(NULL, " ");
                count++;
            }
        }
        write(fd, netStats, sizeof(netStats));
        write(fd, MSG_END, sizeof(MSG_END) - 1);
        free(line);
        fclose(file);
    }
    else
    {
        requestFramework(data.string(), data.size(), fd);
    }
}

int AndroidPerf::createSocket()
{
    int socketFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (socket_local_server_bind(socketFd, LOCAL_SOCKET, ANDROID_SOCKET_NAMESPACE_ABSTRACT) < 0)
    {
        ALOGE("failed to create socket");
        return -1;
    }

    if (nonBlockingSocket(socketFd) < 0)
    {
        ALOGE("failed to make socket nonblocking");
        return -1;
    }

    return socketFd;
}

void AndroidPerf::handleData(int fd, String8 data)
{
    int sfd[2];
    pipe(sfd);
    unique_fd local_end(sfd[0]);
    unique_fd remote_end(sfd[1]);

    if (data.contains("list"))
    {
        dumpLayerListData(remote_end.get());
        while (splice(local_end.get(), NULL, fd, NULL, SEND_SIZE, SPLICE_F_MORE | SPLICE_F_NONBLOCK) == SEND_SIZE)
        {
        }
    }
    else if (data.contains("latency"))
    {
        dumpLayerLatency(remote_end.get(), String16(data.string() + strlen("latency") + 1));
        while (splice(local_end.get(), NULL, fd, NULL, SEND_SIZE, SPLICE_F_MORE | SPLICE_F_NONBLOCK) == SEND_SIZE)
        {
        }
    }
    else if (data.contains("network"))
    {
        dumpNetworkStats(fd, data);
    }
    else if (data.contains("PING_FW"))
    {
        requestFramework(data.string(), data.size(), fd);
    }
    else if (data.contains("PING"))
    {
        writeMSG(fd, "OKAY", 4);
    }
}

void AndroidPerf::writeMSG(int fd, const void *data, size_t size)
{
    write(fd, data, size);
    write(fd, MSG_END, sizeof(MSG_END) - 1);
}

char *AndroidPerf::readMSG(int fd, ssize_t *count)
{
    // TODO deal with buffer overflow
    char *buf = (char *)calloc(4096, sizeof(char));
    *count = 0;
    while (1)
    {
        int len = TEMP_FAILURE_RETRY(read(fd, buf + *count, 4096));
        if (len < 0)
        {
            if (errno != EAGAIN)
            {
                ALOGE("read failed");
            }
            break;
        }
        else
        {
            if (len == 0)
            {
                break;
            }
            *count += len;
            if (*count > (long)sizeof(MSG_END) - 1)
            {
                const char *bufData = (const char *)buf;
                bufData += (*count - sizeof(MSG_END) + 1);
                String8 bufStr(bufData, sizeof(MSG_END) - 1);
                if (bufStr.contains(MSG_END))
                {
                    break;
                }
            }
        }
    }
    return buf;
}

void AndroidPerf::appendPadding(int fd, nsecs_t time)
{
    std::string padding;
    base::StringAppendF(&padding, "%s"
                                  "\t%" PRId64 "\n",
                        PADDING, time);
    write(fd, padding.c_str(), padding.size());
}

int AndroidPerf::addEpollFd(int fd)
{
    struct epoll_event event;
    if (nonBlockingSocket(fd) < 0)
    {
        ALOGE("failed to make socket nonblocking");
        return -1;
    }

    event.data.fd = fd;
    event.events = EPOLLIN | EPOLLET;
    if (epoll_ctl(epollFd, EPOLL_CTL_ADD, fd, &event) == -1)
    {
        ALOGE("epoll_ctl failed");
        return -1;
    }

    return 0;
}

void AndroidPerf::requestFramework(const void *data, size_t size, int outFd)
{
    int fwFd = socket_local_client(FW_SOCKET, ANDROID_SOCKET_NAMESPACE_ABSTRACT, SOCK_STREAM);

    if (fwFd > 0)
    {
        writeMSG(fwFd, data, size);
        ssize_t count;
        char *res = readMSG(fwFd, &count);
        close(fwFd);
        write(outFd, res, count);
        free(res);
    }
    else
    {
        ALOGE("failed to connect fw");
        writeMSG(outFd, "Failed", 6);
    }
}
