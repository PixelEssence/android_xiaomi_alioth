#!/bin/bash

base64 -d device/xiaomi/alioth/configs/camera/secret > device/xiaomi/alioth/configs/camera/st_license.lic

# Clone device_xiaomi_8250-common
if [ ! -d "device/xiaomi/sm8250-common" ]; then
   git clone -b clover-17 https://github.com/PixelEssence/device_xiaomi_8250-common.git    device/xiaomi/sm8250-common
fi

# Clone vendor_xiaomi_aliothh
if [ ! -d "vendor/xiaomi/alioth" ]; then
    git clone https://github.com/PixelEssence/vendor_xiaomi_aliothh.git vendor/xiaomi/alioth
fi

# Clone vendor_xiaomi_sm8250-common
if [ ! -d "vendor/xiaomi/sm8250-common" ]; then
    git clone https://github.com/PixelEssence/vendor_xiaomi_sm8250-common.git vendor/xiaomi/sm8250-common
fi

# Clone hardware_dolby
if [ ! -d "hardware/dolby" ]; then
    git clone https://github.com/Tsaritsa-Prjkt/android_hardware_dolby.git hardware/dolby
fi

# Clone android_hardware_xiaomi
if [ ! -d "hardware/xiaomi" ]; then
    git clone https://github.com/PixelEssence/xiaomi_hardware.git hardware/xiaomi --depth 1
fi

# Clone kernel/xiaomi/sm8250 and remove kernelsu from drivers/Kconfig
if [ ! -d "kernel/xiaomi/sm8250" ]; then
    git clone -b stable-bpf https://github.com/re-noroi/kernel_sm8250.git kernel/xiaomi/sm8250 --depth 1 && cd kernel/xiaomi/sm8250 && sed -i '/kernelsu/d' drivers/Kconfig && cd - > /dev/null
fi

# Clone vendor/xiaomi/camera and clean up Android.bp
if [ ! -d "vendor/xiaomi/camera" ]; then
    git clone https://gitlab.com/johnmart19/vendor_xiaomi_camera vendor/xiaomi/camera -b aosp-16 --depth 1
fi

# Clone device/xiaomi/camera
if [ ! -d "device/xiaomi/camera" ]; then
    git clone https://github.com/PocoF3Releases/device_xiaomi_camera device/xiaomi/camera -b aosp-16 --depth 1
fi
