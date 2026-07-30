SUMMARY = "Static network configuration for the qvm guest links"
DESCRIPTION = "Names this guest's two virtio-net interfaces by MAC and gives \
them their addresses: qhost0 to the hypervisor host, qguest0 direct to the QNX \
guest. The latter is the link motor-ai-server serves SOME/IP on."
LICENSE = "CLOSED"

SRC_URI = "file://network-setup.service \
           file://network-setup.sh \
           file://10-qnx-host.link \
           file://10-qnx-guest.link \
"

inherit systemd

SYSTEMD_AUTO_ENABLE = "enable"
SYSTEMD_SERVICE:${PN} = "network-setup.service"

# The script is the only thing that gives this guest an address, so ip is not
# optional here.
RDEPENDS:${PN} = "iproute2"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/network-setup.sh ${D}${bindir}/

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/network-setup.service ${D}${systemd_system_unitdir}/

    # /usr/lib/systemd/network, not /etc: these are the image's own defaults,
    # and leaving /etc free means the board can be re-pointed at runtime by
    # dropping a higher-priority file there.
    install -d ${D}${nonarch_libdir}/systemd/network
    install -m 0644 ${WORKDIR}/10-qnx-host.link  ${D}${nonarch_libdir}/systemd/network/
    install -m 0644 ${WORKDIR}/10-qnx-guest.link ${D}${nonarch_libdir}/systemd/network/
}

FILES:${PN} += "${systemd_system_unitdir}/network-setup.service \
                ${nonarch_libdir}/systemd/network/*.link \
"
