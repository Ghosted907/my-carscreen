# T1 launcher

Local development update (2026-11-12): the launcher uses a Saudi green and gold theme, a gold
crossed-swords-and-palm emblem and a restrained geometric gold trim. Its display layout follows
the original Jetour screens in `screenshots/original`: a dark, image-led canvas, the vehicle as the
main visual, slim data labels, square control tiles, a circular compass, and bottom navigation.
The vehicle overview now uses
a generated top-down T1 image based on the original car artwork; the earlier T2-like drivetrain image
is no longer shown. Tire pressure and temperature sit beside the corresponding four wheels, with
drive mode, steering, pitch, roll, heading, and altitude below. This overview opens first under
**بيانات السيارة**; the full readings and searchable property catalogue remain in adjacent tabs.
The home screen places live Waze and YouTube freeform tasks over two fixed cards directly below
the Jetour image when freeform support
is enabled. Tapping a card header expands that task to fullscreen; bringing T1 to the front restores
the task to its card where the device permits task reuse. The original home car artwork and live
driving readings remain below. Long-press an app to pin it ahead of the default shortcuts;
the Apps page also shows recently launched apps. Apps can open on the main screen or cluster, and
the edge gesture, configurable quick buttons, and saved cluster placement remain available.
All 99 structured readings and the searchable OEM SDK catalogue
remain under **بيانات السيارة**. Window, roof, and OEM settings controls are under **التحكم**.
See [TELEMETRY.md](TELEMETRY.md) for coverage, raw-value semantics, and validation.
The previous debug APK was installed on the car for a visual and live-reading check. This version
was built locally and has not yet been verified on the connected car.

Home screen for the Jetour T1 main display. It does not replace the OEM launcher unless you turn that on in Settings.

## Install

```bash
adb install -r ..\..\t1launcher-saudi-v1.9.0.apk
adb shell am start -n com.ghosted907.t1launcher/.MainActivity
```

Optional home role, after you enable it inside T1 settings:

```bash
adb shell cmd package set-home-activity com.ghosted907.t1launcher/.HomeAlias
```

To put the OEM launcher back, choose it in the system Home picker. T1 never sets itself as the default during install.

Package `com.ghosted907.t1launcher`, versionCode 10, versionName 1.9.0. This local build is a debug APK, not a published release. The Home alias stays disabled until you enable it yourself. Installing T1 does not replace the OEM home.

## Home app windows

Waze (`com.waze`) and YouTube ReVanced (`app.revanced.android.youtube`) or regular YouTube
(`com.google.android.youtube`) appear as live, interactive windows on the main display when the
device has freeform support enabled. The card header expands an existing task to fullscreen.
When T1 returns to the foreground, it requests the same task at the card bounds; Android may
instead start a new task if the app or OEM window manager refuses reuse. Without freeform support,
the cards explain the requirement and their headers still open the apps fullscreen. The freeform
commands below are also needed for these main-display windows.

Release APKs are signed with a fixed release key that is not in git. Debug builds still use the local debug keystore and do not need that secret. Installing a release APK over a build that was signed with the debug key fails until that older package is uninstalled once:

```bash
adb uninstall com.ghosted907.t1launcher
```

## Cluster window

Apps sent to the cluster launch on display 1 in freeform (`setLaunchWindowingMode(5)` plus the bundle key `android.activity.windowingMode`) with `setLaunchBounds` in 1920×720 pixels. Flags are `NEW_TASK | MULTIPLE_TASK | CLEAR_TASK`. Apply resizes the running task when the system allows it, and otherwise relaunches it at the new rectangle. Presets: full, center (`560 80 1360 640`), left half, right half, and custom. **أزل التطبيق من العدادات** sends that app back to display 0.

Freeform has to be enabled once:

```bash
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
```

Reboot after those. The editor shows the same commands when `enable_freeform_support` is not 1. The small caption bar is drawn by the system on the other app's window.

## Edge swipe

A foreground service draws a 20px transparent strip on the physical right edge of display 0 (left is optional in Settings). It sits below the status bar and stops about 100px above the bottom of a 1080px-tall screen, clear of the HVAC dock. A mostly horizontal drag toward the center, longer than 80px, brings `MainActivity` forward with `FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_REORDER_TO_FRONT`. Taps are not treated as a swipe. The strip is hidden while T1 is in front. Logcat tag `T1Launcher`, line `edgeSwipe open`.

The service starts on `BOOT_COMPLETED` and whenever T1 opens. It needs the overlay app-op:

```bash
adb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow
adb shell am start-foreground-service -n com.ghosted907.t1launcher/.EdgeSwipeService
```

Settings shows the same grant command when the permission is missing, and has the on/off switch and the edge side.

## Windows, sunroof, sunshade

Open **التحكم** from the home shortcuts or the bottom navigation. The window, roof, and shade buttons send only the audited commands listed in `VehicleCommands.kt` through the OEM `alcar_service` Binder interface. A command is sent only after a button press. The app reports whether the service accepted the request; actual movement still needs validation on the car.

The tailgate button stays disabled until its exact OEM command is documented. The live speed and gear readings are shown for context, and unverified values are not presented as current readings.
