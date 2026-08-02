SUMMARY = "BLDC motor fault detection inference app"
LICENSE = "CLOSED"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# 0002-find-models-beside-the-executable.patch is gone: resolving the models
# directory from /proc/self/exe is upstream now. It had to be -- the server
# execs this from whatever working directory systemd left it in, so "../models"
# was never going to be a Yocto-only concern.
SRC_URI = "git://git@github.com/PM-Maestro-ITI-GP-Org/AI.git;protocol=ssh;branch=abdelrahman \
           file://0001-cmake-take-tflite-from-the-sysroot.patch \
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

    # The command motor-ai-server execs, and the only name it knows. It resolves
    # motor_infer beside itself, so both have to land in the same directory --
    # which is also why it is installed here rather than being left to the
    # server's config to point at a build path.
    install -m 0755 ${S}/scripts/motor-ai-infer ${D}${bindir}/

    install -d ${D}${bindir}/models
    install -m 0644 ${S}/models/* ${D}${bindir}/models/
}

FILES:${PN} = "${bindir}"
