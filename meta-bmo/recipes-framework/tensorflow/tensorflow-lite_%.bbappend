# meta-tensorflow's tensorflow-lite installs libtensorflowlite.so but no headers,
# so nothing can be built against the TFLite C++ API from the sysroot. Stage the
# headers so recipes like ai-app can compile.
#
# flatbuffers has to come from TFLite's own bazel tree rather than meta-oe's
# flatbuffers recipe: tensorflow/lite/schema/schema_generated.h carries a
# static_assert pinning it to flatbuffers 23.5.26, and meta-oe ships 24.3.25,
# which fails that assert at compile time.

do_install:append() {
    install -d ${D}${includedir}

    cd ${S}
    find tensorflow/lite -name '*.h' -exec install -Dm 0644 '{}' ${D}${includedir}/'{}' \;

    cd ${BAZEL_OUTPUTBASE_DIR}/external/flatbuffers/include
    find flatbuffers -name '*.h' -exec install -Dm 0644 '{}' ${D}${includedir}/'{}' \;
}

FILES:${PN}-dev += "${includedir}"
