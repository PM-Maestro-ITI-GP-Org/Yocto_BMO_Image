SUMMARY = "Create file in /home"
LICENSE = "CLOSED"


S = "${WORKDIR}"

do_install() {
    install -d ${D}/home/root
    echo "meow <3" > ${D}/home/root/cat.txt
}

FILES:${PN} = " \
  /home \
  /home/root \
  /home/root/cat.txt \
"