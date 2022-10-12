#define LOG_TAG "AndroidPerfJNI"

#include <jni.h>
#include <string>

#include <signal.h>
#include <stdio.h>
#include <sys/stat.h>
#include <unistd.h>
#include <log/log.h>

extern "C" JNIEXPORT void JNICALL
Java_com_androidperf_server_AndroidPerfServerFW_hello() {
    printf("Hello world");
    daemon(1, 0);
    signal(SIGPIPE, SIG_IGN);
    while (true) {
        ALOGD("Hello Jni");
        sleep(1);
    }
}

