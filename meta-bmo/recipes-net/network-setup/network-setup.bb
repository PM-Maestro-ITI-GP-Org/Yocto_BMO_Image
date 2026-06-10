SUMMARY = "Static network configuration for eth0 (IP, multicast)"
LICENSE = "CLOSED"

SRC_URI = "file://network-setup.service"

inherit systemd

SYSTEMD_AUTO_ENABLE = "enable"
SYSTEMD_SERVICE:${PN} = "network-setup.service"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/network-setup.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} += "${systemd_system_unitdir}/network-setup.service"
