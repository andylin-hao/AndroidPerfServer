# Copyright 2007 The Android Open Source Project
#
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MODULE := AndroidPerfServerFWlib
LOCAL_MODULE_STEM := AndroidPerfServerFW
LOCAL_REQUIRED_MODULES := AndroidPerfServer
include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := AndroidPerfServerFW
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := AndroidPerfServerFW
LOCAL_REQUIRED_MODULES := AndroidPerfServerFWlib
include $(BUILD_PREBUILT)
