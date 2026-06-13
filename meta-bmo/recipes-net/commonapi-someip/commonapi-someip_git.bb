LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=815ca599c9df247a0c7f619bab123dad"

SRC_URI = "git://git@github.com/COVESA/capicxx-someip-runtime.git;protocol=ssh;branch=master"

PV = "1.0+git"
SRCREV = "86dfd69802e673d00aed0062f41eddea4670b571"

S = "${WORKDIR}/git"

DEPENDS = "boost vsomeip commonapi-core"
PROVIDES = "CommonAPI-SomeIP"
RDEPENDS_${PN}-dev = "vsomeip"

inherit cmake pkgconfig lib_package

EXTRA_OECMAKE += " \
    -DCMAKE_INSTALL_PREFIX=/usr \
    -DCommonAPI_DIR=${STAGING_LIBDIR}/cmake/CommonAPI \
    -Dvsomeip3_DIR=${STAGING_LIBDIR}/cmake/vsomeip3 \
    -DINSTALL_CMAKE_DIR:PATH=${baselib}/cmake/CommonAPI-SomeIP \
"

FILES_${PN}-dev += " \
    ${libdir}/cmake \
    ${libdir}/cmake/CommonAPI-SomeIP \
    ${libdir}/cmake/CommonAPI-SomeIP/*.cmake \
"