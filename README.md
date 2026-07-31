# Yocto BMO Image

Yocto build for the BMO project: a Linux image for the Raspberry Pi that also
runs as a guest under the QNX hypervisor.

Everything except `meta-bmo` and `meta-hey_man` is an upstream submodule pinned
to `scarthgap`.

## Quick start

```bash
git clone --recurse-submodules git@github.com:PM-Maestro-ITI-GP-Org/Yocto_BMO_Image.git
cd Yocto_BMO_Image
source poky/oe-init-build-env build-bmo
bitbake bmo-image-ai
```

Output lands in `share/tmp/deploy/images/qemuarm64/`.

Already cloned without `--recurse-submodules`:

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

## Documentation

- [building.md](docs/building.md) — build directories, the four images, machines,
  and where the build state lives
- [layers.md](docs/layers.md) — what each layer is for, and what is inside
  `meta-bmo`
- [hypervisor-guest.md](docs/hypervisor-guest.md) — running as guest-2 under
  qvm: the SOME/IP pair, the addresses that have to agree with the QNX tree, and
  how a rebuild here reaches the target
- [ota.md](docs/ota.md) — the RAUC A/B layout, slots and bundles

## The one non-obvious thing

`MACHINE ?= "qemuarm64"` is deliberate, not a stand-in for missing hardware. qvm
presents the same virtio-mmio and pl011 layout qemu's `virt` machine does, which
is exactly what lets this image boot as a hypervisor guest with no changes.
`MACHINE=rpi` builds a different, standalone product; both are supported and the
recipes branch on it.

## Known problems

A permissions error from bitbake on Ubuntu is the unprivileged user namespace
restriction:

```bash
sudo apparmor_parser -R /etc/apparmor.d/unprivileged_userns
```

Several recipes fetch over SSH from private repositories, so a key with access
to the organisation is required.
