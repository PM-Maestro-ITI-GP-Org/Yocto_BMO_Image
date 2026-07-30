# A/B updates with RAUC

Only meaningful on `MACHINE=rpi`. The partition layout that makes it work is
selected by `WKS_FILE:rpi`, and the boot-script half (`rpi-u-boot-scr.bbappend`)
is Pi-only too.

## Layout

`meta-bmo/wic/bmo-image.wks`:

```
boot      vfat   128 MB   --active
rootfs_a  ext4  2000 MB
rootfs_b  ext4  2000 MB
```

Both root slots are written from the same rootfs at image time, so a freshly
flashed card has two identical copies and can take an update immediately.

## Slot devices

The one machine-dependent part of `system.conf`. `rauc-conf.bbappend`
substitutes them at install time rather than shipping two files:

| | slot A | slot B |
| --- | --- | --- |
| default (Pi) | `/dev/mmcblk0p2` | `/dev/mmcblk0p3` |
| `:qemuall` | `/dev/vda2` | `/dev/vda3` |

The `do_install[vardeps]` line is what makes changing either one actually
rebuild the package.

## Slot selection

U-Boot picks the slot; `libubootenv-bin` is in the image so userspace can set
the variables the boot script reads. `u-boot` itself is installed into the
rootfs as well, which is what lets an update rewrite the bootloader.

## Bundles

`recipes-core/bundle/update-bundle.bb`:

```
RAUC_BUNDLE_FORMAT      = "verity"
RAUC_BUNDLE_SLOTS       = "rootfs"
RAUC_SLOT_rootfs        = "bmo-image-ai"
RAUC_BUNDLE_COMPATIBLE  = "raspberrypi3"
```

```bash
bitbake update-bundle
```

Two things to know before relying on this:

- `RAUC_BUNDLE_COMPATIBLE` is `raspberrypi3`, which does not describe the Pi 5
  this actually targets. RAUC refuses a bundle whose compatible string does not
  match the target's, so this is a value to check against `system.conf` rather
  than assume.
- Signing keys come from `RAUC_KEY_FILE` and `RAUC_CERT_FILE` in
  `build-bmo/conf/local.conf`, currently absolute paths under a developer's home
  directory. A fresh checkout needs its own key pair and its own paths — the
  build will not produce a usable bundle otherwise.

## The update client

`recipes-apps/ota-updatesystem/` holds `ota-daemon`, which selects the target
slot and applies bundles. It is currently **commented out** of `bmo-image-ai`:

```bitbake
# IMAGE_INSTALL:append = " ota-daemon"
```

`rauc` itself is installed, so updates can still be driven by hand with
`rauc install`.
