SUMMARY = "Motor AI inference node: the signal-driven half of the AI pipeline"
DESCRIPTION = "Sleeps until motor_ai_server signals it, reads the completed \
window from /motor_data/input_data/data.csv, and writes one stage's verdict to \
/motor_data/results/result.ini -- anomaly on SIGUSR1, fault classification on \
SIGUSR2, remaining useful life on SIGTERM. Feature extraction is time- and \
frequency-domain over a vendored kissfft built in double precision, and the \
three models are real, read at startup from ${datadir}/motor-ai-node."
LICENSE = "CLOSED"

SRC_URI = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor_ai_node.git;protocol=ssh;branch=main \
           file://motor-ai-node.service \
"

# Tracks the branch head, like every other application here.
SRCREV = "${AUTOREV}"
PV = "1.0+git"

S = "${WORKDIR}/git"

# None. That is the point of it being its own recipe rather than a second
# binary in motor-ai-server's: it links no CommonAPI, no vsomeip and no boost,
# so a change to it rebuilds one translation unit instead of dragging the whole
# SOME/IP toolchain along.
DEPENDS = ""

# Not RDEPENDS on motor-ai-server either, and deliberately in neither
# direction at build time. The two find each other at runtime through
# /motor_data and the pidfile, and each is useful without the other: the node
# answers a signal from anything, and the service logs a missing node and
# keeps its previous verdict.
inherit systemd

# No cmake, no autotools -- upstream is one compiler invocation behind a plain
# Makefile, and this drives it with the cross compiler.
#
# The flags are make *overrides* rather than environment: upstream declares
# CXX and CXXFLAGS with ?= so an override is what beats them. LDFLAGS matters
# for more than correctness -- the compile and the link are a single
# invocation, and OE's QA check fails a package whose binaries were linked
# without them.
do_configure[noexec] = "1"

do_compile() {
	# The repository tracks kissfft/*.o, built on somebody's x86-64 host.
	# make finds them already present, never runs the compile rule, and the
	# link fails with "Relocations in generic ELF (EM: 62)" -- 62 being
	# x86-64. Nothing about that message says "stale committed artefact",
	# so: delete them first and let make rebuild for the target.
	#
	# The right fix is upstream removing them from git. Until then this
	# stays, because a build that trusts checked-in binaries is wrong even
	# once they are gone.
	oe_runmake -C ${S} clean

	# c++17, not c++14: include/ uses structured bindings. gcc accepts them
	# at c++14 as an extension and warns on every one, which is what buried
	# the real error above in a screenful of noise.
	oe_runmake -C ${S} \
		CC="${CC}" \
		CFLAGS="${CFLAGS}" \
		CXX="${CXX}" \
		CXXFLAGS="${CXXFLAGS} -std=c++17 -Wall -Wextra" \
		LDFLAGS="${LDFLAGS}"
}

do_install() {
	install -d ${D}${bindir}
	install -m 0755 ${S}/motor_ai_node ${D}${bindir}/motor_ai_node

	# data_dir, the pidfile and the three signals. Every key here has to
	# agree with motor-ai-server's server.conf; nothing checks that, and a
	# mismatch shows up only as a per-window timeout on the other side.
	install -d ${D}${sysconfdir}/motor-ai-node
	install -m 0644 ${S}/node.conf ${D}${sysconfdir}/motor-ai-node/node.conf

	# The trained artefacts. node.conf's model_root points here, and
	# without them the node starts and has nothing to infer with -- the
	# recipe predates the models existing, when the three stages returned a
	# fixed verdict and there was nothing to install.
	#
	# Data, not code: replacing a model is a file copy, not a rebuild.
	install -d ${D}${datadir}/motor-ai-node
	cp -r ${S}/model ${S}/config ${D}${datadir}/motor-ai-node/

	install -d ${D}${systemd_system_unitdir}
	install -m 0644 ${WORKDIR}/motor-ai-node.service \
		${D}${systemd_system_unitdir}/
}

FILES:${PN} += "${systemd_system_unitdir}/motor-ai-node.service"

SYSTEMD_SERVICE:${PN} = "motor-ai-node.service"
SYSTEMD_AUTO_ENABLE = "enable"
