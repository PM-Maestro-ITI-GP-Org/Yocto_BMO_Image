SUMMARY = "CommonAPI/SOME/IP motor data service"
DESCRIPTION = "Two programs from one repository. motor_ai_server takes motor \
telemetry over SOME/IP from the QNX guest's motor_ai_client, writes each \
completed window to /motor_data/input_data/data.csv and asks motor_ai_node for \
a verdict by signal -- anomaly always, fault classification and remaining \
useful life only when the first says something other than normal -- then \
replies with all three. motor_ai_node is packaged \
separately, by the motor-ai-node recipe. The service's CMakeLists runs the CommonAPI generators at \
configure time to turn the .fidl/.fdepl interface definitions into C++ \
bindings, then builds them alongside it."
LICENSE = "CLOSED"

# This is the Linux half of a pair. The client is built for QNX by
# meta-qnx-guest and runs in guest-1; this runs in guest-2, which is what the
# service's own README specifies and what its vsomeip config assumes -- it binds
# 10.0.2.2, the guest_to_guest address configured by network-setup.
SRC_URI = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor_ai_server.git;protocol=ssh;branch=main \
           file://motor-ai-server.service \
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

# The service no longer spawns anything. It writes the window and signals a
# long-running node, which is its own recipe built from its own repository --
# motor_ai_node shares nothing with this one but the directory the two meet in.
#
# A runtime dependency and not a build one: nothing here includes or links
# against it. It is stated at all because a window with no live node is a
# per-window timeout and a stale verdict, which is a runtime symptom for what
# is really a packaging fact.
#
# ai-app is deliberately not here any more. It ships /usr/bin/motor-ai-infer,
# a run-once-and-exit program built around the old infer_command contract;
# nothing signals it and nothing execs it now. It is the real models, though,
# so the intended end state is a signal-driven build of that repository
# replacing motor-ai-node -- which is then a change to this one line.
RDEPENDS:${PN} += "motor-ai-node"

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

	# Window size, the shared directory, the pidfile and the three signals.
	# Beside the other two configs so that everything tunable about this
	# service is in one directory.
	#
	# From ${S} rather than from a copy in this layer. It used to be a
	# file:// in SRC_URI, which meant the defaults compiled into the server
	# and the defaults shipped to the board were maintained in two places --
	# and the four keys that have to agree with the node's node.conf were
	# documented in whichever of the two the reader happened to open.
	install -m 0644 ${S}/server/server.conf \
		${D}${sysconfdir}/motor-ai-server/server.conf

	install -d ${D}${systemd_system_unitdir}
	install -m 0644 ${WORKDIR}/motor-ai-server.service \
		${D}${systemd_system_unitdir}/
}

FILES:${PN} += "${systemd_system_unitdir}/motor-ai-server.service"

SYSTEMD_SERVICE:${PN} = "motor-ai-server.service"
SYSTEMD_AUTO_ENABLE = "enable"
