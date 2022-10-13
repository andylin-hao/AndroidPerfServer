/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Command that dumps interesting system state to the log.
 */

#include "server.h"

#include <binder/IServiceManager.h>
#include <binder/TextOutput.h>

#include <signal.h>
#include <stdio.h>
#include <sys/stat.h>
#include <unistd.h>
#include <jni.h>

using namespace android;

extern "C" JNIEXPORT jint JNICALL
Java_com_androidperf_server_AndroidPerfServer_nativeMain(JNIEnv* env,
        jobject server) {
    sp<IServiceManager> sm = defaultServiceManager();
    if (sm == nullptr) {
        ALOGE("Unable to get default service manager!");
        exit(EXIT_FAILURE);
    }

    AndroidPerf nativeServer(sm.get(), env, server);
    nativeServer.main();

    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_androidperf_server_AndroidPerfServer_nativeWrite(JNIEnv* env,
        jobject server, jbyteArray byteData, jint fd) {
    (void)server;
    char *data = (char *) env->GetByteArrayElements(byteData, NULL);
    int len = env->GetArrayLength(byteData);
    write(fd, data, len);
    env->ReleaseByteArrayElements(byteData,(jbyte*)data, 0);
}