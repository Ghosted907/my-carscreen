import { Link, useRouterState } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

const NAV = [
  { to: "/", label: "الرئيسية" },
  { to: "/install", label: "التثبيت", search: { car: "jetour-t1" } },
  { to: "/deck", label: "القائمة" },
  { to: "/cluster", label: "العدادات" },
  { to: "/apps", label: "التطبيقات" },
  { to: "/guides", label: "الدليل" },
  { to: "/faq", label: "أسئلة" },
] as const;

export function SiteFrame({ children }: { children: ReactNode }) {
  const path = useRouterState({ select: (s) => s.location.pathname });

  return (
    <div className="min-h-screen">
      <header className="border-b border-line bg-bg/90 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3 md:px-6">
          <Link to="/" className="flex items-center gap-3">
            <span className="grid size-9 place-items-center rounded-md border border-copper text-base font-semibold text-copper">
              أ
            </span>
            <span className="leading-tight">
              <span className="block text-base font-semibold tracking-tight">أفق</span>
              <span className="block font-mono text-xs uppercase tracking-widest text-faint">
                headunit
              </span>
            </span>
          </Link>
          <nav className="flex flex-1 flex-wrap items-center gap-1 md:justify-end">
            {NAV.map((item) => {
              const active =
                item.to === "/"
                  ? path === "/"
                  : path === item.to || path.startsWith(`${item.to}/`);
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  search={"search" in item ? item.search : undefined}
                  className={cn(
                    "rounded-md px-3 py-2 text-sm",
                    active ? "bg-surface-2 text-fg" : "text-muted hover:text-fg",
                  )}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>
      </header>
      <main>{children}</main>
      <footer className="mt-16 border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-8 text-sm text-muted md:px-6">
          <p>
            أفق أداة مستقلة. ليست تابعة لـ Jetour أو Chery أو أي وكيل. ما تعيد
            برمجة كمبيوتر السيارة، وتثبّت فقط ملفات APK التي ترفعها أنت.
          </p>
          <p className="font-mono text-xs text-faint" dir="ltr">
            AFAQ · browser ADB · no external box
          </p>
        </div>
      </footer>
    </div>
  );
}
