import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { SiteFrame } from "@/components/site-frame";
import { Button } from "@/components/ui/button";
import {
  explainUsbError,
  launchPackage,
  listThirdParty,
  pressInstallButton,
} from "@/lib/adb-session";
import { cn } from "@/lib/cn";

export const Route = createFileRoute("/deck")({ component: DeckPage });

function shortName(pkg: string): string {
  const tail = pkg.split(".").pop() ?? pkg;
  return tail.length > 18 ? `${tail.slice(0, 16)}…` : tail;
}

function DeckPage() {
  const [packages, setPackages] = useState<string[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [note, setNote] = useState("وصّل الشاشة من صفحة التثبيت، ثم ارجع هنا.");
  const [side, setSide] = useState(true);

  async function refresh() {
    setBusy("list");
    try {
      const pkgs = await listThirdParty();
      setPackages(pkgs);
      setLoaded(true);
      setNote(pkgs.length ? `${pkgs.length} تطبيق على الشاشة.` : "ما فيه تطبيقات طرف ثالث بعد.");
    } catch (err) {
      setNote(explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  async function launch(pkg: string) {
    setBusy(pkg);
    try {
      await launchPackage(pkg);
      setNote(`انرسل أمر التشغيل إلى ${pkg}`);
    } catch (err) {
      setNote(explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  async function pressInstall() {
    setBusy("press");
    try {
      await pressInstallButton();
      setNote("انرسل يمين ثم يمين ثم موافق. إذا النافذة ظاهرة، المفروض زر التثبيت انضغط.");
    } catch (err) {
      setNote(explainUsbError(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <SiteFrame>
      <div className="mx-auto grid max-w-6xl gap-4 px-4 py-8 md:px-6 lg:grid-cols-12">
        <section className="space-y-4 lg:col-span-7">
          <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
            ON SCREEN
          </p>
          <h1 className="text-3xl font-semibold tracking-tight">قائمة الشاشة</h1>
          <p className="text-sm text-muted">
            نفس فكرة برنامج الشاشة: إعدادات، والتطبيقات المثبتة، وتشغيلها. القائمة
            الجانبية هي «من الجنب».
          </p>

          <div className="rounded-xl border border-line bg-surface p-4">
            <h2 className="font-medium">الإعدادات</h2>
            <p className="mt-2 text-sm text-muted">
              زر «بدء التثبيت» على شاشة جيتور وشيري غالباً يطلع وما ينضغط بالإصبع.
              هذا مو خلل في رابط التحميل. أثناء ما النافذة ظاهرة على السيارة، اضغط
              الزر تحت. نرسل ضغطتين يمين ثم موافق، نفس الحركة اللي يستخدمها أصحاب
              الشاشة من الكابل.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button onClick={() => void pressInstall()} disabled={busy !== null}>
                {busy === "press" ? "جاري الضغط…" : "اضغط زر التثبيت الآن"}
              </Button>
              <Button variant="line" onClick={() => void refresh()} disabled={busy !== null}>
                {busy === "list" ? "جاري الجلب…" : "حدّث التطبيقات"}
              </Button>
            </div>
            <p className="mt-3 text-sm text-muted">{note}</p>
          </div>

          <div className="rounded-xl border border-line bg-surface p-4">
            <div className="flex items-center justify-between gap-3">
              <h2 className="font-medium">التطبيقات المحمّلة</h2>
              <button
                type="button"
                className="text-sm text-copper lg:hidden"
                onClick={() => setSide((v) => !v)}
              >
                {side ? "أخفِ الجنب" : "من الجنب"}
              </button>
            </div>
            {!loaded ? (
              <p className="mt-3 text-sm text-muted">
                لسا ما انجلبت القائمة.{" "}
                <Link to="/install" search={{ car: "jetour-t1" }} className="text-copper">
                  وصّل الشاشة
                </Link>
              </p>
            ) : packages.length === 0 ? (
              <p className="mt-3 text-sm text-muted">ما فيه تطبيقات طرف ثالث.</p>
            ) : (
              <ul className="mt-3 grid gap-2 sm:grid-cols-2">
                {packages.map((pkg) => (
                  <li key={pkg} className="rounded-lg border border-line p-3">
                    <p className="truncate text-sm font-medium">{shortName(pkg)}</p>
                    <p className="truncate font-mono text-xs text-faint" dir="ltr">
                      {pkg}
                    </p>
                    <Button
                      className="mt-2"
                      variant="line"
                      disabled={busy !== null}
                      onClick={() => void launch(pkg)}
                    >
                      {busy === pkg ? "…" : "تشغيل"}
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <aside
          className={cn(
            "lg:col-span-5",
            side ? "block" : "hidden lg:block",
          )}
        >
          <div className="rounded-xl border border-copper/50 bg-surface p-4 lg:sticky lg:top-4">
            <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
              SIDE
            </p>
            <h2 className="mt-1 text-lg font-semibold">من الجنب</h2>
            <p className="mt-1 text-sm text-muted">
              اسحبها كقائمة سريعة. الضغط يشغّل التطبيق على شاشة السيارة.
            </p>
            {packages.length === 0 ? (
              <p className="mt-4 text-sm text-muted">القائمة فاضية لين تتصل وتحدّث.</p>
            ) : (
              <ul className="mt-4 space-y-1">
                {packages.map((pkg) => (
                  <li key={pkg}>
                    <button
                      type="button"
                      disabled={busy !== null}
                      onClick={() => void launch(pkg)}
                      className="flex min-h-11 w-full items-center justify-between gap-3 rounded-md px-2 text-start hover:bg-surface-2"
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-sm">{shortName(pkg)}</span>
                        <span className="block truncate font-mono text-xs text-faint" dir="ltr">
                          {pkg}
                        </span>
                      </span>
                      <span className="shrink-0 text-xs text-copper">تشغيل</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </aside>
      </div>
    </SiteFrame>
  );
}
