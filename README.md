# Cluster Diag / تشخيص ثيم العدادات

This repository also contains the T1 launcher in `:t1launcher` (`com.ghosted907.t1launcher`, versionName 1.5.0, versionCode 6). See `t1launcher/README.md`. This app, `:app`, is `com.ghosted907.clusterdiag`, versionName 1.6.0, versionCode 7.

## Releases / الإصدارات

One git tag `v*` builds both release APKs. The tag matches the T1 launcher versionName. Neither package has a GitHub release yet, so this first ship keeps the current numbers: `:t1launcher` versionName `1.5.0` / versionCode `6`, and `:app` (clusterdiag) versionName `1.6.0` / versionCode `7`. clusterdiag's versionName stays independent of the launcher, so the first tag is `v1.5.0` while clusterdiag remains `1.6.0`. After a package is published, the next change to that package increments its versionCode by 1 and its versionName. Do not bump a package that has never been released. GitHub Actions publishes `dist/apps.json` plus the APKs on the `dist` branch for https://raw.githubusercontent.com/Ghosted907/my-carscreen/dist/apps.json . The browser installer reads that file. Release assets may also be attached to the GitHub Release, but the site uses the raw URLs.

Release signing uses repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. Debug builds do not need them. The release key is not in git. Switching a package that was installed with the debug signature onto this release key needs one uninstall:

```bash
adb uninstall com.ghosted907.clusterdiag
adb uninstall com.ghosted907.t1launcher
```

Small sideload diagnostic for a Jetour T1 Autolink head unit (Android 11, API 30, arm64). It binds to the exported service `com.autolink.clusterinteraction.CiService` and sends **preset** cluster commands only.

تطبيق تشخيصي صغير يُثبَّت بجانب النظام على وحدة Jetour T1. يتصل بالخدمة المصدّرة `CiService` ويرسل أوامر **جاهزة** للعدادات فقط.

The APK has no native code, so the same debug build installs on arm64. It does not remount, flash, or replace system apps.

ملف APK بلا مكتبات أصلية، ويثبَّت على arm64. لا يعيد تركيب النظام ولا يستبدل تطبيقات النظام.

## Install / التثبيت

```bash
adb devices
adb install -r artifacts/clusterdiag-debug.apk
adb shell am start -n com.ghosted907.clusterdiag/.MainActivity
```

Package / الحزمة: `com.ghosted907.clusterdiag`

Uninstall / الإزالة:

```bash
adb uninstall com.ghosted907.clusterdiag
```

`adb install` does not need the on-screen “unknown sources” toggle. If the Autolink launcher hides third-party icons, start the activity with the `am start` command above. On a phone the app still opens: it shows an Arabic and English error that `CiService` is missing, and it sends nothing.

التثبيت عبر adb لا يحتاج تفعيل «مصادر غير معروفة». إذا أخفت واجهة السيارة أيقونة التطبيق، شغّله بالأمر `am start` أعلاه. على الهاتف يفتح التطبيق ويعرض خطأ بالعربية والإنجليزية أن الخدمة غير موجودة، ولا يرسل أي أمر.

## Buttons / الأزرار

| Control | What it sends | ما الذي يُرسل |
| --- | --- | --- |
| رقمي / DIGITAL | `setThemeToCluster(1)` | ثيم رقمي |
| كلاسيكي / CLASSIC | `setThemeToCluster(2)` | ثيم كلاسيكي |
| ملاحة / NAVIGATION | `setThemeToCluster(3)` | ثيم ملاحة |
| الخط 1 / Font 1 | `setClusterFont(1)` | الخط الأول في إعدادات السيارة |
| الخط 2 / Font 2 | `setClusterFont(2)` | خط لوحة العدادات (الصورة الثانية في الإعدادات) |
| نهاري / Day | `setDayNightMode(0)` | وضع نهاري |
| ليلي / Night | `setDayNightMode(1)` | وضع ليلي |
| إعادة القراءة / Re-read | `getClusterTheme`, `getClusterFont`, `getDayNightMode` | قراءة فقط |

On launch the app binds, and if the interface loads it reads the current theme. The big number is that reading. A white outline marks the preset that matches the last successful read.

عند الفتح يتصل التطبيق بالخدمة، ثم يقرأ الثيم الحالي إذا نجح تحميل الواجهة. الرقم الكبير هو نتيجة القراءة. الإطار الأبيض يعلّم الخيار المطابق لآخر قراءة ناجحة.

Watch the physical cluster. The on-screen line reports whether the **binder call** finished, not a camera view of the gauges. A numeric return value is shown as-is. `true` / `false` is treated as success / failure. Any other number is only logged.

راقب لوحة العدادات نفسها. السطر على الشاشة يبيّن هل **اكتمل اتصال البايندر**، وليس صورة للعداد. قيمة الرقم تُعرض كما هي. `true` و `false` تُفسَّران نجاحاً أو فشلاً.

Re-read is outlined on purpose: it does not change the cluster. If the bind fails, that same button becomes **إعادة الاتصال / Retry connection**.

زر إعادة القراءة مرسوم بإطار لأنه لا يغيّر العداد. إذا فشل الاتصال يتحوّل الزر نفسه إلى إعادة الاتصال.

## Safety / الأمان

- Presets only: theme `1|2|3`, font `1|2`, day/night `0|1`.
- أوامر جاهزة فقط: الثيم `1|2|3`، الخط `1|2`، النهار/الليل `0|1`.
- Does **not** remount, flash, root, or replace `CiService` or any system APK.
- لا يعيد تركيب الأقسام، ولا يفليش، ولا يستبدل خدمة العداد أو أي تطبيق نظام.
- Does **not** expose free RGB, car-model color, ambient light, doors, windows, or reset.
- لا يوجد تحكم لوني حر، ولا لون هيكل، ولا إضاءة محيطية، ولا أبواب أو نوافذ أو إعادة ضبط.
- `setThemeMode` (1 STANDARD / 2 CROSS_COUNTRY / 3 NAVIGATION) is a **different** API and is never called.
- `setThemeMode` واجهة أخرى (عادي / طرق وعرة / ملاحة) ولا يُستدعى.
- Font 1 and 2 have no official words in Settings; the stock UI uses pictures. Day/night here is the cluster call, which may not be the center-screen theme.
- الخط 1 و 2 بلا أسماء تسويقية في الإعدادات؛ الواجهة الأصلية تعرض صوراً. وضع النهار/الليل هنا نداء العداد، وقد يختلف عن ثيم الشاشة الوسطى.
- Change the cluster while parked if the gauge change is distracting.
- غيّر الثيم والسيارة متوقفة إذا كان تبدّل العداد يشتت النظر.
- No extra permission is requested. The service is exported and has no `android:permission` in the analysis of this unit. That still has to be confirmed on the car: SELinux or the OEM can still deny the bind.
- لا يطلب التطبيق صلاحيات إضافية. الخدمة مصدّرة وبلا صلاحية في تحليل هذه الوحدة. قد يرفض النظام الربط رغم ذلك.
- Turn and hazard indicators are read-only. The app never calls `setIntProperty` or any write on `IALCar` or `IVehicleControl`.
- مؤشرات الانعطاف والخطر للقراءة فقط. التطبيق لا يكتب أي خاصية مركبة.

If the service is missing, dead, or the interface cannot be loaded, the banner is Arabic first and the buttons that would send a command stay disabled.

إذا كانت الخدمة مفقودة أو انقطع الاتصال أو تعذّر تحميل الواجهة، يظهر تنبيه بالعربية أولاً وتبقى أزرار الإرسال معطّلة.

## How the call is made / كيف يُرسل الأمر

1. Explicit intent: action `com.autolink.clusterinteraction.CiService`, class `com.autolink.clusterinteraction.CiService`. If that component is missing, the same action is tried with the package set.
2. Before every call, `binder.getInterfaceDescriptor()` must equal `com.autolink.adapterinterface.clusterinteraction.IClusterInteraction`. Otherwise nothing is sent.
3. The app writes a `Parcel` and calls `IBinder.transact` with the codes taken from this head unit’s CiService APK. It does not load the system stub.

قبل كل أمر يجب أن يطابق معرّف الواجهة النص أعلاه، وإلا لا يُرسل شيء. الأرقام التالية مأخوذة من APK الخدمة على الجهاز، وتُمرَّر كما هي إلى `transact` (ليست إزاحة فوق `FIRST_CALL_TRANSACTION`).

| Call | Transaction | Parcel |
| --- | --- | --- |
| `setThemeToCluster(int)` | **205** | `writeInterfaceToken`, `writeInt(theme)`, flags `0`, `reply.readException()` |
| `getClusterTheme()` | **206** | `writeInterfaceToken`, flags `0`, `readException`, `readInt` |
| `setClusterFont(int)` | **215** | same as set theme |
| `getClusterFont()` | **216** | same as get theme |
| `setDayNightMode(int)` | **74** | same as set theme |

There is no verified getter transaction for day/night, so re-read does not invent one. `setThemeMode` and `setCarModelColor` are never called. The on-screen log shows the descriptor and the result or exception of each call.

لا توجد معاملة قراءة مؤكدة لليل/النهار، لذلك إعادة القراءة لا تخترع رقماً. لا يُستدعى `setThemeMode` ولا `setCarModelColor`. السجل يعرض المعرّف ونتيجة كل أمر أو الاستثناء.

Preset values / القيم:

- Theme / الثيم: `1` رقمي DIGITAL, `2` كلاسيكي CLASSIC, `3` ملاحة NAVIGATION.
- Font / الخط: `1` or `2` only.
- Day/night / النهار والليل: `0` DAY, `1` NIGHT. `2` is not offered.
- `minSdk` / `targetSdk` 30. The manifest `<queries>` entry is required on API 30 so this app can see `com.autolink.clusterinteraction`.

## Build / البناء

Requirements: JDK 17+, Android SDK 34, build-tools 34.0.0.

```bash
export ANDROID_HOME="$PWD/.android-sdk"   # or your SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Outputs:

- `artifacts/clusterdiag-debug.apk` — the file to `adb install -r`
- `app/build/outputs/apk/debug/app-debug.apk` — same APK from Gradle

SHA-256 of the debug APK in this tree: `54afed6c6b9823968bebd46515266ac2d08c1a9f4720e5056e1b881925e5a582`

Preview of the custom cluster (1920×720): `artifacts/saudi-cluster-preview.png`.

Show it on the cluster display:

```bash
pm install -r -i com.android.packageinstaller clusterdiag-debug.apk
adb shell am start --display 1 -n com.ghosted907.clusterdiag/.SaudiClusterActivity
```

The debug APK is signed with the local debug keystore. Reinstall over itself with `adb install -r`.

## What was not run here / ما لم يُختبر هنا

Unit tests check the descriptor string and the five transaction codes. The first on-car run showed the bind succeeding and the descriptor `com.autolink.adapterinterface.clusterinteraction.IClusterInteraction`. This build sends those verified transactions directly. Confirm the gauge change on the cluster itself.
)
