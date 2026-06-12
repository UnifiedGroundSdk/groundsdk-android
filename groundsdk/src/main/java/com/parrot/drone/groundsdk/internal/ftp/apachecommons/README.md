# Parrot-patched Apache Commons Net fork

This package is a **deliberately maintained source fork** of Apache Commons Net
**3.6** (`org.apache.commons.net`), repackaged under
`com.parrot.drone.groundsdk.internal.ftp.apachecommons[.parser]`. It is NOT a
verbatim vendored copy and MUST NOT be replaced by the upstream
`commons-net:commons-net` artifact without the migration described below.

Decision record (2026-06-12, operator): keep the fork; full source-wise control
lives here in-tree. The commented-out `//implementation 'commons-net:commons-net:3.6'`
lines in the consuming `build.gradle` files are historical breadcrumbs only.

## Provenance

- Upstream: Apache Commons Net 3.6 (confirmed by `@since 3.6` javadoc in
  `FTPClient.java`).
- 57 source files: 37 in this package root + 20 in `parser/`.
- All files repackaged from `org.apache.commons.net[.*]` to this namespace.

## Parrot modifications (why the fork exists)

`FTPClient.java` is materially modified relative to upstream 3.6:

- Android imports added: `android.os.Handler`, `android.os.Looper`,
  `android.os.SystemClock`.
- A second constructor accepting `CreateProxyListener` / `RemoveProxyListener`
  (from `FtpSession`), integrating Parrot's FTP-over-SkyController proxy.
- `createProxy()` / `removeProxy()` methods using main-looper `Handler.post()`
  and `SystemClock.sleep()` (near the end of the file, ~lines 4097–4127).

These hooks are load-bearing for the Bebop-era media/FTP path (drone media
download via SkyController proxying).

## Consumers of this package (outside it)

- `groundsdk/.../internal/ftp/FtpSession.java` — `CopyStreamAdapter`,
  `FTPClient`, `FTPFile`, `FTPReply`
- `arsdkengine/.../peripheral/bebop/media/MediaItemImpl.java` — `FTPFile`
- `arsdkengine/.../peripheral/bebop/media/BebopMediaStore.java` — `FTPFile`

## If un-vendoring is ever required (not planned)

1. Enable `implementation 'commons-net:commons-net:3.6'` in `groundsdk` and
   `arsdkengine` build.gradle.
2. Migrate the consumers above to `org.apache.commons.net.*` imports.
3. Extract the proxy integration into a `ParrotFTPClient extends
   org.apache.commons.net.ftp.FTPClient` subclass carrying the listener
   constructor and `createProxy()`/`removeProxy()`.
4. Delete the 57 forked files.

Regression risk concentrates on Bebop media FTP (bench-validation territory);
this is the primary reason the fork is kept rather than un-vendored.
