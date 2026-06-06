# Recipe created by recipetool
LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=815ca599c9df247a0c7f619bab123dad"

SRC_URI = "git://git@github.com/COVESA/capicxx-core-runtime.git;protocol=ssh;branch=master"

PV = "1.0+git"
SRCREV = "0e1d97ef0264622194a42f20be1d6b4489b310b5"

S = "${WORKDIR}/git"

DEPENDS = "boost"

PROVIDES = "commonapi3 commonapi-core"

inherit cmake pkgconfig lib_package

CXXFLAGS += "-include string"

EXTRA_OECMAKE += " \
    -DCMAKE_INSTALL_PREFIX=/usr \
    -DINSTALL_LIB_DIR:PATH=${baselib} \
    -DINSTALL_CMAKE_DIR:PATH=${baselib}/cmake/CommonAPI \
    -DBUILD_SHARED_LIBS=ON \
"

# Install cmake config files into the dev package
FILES_${PN}-dev += " \
    ${libdir}/cmake \
    ${libdir}/cmake/CommonAPI \
    ${libdir}/cmake/CommonAPI/*.cmake \
"