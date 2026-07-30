inherit core-image


SUMMARY = "BMO Simple Image"
DESCRIPTION = "This is a simple test image with no gui."

LICENSE = "MIT"

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
    python3-pip \
    python3-flask \
    ai-app \
"

# --- SOME/IP -----------------------------------------------------------------
# The service half of the motor-AI pair. The client is built for QNX by
# meta-qnx-guest and runs in guest-1; this image is guest-2 under qvm, which is
# where the service belongs -- see its vsomeip config, which binds 10.0.2.2 on
# the guest_to_guest link.
IMAGE_INSTALL:append = " motor-ai-server"

# ota
# rauc and the u-boot env tooling are machine-neutral; the slot devices they
# act on are set per-machine in rauc-conf.bbappend.
IMAGE_INSTALL:append = " network-setup libubootenv-bin u-boot"
IMAGE_INSTALL:append = " rauc home"
# IMAGE_INSTALL:append = " ota-daemon"

IMAGE_INSTALL:append = " hello-world"

# --- Raspberry Pi only -------------------------------------------------------
# On-board WiFi/BT firmware and the wireless stack. None of this has a
# counterpart under qemu, which has no radio hardware, and the rpidistro
# firmware recipes live in meta-raspberrypi.
IMAGE_INSTALL:append:rpi = " \
    linux-firmware-rpidistro-bcm43430 \
    bluez5 \
    bluez5-testtools \
    wpa-supplicant \
    iw \
"

MACHINE_FEATURES:append:rpi = " wifi bluetooth"
DISTRO_FEATURES:append:rpi = " wifi bluetooth"

# The A/B partition table the OTA flow expects. Kept Pi-only because the
# matching slot-selection boot script (rpi-u-boot-scr) is itself Pi-only --
# see COMPATIBLE_MACHINE = "^rpi$" in that recipe. Other machines fall back
# to their own default image layout.
WKS_FILE:rpi = "bmo-image.wks"
