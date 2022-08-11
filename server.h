#ifndef ANDROIDPERF_H_
#define ANDROIDPERF_H_

#include <thread>

#include <android-base/unique_fd.h>
#include <binder/IServiceManager.h>
#include <utils/Timers.h>

namespace android {

#define LOCAL_SOCKET "androidperf"

class AndroidPerf {
public:
    explicit AndroidPerf(android::IServiceManager* sm) : 
        sm_(sm), 
        surfaceFlingerService(sm_->checkService(String16("SurfaceFlinger"))) 
        {}
    int main();
    
    void dumpLayerListData(int fd);
    void dumpLayerLatency(int fd, String16 layerName);
    void writeMSG(int fd, const char *data);
    int  createSocket();
    void handleData(int fd, String8 data);
    void appendPadding(int fd, nsecs_t time);


private:
    android::IServiceManager* sm_;
    sp<IBinder> surfaceFlingerService;
};
}

#endif