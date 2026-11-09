import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, Cable, MonitorSmartphone, ShieldCheck, Usb } from "lucide-react";
import { CopyCode } from "@/components/copy-code";
import { SiteFrame } from "@/components/site-frame";
import { TodayStamp } from "@/components/today-stamp";
import { Button } from "@/components/ui/button";
import { cars } from "@/lib/cars";

export const Route = createFileRoute("/")({ component: Home });

const NEEDS = [
  {
    icon: MonitorSmartphone,
    title: "Chrome أو Edge",
    body: "التثبيت يتم داخل المتصفح. سفاري وآيفون ما يفتحون منفذ USB.",
  },
  {
    icon: Cable,
    title: "كابل بيانات",
    body: "USB-A إلى USB-A. كابل الشحن فقط يوصل كهرباء وما يظهر الجهاز.",
  },
  {
    icon: Usb,
    title: "ADB مفعّل",
    body: "مرة واحدة من القائمة الهندسية حسب موديلك، ثم تطلع منها.",
  },
  {
    icon: ShieldCheck,
    title: "موافقتك على الشاشة",
    body: "أول اتصال تطلع رسالة سماح على شاشة السيارة. بدونها ما يكمل.",
  },
];

function Home() {
  return (
    <SiteFrame>
      <section className="mx-auto grid max-w-6xl gap-8 px-4 py-10 md:grid-cols-12 md:px-6 md:py-16">
        <div className="md:col-span-7">
          <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
            NO BOX · USB · ADB
          </p>
          <h1 className="mt-3 max-w-xl text-4xl font-semibold leading-tight tracking-tight md:text-5xl">
            تطبيقات شاشة الوكالة، من المتصفح.
          </h1>
          <p className="mt-4 max-w-xl text-lg text-muted">
            توصّل شاشة جيتور — أو أي شاشة أندرويد بعد تفعيل التصحيح — بكابل،
            وترفع ملف التطبيق. بدون صندوق خارجي وبدون برنامج على الكمبيوتر.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link to="/install" search={{ car: "jetour-t1" }}>
                ابدأ التثبيت
                <ArrowLeft className="size-4" />
              </Link>
            </Button>
            <Button asChild variant="line" size="lg">
              <Link to="/apps">قائمة التطبيقات</Link>
            </Button>
            <Button asChild variant="line" size="lg">
              <Link to="/deck">قائمة الشاشة</Link>
            </Button>
          </div>
        </div>

        <aside className="rounded-xl border border-line bg-surface p-5 md:col-span-5">
          <div className="flex items-baseline justify-between gap-3">
            <p className="text-sm text-muted">ورقة شغل · جيتور T1</p>
            <p className="font-mono text-xs text-faint" dir="ltr">
              01
            </p>
          </div>
          <h2 className="mt-3 text-xl font-semibold">فعّل التصحيح ثم ارجع هنا</h2>
          <ol className="mt-4 space-y-3 text-sm text-muted">
            <li>من تطبيق الهاتف على الشاشة اطلب الكود، ثم زر الاتصال.</li>
            <li>ادخل إعداد التشفير. كلمة المرور تاريخ السيارة.</li>
            <li>شغّل ADB، اطلع، ووصّل الكابل من جهة السائق.</li>
          </ol>
          <div className="mt-4 space-y-2">
            <CopyCode label="كود شاشة T1" value="*#20241130#" />
            <div className="rounded-md border border-line bg-ink px-3 py-2">
              <p className="text-xs text-faint">كلمة مرور اليوم على ساعتك</p>
              <p className="text-lg">
                <TodayStamp />
              </p>
              <p className="text-xs text-faint">
                إذا ساعة السيارة مختلفة، اكتب تاريخها هي مو تاريخ الجوال.
              </p>
            </div>
          </div>
        </aside>
      </section>

      <section className="mx-auto max-w-6xl px-4 md:px-6">
        <h2 className="text-sm font-medium text-muted">تحتاج هالأشياء بس</h2>
        <ul className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {NEEDS.map((item) => (
            <li key={item.title} className="rounded-lg border border-line bg-surface p-4">
              <item.icon className="size-5 text-copper" strokeWidth={1.75} />
              <h3 className="mt-3 font-medium">{item.title}</h3>
              <p className="mt-1 text-sm text-muted">{item.body}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto mt-14 max-w-6xl px-4 md:px-6">
        <div className="flex items-end justify-between gap-4">
          <h2 className="text-2xl font-semibold">اختر الشاشة</h2>
          <Link to="/guides" className="text-sm text-copper">
            الدليل كامل
          </Link>
        </div>
        <ul className="mt-4 grid gap-3 md:grid-cols-2">
          {cars.map((car) => (
            <li key={car.slug}>
              <Link
                to="/cars/$slug"
                params={{ slug: car.slug }}
                className="flex h-full flex-col rounded-lg border border-line bg-surface p-5 hover:border-copper"
              >
                <div className="flex items-baseline justify-between gap-3">
                  <p className="text-sm text-muted">{car.brand}</p>
                  <p className="font-mono text-xs text-faint" dir="ltr">
                    {car.years}
                  </p>
                </div>
                <h3 className="mt-1 text-2xl font-semibold">{car.name}</h3>
                <p className="mt-2 flex-1 text-sm text-muted">{car.summary}</p>
                <p className="mt-4 text-sm text-copper">خطوات التفعيل</p>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto mt-14 grid max-w-6xl gap-3 px-4 md:grid-cols-2 md:px-6">
        <div className="rounded-lg border border-line p-5">
          <h2 className="text-lg font-semibold">يسويه</h2>
          <ul className="mt-3 space-y-2 text-sm text-muted">
            <li>يتصل بالشاشة عبر WebUSB بعد ما تفعّل ADB بنفسك.</li>
            <li>يثبّت ملف APK ترفعه، مع صلاحيات التشغيل وقت التثبيت.</li>
            <li>يعرض تطبيقات الطرف الثالث، ويقدر يشغّلها أو يحذفها.</li>
            <li>مفتاح الاتصال يبقى داخل متصفحك، ما ينرسل لسيرفر.</li>
          </ul>
        </div>
        <div className="rounded-lg border border-line p-5">
          <h2 className="text-lg font-semibold">ما يسويه</h2>
          <ul className="mt-3 space-y-2 text-sm text-muted">
            <li>ما يوزّع تطبيق يوتيوب الرسمي ولا نتفلكس. فيه بدائل مفتوحة في قائمة التحميل، ونتفلكس ترفعه أنت.</li>
            <li>ما يفتح القائمة الهندسية لوحده، ولا يخترع كود موديل ما نعرفه.</li>
            <li>ما يبرمج القير أو الوسائد أو كمبيوتر المحرك.</li>
            <li>ما يشتغل من سفاري أو من آيفون.</li>
          </ul>
        </div>
      </section>
    </SiteFrame>
  );
}
