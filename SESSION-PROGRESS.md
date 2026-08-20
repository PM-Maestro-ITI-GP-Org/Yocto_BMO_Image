# QNX Yocto layers — session progress

Summary of the work done in this session across the three layers
(`meta-qnx`, `meta-qnx-hyp`, `meta-qnx-guest`). **Everything below is
uncommitted** — the three layer repos have the changes in their working trees,
ready to review and commit.

Build directory: `build-qnx`. SDP: `…/qnx800` (read-only). Nothing has booted on
real hardware — all verification is static (`bitbake`, `file`, `dumpifs`,
`fdisk`, `readelf`).

---

## 1. Architecture review + quality-of-life features

- **`bitbake -c dumpifs <image>`** — new task in `qnx-ifs.bbclass` that builds
  the image if needed and prints its contents on the console (no hunting for
  `dumpifs` or the deploy path).
- **`QNX_ELF_CHECK`** — `do_install` postfunc in `qnx-sdp.bbclass` that rejects
  staged non-aarch64 ELFs, catching a build system that ignored `${CC}` and used
  the host compiler. Disable per-recipe with `QNX_ELF_CHECK = "0"`.
- **`TEMPLATECONF` templates** (`meta-qnx/conf/templates/default/`) — one command
  creates a configured build directory:
  `TEMPLATECONF=meta-qnx/conf/templates/default source poky/oe-init-build-env <dir>`.
- **Dead code removed** — the no-op `HOSTTOOLS +=` in `qnx-cmake.bbclass`.
- Docs brought back in sync across all layers (README, variables, cookbook,
  getting-started); the "not done" lists were pruned to reality.

## 2. Qt for QNX — application + libraries

- **`qt-cluster`** (`meta-qnx-guest/recipes-apps/`) — builds the `qt_cluster` QML
  app for QNX against the staged Qt, via a plain `DEPENDS = "qt6-qnx"`. Verified:
  produces an aarch64/QNX `appCluster` and a ~126 MB self-contained deploy tree.
- **`qt6-qnx`** (`meta-qnx-guest/recipes-qt/`) — Qt 6.8.3 for QNX. **Rewritten
  this session** to be **self-contained** (see §8).

## 3. Guest data disk (`rootfs.img`) — the big new mechanism

An IFS is copied into guest RAM at boot, so large payloads (Qt, the graphics
stack) cannot live in it. This adds the QNX-BSP answer: a QNX6 filesystem the
guest mounts as a disk.

- **`qnx-rootfs.bbclass`** (`meta-qnx`) — builds a bare QNX6 filesystem image with
  `mkqnx6fsimg` from `QNX_ROOTFS_INSTALL` (the same "list what it carries" idea as
  `QNX_IFS_INSTALL`), auto-sized with grow-on-overflow.
- **`qnx-guest-rootfs`** (`meta-qnx-guest/recipes-image/`) — produces `rootfs.img`
  (~366 MB) carrying `qt-cluster` at `/qt-cluster`.
- **Wired into a running guest**, mirroring `qnx_guests/images/guest-1/`:
  1. `virtio-blk` vdev in the guest `.qvmconf` (`loc 0x1c0b0000`, `intr gic:45`)
  2. inline `.rootfs-mount.sh` in the guest boot script → `mount -t qnx6
     /dev/vblk0 /`
  3. `qnx-host-disk` bbappend places `rootfs.img` next to the guest IFS on the
     host data partition (switched to `auto` sizing to fit it)
- Verified: full `qnx-host-disk` builds; `fdisk` shows the FAT boot + QNX6 data
  partition with `rootfs.img` nested inside; the guest IFS carries the mount
  script + block stack.

## 4. Shared filesystem-image builder + two `qnx-disk` bug fixes

While adding `qnx-rootfs` it became clear `qnx-disk` and `qnx-rootfs` do the same
`mkqnx6fsimg` job, so the core was factored into one shared helper.

- **`qnx_build_fsimg`** (module-level def in `qnx-sdp.bbclass`) — runs
  `mkfatfsimg`/`mkqnx6fsimg` and grows the image until it fits. Used by **both**
  `qnx-disk` and `qnx-rootfs`.
- **Bug fixed:** the grow loop only matched `mkfatfsimg`'s "No space left", not
  `mkqnx6fsimg`'s "Insufficient num_sectors … need at least N" — so a data-
  partition overflow hard-failed instead of growing. Now handles both.
- **Bug fixed:** auto disk-sizing summed partition bytes and rounded once, but
  `diskimage` cylinder-aligns each partition — the gap caused "Out of space
  placing partition 2". Now rounds each partition up to whole cylinders.

## 5. `qnx-autotools` — the third build-system driver

- **`qnx-autotools.bbclass`** (`meta-qnx`) — the `./configure` + `make` sibling of
  `qnx-cmake`/`qnx-meson`, driving configure with `qcc` and the stage-tree install
  dirs.
- **`recipes-example/zlib`** — **unmodified upstream zlib**, cross-built for QNX
  with **zero code patches**. Stages `libz.so*` + `zlib.h`.
- **`recipes-example/qnx-zlib-user`** — links `-lz` against it through a plain
  `DEPENDS = "zlib"`. Verified: the binary's `NEEDED` list contains `libz.so.1`.

## 6. `qnx-toolchain` — building recipes from *normal* Yocto layers

The headline result: a **stock recipe from any normal layer** builds for QNX with
no bespoke wrapper.

- **`qnx-toolchain.bbclass`** (`meta-qnx`) — applied globally via `INHERIT`, makes
  `qcc` the default toolchain for every *target* recipe on the QNX machine.
  Guarded: inert on other machines, skips recipes that inherit `qnx-sdp`, leaves
  native/cross recipes on the host toolchain.
- **Proven:** stock poky `bzip2` (unmodified) builds end-to-end — all C compiles
  clean, `libbz2.so.1.0.8` (QNX aarch64) + `libbz2.a` + `bzlib.h` stage to the
  sysroot, `bitbake bzip2` completes 670 tasks.
- **Four generic ecosystem blockers found + fixed** (they apply to every autotools
  recipe, not just bzip2):
  1. `ptest` drags a Linux runtime closure (`bash`/`gdbm`/`acl`) →
     `DISTRO_FEATURES:remove = "ptest"`
  2. base `CFLAGS` carry `-pipe`/debug flags `qcc`'s `cc1` rejects → reset `CFLAGS`
  3. **libtool** left `archive_cmds` empty (no `.so`) because it greps `$LD --help`
     for GNU ld and OE set `LD=qcc` → point `LD` at the **raw** GNU ld; `qcc` still
     links via `$CC`. *(the key shared-library fix)*
  4. packaging QA rejects QNX ELFs → `noexec` `do_package*` (QNX images are
     IFS/QNX6, so the sysroot is the endpoint)
- Enable with two lines in `local.conf` (documented, opt-in):
  `INHERIT += "qnx-toolchain"` and `DISTRO_FEATURES:remove = "ptest"`.
- Regression-checked: bespoke recipes/images (`qnx-hello`, `qnx-ifs-hello`) build
  unchanged; full tree parses with 0 errors.
- New doc: **`docs/reusing-layers.md`**.

### Honest scope (what you can actually build)

Buildability is **per-recipe, not per-layer** — gated by whether a recipe's *code*
and its *dependency closure* are QNX-portable:

- **Tier 1 (builds ~free):** portable C/C++ libraries in oe-core / `meta-oe`
  (`zlib`, `bzip2` proven; `xz`, `expat`, `json-c`, `libffi`, `pcre2`, `zstd`…).
- **Tier 2 (builds after porting):** code using Linux/glibc specifics.
- **Tier 3 (no / huge):** recipes pulling `glib-2.0`/`systemd`/`perl`/`python`, and
  `meta-qt6`'s Qt.

The mechanism is generic; it's been proven on two libraries. Each new recipe is
still an empirical question, but the *toolchain wall is gone*.

## 7. meta-qt6 investigation — why it can't replace `qt6-qnx`

Added `meta-qt6` + `meta-oe` + `meta-python` and probed. **Conclusive result:**
qtbase's generated cmake toolchain contains `set(CMAKE_SYSTEM_NAME Linux)` —
meta-qt6 builds Qt **as a Linux target** and has no QNX path. Even with `qcc` it
would compile Qt's Linux platform code (epoll/inotify) and fail. This is the Tier
3 case: the toolchain swap works, but Qt's *code and config* are Linux-bound.
`bblayers.conf` was restored; `qt6-qnx`/`qt-cluster` were never touched.

> **Superseded — see §11.** This section's conclusion was wrong. Stock meta-qt6 qtbase and
> qtdeclarative build for QNX with bbappends only, no source patches. §10 walked back half
> of it; §11 retired the rest after actually testing instead of reasoning.

## 8. Self-contained `qt6-qnx` recipe (from source) — IN PROGRESS

Replaced the script-driven recipe (which depended on the monorepo working tree)
with one that stands alone, faithfully replicating `src/QT/qt6-qnx-libs`'s
`build.sh`:

- `SRC_URI` fetches `qt-everywhere-src-6.8.3` from download.qt.io (checksummed)
- vendors its own `toolchain_qnx_aarch64le.cmake` (qcc / `CMAKE_SYSTEM_NAME QNX` /
  `-leventfd`) under the recipe's `files/`
- Phase 1: builds host Qt (`moc`/`rcc`/`qmlcachegen`) with the host `gcc`
  (added `gcc`/`g++`/`python3` to `HOSTTOOLS_NONFATAL`)
- Phase 2: cross-builds `qtbase qtdeclarative qtimageformats qtmultimedia
  qtshadertools qtsvg` with the QNX toolchain file, staging to
  `${QNX_STAGE_DIR}/qt` (same layout as before, so `qt-cluster` consumes it
  unchanged)

**Status: correct and building, but not finished.** The build reached
**`[3350/4648]` of the cross-compile** (well into qtdeclarative, `qcc` confirmed
by `cc: warning - lang-c++ is deprecated`) before the background process was
stopped at a session boundary — it did **not fail**. Re-run to finish:

```bash
bitbake qt6-qnx      # cmake/ninja resume incrementally; multi-hour build
```

The Qt source tarball is already seeded in `build-qnx/downloads/`, so `do_fetch`
will not re-download ~950 MB.

---

## 9. Generic layer reuse, and sharing between host and guest

Two gaps closed. §6 got a *stock* recipe to **build**; it could not get one into an
**image**, and the host and guest images still duplicated each other by hand.

### 9a. Any layer → any image

Verified the gap first: `bzip2` built fine, but its `sysroot-destdir` held only
`/usr/lib` + `/usr/include` — the binaries reached **nowhere an image could see
them**, and it wrote no IFS drop-in. Two causes, both generic:

1. With `do_package` noexec'd, the **sysroot is the only endpoint**, but oe-core's
   `SYSROOT_DIRS` stages `bindir`/`sbindir` only for *native* recipes.
   `qnx-toolchain` now adds them for QNX targets.
2. Stock recipes wrote no `ifs.d` fragment. They do now.

- **`qnx-image-contract.bbclass`** (new) — the drop-in contract (`.files`/`.startup`
  format, startup ordering, `QNX_IFS_ATTR`/`QNX_IFS_DEST`, the ELF check), moved out of
  `qnx-sdp` so **both** kinds of recipe share it. Parameterised by two knobs:
  `QNX_IMAGE_HARVEST_DIRS` and `QNX_IMAGE_SOURCE_STYLE` (`search` = bare name resolved
  against the stage tree, for our recipes; `sysroot` = absolute path via the new
  `@QNX_IFS_SYSROOT@` marker, for stock ones whose `/usr/bin` is on no mkifs search path).
- **Proven end to end:** `recipes-image/qnx-ifs-reuse/` is a new image whose *entire
  payload is unmodified oe-core*. `dumpifs` shows `/usr/bin/bzip2`, `bzip2recover`,
  `libbz2.so.1` and the symlink set. Its template mentions bzip2 nowhere.

### 9b. Sharing between the host and guest images

- **`qnx-packagegroup.bbclass`** (new) — a named set of recipes; `qnx-ifs` expands
  `.install` drop-ins transitively (groups nest, order preserved, no duplicates).
  `packagegroup-qnx-hyp-common` (frame-router + rpi-gpio) is now installed by **both**
  images, which previously named them separately in two layers;
  `packagegroup-qnx-someip` replaces the guest's four-library recitation.
- **`#include` in .build templates** — `qnx_read_template` in `qnx-sdp.bbclass`, searched
  along `QNX_TEMPLATE_INCLUDE_PATH` (each layer appends its own in `layer.conf`), with
  cycle detection and `file-checksums` tracking. Five shared fragments in
  `meta-qnx/files/ifs/`: boot header, startup preamble, base utilities/links, block
  stack, network stack. Drivers deliberately stay in each image — that is the part that
  genuinely differs. `/dev/console` became `QNX_CONSOLE_DEV` rather than a second fragment.

### Verified

`bitbake -p` clean (953 recipes, 0 errors). All four images build. The generated
`.build` files for `qnx-host`, `qnx-guest` and `qnx-hello` are **set-identical** to
before the refactor — no entry added, lost or duplicated — and both startup scripts are
**byte-identical**, which is the order-sensitive part.

### Found, not fixed

`dumpifs` shows versioned shared libraries with a **dangling symlink**: the guest image
has `libvsomeip3.so.3 -> libvsomeip3.so.3.5.5` alongside a real file *named*
`libvsomeip3.so.3`, because mkifs stores a shared object under its `DT_SONAME` rather
than the filename it was given. Pre-existing (it predates this refactor) and harmless at
runtime — the loader asks for the SONAME, which does exist — but the emitted links should
be retargeted at the SONAME. Affects `qnx-sdp` and stock recipes alike.

---

## 10. CMake support in `qnx-toolchain` — two silent wrong-target bugs

§6 and §9a were proven on autotools recipes only (zlib, bzip2). CMake turned out to be
broken in a way that produces **no error at all**:

1. `cmake.bbclass` derives `CMAKE_SYSTEM_NAME` from `HOST_OS`, which is `linux` here (the
   documented `TARGET_OS` wart), so a stock CMake recipe compiled its Linux code paths.
2. Worse: `oecmake_map_compiler()` keeps only `argv[0]` of `CC`, so `qcc -Vgcc_ntoaarch64le`
   reached CMake as bare `qcc`. Verified directly — `qcc t.c -o t` with no `-V` produces an
   **x86-64** binary. Every CMake recipe would have built for the wrong CPU.

Both were latent, not hypothetical: nothing in the layer had exercised a stock CMake recipe.

**Fix** (`classes/qnx-toolchain.bbclass`): `CMAKE_SYSTEM_NAME` has no variable to override —
it is written inline by the *shell* function `cmake_do_generate_toolchain_file` — so the
class appends corrective `set()` lines to the generated `toolchain.cmake`. Later `set()`
wins. Precedent: oe-core's own `cmake-qemu.bbclass` appends to the same file the same way.

It stays ~20 lines because CMake already ships QNX support: `Platform/QNX.cmake` and
`Compiler/QCC*.cmake` turn `CMAKE_C_COMPILER_TARGET` into `-V<variant>` and emit
`CMAKE_SYSROOT` as `-Wc,-isysroot,` rather than the `--sysroot` that would displace qcc's
own. New variables: `QNX_CMAKE_SYSTEM_NAME` (empty by default — it is the guard that keeps
`cmake-native` on the host toolchain), `QNX_CMAKE_SYSTEM_VERSION`,
`QNX_CMAKE_SYSTEM_PROCESSOR`.

### Verified

`bitbake json-c` (stock oe-core, `inherit cmake`) → `libjson-c.so.5.3.0` is
`ELF 64-bit LSB shared object, ARM aarch64` with a `.note` section owned by `QNX`, and it
writes its `ifs.d` drop-in unprompted. Added to `qnx-ifs-reuse` alongside bzip2 — one recipe
per build system — and `dumpifs` shows `usr/lib/libjson-c.so.5` in the image. `bitbake -p`
clean (953 recipes, 0 errors); all four images rebuild, sole warning the pre-existing
tainted `qnx-hello:do_install`.

### Correction to §7

§7 said meta-qt6 "has no QNX path" because its toolchain says `CMAKE_SYSTEM_NAME Linux`.
That specific blocker is now known to be **fixable from a `.bbappend`** by the same
mechanism used here, and the conclusion of §7 was overstated on that point. It still holds
overall, for the other reasons: meta-qt6's default `PACKAGECONFIG` pulls a Linux runtime
closure (`dbus glib udev libinput xkbcommon`, plus unconditional `linuxfb`); its delivery
runs through `do_package`, which `qnx-toolchain` noexecs; `CMAKE_SYSROOT` would have to
serve both the SDP and `RECIPE_SYSROOT`; and it is ~6 appends across the module recipes.
`qt6-qnx` stays.

---

## 11. Stock meta-qt6 builds for QNX — §7 was wrong

§7 concluded meta-qt6 "can't replace `qt6-qnx`" and §10 walked half of that back. Tested
properly, the rest does not hold either: **stock meta-qt6 `qtbase` and `qtdeclarative` now
build for QNX**, with no source patches and no recipe rewrite.

Added `meta-oe` + `meta-python` + `meta-qt6` to `build-qnx/conf/bblayers.conf` (backup at
`conf/bblayers.conf.pre-qt6.bak`) and bridged them with bbappends in
`meta-qnx/dynamic-layers/qt6-layer/`, wired through `BBFILES_DYNAMIC` so meta-qnx keeps
`LAYERDEPENDS = "core"` and stays inert without meta-qt6.

### Three more generic `qnx-toolchain` defects, all pre-existing

These are the real result. None are Qt-specific; none were reachable with the
single-library test cases the layer had (zlib, bzip2, json-c are all one level deep).

1. **`oe_multilib_header`** replaces an installed header with a wrapper that does
   `#include <bits/wordsize.h>` — glibc's, absent on QNX, and pointless where there is one
   ABI. `multilib_header.bbclass` already returns early for musl and native; QNX is now a
   third case. Fails in a recipe far from the one that installed the header.
2. **No `-rpath-link`.** GNU ld searches `-L` for libraries named on the command line but
   only `-rpath-link`/`-rpath`/`DT_RUNPATH` for *transitive* `DT_NEEDED`. Linux gets this
   from `--sysroot`, which this class clears so qcc keeps its own.
3. **CMake never saw the link flags.** `cmake.bbclass` passes them as
   `CMAKE_<LANG>_LINK_FLAGS`, which CMake does not consume when linking targets. Fixed by
   writing `CMAKE_{EXE,SHARED,MODULE}_LINKER_FLAGS_INIT` in the generated toolchain file.
   Autotools recipes were never affected — they read `LDFLAGS` directly.

All three fail *misleadingly*: 2 and 3 emit a wall of undefined references from inside a
library that is fine and merely unfound. Three separate failures pointed at the wrong
library.

### Qt-specific, all one line each

`PACKAGECONFIG = "gui no-opengl"`; `-leventfd` (QNX 8 has it outside libc);
`-DFEATURE_libresolv=OFF` (no glibc resolver); `CMAKE_TRY_COMPILE_TARGET_TYPE`; and
`recipes-ports/freetype_%.bbappend` dropping libpng. The last three were all already in
`qt6-qnx`'s vendored toolchain file — reading it properly first would have saved two build
cycles.

Delivery needed **nothing**: `qt6-paths.bbclass` puts plugins and QML under `${libdir}`,
which `qnx-toolchain` already stages, so `do_package` being noexec did not matter.

### Verified

`libQt6Core` links `libslog2`/`libfsnotify`/`libeventfd`, `libqqnx.so` links `libscreen.so.1`,
`libQt6Quick` links `libsocket.so.4` — QNX backends, not Linux ones compiled by qcc. Full
QML/Quick stack including QuickControls2 and the `qml`/`qmlscene` tools.

### Not verified

Nothing has been put in an image or run. OpenGL is off, so QtQuick is on its software
rasteriser. Qt is 6.10.3 here vs `qt6-qnx`'s 6.8.3, so adopting this is a version jump for
`qt-cluster`, not a swap. `qt6-qnx` is **not** superseded until something renders on the
board. See [meta-qnx/docs/qt6.md](meta-qnx/docs/qt6.md).

### Build note

Qt will hang a machine at default parallelism. `BB_NUMBER_THREADS=4 PARALLEL_MAKE="-j 4"`
survives.

---

## Files changed / added

**meta-qnx** (modified): `README.md`, `conf/layer.conf`, `classes/qnx-cmake.bbclass`,
`classes/qnx-disk.bbclass`, `classes/qnx-ifs.bbclass`, `classes/qnx-rootfs.bbclass`,
`classes/qnx-sdp.bbclass`, `classes/qnx-toolchain.bbclass`,
`conf/local.conf.sample`, `docs/{cookbook,getting-started,variables,reusing-layers}.md`
&nbsp;•&nbsp; (new): `classes/qnx-autotools.bbclass`,
`classes/qnx-image-contract.bbclass`, `classes/qnx-packagegroup.bbclass`,
`conf/templates/`, `docs/sharing-between-images.md`, `files/ifs/*.build.inc`,
`recipes-example/zlib/`, `recipes-example/qnx-zlib-user/`,
`recipes-image/qnx-ifs-reuse/`

**meta-qnx-hyp** (modified): `README.md`, `conf/layer.conf`,
`recipes-image/qnx-host-image/qnx-host-image_1.0.bb`,
`recipes-image/qnx-host-image/files/qnx-host.build.in`
&nbsp;•&nbsp; (new): `recipes-packagegroups/packagegroup-qnx-hyp-common/`

**meta-qnx-guest** (modified): `README.md`, `conf/layer.conf`,
`recipes-image/qnx-guest-image/qnx-guest-image_1.0.bb`,
`recipes-image/qnx-guest-image/files/{qnx-guest.build.in,qnx-guest.qvmconf}`,
`recipes-image/qnx-host-disk/qnx-host-disk_%.bbappend`
&nbsp;•&nbsp; (new): `recipes-apps/qt-cluster/`, `recipes-image/qnx-guest-rootfs/`,
`recipes-qt/`, `recipes-packagegroups/packagegroup-qnx-someip/`

## Suggested next steps

1. **Finish the Qt build** (`bitbake qt6-qnx`) and confirm `qt-cluster` links the
   staged Qt.
2. **Boot on real hardware** — the rootfs union-mount and the whole disk are
   verified only statically. Flashing `qnx-host-disk.img` and watching serial is
   the highest-value untested step.
3. **Commit** the three layers (all changes are working-tree only).
4. Optionally prove `qnx-toolchain` breadth by building a few more `meta-oe`
   libraries (`xz`, `libyaml`, `pcre2`) — and now also *installing* them, which
   §9a made possible: add the name to `QNX_IFS_INSTALL` and nothing else. Meson is
   the one build system still unproven end to end; `qnx-meson.bbclass` covers our
   own recipes, but no stock meson recipe has been through `qnx-toolchain`, and §10
   is a warning about exactly that kind of gap.
5. Retarget versioned-library symlinks at the `DT_SONAME` (see §9, "Found, not fixed").
