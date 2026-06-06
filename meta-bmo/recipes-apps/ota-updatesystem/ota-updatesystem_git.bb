# Recipe created by recipetool
# This is the basis of a recipe and may need further editing in order to be fully functional.
# (Feel free to remove these comments when editing.)

# Unable to find any files that looked like license statements. Check the accompanying
# documentation and source headers and set LICENSE and LIC_FILES_CHKSUM accordingly.
#
# NOTE: LICENSE is being set to "CLOSED" to allow you to at least start building - if
# this is not accurate with respect to the licensing of the software being built (it
# will not be in most cases) you must specify the correct value before using this
# recipe for anything other than initial testing/development!
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

SRC_URI = "git://git@github.com/maxmaster55/someip_OTA_update.git;protocol=ssh;branch=main"

# Modify these as desired
PV = "1.0+git"
SRCREV = "3c8d539e7670b94b691e4a8baef150b825fecdb1"

S = "${WORKDIR}/git"

# NOTE: unable to map the following CMake package dependencies: nlohmann_json CommonAPI-SomeIP CommonAPI
DEPENDS = "openssl bzip2 zlib libcommonapi commonapi-someip nlohmann-json"

CXXFLAGS += "-include string"

inherit cmake

# Specify any options you want to pass to cmake using EXTRA_OECMAKE:
EXTRA_OECMAKE = ""

