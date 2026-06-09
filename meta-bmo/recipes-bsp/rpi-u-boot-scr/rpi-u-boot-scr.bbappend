FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "file://boot.cmd.in"

do_compile:prepend() {
    bbnote "Using custom boot.cmd from layer to generate boot.scr"
}