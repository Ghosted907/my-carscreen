export type CatalogCategory = "video" | "audio" | "maps" | "files" | "tools" | "camera";

export type CatalogFile = {
  label: string;
  href: string;
};

export type CatalogApp = {
  id: string;
  name: string;
  category: CatalogCategory;
  summary: string;
  version: string;
  files: CatalogFile[];
  site?: CatalogFile;
};

export const catalogCategories: { id: CatalogCategory | "all"; label: string }[] = [
  { id: "all", label: "الكل" },
  { id: "video", label: "فيديو" },
  { id: "audio", label: "صوت" },
  { id: "maps", label: "خرائط" },
  { id: "files", label: "ملفات" },
  { id: "camera", label: "كاميرا" },
  { id: "tools", label: "أدوات" },
];

/** Official builds only. No store copies of YouTube or Netflix. */
export const catalog: CatalogApp[] = [
  {
    id: "youtube",
    name: "يوتيوب",
    category: "video",
    version: "بدون قوقل",
    summary:
      "ملف يوتيوب الرسمي ما نوزّعه. الزر ينزّل NewPipe أو SmartTube، يشغّلون يوتيوب بدون حساب قوقل. أو ثبّت Aurora Store ونزّل التطبيق الرسمي بحسابك.",
    files: [
      {
        label: "NewPipe",
        href: "https://github.com/TeamNewPipe/NewPipe/releases/download/v0.29.2/NewPipe_v0.29.2.apk",
      },
      {
        label: "SmartTube",
        href: "https://github.com/yuliskov/SmartTube/releases/download/32.71s/SmartTube_stable_32.71_universal.apk",
      },
    ],
  },
  {
    id: "netflix",
    name: "نتفلكس",
    category: "video",
    version: "ملفّك",
    summary:
      "ما نوزّع ملف نتفلكس. ثبّت Aurora Store ونزّله بحسابك، أو ارفع ملف APK عندك من صفحة التثبيت. الشاشة غير المعتمدة غالباً يرفض نتفلكس يشتغل عليها حتى بعد التثبيت.",
    files: [],
  },
  {
    id: "zero-player",
    name: "زيرو بلاي",
    category: "video",
    version: "موقعهم",
    summary:
      "مشغّل فيديو. ما يجي معه قنوات، والتجربة بعدها ترخيص مدفوع. ما فيه رابط ملف ثابت من عندهم، نزّله من موقعهم ثم ارفعه في التثبيت.",
    files: [],
    site: { label: "موقع زيرو بلاي", href: "https://0player.com/" },
  },
  {
    id: "open-camera",
    name: "Open Camera",
    category: "camera",
    version: "1.56.2",
    summary:
      "يشوف ويسجّل الكاميرا إذا الشاشة تعرضها كنظام أندرويد. كاميرا ٣٦٠ الوكالة تبقى من قائمة السيارة، ما تنزل من هنا.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://f-droid.org/repo/net.sourceforge.opencamera_96.apk",
      },
    ],
  },
  {
    id: "usb-camera",
    name: "كاميرا USB",
    category: "camera",
    version: "3.3.3",
    summary:
      "تفتح كاميرا خارجية أو كرت التقاط USB. ما تشغّل كاميرات الجيتور المدمجة. نسخة تجريبية من المطوّر.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://github.com/jiangdongguo/AndroidUSBCamera/raw/master/app/release/app-release.apk",
      },
    ],
  },
  {
    id: "newpipe",
    name: "NewPipe",
    category: "video",
    version: "0.29.2",
    summary: "يوتيوب وساوند كلاود بدون إعلانات وبدون حساب قوقل. يشتغل في الخلفية.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://github.com/TeamNewPipe/NewPipe/releases/download/v0.29.2/NewPipe_v0.29.2.apk",
      },
    ],
  },
  {
    id: "smarttube",
    name: "SmartTube",
    category: "video",
    version: "32.71",
    summary: "واجهة يوتيوب أكبر، أنسب لشاشة السيارة. النسخة الشاملة تغطي أكثر المعالجات.",
    files: [
      {
        label: "النسخة الشاملة",
        href: "https://github.com/yuliskov/SmartTube/releases/download/32.71s/SmartTube_stable_32.71_universal.apk",
      },
    ],
  },
  {
    id: "libretube",
    name: "LibreTube",
    category: "video",
    version: "32.1",
    summary: "واجهة ثانية ليوتيوب، أخف، وما تحتاج خدمات قوقل.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://github.com/libre-tube/LibreTube/releases/download/v32.1/app-release.apk",
      },
    ],
  },
  {
    id: "vlc",
    name: "VLC",
    category: "video",
    version: "3.7.1",
    summary: "تشغيل فيديو وملفات من الفلاشة. إذا التثبيت رفض النسخة الأولى، جرّب الثانية.",
    files: [
      {
        label: "64-بت",
        href: "https://get.videolan.org/vlc-android/3.7.1/VLC-Android-3.7.1-arm64-v8a.apk",
      },
      {
        label: "32-بت",
        href: "https://get.videolan.org/vlc-android/3.7.1/VLC-Android-3.7.1-armeabi-v7a.apk",
      },
    ],
  },
  {
    id: "organic-maps",
    name: "Organic Maps",
    category: "maps",
    version: "2026.09.29",
    summary: "خرائط بدون نت بعد ما تنزّل المنطقة. ما تحتاج حساب قوقل.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://github.com/organicmaps/organicmaps/releases/download/2026.09.29-32-android/OrganicMaps-26092932-web-release.apk",
      },
    ],
  },
  {
    id: "fossify-music",
    name: "Fossify Music",
    category: "audio",
    version: "1.8.5",
    summary: "مشغّل موسيقى للملفات اللي على الفلاشة أو التخزين.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://github.com/FossifyOrg/Music-Player/releases/download/1.8.5/musicplayer-18-foss-release.apk",
      },
    ],
  },
  {
    id: "fossify-files",
    name: "Fossify Files",
    category: "files",
    version: "1.6.3",
    summary: "مدير ملفات. منه تقدر تثبّت أي APK ثاني تحطه على فلاشة.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://github.com/FossifyOrg/File-Manager/releases/download/1.6.3/file-manager-15-foss-release.apk",
      },
    ],
  },
  {
    id: "fossify-launcher",
    name: "Fossify Launcher",
    category: "tools",
    version: "1.10.0",
    summary: "قائمة تطبيقات تظهر فيها البرامج اللي الشاشة الأصلية تخبّيها.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://github.com/FossifyOrg/Launcher/releases/download/1.10.0/launcher-16-foss-release.apk",
      },
    ],
  },
  {
    id: "aurora",
    name: "Aurora Store",
    category: "tools",
    version: "4.8.4",
    summary:
      "متجر مفتوح يسحب يوتيوب ونتفلكس من قوقل بلاي بحسابك. ما نوزّع ملفاتهم من هنا. نتفلكس قد يرفض الجهاز غير المعتمد.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://apt.izzysoft.de/fdroid/repo/com.aurora.store_76.apk",
      },
    ],
  },
  {
    id: "fdroid",
    name: "F-Droid",
    category: "tools",
    version: "رسمي",
    summary: "متجر تطبيقات مفتوحة. بعد ما يثبّت على الشاشة تقدر تجيب برامج زيادة والنت شغال.",
    files: [
      {
        label: "تنزيل APK",
        href: "https://f-droid.org/F-Droid.apk",
      },
    ],
  },
];
