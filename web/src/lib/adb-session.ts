import type { Adb } from "@yume-chan/adb";
import { ReadableStream } from "@yume-chan/stream-extra";

let current: Adb | null = null;
const connectionListeners = new Set<() => void>();

function notifyConnection(): void {
  for (const listener of connectionListeners) listener();
}

export function subscribeAdb(listener: () => void): () => void {
  connectionListeners.add(listener);
  return () => {
    connectionListeners.delete(listener);
  };
}

export type HeadunitInfo = {
  serial: string;
  model: string;
  android: string;
  build: string;
};

const PKG = /^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/;

export function explainUsbError(err: unknown): string {
  if (err instanceof DOMException) {
    if (err.name === "NotFoundError") return "تم إلغاء اختيار الجهاز.";
    if (err.name === "SecurityError") {
      return "المتصفح منع منفذ USB. افتح الصفحة في Chrome أو Edge مباشرة على اتصال آمن، مو من داخل تطبيق آخر.";
    }
    if (err.name === "NetworkError" || err.name === "AbortError") {
      return "انقطع الكابل أو المنفذ مشغول. فك الكابل، أعد تشغيل الشاشة، وجرّب مرة ثانية.";
    }
  }
  if (err instanceof Error) {
    if (/busy/i.test(err.message)) {
      return "المنفذ مشغول ببرنامج ADB ثاني على الكمبيوتر. سكّره ثم أعد الاتصال.";
    }
    return err.message || "صار خطأ أثناء الاتصال.";
  }
  return "صار خطأ غير متوقع.";
}

export async function connectHeadunit(): Promise<HeadunitInfo> {
  const { Adb, AdbDaemonTransport } = await import("@yume-chan/adb");
  const { AdbDaemonWebUsbDeviceManager } = await import(
    "@yume-chan/adb-daemon-webusb"
  );
  const credentialMod = await import("@yume-chan/adb-credential-web");
  const CredentialStore = credentialMod.default;

  const manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  if (!manager) {
    throw new Error(
      "هذا المتصفح ما يدعم WebUSB. استخدم Chrome أو Edge على كمبيوتر، أو Chrome على أندرويد مع محول OTG.",
    );
  }

  const device = await manager.requestDevice();
  if (!device) throw new Error("ما تم اختيار جهاز.");

  const connection = await device.connect();
  const transport = await AdbDaemonTransport.authenticate({
    serial: device.serial,
    connection,
    credentialStore: new CredentialStore("Afaq"),
  });
  const adb = new Adb(transport);
  current = adb;
  notifyConnection();
  void adb.disconnected.then(() => {
    if (current === adb) {
      current = null;
      notifyConnection();
    }
  });

  const [model, android, build] = await Promise.all([
    adb.getProp("ro.product.model"),
    adb.getProp("ro.build.version.release"),
    adb.getProp("ro.build.display.id"),
  ]);

  return {
    serial: device.serial || device.name || "—",
    model: model || device.name || "غير معروف",
    android: android || "—",
    build: build || "—",
  };
}

export async function disconnectHeadunit(): Promise<void> {
  const adb = current;
  current = null;
  notifyConnection();
  if (adb) await adb.close();
}

export function requireAdb(): Adb {
  if (!current) throw new Error("الشاشة مو متصلة.");
  return current;
}

export function adbConnected(): boolean {
  return current !== null;
}

export async function dumpPackage(pkg: string): Promise<string> {
  if (!PKG.test(pkg)) throw new Error("اسم الحزمة غير صالح.");
  const adb = requireAdb();
  return adb.subprocess.noneProtocol.spawnWaitText(`dumpsys package ${pkg}`);
}

export async function runAllowlistedCommands(
  commands: string[],
  allow: (command: string) => boolean,
): Promise<string[]> {
  const adb = requireAdb();
  const skipped: string[] = [];
  for (const command of commands) {
    if (!allow(command)) {
      skipped.push(command);
      continue;
    }
    await adb.subprocess.noneProtocol.spawnWaitText(command);
  }
  return skipped;
}

export async function installApk(file: File): Promise<void> {
  const adb = requireAdb();
  const { PackageManager } = await import("@yume-chan/android-bin");
  const pm = new PackageManager(adb);
  const reader = file.stream().getReader();
  const stream = new ReadableStream<Uint8Array>({
    async pull(controller) {
      const { done, value } = await reader.read();
      if (done) {
        controller.close();
        return;
      }
      if (value) controller.enqueue(value);
    },
    cancel(reason) {
      return reader.cancel(reason);
    },
  });
  await pm.installStream(file.size, stream, {
    grantRuntimePermissions: true,
    allowTest: true,
    user: 0,
  });
}

export async function listThirdParty(): Promise<string[]> {
  const adb = requireAdb();
  const text = await adb.subprocess.noneProtocol.spawnWaitText(
    "pm list packages -3",
  );
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.startsWith("package:"))
    .map((line) => line.slice("package:".length))
    .filter((name) => PKG.test(name))
    .sort((a, b) => a.localeCompare(b));
}

export async function launchPackage(pkg: string): Promise<void> {
  if (!PKG.test(pkg)) throw new Error("اسم الحزمة غير صالح.");
  const adb = requireAdb();
  await adb.subprocess.noneProtocol.spawnWaitText(
    `monkey -p ${pkg} -c android.intent.category.LAUNCHER 1`,
  );
}

const THEME_KEYS = [
  ["system", "cluster_theme"],
  ["system", "cluster_theme_mode"],
  ["system", "instrument_theme"],
  ["system", "meter_theme"],
  ["global", "instrument_theme"],
] as const;

export type ClusterKey = { ns: "system" | "global"; key: string; value: string };
export type ClusterProbe = { keys: ClusterKey[]; displays: number[] };

async function sh(cmd: string): Promise<string> {
  const adb = requireAdb();
  return (await adb.subprocess.noneProtocol.spawnWaitText(cmd)).trim();
}

export async function probeCluster(): Promise<ClusterProbe> {
  const keys: ClusterKey[] = [];
  for (const [ns, key] of THEME_KEYS) {
    const value = await sh(`settings get ${ns} ${key}`);
    if (value && value !== "null") keys.push({ ns, key, value });
  }
  let displays: number[] = [];
  try {
    const dump = await sh("dumpsys display");
    const ids = new Set<number>();
    for (const match of dump.matchAll(/mDisplayId=(\d+)/g)) {
      const id = Number(match[1]);
      if (id >= 0 && id < 8) ids.add(id);
    }
    displays = [...ids].sort((a, b) => a - b);
  } catch {
    displays = [];
  }
  return { keys, displays };
}

export async function writeClusterTheme(
  ns: "system" | "global",
  key: string,
  value: string,
): Promise<string> {
  const allowed = THEME_KEYS.some((item) => item[0] === ns && item[1] === key);
  if (!allowed || !/^-?\d{1,2}$/.test(value)) {
    throw new Error("قيمة الثيم غير مسموحة.");
  }
  await sh(`settings put ${ns} ${key} ${value}`);
  const now = await sh(`settings get ${ns} ${key}`);
  if (now !== value) {
    throw new Error(`الشاشة رفضت الكتابة. القيمة بقيت ${now || "فاضية"}.`);
  }
  return now;
}

export async function restoreClusterDisplay(displayId: number): Promise<string> {
  if (!Number.isInteger(displayId) || displayId < 1 || displayId > 7) {
    throw new Error("ما لقينا شاشة عدادات منفصلة عن شاشة الوسط.");
  }
  return sh(
    `am start --display ${displayId} -a android.intent.action.MAIN -c android.intent.category.HOME`,
  );
}

export async function pressInstallButton(): Promise<void> {
  const adb = requireAdb();
  // Jetour/Chery guides: the system Install control often ignores touch.
  // DPAD_RIGHT twice, then ENTER, while that dialog is on screen.
  for (const cmd of ["input keyevent 22", "input keyevent 22", "input keyevent 66"]) {
    await adb.subprocess.noneProtocol.spawnWaitText(cmd);
  }
}

export async function uninstallPackage(pkg: string): Promise<void> {
  if (!PKG.test(pkg)) throw new Error("اسم الحزمة غير صالح.");
  const adb = requireAdb();
  const out = (
    await adb.subprocess.noneProtocol.spawnWaitText(
      `pm uninstall --user 0 ${pkg}`,
    )
  ).trim();
  if (!/success/i.test(out)) {
    throw new Error(out || "ما انحذف التطبيق.");
  }
}
