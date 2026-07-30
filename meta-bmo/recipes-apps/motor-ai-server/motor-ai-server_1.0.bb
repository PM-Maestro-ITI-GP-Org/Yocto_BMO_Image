SUMMARY = "CommonAPI/SOME/IP motor data service"
DESCRIPTION = "Serves motor telemetry over SOME/IP to the QNX guest's \
motor_ai_client: logs each batch to CSV, runs the AI pipeline (anomaly \
detection, fault classification, predictive maintenance) and replies with the \
results. Its CMakeLists runs the CommonAPI generators at configure time to turn \
the .fidl/.fdepl interface definitions into C++ bindings, then builds them \
alongside the service."
LICENSE = "CLOSED"

# This is the Linux half of a pair. The client is built for QNX by
# meta-qnx-guest and runs in guest-1; this runs in guest-2, which is what the
# service's own README specifies and what its vsomeip config assumes -- it binds
# 10.0.2.2, the guest_to_guest address configured by network-setup.
SRC_URI = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor_ai_server.git;protocol=ssh;branch=main \
           file://motor-ai-server.service \
"

# Pinned rather than AUTOREV so the two halves of the SOME/IP pair cannot drift:
# the interface definitions live in both repositories, and a client generated
# from a different .fidl than the server fails at runtime, not at build time.
# Same commit the QNX side pins in build-qnx/conf/local.conf.
SRCREV = "c462d3886cf0fc489b7c7521fbdb65234a156886"
PV = "1.0+git"

S = "${WORKDIR}/git"

# libcommonapi PROVIDES commonapi-core; commonapi-someip and vsomeip are the
# runtimes it links. The generators are native: x86_64 host tools that produce
# source, not target binaries.
DEPENDS = "libcommonapi commonapi-someip vsomeip boost commonapi-generators-native"
# Appended, not assigned: the systemd class contributes to this too.
RDEPENDS:${PN} += "libcommonapi commonapi-someip vsomeip"

inherit cmake pkgconfig systemd

# The service lives in server/, but its CMakeLists reaches ../interface for the
# .fidl definitions, so the repository root has to be the source directory.
OECMAKE_SOURCEPATH = "${S}/server"

# Upstream looks for its dependencies under one output directory: lib/ for the
# libraries and generators/{core,someip}/ for the code generators. Neither
# exists here -- the libraries are in the sysroot and the generators are native
# tools -- so a directory of that shape is assembled from both.
#
# Note what actually resolves the generators. CMAKE_FIND_ROOT_PATH_MODE_PROGRAM
# is ONLY under OE's cmake.bbclass, so the `PATHS` argument in the project's
# find_program() is re-rooted and never matches this shim. What matches is the
# bare name, found at ${STAGING_DIR_NATIVE}${bindir}, where
# commonapi-generators-native drops symlinks under exactly the full upstream
# names the project searches for. The generators/ links below are for a host
# build of the same tree, and cost nothing here.
SOMEIP_SHIM = "${WORKDIR}/someip-shim"

export LIBS_DIR = "${SOMEIP_SHIM}"
export OUTPUT_DIR = "${SOMEIP_SHIM}"

do_configure:prepend() {
	install -d ${SOMEIP_SHIM}/generators
	ln -sfn ${RECIPE_SYSROOT}${libdir} ${SOMEIP_SHIM}/lib
	ln -sfn ${STAGING_DATADIR_NATIVE}/commonapi-generators/core   ${SOMEIP_SHIM}/generators/core
	ln -sfn ${STAGING_DATADIR_NATIVE}/commonapi-generators/someip ${SOMEIP_SHIM}/generators/someip
}

# The project's install() rules put the binary in bin/ and rename the vsomeip
# config, which is not the layout the service unit expects -- it wants the
# config under ${sysconfdir} where it can be edited on a running target without
# touching the executable's directory. Installed by hand for that reason.
do_install() {
	install -d ${D}${bindir}
	install -m 0755 ${B}/MotorDataService ${D}${bindir}/motor_ai_server

	install -d ${D}${sysconfdir}/motor-ai-server
	install -m 0644 ${S}/server/vsomeip_multicast.json \
		${D}${sysconfdir}/motor-ai-server/vsomeip.json
	install -m 0644 ${S}/interface/commonapi4someip.ini \
		${D}${sysconfdir}/motor-ai-server/commonapi.ini

	install -d ${D}${systemd_system_unitdir}
	install -m 0644 ${WORKDIR}/motor-ai-server.service \
		${D}${systemd_system_unitdir}/
}

FILES:${PN} += "${systemd_system_unitdir}/motor-ai-server.service"

SYSTEMD_SERVICE:${PN} = "motor-ai-server.service"
SYSTEMD_AUTO_ENABLE = "enable"
