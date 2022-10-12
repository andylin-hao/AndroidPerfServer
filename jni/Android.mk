LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    server_jni.cpp

LOCAL_SHARED_LIBRARIES := \
    libandroid_runtime \
    liblog \
    libnativehelper \
    libutils

LOCAL_MODULE := libandroidperf_jni
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS += -Wall -Wextra -Werror

include $(BUILD_SHARED_LIBRARY)
