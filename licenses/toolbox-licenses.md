# toolbox binaries — licenses & source

aisoul ships three static binaries inside the apk (as `lib*.so` native libs,
per-abi). none of them are modified; checksums of the exact files are recorded
in `toolbox/CHECKSUMS.sha256` in the source tree.

## busybox — GPL-2.0-only

- arm64-v8a: busybox 1.36.1, static musl build, from the Alpine Linux
  `busybox-static` package (aarch64, v3.20 main repository).
- x86_64: busybox 1.35.0, static musl build, from busybox.net official
  binaries (`1.35.0-x86_64-linux-musl`).
- license: GNU GPL v2 (see `GPL-2.0.txt` in this folder).
- **written offer of source:** busybox source corresponding to these builds is
  available at <https://busybox.net/downloads/> (busybox-1.35.0.tar.bz2,
  busybox-1.36.1.tar.bz2) and Alpine's aports tree
  <https://gitlab.alpinelinux.org/alpine/aports> (main/busybox). on request to
  the developer address in the play listing, we will provide the complete
  corresponding source on physical media at cost, for at least three years.

## curl — curl license (MIT/X derivative)

- curl 8.7.1 static builds from <https://github.com/moparisthebest/static-curl>
  (aarch64 + amd64).
- license: <https://curl.se/docs/copyright.html> — copyright (c) Daniel
  Stenberg and contributors. permission to use, copy, modify, and distribute
  granted per the curl license.

## jq — MIT

- jq 1.7.1 official release binaries (`jq-linux-arm64`, `jq-linux-amd64`)
  from <https://github.com/jqlang/jq/releases>.
- license: MIT — copyright (c) 2012 Stephen Dolan and jq contributors.

## android system ca certificates

at first run the app concatenates the device's own system ca store
(`/system/etc/security/cacerts`) into a pem bundle so the bundled curl can
verify tls. no certificates are shipped or added.
