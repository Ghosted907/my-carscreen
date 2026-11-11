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
app launch, floating buttons, edge zones and app hiding follow JCarTools/NaviStart 1.4.1 (see below).
All 99 structured readings and the searchable OEM SDK catalogue
remain under **بيانات السيارة**. Window, roof, and OEM settings controls are under **التحكم**.
See [TELEMETRY.md](TELEMETRY.md) for coverage, raw-value semantics, and validation.
The previous debug APK was installed on the car for a visual and live-reading check. This version
was built locally and has not yet been verified on the connected car.

Home screen for the Jetour T1 main display. It does not replace the OEM launcher unless you turn that on in Settings.

## Install

```bash
adb install -r ..\..\t1launcher-saudi-v1.9.2.apk
adb shell am start -n com.ghosted907.t1launcher/.MainActivity
```

Optional home role, after you enable it inside T1 settings:

```bash
adb shell cmd package set-home-activity com.ghosted907.t1launcher/.HomeAlias
```

To put the OEM launcher back, choose it in the system Home picker. T1 never sets itself as the default during install.

Package `com.ghosted907.t1launcher`, versionCode 12, versionName 1.9.2. This local build is a debug APK, not a published release. The Home alias stays disabled until you enable it yourself. Installing T1 does not replace the OEM home.

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

## JCarTools (NaviStart 1.4.1) port

Since 1.9.0 the launcher's own launch path, configurable edge buttons, cluster placement presets and the
20px edge strip are gone (sources kept in `t1launcher/removed-by-jcartools-port/`). They are replaced by
NaviStart's methods, ported 1:1:

* **Launch on a display**: `getLaunchIntentForPackage(pkg)` + `FLAG_ACTIVITY_NEW_TASK` +
  `ActivityOptions.makeBasic().setLaunchDisplayId(displays[d])`, where `displays` is
  `DisplayManager.getDisplays()` and `d` is the head-unit index (`gu_display_index`, default 0) or the
  cluster index (`pri_display_index`, default 1). No freeform, no bounds. `JctLaunch.kt`.
* **Buttons**: the single floating button and the 6/8-slot multi-button (`JctLounchService`), each slot
  an action string: `app:<pkg>`, `enum:<ACTION>`, `split:<pkg>|<pkg>`, `task:<group>`. Task groups run
  their steps in order and can auto-start at boot (`JctBootService`). Settings → تشغيل التطبيقات.
* **Edge zones**: six zones (left top/bottom, right top/bottom, bottom left/right), 32dp thick,
  `TYPE_ACCESSIBILITY_OVERLAY` (2032) windows from the accessibility service `T1TouchService`.
  A drag longer than 90px (capped at 500px) runs the zone's action. Enable T1 remote touch in
  Accessibility settings.
* **App hiding**: unprivileged mode disables the launcher's own shortcut components with
  `setComponentEnabledSetting`. Full hiding uses `DevicePolicyManager.setApplicationHidden`, which only
  works when T1 is device owner. Restore: 14 taps on the transparent recovery square, or
  `adb shell am broadcast -a com.ghosted907.t1launcher.action.USER_APPS_HIDE_OPEN -n com.ghosted907.t1launcher/.UserAppsHideCommandReceiver`.

Device owner is never set by the app. It is a one-time manual step, only possible when the device has
no accounts, and it is hard to undo (the app must clear it itself or the device must be reset).
Do not run it on the car unless you accept that:

```bash
adb shell dpm set-device-owner com.ghosted907.t1launcher/.ClusterDeviceAdmin
```

The floating buttons and recovery square need the overlay app-op:

```bash
adb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow
```

## Windows, sunroof, sunshade

Open **التحكم** from the home shortcuts or the bottom navigation. The window, roof, and shade buttons send only the audited commands listed in `VehicleCommands.kt` through the OEM `alcar_service` Binder interface. A command is sent only after a button press. The app reports whether the service accepted the request; actual movement still needs validation on the car.

The tailgate button stays disabled until its exact OEM command is documented. The live speed and gear readings are shown for context, and unverified values are not presented as current readings.
