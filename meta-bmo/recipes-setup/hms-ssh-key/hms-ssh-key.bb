SUMMARY = "Authorise the hypervisor host's hms key to log in as root"
DESCRIPTION = "This image runs as guest-2 under the QNX hypervisor, and hms on \
the host manages it over ssh -- starting it, stopping it, reading its state. \
That is key-based, so without the host's public key here every one of those \
connections falls back to asking for a password nobody is there to type."
LICENSE = "CLOSED"

# The public half of the pair whose private half lives on the QNX host at
# /root/.ssh/id_ed25519, installed there by meta-qnx's QNX_SSH_IDENTITY. The
# same key is in meta-qnx-hyp/conf/hms-ssh-key.inc, which is what the QNX guest
# authorises -- the two have to stay identical or hms reaches one guest and not
# the other.
#
# A literal because a public key is not a secret. Override it here or in
# local.conf if the pair is ever regenerated:
#
#     HMS_PUBKEY = "ssh-ed25519 AAAA... hms@hypervisor"
HMS_PUBKEY ?= "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINjet1l4AgueqR+EnUmlXw2yQcSckVrqIFQJ9RWLsd9y hms@hypervisor"

# /home/root, not /root. This is poky's layout -- /etc/passwd gives root
# /home/root -- and it is where dropbear looks, this image having dropbear
# rather than openssh. Putting the file in /root would be silently ignored.
HMS_SSH_DIR = "/home/root/.ssh"

do_install() {
    install -d -m 0700 ${D}${HMS_SSH_DIR}
    echo "${HMS_PUBKEY}" > ${D}${HMS_SSH_DIR}/authorized_keys
    chmod 0600 ${D}${HMS_SSH_DIR}/authorized_keys
}

# Both are checked by dropbear, which refuses a key file it considers loosely
# permissioned and says so only in its log.
FILES:${PN} = "${HMS_SSH_DIR} ${HMS_SSH_DIR}/authorized_keys"

do_install[vardeps] += "HMS_PUBKEY HMS_SSH_DIR"

# Nothing to compile or configure.
do_configure[noexec] = "1"
do_compile[noexec] = "1"
