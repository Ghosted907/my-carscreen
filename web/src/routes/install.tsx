import { createFileRoute, Link } from "@tanstack/react-router";
import { PlugZap, Trash2, Unplug, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { CopyCode } from "@/components/copy-code";
import { SiteFrame } from "@/components/site-frame";
import { TodayStamp } from "@/components/today-stamp";
import { Button } from "@/components/ui/button";
import {
  connectHeadunit,
  disconnectHeadunit,
  explainUsbError,
  installApk,
  launchPackage,
  listThirdParty,
  uninstallPackage,
  type HeadunitInfo,
} from "@/lib/adb-session";
import { cars } from "@/lib/cars";
import { cn } from "@/lib/cn";

export const Route = createFileRoute("/install")({
  validateSearch: (search: Record<string, unknown>) => ({
    car: typeof search.car === "string" ? search.car : "jetour-t1",
  }),
  component: InstallScreen,
});

type LogLevel = "info" | "ok" | "err";
type LogLine = { id: number; level: LogLevel; text: string };
type Job = {
  id: string;
  name: string;
  size: number;
  status: "wait" | "run" | "ok" | "err";
  detail?: string;
};

let logSeq = 0;

function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function InstallScreen() {
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  const car =
    cars.find((item) => item.slug === search.car) ??
    cars.find((item) => item.slug === "other")!;
  const usbOk = typeof navigator !== "undefined" && "usb" in navigator;

  const [info, setInfo] = useState<HeadunitInfo | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [logs, setLogs] = useState<LogLine[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [packages, setPackages] = useState<string[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function pushLog(level: LogLevel, text: string) {
    logSeq += 1;
    setLogs((prev) => [...prev.slice(-80), { id: logSeq, level, text }]);
  }

  function addFiles(list: FileList | File[]) {
    const next = Array.from(list);
    const apk = next.filter((file) => file.name.toLowerCase().endsWith(".apk"));
    const skipped = next.length - apk.length;
    if (skipped > 0) {
      pushLog("err", "تجاهلنا ملفات ليست APK. الصيغ xapk و apks غير مدعومة.");
    }
    if (apk.length === 0) return;
    setFiles((prev) => {
      const names = new Set(prev.map((file) => `${file.name}:${file.size}`));
      return [
        ...prev,
        ...apk.filter((file) => !names.has(`${file.name}:${file.size}`)),
      ];
    });
  }

  async function connect() {
    setBusy("connect");
    pushLog("info", "اختر الشاشة من نافذة المتصفح، ثم وافق على رسالة السماح على شاشة السيارة.");
    try {
      const next = await connectHeadunit();
      setInfo(next);
      pushLog("ok", `اتصلنا بـ ${next.model} · أندرويد ${next.android}`);
      const pkgs = await listThirdParty();
      setPackages(pkgs);
      pushLog("info", pkgs.length ? `${pkgs.length} تطبيق طرف ثالث.` : "ما فيه تطبيقات طرف ثالث بعد.");
    } catch (err) {
      pushLog("err", explainUsbError(err));
      setInfo(null);
    } finally {
      setBusy(null);
    }
  }

  async function disconnect() {
    setBusy("disconnect");
    try {
      await disconnectHeadunit();
      setInfo(null);
      setPackages([]);
      pushLog("info", "انفصل الاتصال.");
    } catch (err) {
      pushLog("err", explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  async function installAll() {
    if (!info || files.length === 0) return;
    setBusy("install");
    const queue = files.map((file) => ({
      id: `${file.name}:${file.size}:${file.lastModified}`,
      name: file.name,
      size: file.size,
      status: "wait" as const,
    }));
    setJobs(queue);
    for (const file of files) {
      const id = `${file.name}:${file.size}:${file.lastModified}`;
      setJobs((prev) =>
        prev.map((job) => (job.id === id ? { ...job, status: "run" } : job)),
      );
      pushLog("info", `تثبيت ${file.name}`);
      try {
        await installApk(file);
        setJobs((prev) =>
          prev.map((job) => (job.id === id ? { ...job, status: "ok" } : job)),
        );
        pushLog("ok", `تم ${file.name}`);
      } catch (err) {
        const detail = explainUsbError(err);
        setJobs((prev) =>
          prev.map((job) =>
            job.id === id ? { ...job, status: "err", detail } : job,
          ),
        );
        pushLog("err", `${file.name}: ${detail}`);
      }
    }
    try {
      setPackages(await listThirdParty());
    } catch (err) {
      pushLog("err", explainUsbError(err));
    }
    setFiles([]);
    setBusy(null);
  }

  async function refreshPackages() {
    setBusy("list");
    try {
      const pkgs = await listThirdParty();
      setPackages(pkgs);
      pushLog("info", `القائمة محدّثة · ${pkgs.length}`);
    } catch (err) {
      pushLog("err", explainUsbError(err));
      setInfo(null);
    } finally {
      setBusy(null);
    }
  }

  async function onLaunch(pkg: string) {
    setBusy(pkg);
    try {
      await launchPackage(pkg);
      pushLog("ok", `أُرسل أمر التشغيل إلى ${pkg}`);
    } catch (err) {
      pushLog("err", explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  async function onUninstall(pkg: string) {
    if (!window.confirm(`حذف ${pkg} من الشاشة؟`)) return;
    setBusy(pkg);
    try {
      await uninstallPackage(pkg);
      setPackages((prev) => prev.filter((item) => item !== pkg));
      pushLog("ok", `انحذف ${pkg}`);
    } catch (err) {
      pushLog("err", explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <SiteFrame>
      <div className="mx-auto grid max-w-6xl gap-6 px-4 py-8 md:px-6 lg:grid-cols-12">
        <section className="lg:col-span-5">
          <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
            BENCH
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">طاولة التثبيت</h1>
          <p className="mt-2 text-sm text-muted">
            فعّل ADB أولاً، ثم وصّل الكابل. الملفات ما تطلع من جهازك.
          </p>

          <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
            {cars.map((item) => (
              <button
                key={item.slug}
                type="button"
                onClick={() => void navigate({ search: { car: item.slug } })}
                className={cn(
                  "shrink-0 rounded-md border px-3 py-2 text-sm",
                  car.slug === item.slug
                    ? "border-copper bg-surface text-fg"
                    : "border-line text-muted",
                )}
              >
                {item.name}
              </button>
            ))}
          </div>

          <ol className="mt-5 space-y-4">
            {car.steps.map((step, index) => (
              <li key={step.title} className="flex gap-3">
                <span className="font-mono text-sm text-copper" dir="ltr">
                  {String(index + 1).padStart(2, "0")}
                </span>
                <div>
                  <h2 className="text-sm font-medium">{step.title}</h2>
                  <p className="mt-1 text-sm text-muted">{step.body}</p>
                </div>
              </li>
            ))}
          </ol>

          <div className="mt-5 space-y-2">
            {car.codes.map((code) => (
              <CopyCode key={code.value} label={code.label} value={code.value} />
            ))}
            {car.datePassword ? (
              <div className="rounded-md border border-line bg-surface px-3 py-2">
                <p className="text-xs text-faint">كلمة مرور اليوم</p>
                <p className="text-lg">
                  <TodayStamp />
                </p>
              </div>
            ) : null}
            <p className="text-sm text-muted">{car.usb}</p>
            <Link
              to="/cars/$slug"
              params={{ slug: car.slug }}
              className="inline-flex min-h-11 items-center text-sm text-copper"
            >
              شرح الموديل كامل
            </Link>
          </div>
        </section>

        <section className="space-y-4 lg:col-span-7">
          <div className="rounded-xl border border-line bg-surface p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="flex items-center gap-2 text-sm">
                  <span
                    className={cn(
                      "size-2 rounded-full",
                      info ? "bg-ok" : "bg-faint",
                    )}
                  />
                  {info ? "متصل" : "غير متصل"}
                </p>
                {info ? (
                  <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                    <dt className="text-faint">الموديل</dt>
                    <dd className="font-mono" dir="ltr">
                      {info.model}
                    </dd>
                    <dt className="text-faint">أندرويد</dt>
                    <dd className="font-mono" dir="ltr">
                      {info.android}
                    </dd>
                    <dt className="text-faint">البناء</dt>
                    <dd className="truncate font-mono" dir="ltr">
                      {info.build}
                    </dd>
                    <dt className="text-faint">التسلسل</dt>
                    <dd className="truncate font-mono" dir="ltr">
                      {info.serial}
                    </dd>
                  </dl>
                ) : (
                  <p className="mt-2 max-w-md text-sm text-muted">
                    {usbOk
                      ? "اضغط اتصال. إذا ما ظهر الجهاز، ADB مقفّل أو الكابل ما ينقل بيانات."
                      : "المعاينة هذي ما تفتح منفذ USB. افتح الصفحة في Chrome أو Edge على الكمبيوتر."}
                  </p>
                )}
              </div>
              <div className="flex gap-2">
                {info ? (
                  <Button
                    variant="line"
                    onClick={() => void disconnect()}
                    disabled={busy !== null}
                  >
                    <Unplug className="size-4" />
                    افصل
                  </Button>
                ) : (
                  <Button
                    onClick={() => void connect()}
                    disabled={busy !== null || !usbOk}
                  >
                    <PlugZap className="size-4" />
                    {busy === "connect" ? "بانتظار الموافقة…" : "وصّل الشاشة"}
                  </Button>
                )}
              </div>
            </div>
          </div>

          <div
            className={cn(
              "rounded-xl border border-dashed bg-surface p-4",
              dragOver ? "border-copper" : "border-line",
            )}
            onDragOver={(event) => {
              event.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragOver(false);
              addFiles(event.dataTransfer.files);
            }}
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="font-medium">ملفات APK</h2>
                <p className="text-sm text-muted">اسحب الملفات أو اخترها. ما نرفعها لأي سيرفر.</p>
                <Link to="/apps" className="mt-1 inline-flex text-sm text-copper">
                  ما عندك الملفات؟ نزّلها من قائمة التطبيقات
                </Link>
              </div>
              <Button variant="line" onClick={() => inputRef.current?.click()}>
                <Upload className="size-4" />
                اختيار
              </Button>
              <input
                ref={inputRef}
                type="file"
                accept=".apk,application/vnd.android.package-archive"
                multiple
                className="sr-only"
                onChange={(event) => {
                  if (event.target.files) addFiles(event.target.files);
                  event.target.value = "";
                }}
              />
            </div>
            {files.length > 0 ? (
              <ul className="mt-3 divide-y divide-line">
                {files.map((file) => (
                  <li
                    key={`${file.name}:${file.size}:${file.lastModified}`}
                    className="flex items-center justify-between gap-3 py-2 text-sm"
                  >
                    <span className="min-w-0 truncate" dir="ltr">
                      {file.name}
                    </span>
                    <span className="shrink-0 font-mono text-faint" dir="ltr">
                      {formatSize(file.size)}
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}
            {jobs.length > 0 ? (
              <ul className="mt-2 space-y-1 text-sm">
                {jobs.map((job) => (
                  <li key={job.id} className="flex items-center justify-between gap-3">
                    <span className="min-w-0 truncate" dir="ltr">
                      {job.name}
                    </span>
                    <span
                      className={cn(
                        "font-mono text-xs",
                        job.status === "ok" && "text-ok",
                        job.status === "err" && "text-danger",
                        job.status === "run" && "text-copper",
                        job.status === "wait" && "text-faint",
                      )}
                    >
                      {job.status === "ok"
                        ? "تم"
                        : job.status === "err"
                          ? "فشل"
                          : job.status === "run"
                            ? "يثبّت"
                            : "ينتظر"}
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}
            <div className="mt-3">
              <Button
                onClick={() => void installAll()}
                disabled={!info || files.length === 0 || busy !== null}
              >
                {busy === "install" ? "جاري التثبيت…" : "ثبّت على الشاشة"}
              </Button>
            </div>
          </div>

          <div className="rounded-xl border border-line bg-surface p-4">
            <div className="flex items-center justify-between gap-3">
              <h2 className="font-medium">تطبيقات الطرف الثالث</h2>
              <Link to="/deck" className="text-sm text-copper">
                القائمة من الجنب
              </Link>
              <Button
                variant="quiet"
                onClick={() => void refreshPackages()}
                disabled={!info || busy !== null}
              >
                تحديث
              </Button>
            </div>
            {packages.length === 0 ? (
              <p className="mt-3 text-sm text-muted">
                {info
                  ? "ما ظهر شيء بعد. ثبّت ملف APK ثم حدّث."
                  : "تتبين بعد الاتصال."}
              </p>
            ) : (
              <ul className="mt-3 max-h-64 divide-y divide-line overflow-auto">
                {packages.map((pkg) => (
                  <li key={pkg} className="flex items-center gap-2 py-2">
                    <span className="min-w-0 flex-1 truncate font-mono text-xs" dir="ltr">
                      {pkg}
                    </span>
                    <Button
                      variant="quiet"
                      onClick={() => void onLaunch(pkg)}
                      disabled={busy !== null}
                    >
                      تشغيل
                    </Button>
                    <button
                      type="button"
                      className="grid size-11 place-items-center rounded-md text-muted hover:text-danger"
                      aria-label={`حذف ${pkg}`}
                      disabled={busy !== null}
                      onClick={() => void onUninstall(pkg)}
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="rounded-xl border border-line bg-ink p-4">
            <p className="font-mono text-xs text-faint" dir="ltr">
              session
            </p>
            <ul className="mt-2 max-h-48 space-y-1 overflow-auto font-mono text-xs" dir="ltr">
              {logs.length === 0 ? (
                <li className="text-faint">waiting</li>
              ) : (
                logs.map((line) => (
                  <li
                    key={line.id}
                    className={cn(
                      line.level === "ok" && "text-ok",
                      line.level === "err" && "text-danger",
                      line.level === "info" && "text-muted",
                    )}
                  >
                    {line.text}
                  </li>
                ))
              )}
            </ul>
          </div>
        </section>
      </div>
    </SiteFrame>
  );
}
