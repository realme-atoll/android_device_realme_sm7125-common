# camera.mk
LOCAL_PATH := $(call my-dir)

# call all the product files
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(LOCAL_PATH),$(TARGET_COPY_OUT_ODM)/etc/camera)
