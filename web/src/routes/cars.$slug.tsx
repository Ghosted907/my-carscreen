import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import { CopyCode } from "@/components/copy-code";
import { SiteFrame } from "@/components/site-frame";
import { TodayStamp } from "@/components/today-stamp";
import { Button } from "@/components/ui/button";
import { cars, findCar } from "@/lib/cars";

export const Route = createFileRoute("/cars/$slug")({
  component: CarPage,
});

function CarPage() {
  const { slug } = Route.useParams();
  const known = cars.some((car) => car.slug === slug);
  const car = findCar(slug);

  return (
    <SiteFrame>
      <article className="mx-auto max-w-3xl px-4 py-10 md:px-6">
        {!known ? (
          <p className="mb-6 rounded-md border border-line bg-surface px-3 py-2 text-sm text-muted">
            ما لقينا هذا الموديل. هذي الخطوات العامة، والكود لازم تجيبه أنت.
          </p>
        ) : null}
        <p className="text-sm text-muted">{car.brand}</p>
        <h1 className="mt-1 text-4xl font-semibold tracking-tight">{car.name}</h1>
        <p className="mt-2 font-mono text-xs text-faint" dir="ltr">
          {car.years}
        </p>
        <p className="mt-4 text-lg text-muted">{car.summary}</p>

        <div className="mt-6 flex flex-wrap gap-3">
          <Button asChild size="lg">
            <Link to="/install" search={{ car: known ? car.slug : "other" }}>
              انتقل للتثبيت
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <Button asChild variant="line" size="lg">
            <Link to="/guides">الدليل</Link>
          </Button>
        </div>

        {car.codes.length > 0 ? (
          <div className="mt-8 space-y-2">
            {car.codes.map((code) => (
              <div key={code.value}>
                <CopyCode label={code.label} value={code.value} />
                <p className="mt-1 px-1 text-sm text-muted">{code.hint}</p>
              </div>
            ))}
          </div>
        ) : null}

        {car.datePassword ? (
          <div className="mt-4 rounded-lg border border-line bg-surface p-4">
            <p className="text-sm text-muted">كلمة مرور إعداد التشفير</p>
            <p className="mt-1 text-2xl">
              <TodayStamp />
            </p>
            <p className="mt-1 text-sm text-faint">
              سنة ثم شهر ثم يوم، حسب ساعة الشاشة. اليوم على جهازك ظاهر فوق، وإذا
              ساعة السيارة متأخرة اكتب تاريخها.
            </p>
          </div>
        ) : null}

        <ol className="mt-8 space-y-4">
          {car.steps.map((step, index) => (
            <li key={step.title} className="flex gap-4">
              <span
                className="font-mono text-sm text-copper"
                dir="ltr"
              >
                {String(index + 1).padStart(2, "0")}
              </span>
              <div>
                <h2 className="font-medium">{step.title}</h2>
                <p className="mt-1 text-sm text-muted">{step.body}</p>
              </div>
            </li>
          ))}
        </ol>

        <div className="mt-8 rounded-lg border border-line bg-surface p-4">
          <h2 className="font-medium">المنفذ</h2>
          <p className="mt-1 text-sm text-muted">{car.usb}</p>
        </div>
        <p className="mt-4 text-sm text-muted">{car.after}</p>

        <ul className="mt-10 flex flex-wrap gap-2">
          {cars
            .filter((item) => item.slug !== car.slug)
            .map((item) => (
              <li key={item.slug}>
                <Link
                  to="/cars/$slug"
                  params={{ slug: item.slug }}
                  className="inline-flex min-h-11 items-center rounded-md border border-line px-3 text-sm text-muted hover:border-copper hover:text-fg"
                >
                  {item.brand} {item.name}
                </Link>
              </li>
            ))}
        </ul>
      </article>
    </SiteFrame>
  );
}
