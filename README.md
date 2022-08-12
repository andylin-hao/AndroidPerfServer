# AndroidPerfServer

该部分实现了Android端的server component。Server component为一个能在Android中执行的shell程序，其通过binder与SurfaceFlinger连接，从而以极低的开销获取性能数据，并构建一个Unix domain socket来与PC的client端进行通信连接。

## 源码结构

* `main.cpp`
    
    启动daemon进程在Android后台运行，然后获取SurfaceFlinger的binder，并启动Server的main函数。

* `server.cpp`

    创建一个名为`@androidperf`的Unix domain socket作为server socket，并创建epoll event循环来监听client连接与通信。client连接建立后，基于client发送的指令来使用binder获取性能数据。性能数据通过构建pipe和splice通道直接对接socket，在内核态完成通信连接，以实现零拷贝传输开销。

## 编译

Server的编译需要在AOSP的编译工具链中完成，为此首先需要下载AOSP的源码（AOSP 7~10均可），具体教程参见[这里](https://source.android.com/setup/build/downloading)。 

具体编译流程为首先完成对整个AOSP的编译（source build/envsetup.sh&lunch，然后选择aosp-arm64或者aosp-x86_64，具体看需要编译ARM还是x86平台的可执行程序)，然后将AndroidPerfServer的源码放于`frameworks/native/cmds`目录下（其它目录理论上也可以，只要能正常编译就行）。

`cd`到该目录并执行`mm`。

最后就可以在`$ANDROID_PRODUCT_OUT/system/bin`目录下找到`AndroidPerfServer32`和`AndroidPerfServer64`两个可执行程序，分别为32位和64位平台下的程序。

## 使用

编译完成后将文件替换掉对应的AndroidPerf的源码（或者`AndroidPerf.jar`）中的`android`目录下的`AndroidPerfServer`文件。比如假如编译来ARM平台的可执行程序，就将编译得到的`AndroidPerfServer32`和`AndroidPerfServer64`均改名成`AndroidPerfServer`然后分别放入`android/armeabi-v7a`和`android/arm64-v8a`中即可。AndroidPerf的client端在启动时会自动将程序导入连接的手机中，并建立端口映射以及socket连接。

