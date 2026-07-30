# Layers

Everything except `meta-bmo` and `meta-hey_man` is an unmodified upstream
submodule, pinned to `scarthgap`. The two local layers are where all the
project's own work lives.

| Layer | Origin | What it is here for |
| --- | --- | --- |
| `poky/` | yoctoproject/poky | Yocto itself, plus `meta-yocto-bsp` |
| `meta-openembedded/` | openembedded | `meta-oe` and `meta-python` only |
| `meta-raspberrypi/` | agherzan | the `rpi` BSP |
| `meta-lts-mixins-u-boot/` | yoctoproject | a newer U-Boot than scarthgap ships |
| `meta-qt6/` | code.qt.io, `lts-6.8` | Qt, for `bmo-image-qt` |
| `meta-tensorflow/` | yoctoproject | TensorFlow Lite, for `bmo-image-ai` |
| `meta-rauc/` | rauc | the A/B update mechanism |
| `meta-bmo/` | **local** | the distro, the images, and the applications |
| `meta-hey_man/` | **local** | two scratch recipes (`hello-world`, `dash_git`) |

`meta-bmo` has `BBFILE_PRIORITY 6`, which is what lets its bbappends win over
the BSP layers'.

## What is in meta-bmo

Grouped by what it is rather than by directory, since the directory names do not
always say much.

**The distro.** `conf/distro/bmo.conf` requires `poky.conf` and adds `systemd`,
`usrmerge`, `pam`, `opengl` and `rauc` to `DISTRO_FEATURES`; `splash`,
`hwcodecs` and `ssh-server-dropbear` to `IMAGE_FEATURES`. `systemd` is the init
manager, and `VIRTUAL-RUNTIME_initscripts` is emptied so sysvinit scripts do not
come along for the ride.

**The images**, in `recipes-core/images/` — see [building.md](building.md).

**SOME/IP**, in `recipes-net/`. Three COVESA runtimes built from git at pinned
revisions (`vsomeip`, `libcommonapi` → CommonAPI core, `commonapi-someip`), plus
`commonapi-generators` — prebuilt x86_64 Eclipse tools that turn `.fidl`/`.fdepl`
interface definitions into C++ bindings at configure time. The generators are a
`native` recipe and need a JRE, which is why `conf/layer.conf` carries
`HOSTTOOLS_NONFATAL += "java"`: bitbake's PATH is sanitised, and those symlinks
are created once at cooker startup, too early for a bbclass to add to.

`recipes-net/network-setup` is not a SOME/IP recipe as such, but it exists
because of one — see [hypervisor-guest.md](hypervisor-guest.md).

**Applications**, in `recipes-apps/`:

- `ai-app` — the TFLite motor-fault inference binary, from the `AI` repository.
  Tracks `AUTOREV`, so every build resolves the branch head.
- `motor-ai-server` — the SOME/IP service half of the motor-AI pair. Pinned, and
  deliberately so; see [hypervisor-guest.md](hypervisor-guest.md).
- `ota-daemon` / `ota-updatesystem` — the update client. Currently commented out
  of `bmo-image-ai`.
- `qt-calc-app` — for `bmo-image-qt`.

**OTA**, spread across `recipes-core/rauc/`, `recipes-core/bundle/` and
`wic/bmo-image.wks` — see [ota.md](ota.md).

## Adding a layer

Add it as a submodule, then list it in the build directory's
`conf/bblayers.conf`. Nothing is auto-discovered.

```bash
git submodule add -b scarthgap <url> meta-something
```

Note that `bblayers.conf` in each build directory holds **absolute** paths for
this checkout, which is why those files are per-machine and not shared.
