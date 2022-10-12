#ifndef ANDROIDPERF_H_
#define ANDROIDPERF_H_

#include <thread>

#include <android-base/unique_fd.h>
#include <binder/IServiceManager.h>
#include <utils/Timers.h>
#include <jni.h>

namespace android {

#define LOCAL_SOCKET "AndroidPerf"
#define FW_SOCKET "AndroidPerfFW"

class AndroidPerf {
public:
    explicit AndroidPerf(android::IServiceManager* sm, JNIEnv *jniEnv, jobject& server) : 
        sm_(sm), 
        env(jniEnv),
        serverInstance(server),
        surfaceFlingerService(sm_->checkService(String16("SurfaceFlinger"))) 
        {}
    int main();
    
    void dumpLayerListData(int fd);
    void dumpLayerLatency(int fd, String16 layerName);
    void dumpNetworkStats(int fd, String8 data);
    
    void writeMSG(int fd, const void *data, size_t size);
    char* readMSG(int fd, ssize_t *count);
    int  createSocket();
    int  addEpollFd(int fd);

    void handleData(int fd, String8 data);
    void appendPadding(int fd, nsecs_t time);
    void requestFramework(const char * data, int outFd);


private:
    android::IServiceManager* sm_;
    JNIEnv *env;
    jobject &serverInstance;
    sp<IBinder> surfaceFlingerService;
    int epollFd;
};
}

#endif