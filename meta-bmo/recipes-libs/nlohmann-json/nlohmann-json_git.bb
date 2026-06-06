# Recipe created by recipetool
# This is the basis of a recipe and may need further editing in order to be fully functional.
# (Feel free to remove these comments when editing.)

# WARNING: the following LICENSE and LIC_FILES_CHKSUM values are best guesses - it is
# your responsibility to verify that the values are complete and correct.
#
# The following license files were not able to be identified and are
# represented as "Unknown" below, you will need to check them yourself:
#   docs/mkdocs/docs/home/license.md
#
# NOTE: multiple licenses have been detected; they have been separated with &
# in the LICENSE value for now since it is a reasonable assumption that all
# of the licenses apply. If instead there is a choice between the multiple
# licenses then you should change the value to separate the licenses with |
# instead of &. If there is any doubt, check the accompanying documentation
# to determine which situation is applicable.
LICENSE = "GPL-3.0-only & MIT & Unknown"
LIC_FILES_CHKSUM = "file://LICENSE.MIT;md5=3b489645de9825cca5beeb9a7e18b6eb \
                    file://LICENSES/GPL-3.0-only.txt;md5=8da5784ab1c72e63ac74971f88658166 \
                    file://docs/mkdocs/docs/home/license.md;md5=0b50ae6ab6b292c61ea813fc02dafce5"

SRC_URI = "git://git@github.com/nlohmann/json.git;protocol=ssh;branch=master"

# Modify these as desired
PV = "1.0+git"
SRCREV = "55f93686c01528224f448c19128836e7df245f72"

S = "${WORKDIR}/git"

PROVIDES = "nlohmann-json"
inherit cmake

# Specify any options you want to pass to cmake using EXTRA_OECMAKE:
EXTRA_OECMAKE = ""

