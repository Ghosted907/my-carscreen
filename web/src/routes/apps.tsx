import { createFileRoute, Link } from "@tanstack/react-router";
import { Download } from "lucide-react";
import { useMemo, useState } from "react";
import { SiteFrame } from "@/components/site-frame";
import { Button } from "@/components/ui/button";
import {
  catalog,
  catalogCategories,
  type CatalogCategory,
} from "@/lib/catalog";
import { cn } from "@/lib/cn";

export const Route = createFileRoute("/apps")({ component: AppsPage });

function AppsPage() {
  const [filter, setFilter] = useState<CatalogCategory | "all">("all");
  const items = useMemo(
    () => (filter === "all" ? catalog : catalog.filter((app) => app.category === filter)),
    [filter],
  );

  return (
    <SiteFrame>
      <div className="mx-auto max-w-6xl px-4 py-10 md:px-6">
        <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
          APK LIST
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">تطبيقات تنزّلها وتثبّتها</h1>
        <p className="mt-3 max-w-2xl text-sm text-muted">
          يوتيوب هنا بدائله بدون قوقل. نتفلكس وزيرو بلاي ما نرفع ملفاتهم: إما من
          المصدر المكتوب على البطاقة، أو ترفع الملف أنت في التثبيت. كاميرا ٣٦٠ الوكالة
          ما تنزل من برا.
        </p>
        <div className="mt-4">
          <Button asChild variant="line">
            <Link to="/install" search={{ car: "jetour-t1" }}>
              بعد التنزيل: ارفع الملف في التثبيت
            </Link>
          </Button>
        </div>

        <div className="mt-6 flex gap-2 overflow-x-auto pb-1">
          {catalogCategories.map((category) => (
            <button
              key={category.id}
              type="button"
              onClick={() => setFilter(category.id)}
              className={cn(
                "shrink-0 rounded-md border px-3 py-2 text-sm",
                filter === category.id
                  ? "border-copper bg-surface text-fg"
                  : "border-line text-muted",
              )}
            >
              {category.label}
            </button>
          ))}
        </div>

        <ul className="mt-4 grid gap-3 md:grid-cols-2">
          {items.map((app) => (
            <li key={app.id} className="flex flex-col rounded-lg border border-line bg-surface p-4">
              <div className="flex items-baseline justify-between gap-3">
                <h2 className="text-lg font-semibold">{app.name}</h2>
                <span className="font-mono text-xs text-faint" dir="ltr">
                  {app.version}
                </span>
              </div>
              <p className="mt-2 flex-1 text-sm text-muted">{app.summary}</p>
              <div className="mt-4 flex flex-wrap gap-2">
                {app.files.map((file) => (
                  <a
                    key={file.href}
                    href={file.href}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex min-h-11 items-center gap-2 rounded-md bg-copper px-4 text-sm font-medium text-ink hover:bg-copper-deep"
                  >
                    <Download className="size-4" />
                    {file.label}
                  </a>
                ))}
                {app.site ? (
                  <a
                    href={app.site.href}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex min-h-11 items-center rounded-md border border-line px-4 text-sm text-copper"
                  >
                    {app.site.label}
                  </a>
                ) : null}
                {app.files.length === 0 ? (
                  <Link
                    to="/install"
                    search={{ car: "jetour-t1" }}
                    className="inline-flex min-h-11 items-center rounded-md border border-line px-4 text-sm"
                  >
                    ارفع ملفك في التثبيت
                  </Link>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      </div>
    </SiteFrame>
  );
}
