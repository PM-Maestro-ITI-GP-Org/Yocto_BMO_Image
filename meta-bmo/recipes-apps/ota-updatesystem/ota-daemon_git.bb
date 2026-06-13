# Recipe created by recipetool
# This is the basis of a recipe and may need further editing in order to be fully functional.
# (Feel free to remove these comments when editing.)

# Unable to find any files that looked like license statements. Check the accompanying
# documentation and source headers and set LICENSE and LIC_FILES_CHKSUM accordingly.
#
# NOTE: LICENSE is being set to "CLOSED" to allow you to at least start building - if
# this is not accurate with respect to the licensing of the software being built (it
# will not be in most cases) you must specify the correct value before using this
# recipe for anything other than initial testing/development!
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

SRC_URI = "\
    git://git@github.com/maxmaster55/someip_OTA_update.git;protocol=ssh;branch=main \
    file://config.json \
    file://client.json \
    file://ota-daemon.service \
    file://staging.mount \
"

# Modify these as desired
PV = "1.0+git"
SRCREV = "b4fec71a9223a53f51ec105fcfd205a030196dce"

S = "${WORKDIR}/git/daemon"

# NOTE: unable to map the following CMake package dependencies: nlohmann_json CommonAPI-SomeIP CommonAPI
DEPENDS = "libarchive zlib bzip2 openssl nlohmann-json CommonAPI-SomeIP CommonAPI"

inherit cmake systemd


SYSTEMD_AUTO_ENABLE = "enable"

SYSTEMD_SERVICE:${PN} = " \
    ota-daemon.service \
    staging.mount \
"

# Specify any options you want to pass to cmake using EXTRA_OECMAKE:
EXTRA_OECMAKE = ""

CXXFLAGS:append = " -include string -Wno-deprecated-declarations"

EXTRA_OECMAKE = " \
    -DCMAKE_BUILD_TYPE=Release \
"


do_install:append() {
    # Install daemon config
    install -d ${D}${sysconfdir}/ota
    install -m 0644 ${WORKDIR}/config.json ${D}${sysconfdir}/ota/config.json

    # Install someip config
    install -d ${D}${sysconfdir}/ota
    install -m 0644 ${WORKDIR}/client.json ${D}${sysconfdir}/ota/client.json

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
    ${sysconfdir}/ota/client.json \
    /staging \
"