inherit core-image


SUMMARY = "BMO Simple Image"
DESCRIPTION = "This is a simple test image with no gui."

LICENSE = "MIT"

MACHINE_FEATURES:append = " wifi bluetooth"
DISTRO_FEATURES:append = " wifi bluetooth"


# add image features
IMAGE_FEATURES += "debug-tweaks"

# some tools
IMAGE_INSTALL:append = " \
        packagegroup-core-boot \
        vim \
        python3 \
        net-tools \
        curl \
        wget \
"

# AI tools
IMAGE_INSTALL:append = " \
    tensorflow-lite \
    keras \
    python3-pillow \
    python3-numpy \
    python3-pandas \
"

# RPI firmware and bluetooth
IMAGE_INSTALL:append = " \
    linux-firmware-rpidistro-bcm43430 \
    bluez5 \
    bluez5-testtools \
    wpa-supplicant \
    iw \
"

# ota
IMAGE_INSTALL:append = " network-setup ota-daemon libubootenv-bin u-boot"
# IMAGE_BOOT_FILES:append = " u-boot.bin boot.scr"

# RPI_EXTRA_CONFIG:append = " \
# kernel=u-boot.bin \n\
# dtoverlay=miniuart-bt \n\
# "

IMAGE_INSTALL:append = " hello-world"