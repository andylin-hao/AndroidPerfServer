LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    server_jni.cpp \
	server.cpp 

LOCAL_SHARED_LIBRARIES := \
    libbase \
	libutils \
	liblog \
    libcutils \
	libbinder

LOCAL_MODULE := libandroidperf
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS += -Wall -Wextra -Werror

include $(BUILD_SHARED_LIBRARY)
