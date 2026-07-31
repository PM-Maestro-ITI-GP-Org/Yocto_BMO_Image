# bmo-image-ai as a hypervisor guest

`bmo-image-ai` does not only run standalone. Its main deployment is as **guest-2
under the QNX hypervisor (qvm)** on a Raspberry Pi 5, alongside a QNX guest.
Several things in this repository only make sense in that light, and this is the
document that says why.

The QNX side lives in a **separate repository** and is not built from here.

```
                Raspberry Pi 5
    ┌─────────────────────────────────────────┐
    │  QNX hypervisor host  (qnx-host.ifs)    │
    │    vp0 10.0.0.1        vp1 10.0.1.1     │
    └────┬───────────────────────┬────────────┘
         │                       │
   ┌─────┴────────┐        ┌─────┴──────────────┐
   │ guest-1 QNX  │        │ guest-2 Linux      │
   │ vtnet0 .0.2  │        │ qhost0  10.0.1.2   │
   │              │        │                    │
   │ vtnet1       │◄──────►│ qguest0            │
   │ 10.0.2.1     │ direct │ 10.0.2.2           │
   │              │  link  │                    │
   │ motor_ai_    │ SOME/IP│ motor_ai_server    │
   │ client       │        │ + ai-app (TFLite)  │
   └──────────────┘        └────────────────────┘
```

## The split, and why it is this way

`motor_ai_client` runs on QNX because that is where the SPI motor data arrives.
`motor-ai-server` runs **here** because that is where the AI runtime is. They
talk CommonAPI over SOME/IP across a virtio-net link that bypasses the host
entirely.

Both halves carry their own copy of the `.fidl`/`.fdepl` interface definitions,
and bindings generated from mismatched revisions fail *at runtime*, not at build
time — a service that starts cleanly and is simply never found. That is why
`motor-ai-server` pins `SRCREV` rather than tracking `AUTOREV` like `ai-app`
does, and why the pin must match the one the QNX tree uses for the client.

## The addresses are not free choices

Three files have to agree, and two of them are in the other repository:

| Value | Set here | Must match |
| --- | --- | --- |
| `10.0.2.2` | `network-setup.sh` (qguest0) | `"unicast"` in the server's `vsomeip.json`, and the address the client dials |
| `10.0.2.1` | — | `QNX_GUEST_PEER_IP` in the QNX guest image |
| `10.0.1.2` | `network-setup.sh` (qhost0) | the host's `vp1` network, `10.0.1.1` |

Change one, change all of them.

## Interface naming

This guest has two virtio-net devices, and the kernel names them in probe order,
which is not stable. `eth0` is therefore not a reliable way to say "the one
facing the host".

`recipes-net/network-setup/files/10-qnx-*.link` pin the names by MAC:

| MAC | Name | Link |
| --- | --- | --- |
| `52:54:00:00:01:02` | `qhost0` | to the hypervisor host |
| `52:54:00:00:02:02` | `qguest0` | direct to the QNX guest |

**Those MACs are set by the QNX side** (`QNX_LINUX_GUEST_MAC` and
`..._MAC2`). Override them there without updating these files and udev applies
neither rename, `network-setup` finds neither interface, and this guest silently
comes up with no addresses at all. The failure is quiet, so check here first.

`network-setup.sh` waits up to ten seconds for the renamed interfaces rather than
ordering after the deprecated `systemd-udev-settle.service`. On hardware where
neither exists (`MACHINE=rpi`), it falls through to configuring `eth0` with the
legacy `192.168.2.2/16`.

## The multicast route is load-bearing

vsomeip announces services over `224.244.224.245`. Without a route for the group
its `sendto()` fails with `ENETUNREACH`, and the service starts, logs nothing
unusual, and is never discovered. `network-setup.sh` adds `224.0.0.0/4` on
`qguest0` for exactly this.

## How this image reaches the target

The QNX build stages the artefacts of *this* build into the hypervisor host's
data partition. Nothing here pushes; the QNX side pulls, from a path it is
given:

```
QNX_LINUX_GUEST_DEPLOY = ".../share/tmp/deploy/images/qemuarm64"
```

It takes two files, renaming them to what the guest's `qvmconf` loads by name:

```
Image-qemuarm64.bin                 → /guests/guest-2/image.bin
bmo-image-ai-qemuarm64.rootfs.ext4  → /guests/guest-2/fs.img
```

Both are the unversioned symlinks Yocto keeps pointing at the newest build, so a
rebuild here is picked up without editing anything there.

A rebuild here is picked up automatically. `qnx-linux-guest` carries a
`do_install[file-checksums]` on both absolute paths, so the *contents* of the
kernel and rootfs are part of its task signature, and the change propagates down
the dependency chain to the disk image on its own.

> **Historical note.** That was not always true. The recipe originally hashed
> only the *variables* naming the files, which are identical across rebuilds —
> the names are unversioned symlinks. A rebuilt rootfs therefore produced the
> same signature, bitbake restored `do_install` from sstate, and the disk went
> out carrying the **previous** rootfs with no error and no warning. If you are
> on a checkout of the QNX tree predating that fix, run
> `bitbake -c cleansstate qnx-linux-guest qnx-host-data` before building the
> disk. Check for `file-checksums` in `qnx-linux-guest_1.0.bb` to tell which
> you have.

`root=/dev/vda` in the guest's kernel command line relies on the rootfs being a
raw filesystem image rather than a partitioned disk — a `.rootfs.ext4`, not a
`.wic`. That is why `IMAGE_FSTYPES` must keep producing ext4.

## The hypervisor has to be 8.0.4 or newer

Nothing in this repository can fix it if it is not, so it is worth knowing what
the failure looks like from this side. The Pi 5 is GICv2, and a guest has to be
told so — `vdev gic / version 2` in its qvmconf. QNX Hypervisor **8.0** rejects
that value outright, and with no stanza at all qvm offers a memory-mapped GICv3
that Linux's `gic-v3` driver cannot drive. This image then panics early, before
any of its own userspace runs:

```
GICv3: CPU0: found redistributor 0 region 0:0x000000002f100000
Internal error: Oops - Undefined instruction
pc : gic_cpu_sys_reg_init+0x5c/0x2b8
```

That is `MRS x0, ICC_SRE_EL1` — the GICv3 CPU system-register interface, which
this hardware does not provide. It is not a kernel configuration problem and no
change to `bmo-image-ai` affects it.

Hypervisor **8.0.4 Update 1** accepts GICv2 and the image boots. The QNX tree's
`qnx-linux-guest_1.0.bb` carries the disassembly of both versions.

## Debugging from this side

`motor-ai-server` is a systemd unit, so it talks:

```bash
journalctl -u motor-ai-server -f
systemctl status network-setup
ip -br addr
```

The QNX client is backgrounded from a boot script with output to `/dev/null` and
says nothing. When the pair does not connect, start here.
