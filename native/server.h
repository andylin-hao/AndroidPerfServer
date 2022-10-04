#ifndef ANDROIDPERF_H_
#define ANDROIDPERF_H_

#include <thread>

#include <android-base/unique_fd.h>
#include <binder/IServiceManager.h>
#include <utils/Timers.h>

namespace android {

#define LOCAL_SOCKET "androidperf"
#define SERVER_SOCKET "AndroidPerfServer"

class AndroidPerf {
public:
    explicit AndroidPerf(android::IServiceManager* sm) : 
        sm_(sm), 
        surfaceFlingerService(sm_->checkService(String16("SurfaceFlinger"))) 
        {}
    int main();
    
    void dumpLayerListData(int fd);
    void dumpLayerLatency(int fd, String16 layerName);
    void dumpNetworkStats(int fd, String8 data);
    
    void writeMSG(int fd, const void *data, size_t size);
    void readMSG(int fd, String8 *data);
    int  createSocket();
    void handleData(int fd, String8 data);
    void appendPadding(int fd, nsecs_t time);
    void requestFramework(const void * data, size_t size, int outFd);


private:
    android::IServiceManager* sm_;
    sp<IBinder> surfaceFlingerService;
};
}

#endif