FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# The A/B slot devices are the one machine-dependent part of system.conf:
# SD/eMMC on the Pi, virtio on qemu. Substituted into the placeholders in
# system.conf at install time, in the same style as boot.cmd.in.
RAUC_SLOT_A_DEVICE ?= "/dev/mmcblk0p2"
RAUC_SLOT_B_DEVICE ?= "/dev/mmcblk0p3"

RAUC_SLOT_A_DEVICE:qemuall = "/dev/vda2"
RAUC_SLOT_B_DEVICE:qemuall = "/dev/vda3"

do_install:append() {
    sed -i \
        -e 's|@@RAUC_SLOT_A_DEVICE@@|${RAUC_SLOT_A_DEVICE}|' \
        -e 's|@@RAUC_SLOT_B_DEVICE@@|${RAUC_SLOT_B_DEVICE}|' \
        ${D}${sysconfdir}/rauc/system.conf
}

do_install[vardeps] += "RAUC_SLOT_A_DEVICE RAUC_SLOT_B_DEVICE"
