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

> **The one real trap.** The QNX recipe hashes the *variables* naming those
> files, not the files' *contents*. Rebuilding this image produces a new rootfs
> at the same path and an identical task signature, so bitbake reuses sstate and
> ships the **old** `fs.img` without a word. After rebuilding here, the QNX side
> needs `bitbake -c cleansstate qnx-linux-guest qnx-host-data` before its disk
> image, or the change will not be on the card.

`root=/dev/vda` in the guest's kernel command line relies on the rootfs being a
raw filesystem image rather than a partitioned disk — a `.rootfs.ext4`, not a
`.wic`. That is why `IMAGE_FSTYPES` must keep producing ext4.

## Debugging from this side

`motor-ai-server` is a systemd unit, so it talks:

```bash
journalctl -u motor-ai-server -f
systemctl status network-setup
ip -br addr
```

The QNX client is backgrounded from a boot script with output to `/dev/null` and
says nothing. When the pair does not connect, start here.
