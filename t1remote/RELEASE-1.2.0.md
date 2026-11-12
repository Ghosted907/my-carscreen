# T1 1.14.0 and T1 Remote 1.2.0

The phone's «لوحتي» tab supports freehand drawing, text, preset stickers and imported images.
Send the transparent composition to a bounded cluster overlay, choose its horizontal and vertical
position, or hide it. Both devices must use the same local network and the launcher's phone-control
switch must be enabled. There is no password or pairing code.

The overlay requires Android's draw-over-other-apps permission and a recognized cluster display.
It is removed when the remote host service stops. The editor currently flattens additions into one
image and its draft lasts while the editor is open. Physical Jetour display behavior needs an in-car test.

Reviewed the existing camera launch fallback, gesture styling, live light signals and font picker.
Bundled the full licenses for all eight added fonts. Added bounded image payload validation and tests.
Launcher and phone debug builds and JVM tests pass. Afaq consumes the versioned APK manifest
published by the release workflow, including SHA-256 checks and the phone APK.