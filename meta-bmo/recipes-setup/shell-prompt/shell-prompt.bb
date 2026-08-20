SUMMARY = "Name the machine in the shell prompt"
DESCRIPTION = "There are three shells on this board that look identical -- the \
QNX host's console, guest-1's, and this one -- and any of them can also be \
reached over ssh from the others. Which one you are typing at is otherwise a \
matter of memory, and a command meant for the host run in a guest is not \
always harmless. This is the guest-2 half; the QNX images do the same thing \
through QNX_IFS_PROMPT in meta-qnx's qnx-ifs.bbclass."
LICENSE = "CLOSED"

# Matches the QNX side: (HOST) is the hypervisor host, (G1) the QNX guest,
# (G2) this one. Only the tag is a variable -- the rest of the prompt is
# poky's own \u@\h:\w\$ and is worth keeping, this being the one machine of the
# three with a working directory to lose track of.
SHELL_PROMPT_TAG ?= "(G2)"

# A profile.d drop-in rather than a change to /etc/profile: poky's profile sets
# PS1 near the top and sources this directory at the end, so a drop-in wins
# without a bbappend patching base-files.
#
# The heredoc delimiter is quoted so the shell expands nothing in the body --
# \u, \w and $PS1 have to reach the file as written. bitbake still expands
# ${...}, which is how the tag gets in.
#
# The [ -z "$PS1" ] guard is poky's own. A non-interactive shell has no PS1, and
# setting one there is at best noise.
do_install() {
    install -d ${D}${sysconfdir}/profile.d
    cat > ${D}${sysconfdir}/profile.d/00-machine-prompt.sh <<'EOF'
[ -z "$PS1" ] || PS1='${SHELL_PROMPT_TAG} \u@\h:\w\$ '
EOF
    chmod 0644 ${D}${sysconfdir}/profile.d/00-machine-prompt.sh
}

FILES:${PN} = "${sysconfdir}/profile.d/00-machine-prompt.sh"

do_install[vardeps] += "SHELL_PROMPT_TAG"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
