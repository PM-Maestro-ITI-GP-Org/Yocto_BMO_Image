FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "file://boot.cmd.in"

# Kernel console for the bootargs set explicitly in boot.cmd.in, derived from the
# machine's SERIAL_CONSOLES ("<baud>;<tty>") so each board gets its own console:
# ttyAMA10 (debug header) on the Pi 5, ttyS0 (mini-UART on GPIO 14/15) on the Pi
# 3/4 -- or ttyAMA0 there if SERIAL_CONSOLES is overridden alongside a
# dtoverlay=miniuart-bt that moves the PL011 onto those pins.
def rpi_cmdline_serial(d):
    if d.getVar("ENABLE_UART") != "1":
        return ""
    consoles = (d.getVar("SERIAL_CONSOLES") or "").split()
    if not consoles:
        return ""
    parts = consoles[0].split(";")
    if len(parts) < 2:
        return ""
    return "console=%s,%s" % (parts[1], parts[0])

RPI_CMDLINE_SERIAL ?= "${@rpi_cmdline_serial(d)}"
RPI_CMDLINE_SERIAL[vardeps] += "SERIAL_CONSOLES ENABLE_UART"

do_compile:prepend() {
    bbnote "Using custom boot.cmd from layer to generate boot.scr"
    sed -i -e 's/@@CMDLINE_SERIAL@@/${RPI_CMDLINE_SERIAL}/' "${WORKDIR}/boot.cmd.in"
}
