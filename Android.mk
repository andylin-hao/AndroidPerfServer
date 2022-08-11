LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main.cpp server.cpp

LOCAL_SHARED_LIBRARIES := \
	libbase \
	libutils \
	liblog \
	libbinder \
	libcutils

LOCAL_MODULE:= AndroidPerfServer
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := AndroidPerfServer32
LOCAL_MODULE_STEM_64 := AndroidPerfServer64


include $(BUILD_EXECUTABLE)
