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
           file://server.conf \
"

# Tracks the branch head, like every other application here.
#
# Worth knowing what that gives up. This is one half of a SOME/IP pair: the
# interface definitions live in BOTH this repository and motor_ai_client's, and
# a client generated from a different .fidl than the server does not fail to
# build -- it fails on the board, as a call that never completes. While both
# sides were pinned, that could not happen by accident. Now the two move
# independently and nothing checks they still agree.
#
# So: after changing interface/MotorDataService.{fidl,fdepl} on either side,
# rebuild both. If that directory is untouched, the other side is unaffected.
SRCREV = "${AUTOREV}"
PV = "1.0+git"

S = "${WORKDIR}/git"

# libcommonapi PROVIDES commonapi-core; commonapi-someip and vsomeip are the
# runtimes it links. The generators are native: x86_64 host tools that produce
# source, not target binaries.
DEPENDS = "libcommonapi commonapi-someip vsomeip boost commonapi-generators-native"
# Appended, not assigned: the systemd class contributes to this too.
RDEPENDS:${PN} += "libcommonapi commonapi-someip vsomeip"

# ai-app ships /usr/bin/motor-ai-infer, which this service execs once per
# completed window (infer_command in server.conf). Without it the service runs,
# accepts batches and logs a failure per window -- a runtime symptom for what is
# really a packaging fact, so it is stated as a dependency instead.
RDEPENDS:${PN} += "ai-app"

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

	# Window size, where the CSVs go, and which command runs the inference.
	# Beside the other two configs so that everything tunable about this
	# service is in one directory.
	install -m 0644 ${WORKDIR}/server.conf \
		${D}${sysconfdir}/motor-ai-server/server.conf

	install -d ${D}${systemd_system_unitdir}
	install -m 0644 ${WORKDIR}/motor-ai-server.service \
		${D}${systemd_system_unitdir}/
}

FILES:${PN} += "${systemd_system_unitdir}/motor-ai-server.service"

SYSTEMD_SERVICE:${PN} = "motor-ai-server.service"
SYSTEMD_AUTO_ENABLE = "enable"
