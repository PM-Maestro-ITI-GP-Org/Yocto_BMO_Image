


do_install:append() {
    echo "LABEL=boot  /boot  vfat  defaults,sync  0  2" >> ${D}${sysconfdir}/fstab
}