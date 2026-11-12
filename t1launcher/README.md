# T1 launcher

Local development update (2026-11-12): the launcher uses a Saudi green and gold theme, a gold
crossed-swords-and-palm emblem and a restrained geometric gold trim. Its display layout follows
the original Jetour screens in `../../../screenshots/original`: a dark, image-led canvas, the vehicle as the
main visual, slim data labels, three-part window controls, a circular compass, and bottom navigation.
The home scene uses a clean landscape derived from the original screen and a transparent T1 cutout.
The main navigation is a vertical rail on the left, with all eight pages stacked top to bottom.
App and action pickers in Settings now open as wide, scrollable grids, including the floating-button
and edge-gesture action pickers and the two-app picker.
On first launch, a permission setup page requests microphone and location access and links to Android's
overlay, accessibility, notification-listener, usage-access, and battery-background consent screens.
Every grant is checked again on return, and Settings can reopen the page. Android must grant each
special access; the launcher cannot grant itself system or OEM privileges.
Day mode uses a brighter morning panorama with green/gold overlays.
The vehicle overview places a 3/4 T1 in the center, a portrait top-down T1 with its four tire
readings on the left, and the compass on the right. The earlier T2-like drivetrain image
is no longer shown. Tire pressure and temperature sit symmetrically beside the corresponding wheels, with
drive mode, steering, pitch, roll, heading, and altitude below. This overview opens first under
**بيانات السيارة**; the full readings and searchable property catalogue remain in adjacent tabs.
The home screen places live Waze and YouTube freeform tasks over two fixed cards directly below
the Jetour hero image when freeform support
is enabled. Tapping a card header expands that task to fullscreen; bringing T1 to the front restores
the task to its card where the device permits task reuse. The reference-inspired home scene and live
driving readings remain above the app cards. A full-width vehicle data scene below Waze and YouTube
uses the same compass dial and tire readouts as the Vehicle page: the top-down T1 has four pressure
and temperature readings beside its wheels, while a transparent T1 with its tailgate midway open stays in the middle.
The home hero shows a Saudi analog clock with the crossed swords and palm inside its dial; Riyadh
time drives its hands, and equal-size numeric Gregorian and Arabic-digit Umm al-Qura Hijri dates sit
at the bottom of the dial. The Riyadh-time greeting and OEM outside-temperature reading remain. A compass,
four tire pressures and temperatures, and the new half-open-tailgate T1 image sit below the app cards.
The T1 hero headlamps follow live left/right indicator, low beam and high beam readings.
Amber indicators flash around the lamp housings; a white projector stays lit with low beam and grows
brighter with high beam. Stale or unavailable readings never light the graphic. The indicator reference
is `../../../screenshots/اشارات/فليشرpng.png`; actual timing and side mapping require an on-car check.
The Home page shows read-only live lamp states. The old visual-test buttons have been removed.
The available T1 files identify `KeyVehiclePropertyHAZARD` as emergency-brake flashing,
not the hazard switch, and provide no verified indicator or beam command. No lamp command is sent.
The navigation quick tile is replaced by a saved note. The home vehicle panel now shows the
half-open T1 cutout, the live tailgate height when connected, and a clearly disabled open button.
The OEM settings APK contains a power-liftgate key (2050), but its accessible command route and
permissions are not verified on this T1; the launcher does not send an untested motion command.
The Controls page now has a tailgate button that opens Jetour's exported tailgate settings action
(`com.car.settings.car.tailgate`) in the original OEM app; the user performs opening there.
The full-viewport art board is now under **العدادات**. It supports draggable Arabic text with saved
color and size, drawing with a tappable rainbow strip and hue/saturation/brightness controls, the draggable
gold emblem, and one imported image sticker from saved files. Both stickers have separate saved size controls.
JCarTools controls appear directly in
main Settings without the redundant entry button. **بيانات السيارة** begins with a live driving row
for speed, fuel, gear, outside temperature, drive mode, range, and RPM. The control page's
left-side shortcut list has been removed. Long-press an app to pin it ahead of the default shortcuts;
the Apps page also shows recently launched apps. Apps can open on the main screen or cluster, and
app launch, floating buttons, edge zones and app hiding follow JCarTools/NaviStart 1.4.1 (see below).
All 99 structured readings and the searchable OEM SDK catalogue
remain under **بيانات السيارة**. Window, roof, and shade controls each use one three-part segmented
control under **التحكم**; OEM settings are also there.
See [TELEMETRY.md](TELEMETRY.md) for coverage, raw-value semantics, and validation.
The previous debug APK was installed on the car for a visual and live-reading check. This version
was built locally and has not yet been verified on the connected car.

Home screen for the Jetour T1 main display. It does not replace the OEM launcher unless you turn that on in Settings.

## Install

```bash
adb install -r ..\..\t1launcher-saudi-v1.13.1.apk
adb shell am start -n com.ghosted907.t1launcher/.MainActivity
```

Optional home role, after you enable it inside T1 settings:

```bash
adb shell cmd package set-home-activity com.ghosted907.t1launcher/.HomeAlias
```

To put the OEM launcher back, choose it in the system Home picker. T1 never sets itself as the default during install.

Package `com.ghosted907.t1launcher`, versionCode 32, versionName 1.13.1. The Home alias stays disabled until you enable it yourself. Installing T1 does not replace the OEM home.

1.11.0 merges Codex's 1.10.3-1.10.6 work (home clock and Hijri date, telemetry scene with the half-open tailgate image, Settings art board, control-page shortcut list removed; described above) with the NaviStart 1.11.0 additions (display-routing actions, black screen, recents, play/pause, split screen, Arabic/English app language; see the JCarTools section).

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

### 1.11.0 additions from the NaviStart report (`research/navistart/features.md`)

* **Display routing actions** (NaviStart `n0.d` / `e0.z.e`): `enum:GO_TO_PP` (code 1, stored `pkg` on the
  cluster), `enum:GO_TO_GU` (6, on the head unit) and `enum:TOGGLE_GU_PP` (9, on the other display than
  last time, remembered in `pkg_area`: 2 = cluster, 3 = head unit). Same `setLaunchDisplayId` path as above.
  The cluster card also has a "تبديل بين الشاشتين" button and shows the last destination.
* **Black screen** (`enum:RUN_BLACK`, code 2): `JctBlackActivity` on the head-unit display index, optional dim
  clock (`black_clock`) and window brightness (`black_brightness`). Tap to close. Widgets/HTML screens not ported.
* **Recents** (`enum:RECENT_TASK_MANAGER`, 74): system recents through the accessibility service, else the
  T1 apps page. **Play/pause** (`enum:MEDIA_PLAY_PAUSE`, 75).
* **Split screen** (`split:<a>,<b>,<orient>,<ratio>`): now runnable. Pick two apps, side by side or stacked,
  30:70 / 50:50 / 70:30; both open as freeform windows on display 0 (`JctSplit.bounds` + `HomeAppWindows.dock`).
  Needs freeform support; otherwise a message explains it. NaviStart's own split goes through a closed native module.
* **App language** (NaviStart `e0.b0`, key `app_language`: `ar` default, `en`, `system`): MainActivity wraps
  its base context with that Locale. The JCarTools settings page, action names and their messages are in
  Arabic and English and follow the language (LTR in English). The rest of the launcher is still Arabic RTL.

Not ported: vehicle/climate/ADAS/driver-profile commands (`CarCmdProxy`, model-specific, nothing for T1),
VPN, licence, store, GPS substitution, radar database, voice models, HUD and custom instrument themes.

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

The direct tailgate motion button on Home stays disabled until its exact OEM command is documented.
Controls opens the OEM tailgate page instead. The live speed and gear readings are shown for context,
and unverified values are not presented as current readings.


## 1.13.1 (local, not committed)
* Phone control: back to plain auto-discovery (no PIN, no manual address). Anyone on the same network can
  control while "التحكم من الجوال" is on; use the phone hotspot. Both switches stay off by default, only the
  nine window/sunroof/sunshade ids pass, 1-second hold, no tailgate, no audio.
* Gauges art board: 13 fonts (system + bundled OFL Google Fonts: Cairo, Tajawal, Amiri, Reem Kufi, Lalezar,
  Orbitron, Bebas Neue, Pacifico; see FONTS-LICENSE.md), scrollable font row, colour board supports tap and slide.
* Edge zones: the handle widens out of the edge and follows the finger while dragging, then slides back.
* Floating button: shows the assigned app's icon, new "كبسولة" shape, fades/scales in.
* Lights: read-only (Low 557843647, High 557843648, L/R 557843645/646). No light write path is used or added.
