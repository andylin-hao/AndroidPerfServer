# Copyright 2007 The Android Open Source Project
#
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MODULE := AndroidPerfServerlib
LOCAL_MODULE_STEM := AndroidPerfServer
include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := AndroidPerfServer
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := AndroidPerfServer
LOCAL_REQUIRED_MODULES := AndroidPerfServerlib
include $(BUILD_PREBUILT)
