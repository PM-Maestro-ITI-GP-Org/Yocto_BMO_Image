#!/bin/sh
# Static addressing for this image's interfaces.
#
# Under qvm this image is guest-2 and has two virtio-net links, named by the
# .link files beside this script:
#
#   qhost0   10.0.1.2/24   to the hypervisor host (its vp1 is 10.0.1.1, and it
#                          routes and NATs onward, so it is also the gateway)
#   qguest0  10.0.2.2/24   direct to the QNX guest-1, whose end is 10.0.2.1
#
# The addresses are not free choices. 10.0.1.x must agree with QNX_HOST_LINUX_IP
# in meta-qnx-hyp's qnx-host-image, and 10.0.2.x with the "unicast" fields in
# both halves of the motor-AI pair's vsomeip configuration -- the server binds
# 10.0.2.2 and the client dials it by literal address.
#
# On real hardware (MACHINE=rpi) neither interface exists; the physical NIC
# keeps its kernel name and gets the legacy address instead.

set -u

# udev applies the .link files that rename the two virtio-net devices, and it
# does so asynchronously. Rather than order this unit after the deprecated
# systemd-udev-settle.service, wait here for the names to appear -- the wait is
# bounded, and on hardware where they never will (MACHINE=rpi) it costs the
# timeout once at boot and then falls through to the legacy branch below.
wait_for_links() {
	# Whole seconds only -- busybox sleep does not reliably take a fraction,
	# and this script may run against either busybox or coreutils.
	i=0
	while [ "$i" -lt 10 ]; do
		if ip link show qhost0 >/dev/null 2>&1 || \
		   ip link show qguest0 >/dev/null 2>&1; then
			return 0
		fi
		sleep 1
		i=$((i + 1))
	done
	echo "network-setup: no qvm guest links appeared after 10s"
	return 1
}

wait_for_links || true

configure() {
	iface=$1
	addr=$2

	if ! ip link show "$iface" >/dev/null 2>&1; then
		return 1
	fi

	ip link set "$iface" up
	# `replace` rather than `add`: this unit is RemainAfterExit and may be
	# restarted by hand during bring-up, and `add` fails on an address that
	# is already there.
	ip addr replace "$addr" dev "$iface"
	ip link set "$iface" multicast on
	echo "network-setup: $iface -> $addr"
	return 0
}

configured_guest_link=no

if configure qhost0 10.0.1.2/24; then
	# The host NATs this network onto the LAN; see pf-nat.conf in the host
	# image.
	ip route replace default via 10.0.1.1 dev qhost0 || true

	# ...and then every name lookup fails anyway unless something writes a
	# resolver. Nothing does here: the address is static, so no DHCP client
	# runs, and that is what normally produces this file. The symptom reads
	# as a routing fault and is not one -- 8.8.8.8 answers, a hostname does
	# not.
	#
	# Only when there is no nameserver already, so a resolver put here by
	# hand, or by systemd-resolved on an image that has it, wins.
	if ! grep -q "^nameserver" /etc/resolv.conf 2>/dev/null; then
		printf 'nameserver 8.8.8.8\nnameserver 8.8.4.4\n' \
			>> /etc/resolv.conf 2>/dev/null || true
		echo "network-setup: wrote default nameservers to /etc/resolv.conf"
	fi
fi

if configure qguest0 10.0.2.2/24; then
	configured_guest_link=yes
fi

if [ "$configured_guest_link" = yes ]; then
	# SOME/IP service discovery is multicast to 224.244.224.245. Without a
	# route for the group vsomeip's sendto() fails with ENETUNREACH and the
	# service never announces itself -- it starts cleanly and is simply never
	# found, which is a much harder failure to read than a crash.
	ip route replace 224.0.0.0/4 dev qguest0 || true
fi

# Legacy path for a build on real hardware, where neither virtual link exists.
if [ "$configured_guest_link" = no ] && ip link show eth0 >/dev/null 2>&1; then
	ip link set eth0 up
	ip addr replace 192.168.2.2/16 dev eth0
	ip link set eth0 multicast on
	ip route replace 224.0.0.0/4 dev eth0 || true
	echo "network-setup: eth0 -> 192.168.2.2/16 (no qvm links present)"
fi

exit 0
