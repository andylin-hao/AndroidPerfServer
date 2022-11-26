LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main.cpp \
	server.cpp 

LOCAL_SHARED_LIBRARIES := \
	libbase \
	libutils \
	liblog \
    libcutils \
	libbinder

LOCAL_MODULE:= AndroidPerfServer

LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64

include $(BUILD_EXECUTABLE)
