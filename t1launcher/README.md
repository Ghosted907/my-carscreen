# T1 launcher

Home screen for the Jetour T1 main display. It does not replace the OEM launcher unless you turn that on in Settings.

## Install

```bash
pm install -r -i com.android.packageinstaller t1launcher-debug.apk
adb shell am start -n com.ghosted907.t1launcher/.MainActivity
```

Optional home role, after you enable it inside T1 settings:

```bash
adb shell cmd package set-home-activity com.ghosted907.t1launcher/.HomeAlias
```

To put the OEM launcher back, choose it in the system Home picker. T1 never sets itself as the default during install.

Package `com.ghosted907.t1launcher`, versionCode 6, versionName 1.5.0. The Home alias stays disabled until you enable it yourself. Installing T1 does not replace the OEM home.

Release APKs are signed with a fixed release key that is not in git. Debug builds still use the local debug keystore and do not need that secret. Installing a release APK over a build that was signed with the debug key fails until that older package is uninstalled once:

```bash
adb uninstall com.ghosted907.t1launcher
```

## Compass photo

A non-touch circle on display 1 covers the CLASSIC compass at 1556,384 (diameter 232). Pick a picture in Settings, or push one and start the overlay:

```bash
adb push t1_compass.png /sdcard/Download/t1_compass.png
adb shell pm grant com.ghosted907.t1launcher android.permission.READ_EXTERNAL_STORAGE
adb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow
adb shell am start-foreground-service -n com.ghosted907.t1launcher/.ClusterOverlayService
```

The service also starts on boot and when T1 opens. It copies that file into app-private storage. No photo is stored in the APK. Logcat tag `T1Launcher`, line `compassOverlay shown cx=.. cy=.. d=..`.

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

Home tile **النوافذ والفتحة**. Each control is one tap and stays available while driving. There is no speed or gear lock and no press-and-hold. **إيقاف / سكّر الكل** stays on screen and sends close for all windows, the sunroof, and the sunshade.

Commands go only through the OEM assistant receiver `com.autolink.voiceassistant/.adapter.core.iflytek.IflytekMessageReceiver`, action `com.iflytek.autofly.handMessage`, string extra `value`. The JSON lives in `src/main/assets/window_commands.json`. Every send is logged with tag `T1Launcher` as `handMessage id=... value=...`.

Test one at a time with `adb shell am broadcast -n com.autolink.voiceassistant/.adapter.core.iflytek.IflytekMessageReceiver -a com.iflytek.autofly.handMessage --es value '<json>'`.

All windows open:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"open\",\"semantic\":{\"slots\":{\"name\":\"car_window\",\"position\":\"all\"}}}"}
```

All windows close:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"close\",\"semantic\":{\"slots\":{\"name\":\"car_window\",\"position\":\"all\"}}}"}
```

Driver window open:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"open\",\"semantic\":{\"slots\":{\"name\":\"car_window\",\"position\":\"main_driver\"}}}"}
```

Driver window close:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"close\",\"semantic\":{\"slots\":{\"name\":\"car_window\",\"position\":\"main_driver\"}}}"}
```

Sunroof open:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"open\",\"semantic\":{\"slots\":{\"name\":\"sunroof\"}}}"}
```

Sunroof close:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"close\",\"semantic\":{\"slots\":{\"name\":\"sunroof\"}}}"}
```

Sunshade open:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"open\",\"semantic\":{\"slots\":{\"name\":\"sunshade\"}}}"}
```

Sunshade close:

```text
{"messageType":"REQUEST","focus":"carControl","position":"main_driver","content":"{\"service\":\"carControl\",\"operation\":\"close\",\"semantic\":{\"slots\":{\"name\":\"sunshade\"}}}"}
```
