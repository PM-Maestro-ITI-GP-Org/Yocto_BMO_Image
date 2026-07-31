# The boot partition, for the images that have one.
#
# `nofail` is not optional here, because this rootfs is used two ways. Flashed
# to a Pi it is one partition of bmo-image.wks and /boot is a real vfat
# filesystem. Handed to the QNX hypervisor as guest-2 it is the whole disk --
# qvm attaches a single virtio-blk, the guest sees /dev/vda and nothing else,
# and no partition with this label will ever appear.
#
# Without nofail systemd treats the mount as required: it waits out the 90s
# device timeout, fails the mount, fails fsck, fails local-fs.target, and drops
# to an emergency shell. The guest is otherwise completely healthy, which makes
# it read as a far worse fault than a missing optional filesystem.
#
#     [ TIME ] Timed out waiting for device /dev/disk/by-label/boot.
#     [DEPEND] Dependency failed for /boot.
#     You are in emergency mode.
#
# With nofail the same rootfs boots on both: mounted where the partition
# exists, skipped where it does not. The fsck pass stays 2 so the Pi still
# checks it -- systemd applies nofail to the generated fsck unit as well.
do_install:append() {
    echo "LABEL=boot  /boot  vfat  defaults,sync,nofail  0  2" >> ${D}${sysconfdir}/fstab
}
