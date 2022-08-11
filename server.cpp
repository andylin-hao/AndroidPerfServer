#define LOG_TAG "androidperf"

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
#define MSG_END   "PERF_MSG_END\n"
#define PADDING   "PADDING"

static int nonBlockingSocket(int sfd) {
    int flags;

    flags = fcntl(sfd, F_GETFL, 0);
    if (flags == -1) {
        ALOGE("fcntl failed");
        return -1;
    }

    flags |= O_NONBLOCK;
    if (fcntl(sfd, F_SETFL, flags) == -1) {
        ALOGE("fcntl nonblocking failed");
        return -1;
    }

    return 0;
}

int AndroidPerf::main() {
    struct epoll_event event;
    struct epoll_event *events;
    int socketFd;
    if ((socketFd = createSocket()) < 0) {
        ALOGE("create socket failed");
        exit(EXIT_FAILURE);
    }

    if (listen(socketFd, SOMAXCONN) < 0) {
        ALOGE("failed to listen socket");
        exit(EXIT_FAILURE);
    }

    int epollFd = epoll_create1(0);
    if (epollFd == -1) {
        ALOGE("epoll_create failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }
    event.events = EPOLLIN | EPOLLPRI | EPOLLERR | EPOLLHUP;
    event.data.fd = socketFd;
    if (epoll_ctl(epollFd, EPOLL_CTL_ADD, socketFd, &event) == -1) {
        ALOGE("epoll_ctl failed");
        close(socketFd);
        exit(EXIT_FAILURE);
    }

    /* Buffer where events are returned */
    events = (struct epoll_event *)calloc(MAX_EVENTS, sizeof event);

    /* The event loop */
    while (1) {
        int n, i;

        n = epoll_wait(epollFd, events, MAX_EVENTS, -1);
        for (i = 0; i < n; i++) {
            if (!(events[i].events & EPOLLIN)) {
                continue;
            }

            else if (socketFd == events[i].data.fd) {
                while (1) {
                    struct sockaddr in_addr;
                    socklen_t in_len;
                    int clientFd;

                    in_len = sizeof in_addr;
                    clientFd = accept(socketFd, &in_addr, &in_len);
                    if (clientFd == -1) {
                        if ((errno == EAGAIN) || (errno == EWOULDBLOCK)) {
                            break;
                        } else {
                            ALOGE("accept connection failed");
                            break;
                        }
                    }

                    ALOGD("connection accepted!");
                    if (nonBlockingSocket(clientFd) < 0) {
                        ALOGE("failed to make client socket nonblocking");
                        break;
                    }

                    event.data.fd = clientFd;
                    event.events = EPOLLIN | EPOLLET;
                    if (epoll_ctl(epollFd, EPOLL_CTL_ADD, clientFd, &event) == -1) {
                        ALOGE("epoll_ctl failed");
                        break;
                    }
                }
                continue;
            } else {
                String8 data;

                while (1) {
                    ssize_t count;
                    char buf[512] = {0};

                    count = TEMP_FAILURE_RETRY(read(events[i].data.fd, buf, sizeof buf));
                    if (count == -1) {
                        if (errno != EAGAIN) {
                            ALOGE("read failed");
                        }
                        break;
                    } else if (count == 0) {
                        break;
                    }
                    data += String8(buf);
                }

                ALOGD("data received: %s", data.string());
                handleData(events[i].data.fd, data);
            }
        }
    }

    free(events);
    close(socketFd);

    return 0;
}

void AndroidPerf::dumpLayerListData(int fd) {
    Vector<String16> argList;
    argList.add(String16("--list"));
    surfaceFlingerService->dump(fd, argList);
    write(fd, MSG_END, sizeof(MSG_END) - 1);
}

void AndroidPerf::dumpLayerLatency(int fd, String16 layerName) {
    Vector<String16> argLatency;
    argLatency.add(String16("--latency"));
    argLatency.add(layerName);
    surfaceFlingerService->dump(fd, argLatency);
    nsecs_t now = systemTime(CLOCK_MONOTONIC);
    appendPadding(fd, now);
    write(fd, MSG_END, sizeof(MSG_END) - 1);
}

int AndroidPerf::createSocket() {
    int socketFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (socket_local_server_bind(socketFd, LOCAL_SOCKET, ANDROID_SOCKET_NAMESPACE_ABSTRACT) < 0) {
        ALOGE("failed to create socket");
        return -1;
    }

    if (nonBlockingSocket(socketFd) < 0) {
        ALOGE("failed to make socket nonblocking");
        return -1;
    }

    return socketFd;
}

void AndroidPerf::handleData(int fd, String8 data) {
    int sfd[2];
    pipe(sfd);
    unique_fd local_end(sfd[0]);
    unique_fd remote_end(sfd[1]);

    if (data.contains("list")) {
        dumpLayerListData(remote_end.get());
        while(splice(local_end.get(), NULL, fd, NULL, SEND_SIZE, SPLICE_F_MORE|SPLICE_F_NONBLOCK) == SEND_SIZE){}
    } else if (data.contains("latency")) {
        dumpLayerLatency(remote_end.get(), String16(data.string() + strlen("latency") + 1));
        while(splice(local_end.get(), NULL, fd, NULL, SEND_SIZE, SPLICE_F_MORE|SPLICE_F_NONBLOCK) == SEND_SIZE){}
    } else if (data.contains("PING")) {
        writeMSG(fd, "OKAY");
    }
}

void AndroidPerf::writeMSG(int fd, const char *data) {
    write(fd, data, strlen(data));
    write(fd, MSG_END, sizeof(MSG_END) - 1);
}

void AndroidPerf::appendPadding(int fd, nsecs_t time) {
    std::string padding;
    base::StringAppendF(&padding, "%s" "\t%" PRId64 "\n", PADDING, time);
    write(fd, padding.c_str(), padding.size());
}
