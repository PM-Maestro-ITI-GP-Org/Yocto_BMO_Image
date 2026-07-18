SUMMARY = "BLDC motor fault detection inference app"
LICENSE = "CLOSED"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "git://git@github.com/PM-Maestro-ITI-GP-Org/AI.git;protocol=ssh;branch=abdelrahman \
           file://0001-cmake-take-tflite-from-the-sysroot.patch \
           file://0002-find-models-beside-the-executable.patch \
"

PV = "1.0+git"
SRCREV = "${AUTOREV}"

# The CMake project lives in a subdirectory of the repo, not at its root.
S = "${WORKDIR}/git/motor_fault_cpp_v2"

DEPENDS = "tensorflow-lite"
RDEPENDS:${PN} = "tensorflow-lite"

inherit cmake

# The CMake project defines no install() rules, so cmake_do_install would stage
# nothing -- install the binary and its models by hand. The models directory
# goes beside the binary, which is where main.cpp looks for it.
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/motor_infer ${D}${bindir}/

    install -d ${D}${bindir}/models
    install -m 0644 ${S}/models/* ${D}${bindir}/models/
}

FILES:${PN} = "${bindir}"
