# Building

## Set up

```bash
git clone --recurse-submodules git@github.com:PM-Maestro-ITI-GP-Org/Yocto_BMO_Image.git
cd Yocto_BMO_Image
```

Already cloned without the submodules:

```bash
git submodule update --init --recursive
```

Then source the environment. `build-bmo` is the build directory that matters;
its `conf/` is tracked, so this reuses it rather than generating a fresh one.

```bash
source poky/oe-init-build-env build-bmo
```

## Build

```bash
bitbake bmo-image-ai
```

Output lands in `share/tmp/deploy/images/<machine>/`.

## The images

All four inherit `core-image` and share `debug-tweaks`, so every one of them has
a passwordless root login. None is hardened; do not ship one.

| Image | What it adds |
| --- | --- |
| `bmo-image` | the baseline — `packagegroup-core-boot` and a few tools |
| `bmo-image-ai` | **the one in active use.** TFLite, Keras, the Python data stack, `ai-app`, `motor-ai-server`, RAUC and the network configuration |
| `bmo-image-qt` | Qt 6 and `qt-calc-app` |
| `bmo-image-connected` | networking extras |

`bmo-image-ai` is the image that runs as a guest under the QNX hypervisor.
[hypervisor-guest.md](hypervisor-guest.md) covers what that implies, and it is
worth reading before changing anything about its networking.

## Machines

`MACHINE ?= "qemuarm64"` in `build-bmo/conf/local.conf`.

That is not a placeholder for "we have not got hardware yet". `qemuarm64` is the
target: qvm presents the same virtio-mmio and pl011 layout qemu's `virt` machine
does, so a kernel built for qemu boots as a hypervisor guest unmodified.
Changing it to `rpi` produces a different, standalone product.

Both are supported, and the recipes branch on it:

- `IMAGE_INSTALL:append:rpi` adds the wifi/bluetooth stack and firmware, none of
  which has a counterpart under qemu.
- `WKS_FILE:rpi` selects the A/B partition layout; other machines fall back to
  the default image layout.
- `RAUC_SLOT_*_DEVICE` is `/dev/mmcblk0p{2,3}` by default and
  `/dev/vda{2,3}` under `:qemuall`.

## Where the build state lives

`build-bmo/conf/local.conf` points all three of these outside the build
directory, so wiping `build-bmo` costs nothing but a re-parse:

```
DL_DIR     = share/downloads
SSTATE_DIR = share/sstate-cache
TMPDIR     = share/tmp
```

These are absolute paths in that file and will need editing on a fresh checkout.

## Other build directories

- `build-hyp/` — Pi-only, U-Boot mixin work. Small, and not part of the main flow.
- `build-qnx/` — **dead.** It refers to `meta-qnx*` layers that were removed from
  this tree; that work now lives in a separate repository. The directory is left
  in place only because it holds several GB of build state.

## Known problems

A permissions error from bitbake on Ubuntu is the unprivileged user namespace
restriction, not a bug in the build:

```bash
sudo apparmor_parser -R /etc/apparmor.d/unprivileged_userns
```

`BB_DISABLE_NETWORK_SANDBOX = "1"` in `local.conf` is there for the same reason.

Several recipes fetch over SSH from private GitHub repositories
(`protocol=ssh`), so a working key with access to the organisation is required
or those fetches fail.
