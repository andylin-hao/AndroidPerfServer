# AndroidPerfServer

该部分实现了Android端的server component。Server component为一个能在Android中执行的shell程序，其通过binder与SurfaceFlinger连接，从而以极低的开销获取性能数据，并构建一个Unix domain socket来与PC的client端进行通信连接。

## 源码结构

* `framework/AndroidPerfServer.java`

    Framework层的server模块，主要负责与NetworkStatsManager对接，获取eBPF统计的应用流量信息。

* `jni/server_jni.cpp`
    
    JNI层函数nativeMain和nativeWrite。负责获取SurfaceFlinger的binder，并启动Server的main函数以及fork执行framework层的server模块。

* `jni/server.cpp`

    创建一个名为`@androidperf`的Unix domain socket作为server socket，并创建epoll event循环来监听client连接与通信。client连接建立后，基于client发送的指令来使用binder获取性能数据。性能数据通过构建pipe和splice通道直接对接socket，在内核态完成通信连接，以实现零拷贝传输开销。

## 编译

Server的编译需要在AOSP的编译工具链中完成，为此首先需要下载AOSP的源码（建议android-7.1.2_r33分支），具体教程参见[这里](https://source.android.com/setup/build/downloading)。 

具体编译流程为首先完成对整个AOSP的编译（source build/envsetup.sh&lunch，然后选择aosp_arm64或者aosp_x86_64，具体看需要编译ARM还是x86平台的可执行程序)，然后将AndroidPerfServer的源码放于`frameworks/base/cmds`目录下（其它目录理论上也可以，只要能正常编译就行）。

执行`mmm frameworks/base/cmds/AndroidPerfServer/`。

## 使用

编译产生的server程序包含两部分，一部分为framework层的Java程序，一部分为native层的jni动态库。

1. 对于framework层的Java程序，编译产生了一个dex二进制，一个shell脚本。其中dex二进制位于Android编译目录的`out/target/common/obj/JAVA_LIBRARIES/AndroidPerfServerlib_intermediates`，名为`classes.dex`，将其改名、复制并替换掉AndroidPerf目录的`android`子目录下的`AndroidPerfServer.dex`文件。shell脚本位于`out/target/product/generic_x86_64(也可能是arm，视编译目标而定)/system/bin`，名为`AndroidPerfServer`，同样将其复制并替换掉AndroidPerf目录的`android`子目录下的同名文件

2. 对于native层的动态库，可以在`out/target/product/generic_x86_64(也可能是arm，视编译目标而定)/system/lib`以及`out/target/product/generic_x86_64(也可能是arm，视编译目标而定)/system/lib64`目录下找到`libandroidperf.so`动态链接库，分别为32位和64位平台下的程序。编译完成后将文件替换掉对应的AndroidPerf目录的`android`子目录下的`libandroidperf.so`文件。假如编译ARM平台的可执行程序，就将编译得到的`lib/libandroidperf.so`和`lib64/libandroidperf.so`分别放入`android/armeabi-v7a`和`android/arm64-v8a`中即可。

AndroidPerf的client端在启动时会自动将程序导入连接的手机中，并建立端口映射以及socket连接。

