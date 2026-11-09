export const APPS_JSON_URL =
  "https://raw.githubusercontent.com/Ghosted907/my-carscreen/dist/apps.json";

const OUR_PACKAGE = /^com\.ghosted907\.[A-Za-z0-9_]+$/;

export type DistApp = {
  id: string;
  name: string;
  packageName: string;
  versionCode: number | null;
  versionName: string | null;
  apk: string;
  sha256: string | null;
  size: number | null;
  external: boolean;
  postInstall: string[];
  notes: string;
};

export type InstalledPackage = {
  installed: boolean;
  versionCode: number | null;
  versionName: string | null;
  system: boolean;
};

export type VersionRelation = "missing" | "older" | "current" | "newer" | "system" | "unknown";

export function isHostedReleaseApk(url: string): boolean {
  try {
    const parsed = new URL(url);
    return (
      parsed.protocol === "https:" &&
      parsed.host === "raw.githubusercontent.com" &&
      parsed.pathname.startsWith("/Ghosted907/my-carscreen/dist/") &&
      parsed.pathname.endsWith(".apk")
    );
  } catch {
    return false;
  }
}

export function canInstallFromDist(app: DistApp): boolean {
  return (
    !app.external &&
    app.versionCode !== null &&
    typeof app.sha256 === "string" &&
    /^[0-9a-f]{64}$/i.test(app.sha256) &&
    OUR_PACKAGE.test(app.packageName) &&
    isHostedReleaseApk(app.apk)
  );
}

export function parsePackageDump(text: string): InstalledPackage {
  if (/Unable to find package/i.test(text) || !/versionCode=\d+/.test(text)) {
    return { installed: false, versionCode: null, versionName: null, system: false };
  }
  const code = text.match(/versionCode=(\d+)/);
  const name = text.match(/versionName=(\S+)/);
  const system = /(?:pkgFlags|flags)=\[[^\]]*\bSYSTEM\b/.test(text);
  return {
    installed: true,
    versionCode: code ? Number(code[1]) : null,
    versionName: name?.[1] ?? null,
    system,
  };
}

export function compareVersion(installed: InstalledPackage, remoteCode: number | null): VersionRelation {
  if (installed.system) return "system";
  if (!installed.installed) return "missing";
  if (remoteCode === null || installed.versionCode === null) return "unknown";
  if (installed.versionCode < remoteCode) return "older";
  if (installed.versionCode === remoteCode) return "current";
  return "newer";
}

export function isSignatureMismatch(message: string): boolean {
  return /INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match/i.test(message);
}

/** Only the release manifest's own-app steps. No vehicle writes and no system packages. */
export function isAllowedPostInstall(command: string, packageName: string): boolean {
  if (!OUR_PACKAGE.test(packageName)) return false;
  const allowed = [
    `appops set ${packageName} SYSTEM_ALERT_WINDOW allow`,
    `pm grant ${packageName} android.permission.READ_EXTERNAL_STORAGE`,
    `cmd notification allow_listener ${packageName}/${packageName}.T1NotificationListener`,
    "settings put global enable_freeform_support 1",
    "settings put global force_resizable_activities 1",
  ];
  return allowed.includes(command);
}

export function bytesToHex(bytes: Uint8Array): string {
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function sha256Hex(data: BufferSource): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", data);
  return bytesToHex(new Uint8Array(digest));
}

export async function fetchVerifiedApk(app: DistApp): Promise<Uint8Array> {
  if (!canInstallFromDist(app) || !app.sha256) {
    throw new Error("هذا التطبيق ما ينزّل من المستودع.");
  }
  const response = await fetch(app.apk);
  if (!response.ok) {
    throw new Error(`تعذّر تنزيل ${app.name} (${response.status}).`);
  }
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (app.size !== null && bytes.byteLength !== app.size) {
    throw new Error(`حجم ${app.name} ما يطابق apps.json. ما ثبّتنا الملف.`);
  }
  const digest = await sha256Hex(bytes);
  if (digest !== app.sha256.toLowerCase()) {
    throw new Error(`بصمة ${app.name} ما تطابق apps.json. ما ثبّتنا الملف.`);
  }
  return bytes;
}

export function normalizeDistApp(raw: unknown): DistApp | null {
  if (!raw || typeof raw !== "object") return null;
  const item = raw as Record<string, unknown>;
  if (typeof item.id !== "string" || typeof item.name !== "string") return null;
  const packageName = typeof item.package === "string" ? item.package : "";
  return {
    id: item.id,
    name: item.name,
    packageName,
    versionCode: typeof item.versionCode === "number" ? item.versionCode : null,
    versionName: typeof item.versionName === "string" ? item.versionName : null,
    apk: typeof item.apk === "string" ? item.apk : "",
    sha256: typeof item.sha256 === "string" ? item.sha256 : null,
    size: typeof item.size === "number" ? item.size : null,
    external: item.external === true,
    postInstall: Array.isArray(item.postInstall)
      ? item.postInstall.filter((step): step is string => typeof step === "string")
      : [],
    notes: typeof item.notes === "string" ? item.notes : "",
  };
}

export async function loadDistApps(): Promise<DistApp[]> {
  const response = await fetch(APPS_JSON_URL);
  if (!response.ok) {
    throw new Error(`تعذّر قراءة apps.json (${response.status}).`);
  }
  const data = (await response.json()) as { apps?: unknown };
  if (!Array.isArray(data.apps)) throw new Error("apps.json بلا قائمة تطبيقات.");
  return data.apps.flatMap((item) => {
    const app = normalizeDistApp(item);
    return app ? [app] : [];
  });
}
