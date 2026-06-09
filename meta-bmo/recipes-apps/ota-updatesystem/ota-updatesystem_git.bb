LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

SRC_URI = " \
    git://git@github.com/maxmaster55/someip_OTA_update.git;protocol=ssh;branch=main \
    file://ota-daemon.service \
    file://staging.mount \
    file://config.json \
"

PV = "1.0+git"
SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"

DEPENDS = " \
    openssl \
    bzip2 \
    zlib \
    libcommonapi \
    commonapi-someip \
    nlohmann-json \
"

CXXFLAGS += "-include string"

inherit cmake systemd

SYSTEMD_AUTO_ENABLE = "enable"

SYSTEMD_SERVICE:${PN} = " \
    ota-daemon.service \
    staging.mount \
"

do_install:append() {
    # Install daemon config
    install -d ${D}${sysconfdir}/ota
    install -m 0644 ${WORKDIR}/config.json ${D}${sysconfdir}/ota/config.json

    # Install service file
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/ota-daemon.service ${D}${systemd_system_unitdir}/
    
    # Install mount staging service
    install -d ${D}/staging
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/staging.mount ${D}${systemd_system_unitdir}/

}

FILES:${PN} += " \
    ${systemd_system_unitdir}/ota-daemon.service \
    ${systemd_system_unitdir}/staging.mount \
    ${sysconfdir}/ota/config.json \
    /staging \
"