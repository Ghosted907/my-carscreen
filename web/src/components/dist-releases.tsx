import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  adbConnected,
  dumpPackage,
  subscribeAdb,
  explainUsbError,
  installApk,
  runAllowlistedCommands,
  uninstallPackage,
} from "@/lib/adb-session";
import {
  canInstallFromDist,
  compareVersion,
  fetchVerifiedApk,
  isAllowedPostInstall,
  isSignatureMismatch,
  loadDistApps,
  parsePackageDump,
  type DistApp,
  type InstalledPackage,
  type VersionRelation,
} from "@/lib/dist-apps";

const emptyInstall: InstalledPackage = {
  installed: false,
  versionCode: null,
  versionName: null,
  system: false,
};

function relationLabel(relation: VersionRelation | "offline"): string {
  switch (relation) {
    case "missing":
      return "غير مثبّت";
    case "older":
      return "يحتاج تحديث";
    case "current":
      return "مثبّت بنفس الإصدار";
    case "newer":
      return "المثبت أحدث من المستودع";
    case "system":
      return "تطبيق نظام — لا نلمسه";
    case "unknown":
      return "ما انقرأ رقم الإصدار";
    default:
      return "وصّل الشاشة للمقارنة";
  }
}

export function DistReleases({
  onLog,
}: {
  onLog?: (level: "info" | "ok" | "err", text: string) => void;
}) {
  const [apps, setApps] = useState<DistApp[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [installed, setInstalled] = useState<Record<string, InstalledPackage>>({});
  const [connected, setConnected] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  function log(level: "info" | "ok" | "err", text: string) {
    onLog?.(level, text);
  }

  useEffect(() => {
    let cancelled = false;
    loadDistApps()
      .then((next) => {
        if (!cancelled) setApps(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(explainUsbError(err));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function refreshInstalled(list: DistApp[]) {
    if (!adbConnected()) {
      setConnected(false);
      setInstalled({});
      return;
    }
    setConnected(true);
    const next: Record<string, InstalledPackage> = {};
    for (const app of list) {
      if (!app.packageName || app.external) continue;
      try {
        next[app.packageName] = parsePackageDump(await dumpPackage(app.packageName));
      } catch (err) {
        log("err", explainUsbError(err));
      }
    }
    setInstalled(next);
  }

  useEffect(() => {
    if (!apps) return;
    void refreshInstalled(apps);
    return subscribeAdb(() => {
      void refreshInstalled(apps);
    });
    // refreshInstalled closes over the latest apps list from this effect.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apps]);

  async function installRelease(app: DistApp, replaceDebug: boolean) {
    if (!canInstallFromDist(app)) return;
    const current = installed[app.packageName] ?? emptyInstall;
    if (current.system) {
      log("err", `${app.name} تطبيق نظام. ما نثبّته ولا نحذفه.`);
      return;
    }
    setBusy(app.id);
    try {
      log("info", `تنزيل ${app.name} والتحقق من البصمة`);
      const bytes = await fetchVerifiedApk(app);
      const copy = new ArrayBuffer(bytes.byteLength);
      new Uint8Array(copy).set(bytes);
      const file = new File([copy], `${app.id}.apk`, {
        type: "application/vnd.android.package-archive",
      });
      try {
        await installApk(file);
      } catch (err) {
        const message = explainUsbError(err);
        if (!replaceDebug && isSignatureMismatch(message) && current.installed && !current.system) {
          const confirmed = window.confirm(
            `${app.name} المثبت موقّع بمفتاح مختلف، وغالباً هذه نسخة debug. نسخة الإصدار ما تركب فوقها إلا بعد الحذف. تحذف النسخة الحالية الآن ثم تثبّت نسخة الإصدار؟`,
          );
          if (!confirmed) {
            log("info", "توقفنا. ما انحذف شيء.");
            return;
          }
          const again = parsePackageDump(await dumpPackage(app.packageName));
          if (again.system) {
            log("err", "الحزمة صارت تطبيق نظام. ما نحذفها.");
            return;
          }
          await uninstallPackage(app.packageName);
          log("ok", `انحذفت النسخة السابقة من ${app.name}`);
          await installApk(file);
        } else {
          throw err;
        }
      }
      const skipped = await runAllowlistedCommands(app.postInstall, (command) =>
        isAllowedPostInstall(command, app.packageName),
      );
      for (const command of skipped) {
        log("err", `تجاهلنا أمر ما بعد التثبيت لأنه خارج المسموح: ${command}`);
      }
      log("ok", `تم تثبيت ${app.name} ${app.versionName ?? ""}`.trim());
      if (apps) await refreshInstalled(apps);
    } catch (err) {
      log("err", explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="rounded-xl border border-line bg-surface p-4">
      <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
        apps.json
      </p>
      <h2 className="mt-2 text-xl font-semibold">إصدارات المستودع</h2>
      <p className="mt-2 text-sm text-muted">
        التنزيل من فرع dist، وبعدها نطابق بصمة SHA-256 قبل التثبيت. إذا الملف ما
        يطابق apps.json نرفضه. الانتقال من نسخة debug يحتاج حذفاً لأن التوقيع
        مختلف، والحذف ما يصير إلا بعد ما توافق صراحة.
      </p>
      {error ? <p className="mt-3 text-sm text-danger">{error}</p> : null}
      {!apps ? (
        <p className="mt-3 text-sm text-muted">نقرأ apps.json…</p>
      ) : (
        <ul className="mt-4 space-y-3">
          {apps.map((app) => {
            const installable = canInstallFromDist(app);
            const known = installed[app.packageName];
            const relation: VersionRelation | "offline" = !connected
              ? "offline"
              : known
                ? compareVersion(known, app.versionCode)
                : "unknown";
            const action =
              relation === "missing" ? "ثبّت" : relation === "older" ? "حدّث" : null;
            return (
              <li key={app.id} className="rounded-lg border border-line bg-ink px-3 py-3">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h3 className="text-lg font-semibold">{app.name}</h3>
                  <span className="font-mono text-xs text-faint" dir="ltr">
                    {app.external
                      ? "مصدر رسمي"
                      : `${app.versionName ?? "—"} · ${app.versionCode ?? "—"}`}
                  </span>
                </div>
                {app.packageName ? (
                  <p className="mt-1 font-mono text-xs text-faint" dir="ltr">
                    {app.packageName}
                    {connected && known?.installed && known.versionCode !== null
                      ? ` · installed ${known.versionName ?? "?"} (${known.versionCode})`
                      : ""}
                  </p>
                ) : null}
                <p className="mt-2 text-sm text-muted">{relationLabel(relation)}</p>
                {app.notes ? <p className="mt-1 text-sm text-muted">{app.notes}</p> : null}
                <div className="mt-3 flex flex-wrap gap-2">
                  {installable && action ? (
                    <Button
                      onClick={() => void installRelease(app, false)}
                      disabled={!connected || busy !== null || relation === "system"}
                    >
                      {busy === app.id ? "جارٍ…" : action}
                    </Button>
                  ) : null}
                  {!installable && app.apk ? (
                    <a
                      href={app.apk}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex min-h-11 items-center rounded-md border border-line px-4 text-sm text-copper"
                    >
                      الموقع الرسمي
                    </a>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <p className="mt-3 text-xs text-faint">
        مقارنة الإصدار تتم بـ dumpsys package بعد الاتصال. ما نكتب خصائص المركبة،
        وما نعدّل تطبيقات النظام.
      </p>
    </section>
  );
}
